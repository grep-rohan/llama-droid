#!/usr/bin/env python3
"""Opt-in real Gradle/APK integration checks, separate from fast host discovery.

Requires the documented JDK/SDK and a verified real native stage. Run:
    python scripts/tests/check_native_packaging.py -v

Only a temporary project copy is mutated. Logs remain in .cache/blocker3/.
No device, native process, network download or fake ELF execution is involved.
"""
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]


class NativePackagingTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cache = ROOT / ".cache"
        cache.mkdir(exist_ok=True)
        cls.logs = cache / "blocker3"
        cls.logs.mkdir(exist_ok=True)
        cls.temporary = tempfile.TemporaryDirectory(prefix="packaging-check-", dir=cache)
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.project = Path(cls.temporary.name)
        for filename in ("settings.gradle.kts", "build.gradle.kts", "gradle.properties", "gradlew", "gradlew.bat"):
            shutil.copy2(ROOT / filename, cls.project / filename)
        for directory in ("gradle", "third_party", "scripts"):
            shutil.copytree(ROOT / directory, cls.project / directory,
                            ignore=shutil.ignore_patterns("__pycache__"))
        (cls.project / "app").mkdir()
        shutil.copy2(ROOT / "app/build.gradle.kts", cls.project / "app/build.gradle.kts")
        shutil.copytree(ROOT / "app/src", cls.project / "app/src")
        cls.stage = cls.project / "app/src/main/jniLibs/arm64-v8a"
        cls.apk = cls.project / "app/build/outputs/apk/debug/app-debug.apk"
        cls.manifest = json.loads((cls.project / "third_party/llama.cpp.json").read_text(encoding="utf-8"))
        cls.build("baseline")

    @classmethod
    def build(cls, label, *extra, task=":app:assembleDebug", success=True):
        wrapper = ([str(cls.project / "gradlew.bat")] if os.name == "nt"
                   else ["bash", str(cls.project / "gradlew")])
        result = subprocess.run(wrapper + ["--no-daemon", "--console=plain", task, *extra],
                                cwd=cls.project, capture_output=True, text=True,
                                encoding="utf-8", errors="replace", timeout=180)
        output = result.stdout + result.stderr
        (cls.logs / (label + ".log")).write_text(output, encoding="utf-8")
        if (result.returncode == 0) != success:
            raise AssertionError(f"{label}: unexpected exit {result.returncode}; see {cls.logs / (label + '.log')}\n"
                                 + output[-5000:])
        return output

    @contextmanager
    def saved_file(self, path):
        content = path.read_bytes()
        try:
            yield path
        finally:
            path.write_bytes(content)

    def rejected(self, label, detail, *, task=":app:assembleDebug"):
        output = self.build(label, task=task, success=False)
        self.assertIn("Native stage verification failed", output)
        self.assertIn(detail.lower(), output.lower())

    def assert_apk(self):
        with zipfile.ZipFile(self.apk) as apk:
            expected = {"lib/arm64-v8a/" + p["apk_name"] for p in self.manifest["payloads"]}
            self.assertEqual(expected, {n for n in apk.namelist() if n.startswith("lib/")})
            self.assertFalse(any(n.lower().endswith(".gguf") for n in apk.namelist()))
            self.assertFalse(any(n.endswith(".payload_receipt.json") for n in apk.namelist()))
            for payload in self.manifest["payloads"]:
                path = "lib/arm64-v8a/" + payload["apk_name"]
                self.assertEqual(payload["sha256"], hashlib.sha256(apk.read(path)).hexdigest(), path)
                self.assertEqual(zipfile.ZIP_DEFLATED, apk.getinfo(path).compress_type, path)
            for source, destination in (("llama.cpp-LICENSE", "THIRD_PARTY_LICENSE.txt"),
                                        ("NOTICE.md", "THIRD_PARTY_NOTICES.txt")):
                self.assertEqual((self.project / "third_party" / source).read_bytes(),
                                 apk.read("assets/" + destination))

        # Exercise the shipped CLI against the real APK in the disposable project,
        # in addition to the independent ZIP assertions above. No SDK claims here.
        audit = subprocess.run([sys.executable, str(self.project / "scripts/verify_apk.py"), str(self.apk)],
                               cwd=self.project, capture_output=True, text=True,
                               encoding="utf-8", errors="replace", timeout=60)
        (self.logs / "standalone-audit.log").write_text(audit.stdout + audit.stderr, encoding="utf-8")
        self.assertEqual(0, audit.returncode, audit.stdout + audit.stderr)
        self.assertIn("ZIP/payload audit passed", audit.stdout)
        self.assertIn("not verified", audit.stdout)

    def test_exact_native_bytes_attribution_and_no_unrelated_payload(self):
        self.build("exact-payload")
        self.assert_apk()

    def test_absent_stage_rejected_even_with_existing_apk(self):
        saved = self.project / "saved-stage"
        self.stage.rename(saved)
        try:
            self.rejected("absent-stage", "directory")
        finally:
            saved.rename(self.stage)

    def test_modified_payload_rejected(self):
        with self.saved_file(self.stage / "libllama-server.so") as path:
            path.write_bytes(b"tampered payload")
            self.rejected("modified-payload", "SHA-256")

    def test_extra_native_file_rejected(self):
        extra = self.stage / "librpc.so"
        try:
            extra.write_text("unexpected fixture", encoding="utf-8")
            self.rejected("extra-file", "Unexpected")
        finally:
            extra.unlink()

    def test_unexpected_directory_rejected(self):
        extra = self.stage / "nested"
        try:
            extra.mkdir()
            self.rejected("extra-directory", "Unexpected")
        finally:
            extra.rmdir()

    def test_missing_receipt_rejected(self):
        with self.saved_file(self.stage / ".payload_receipt.json") as path:
            path.unlink()
            self.rejected("missing-receipt", "receipt")

    def test_interrupted_import_rejected(self):
        marker = self.stage.parent / ".arm64-v8a.importing"
        try:
            marker.write_text("interrupted fixture", encoding="utf-8")
            self.rejected("incomplete-import", "Incomplete")
        finally:
            marker.unlink()

    def test_missing_python_fails_closed(self):
        output = self.build("missing-python", "-PnativeVerifier.python=missing-native-verifier-python", success=False)
        self.assertIn("Native stage verification failed", output)
        self.assertIn("missing-native-verifier-python", output)

    def test_missing_attribution_fails_closed(self):
        with self.saved_file(self.project / "third_party/NOTICE.md") as path:
            path.unlink()
            self.rejected("missing-attribution", "attribution")

    def test_direct_package_task_cannot_bypass_verification(self):
        with self.saved_file(self.stage / "libllama-server.so") as path:
            path.write_bytes(b"tampered direct package fixture")
            self.rejected("direct-package", "SHA-256", task=":app:packageDebug")

    def test_unchanged_build_still_runs_python_verification(self):
        output = self.build("unchanged-build")
        self.assertIn("Verification passed: exact payloads", output)
        self.assertNotIn(":app:verifyNativeStage UP-TO-DATE", output)
        self.assertNotIn(":app:verifyNativeStage SKIPPED", output)

    def test_only_verified_abi_is_packaged_and_generated_tampering_is_repaired(self):
        # Transaction leftovers/other ABI siblings must not become packaging inputs.
        sibling = self.stage.parent / "x86_64"
        orphan = self.stage.parent / ".arm64-v8a.candidate-orphan"
        sibling.mkdir()
        orphan.mkdir()
        try:
            (sibling / "libunexpected.so").write_text("excluded fixture", encoding="utf-8")
            (orphan / "librpc.so").write_text("excluded fixture", encoding="utf-8")
            generated = self.project / "app/build/generated/verifiedNative/arm64-v8a/libllama-server.so"
            self.assertTrue(generated.is_file())
            generated.write_bytes(b"tampered generated output")
            self.build("generated-repair-and-siblings")
            self.assert_apk()
        finally:
            shutil.rmtree(sibling)
            shutil.rmtree(orphan)


if __name__ == "__main__":
    unittest.main()
