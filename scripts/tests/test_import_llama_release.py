#!/usr/bin/env python3
"""Adversarial staging tests. ELF headers below are structural fixtures, never executed."""
from __future__ import annotations

from contextlib import redirect_stderr, redirect_stdout
from dataclasses import replace
import hashlib
import io
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tarfile
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest import mock

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import import_llama_release as importer


def elf(*, machine=183, elf_class=2, endian=1, elf_type=3, version=1, marker=b"fixture"):
    header = bytearray(64)
    header[:7] = b"\x7fELF" + bytes((elf_class, endian, 1))
    struct.pack_into("<HHI", header, 16, elf_type, machine, version)
    struct.pack_into("<H", header, 52, 64)
    return bytes(header) + marker


def member(name, data=b"", kind=tarfile.REGTYPE, linkname=""):
    info = tarfile.TarInfo(name)
    info.type = kind
    info.mode = 0o755
    info.size = len(data) if kind == tarfile.REGTYPE else 0
    info.linkname = linkname
    return info, data


class Fixture(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="llama-import-test-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.stage = self.root / "jniLibs" / "arm64-v8a"
        self.archive = self.root / "release.tar.gz"
        self.manifest_path = self.root / "manifest.json"
        self.data = {"release/llama-server": elf(), "release/libbase.so": elf(marker=b"base")}
        self.raw = {
            "release": "test-release", "source_commit": "a" * 40,
            "archive_url": "https://example.invalid/release.tar.gz",
            "archive_size": 1, "archive_sha256": "0" * 64,
            "payloads": [
                {"member": name, "apk_name": apk, "description": "fixture",
                 "sha256": hashlib.sha256(self.data[name]).hexdigest()}
                for name, apk in (("release/llama-server", "libllama-server.so"),
                                  ("release/libbase.so", "libbase.so"))
            ],
            "excluded": ["librpc.so"],
        }
        self.make_archive()

    def load(self):
        self.manifest_path.write_text(json.dumps(self.raw), encoding="utf-8")
        self.manifest = importer.load_manifest(self.manifest_path)
        return self.manifest

    def make_archive(self, entries=None):
        if entries is None:
            # Reversed order and unrelated files must not affect allowlist selection.
            entries = [member("release", kind=tarfile.DIRTYPE),
                       member("release/librpc.so", b"excluded"),
                       *[member(name, data) for name, data in reversed(list(self.data.items()))]]
        with tarfile.open(self.archive, "w:gz") as tar:
            for info, data in entries:
                tar.addfile(info, io.BytesIO(data) if info.isreg() else None)
        self.raw["archive_size"] = self.archive.stat().st_size
        self.raw["archive_sha256"] = hashlib.sha256(self.archive.read_bytes()).hexdigest()
        return self.load()

    def import_good(self):
        importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assertEqual([], importer.verify_staging(self.stage, self.manifest))

    def snapshot(self):
        return {p.relative_to(self.stage).as_posix(): (p.read_bytes(), p.stat().st_mtime_ns)
                for p in self.stage.rglob("*") if p.is_file()}

    def assert_invalid(self, expected=""):
        errors = importer.verify_staging(self.stage, self.manifest)
        self.assertTrue(errors, "Invalid/interrupted stage was accepted")
        if expected:
            self.assertIn(expected.lower(), "\n".join(errors).lower())


class ManifestTests(Fixture):
    def test_committed_provenance_uses_full_commit(self):
        raw = json.loads((SCRIPTS.parent / "third_party/llama.cpp.json").read_text())
        self.assertEqual(40, len(raw["source_commit"]))

    def test_unsafe_member_paths(self):
        for path in ("/absolute", "../outside", "release/../outside", "C:/outside",
                     "C:outside", "\\server\\share", "release\\file", "release//file",
                     "release/./file", "", "release/file:stream", "release/file\x00x"):
            with self.subTest(path=path):
                raw = json.loads(json.dumps(self.raw))
                raw["payloads"][0]["member"] = path
                self.manifest_path.write_text(json.dumps(raw))
                with self.assertRaises(ValueError):
                    importer.load_manifest(self.manifest_path)

    def test_unsafe_apk_names(self):
        for name in ("../evil.so", "/evil.so", "nested/lib.so", "C:evil.so", "lib.so:stream",
                     "lib\\evil.so", "lib.so.", ".payload_receipt.json", "not-a-library"):
            with self.subTest(name=name):
                raw = json.loads(json.dumps(self.raw))
                raw["payloads"][0]["apk_name"] = name
                self.manifest_path.write_text(json.dumps(raw))
                with self.assertRaises(ValueError):
                    importer.load_manifest(self.manifest_path)

    def test_duplicate_member_mapping(self):
        self.raw["payloads"][1]["member"] = self.raw["payloads"][0]["member"]
        with self.assertRaisesRegex(ValueError, "[Dd]uplicate"):
            self.load()

    def test_duplicate_apk_mapping(self):
        self.raw["payloads"][1]["apk_name"] = self.raw["payloads"][0]["apk_name"].upper()
        with self.assertRaises(ValueError):
            self.load()

    def test_invalid_digest_size_commit_and_empty_allowlist(self):
        for key, value in (("archive_sha256", "bad"), ("archive_size", 0),
                           ("archive_size", True), ("source_commit", "short"), ("payloads", [])):
            with self.subTest(key=key):
                raw = dict(self.raw, **{key: value})
                self.manifest_path.write_text(json.dumps(raw))
                with self.assertRaises(ValueError):
                    importer.load_manifest(self.manifest_path)

    def test_invalid_payload_digest(self):
        self.raw["payloads"][0]["sha256"] = "not a digest"
        with self.assertRaises(ValueError):
            self.load()

    def test_duplicate_json_keys_rejected(self):
        self.manifest_path.write_text('{"release":"one","release":"two"}')
        with self.assertRaises(ValueError):
            importer.load_manifest(self.manifest_path)


class ArchiveTests(Fixture):
    def test_archive_size_and_digest_rejected_by_staging_itself(self):
        for change in ({"archive_size": self.manifest.archive_size + 1},
                       {"archive_sha256": "f" * 64}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                importer.stage_payloads(self.archive, self.stage, replace(self.manifest, **change))
        self.assertFalse(self.stage.exists())

    def test_standalone_archive_verification(self):
        importer.verify_archive(self.archive, self.manifest)
        self.archive.write_bytes(b"bad")
        with self.assertRaisesRegex(ValueError, "size mismatch"):
            importer.verify_archive(self.archive, self.manifest)

    def test_real_duplicate_member_after_selected_payload(self):
        entries = [member(n, d) for n, d in self.data.items()]
        self.make_archive(entries + [entries[0]])
        with self.assertRaisesRegex(ValueError, "[Dd]uplicate"):
            importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assertFalse(self.stage.exists())

    def test_duplicate_unselected_member(self):
        entries = [member(n, d) for n, d in self.data.items()]
        self.make_archive(entries + [member("unused", b"one"), member("unused", b"two")])
        with self.assertRaisesRegex(ValueError, "[Dd]uplicate"):
            importer.stage_payloads(self.archive, self.stage, self.manifest)

    def test_missing_payload(self):
        self.make_archive([member("release/libbase.so", self.data["release/libbase.so"])])
        with self.assertRaisesRegex(ValueError, "[Mm]issing"):
            importer.stage_payloads(self.archive, self.stage, self.manifest)

    def test_unsafe_unselected_paths_are_not_ignored(self):
        for path in ("../escape", "/escape", "C:/escape", "C:escape", "dir\\escape",
                     "release//evil", "release/./evil", "file:stream"):
            with self.subTest(path=path):
                self.make_archive([*[member(n, d) for n, d in self.data.items()], member(path)])
                with self.assertRaisesRegex(ValueError, "[Uu]nsafe"):
                    importer.stage_payloads(self.archive, self.stage, self.manifest)

    def test_links_and_special_files_in_selected_and_unselected_members(self):
        for kind in (tarfile.SYMTYPE, tarfile.LNKTYPE, tarfile.BLKTYPE, tarfile.CHRTYPE,
                     tarfile.FIFOTYPE, b"V"):
            for selected in (True, False):
                with self.subTest(kind=kind, selected=selected):
                    entries = [member("release/libbase.so", self.data["release/libbase.so"]),
                               member("release/llama-server", self.data["release/llama-server"])]
                    bad = member("release/llama-server" if selected else "unselected", kind=kind,
                                 linkname="release/libbase.so")
                    if selected:
                        entries[-1] = bad
                    else:
                        entries.append(bad)
                    self.make_archive(entries)
                    with self.assertRaises(ValueError):
                        importer.stage_payloads(self.archive, self.stage, self.manifest)

    def test_payload_checksum_mismatch(self):
        self.raw["payloads"][0]["sha256"] = "0" * 64
        self.load()
        with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
            importer.stage_payloads(self.archive, self.stage, self.manifest)

    def test_truncated_tar_is_diagnosed(self):
        self.archive.write_bytes(self.archive.read_bytes()[:30])
        self.raw["archive_size"] = self.archive.stat().st_size
        self.raw["archive_sha256"] = hashlib.sha256(self.archive.read_bytes()).hexdigest()
        self.load()
        with self.assertRaises((ValueError, tarfile.TarError, EOFError)):
            importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assertFalse(self.stage.exists())


class ElfAndReceiptTests(Fixture):
    def test_flat_allowlist_mapping_and_receipt(self):
        self.import_good()
        self.assertEqual({"libllama-server.so", "libbase.so", importer.RECEIPT_NAME},
                         {p.name for p in self.stage.iterdir()})
        for spec in self.manifest.payloads:
            self.assertEqual(self.data[spec.member], (self.stage / spec.apk_name).read_bytes())
        receipt = json.loads((self.stage / importer.RECEIPT_NAME).read_text())
        self.assertEqual(self.manifest.archive_sha256, receipt["archive_sha256"])
        self.assertEqual(self.manifest.archive_size, receipt["archive_size"])
        self.assertEqual("a" * 40, receipt["source_commit"])
        self.assertEqual({s.apk_name: {"member": s.member, "sha256": s.sha256}
                          for s in self.manifest.payloads}, receipt["payloads"])

    def test_wrong_elf_even_with_matching_digest(self):
        for data in (elf(machine=62), elf(elf_class=1), elf(endian=2), elf(elf_type=2),
                     elf(version=0), b"not ELF", elf()[:40]):
            with self.subTest(header=data[:24]):
                self.data["release/llama-server"] = data
                self.raw["payloads"][0]["sha256"] = hashlib.sha256(data).hexdigest()
                self.make_archive()
                with self.assertRaisesRegex(ValueError, "ELF"):
                    importer.stage_payloads(self.archive, self.stage, self.manifest)

    def test_verifier_checks_elf_not_just_matching_hash(self):
        self.import_good()
        bad = elf(machine=62)
        self.raw["payloads"][0]["sha256"] = hashlib.sha256(bad).hexdigest()
        self.load()
        (self.stage / "libllama-server.so").write_bytes(bad)
        receipt_path = self.stage / importer.RECEIPT_NAME
        receipt = json.loads(receipt_path.read_text())
        receipt["payloads"]["libllama-server.so"]["sha256"] = hashlib.sha256(bad).hexdigest()
        receipt_path.write_text(json.dumps(receipt))
        self.assert_invalid("ELF")

    def test_missing_malformed_and_tampered_receipt(self):
        self.import_good()
        receipt = self.stage / importer.RECEIPT_NAME
        original = receipt.read_bytes()
        receipt.unlink()
        self.assert_invalid("receipt")
        for content in (b"not JSON", b"{}", b"[]", b'{"schema_version":1,"schema_version":2}'):
            receipt.write_bytes(content)
            self.assert_invalid("receipt")
        receipt.write_bytes(original)
        self.assertEqual([], importer.verify_staging(self.stage, self.manifest))

    def test_receipt_must_match_all_provenance_and_mapping_fields(self):
        self.import_good()
        path = self.stage / importer.RECEIPT_NAME
        original = path.read_text()
        for key, value in (("archive_sha256", "f" * 64), ("archive_size", 123),
                           ("source_commit", "f" * 40), ("release", "other"),
                           ("schema_version", 999), ("payloads", {}), ("extra", True)):
            with self.subTest(key=key):
                receipt = json.loads(original)
                receipt[key] = value
                path.write_text(json.dumps(receipt))
                self.assert_invalid("receipt")

    def test_receipt_type_aliases_and_size_limit_are_rejected(self):
        self.import_good()
        path = self.stage / importer.RECEIPT_NAME
        receipt = json.loads(path.read_text())
        receipt["schema_version"] = True
        path.write_text(json.dumps(receipt))
        self.assert_invalid("receipt")
        path.write_bytes(b" " * ((1 << 20) + 1))
        self.assert_invalid("receipt")

    def test_missing_and_tampered_payload(self):
        self.import_good()
        path = self.stage / "libbase.so"
        path.unlink()
        self.assert_invalid("missing")
        path.write_bytes(b"tampered")
        self.assert_invalid("SHA-256")

    def test_unexpected_files_and_directories(self):
        self.import_good()
        for name, directory in (("librpc.so", False), ("leftover.tmp", False), ("nested", True)):
            path = self.stage / name
            path.mkdir() if directory else path.write_text("unexpected")
            self.assert_invalid("unexpected")
            path.rmdir() if directory else path.unlink()

    def test_payload_directory_not_followed(self):
        self.import_good()
        path = self.stage / "libbase.so"
        path.unlink()
        path.mkdir()
        self.assert_invalid("regular file")

    def test_link_detection_is_fail_closed_before_reading(self):
        self.import_good()
        original = Path.is_symlink
        for target in (self.stage, self.stage / "libbase.so", self.stage / importer.RECEIPT_NAME):
            with self.subTest(target=target), mock.patch.object(
                Path, "is_symlink", lambda p: p == target or original(p)
            ):
                self.assert_invalid("link")

    def test_windows_reparse_metadata_is_rejected_without_following_target(self):
        with mock.patch.object(Path, "is_symlink", return_value=False), \
                mock.patch.object(Path, "lstat", return_value=SimpleNamespace(st_file_attributes=0x400)):
            self.assertTrue(importer._linked(self.stage))

    def test_verification_does_not_write_or_repair(self):
        self.import_good()
        before = self.snapshot()
        with mock.patch.object(importer, "stage_payloads", side_effect=AssertionError("No import")), \
                mock.patch.object(Path, "mkdir", side_effect=AssertionError("No mutation")):
            self.assertEqual([], importer.verify_staging(self.stage, self.manifest))
        self.assertEqual(before, self.snapshot())
        (self.stage / "libbase.so").write_bytes(b"bad")
        bad_before = self.snapshot()
        self.assert_invalid()
        self.assertEqual(bad_before, self.snapshot())

    def test_verification_missing_directory_does_not_create_it(self):
        self.assert_invalid("directory")
        self.assertFalse(self.stage.parent.exists())


class TransactionTests(Fixture):
    def test_failed_archive_validation_invalidates_old_stage_but_preserves_bytes(self):
        self.import_good()
        before = self.snapshot()
        self.archive.write_bytes(b"bad archive")
        with self.assertRaises(ValueError):
            importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assertEqual(before, self.snapshot())
        self.assert_invalid("incomplete")
        self.make_archive()
        self.import_good()  # A complete retry clears the persistent failure marker.

    def test_missing_archive_invalidates_old_stage(self):
        self.import_good()
        self.archive.unlink()
        with self.assertRaises(OSError):
            importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assert_invalid("incomplete")

    def test_partial_write_failure_is_not_published(self):
        self.import_good()
        before = self.snapshot()
        real = importer._stage_member
        calls = 0
        def partial(*args, **kwargs):
            nonlocal calls
            calls += 1
            if calls == 2:
                (args[2] / args[3].apk_name).write_bytes(b"partial payload")
                raise OSError("simulated full disk during copy")
            return real(*args, **kwargs)
        with mock.patch.object(importer, "_stage_member", side_effect=partial):
            with self.assertRaisesRegex(OSError, "full disk"):
                importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assertEqual(before, self.snapshot())
        self.assert_invalid("incomplete")
        self.assertFalse(list(self.stage.parent.glob("*.candidate-*")))

    def test_receipt_write_failure_preserves_old_stage(self):
        self.import_good()
        before = self.snapshot()
        with mock.patch.object(importer, "write_receipt", side_effect=OSError("receipt disk full")):
            with self.assertRaisesRegex(OSError, "receipt disk full"):
                importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assertEqual(before, self.snapshot())
        self.assert_invalid("incomplete")

    def test_rename_failure_rolls_back_old_stage(self):
        self.import_good()
        before = self.snapshot()
        rename = Path.rename
        def fail_candidate(path, target):
            if ".candidate-" in path.name:
                raise OSError("publish failed")
            return rename(path, target)
        with mock.patch.object(Path, "rename", fail_candidate):
            with self.assertRaisesRegex(OSError, "publish failed"):
                importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assertEqual(before, self.snapshot())
        self.assert_invalid("incomplete")
        self.import_good()

    def test_failed_first_import_leaves_no_accepted_stage(self):
        with mock.patch.object(importer, "_stage_member", side_effect=OSError("disk full")):
            with self.assertRaises(OSError):
                importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assertFalse(self.stage.exists())
        self.assert_invalid("incomplete")
        self.assertFalse(list(self.stage.parent.glob("*.candidate-*")))
        self.import_good()

    def test_rollback_failure_keeps_backup_and_blocks_verification(self):
        self.import_good()
        before = self.snapshot()
        rename = Path.rename
        def fail_publish_and_rollback(path, target):
            if ".candidate-" in path.name or path.name.endswith(".previous"):
                raise OSError("rename unavailable")
            return rename(path, target)
        with mock.patch.object(Path, "rename", fail_publish_and_rollback):
            with self.assertRaises((OSError, ValueError)):
                importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assert_invalid("incomplete")
        backup = self.stage.parent / ("." + self.stage.name + ".previous")
        self.assertTrue(backup.is_dir())
        self.assertEqual(before["libbase.so"][0], (backup / "libbase.so").read_bytes())
        self.import_good()

    def test_failure_clearing_marker_keeps_new_stage_unaccepted_until_retry(self):
        self.import_good()
        unlink = Path.unlink
        def fail_marker(path, *args, **kwargs):
            if path.name.endswith(".importing"):
                raise OSError("marker cannot be cleared")
            return unlink(path, *args, **kwargs)
        with mock.patch.object(Path, "unlink", fail_marker):
            with self.assertRaisesRegex(OSError, "marker cannot be cleared"):
                importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assert_invalid("incomplete")
        self.import_good()

    def test_non_stage_directory_is_not_deleted(self):
        self.stage.mkdir(parents=True)
        sentinel = self.stage / "user-file.txt"
        sentinel.write_text("preserve")
        with self.assertRaisesRegex(ValueError, "Refusing to replace non-stage"):
            importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assertEqual("preserve", sentinel.read_text())

    def test_keyboard_interrupt_leaves_stage_unaccepted(self):
        self.import_good()
        before = self.snapshot()
        with mock.patch.object(importer, "_stage_member", side_effect=KeyboardInterrupt):
            with self.assertRaises(KeyboardInterrupt):
                importer.stage_payloads(self.archive, self.stage, self.manifest)
        self.assertEqual(before, self.snapshot())
        self.assert_invalid("incomplete")
        self.import_good()

    def test_actual_process_exit_after_backup_rename_and_retry(self):
        self.import_good()
        before = self.snapshot()
        code = '''
import os, sys
from pathlib import Path
sys.path.insert(0, sys.argv[1])
import import_llama_release as i
stage = Path(sys.argv[3])
original = Path.rename
def crash(path, target):
    result = original(path, target)
    if path == stage:
        os._exit(17)
    return result
Path.rename = crash
i.stage_payloads(Path(sys.argv[2]), stage, i.load_manifest(Path(sys.argv[4])))
'''
        result = subprocess.run([sys.executable, "-c", code, str(SCRIPTS), str(self.archive),
                                 str(self.stage), str(self.manifest_path)], capture_output=True, timeout=10)
        self.assertEqual(17, result.returncode, result.stderr.decode())
        self.assert_invalid("incomplete")
        self.import_good()
        self.assertEqual({k: v[0] for k, v in before.items()},
                         {k: v[0] for k, v in self.snapshot().items()})

    def test_concurrent_import_is_rejected_without_stealing_lock(self):
        self.import_good()
        ready = self.root / "lock-ready"
        code = '''
import sys,time
from pathlib import Path
sys.path.insert(0,sys.argv[1])
import import_llama_release as i
with i._import_lock(Path(sys.argv[2])):
    Path(sys.argv[3]).touch()
    time.sleep(20)
'''
        child = subprocess.Popen([sys.executable, "-c", code, str(SCRIPTS), str(self.stage), str(ready)])
        try:
            deadline = time.monotonic() + 5
            while not ready.exists() and child.poll() is None and time.monotonic() < deadline:
                time.sleep(.02)
            self.assertTrue(ready.exists(), "Child did not acquire import lock")
            with self.assertRaisesRegex(ValueError, "[Aa]nother import"):
                importer.stage_payloads(self.archive, self.stage, self.manifest)
        finally:
            child.terminate()
            child.wait(timeout=5)
        self.import_good()  # OS lock is released even by termination.

    def test_marker_appearing_during_verification_is_detected(self):
        self.import_good()
        real = importer.sha256_file
        marker = self.stage.parent / ("." + self.stage.name + ".importing")
        def hash_then_mark(path):
            value = real(path)
            marker.write_text("interrupted")
            return value
        with mock.patch.object(importer, "sha256_file", side_effect=hash_then_mark):
            self.assert_invalid("incomplete")

    def test_backup_without_marker_is_never_accepted(self):
        self.import_good()
        backup = self.stage.parent / ("." + self.stage.name + ".previous")
        backup.mkdir()
        self.assert_invalid("incomplete")


class CliTests(Fixture):
    def cli(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.object(sys, "argv", ["import_llama_release.py", *map(str, args)]), \
                mock.patch.object(importer, "load_manifest", return_value=self.manifest), \
                redirect_stdout(out), redirect_stderr(err):
            result = importer.main()
        return result, out.getvalue(), err.getvalue()

    def test_default_stage_is_abi_specific(self):
        self.assertEqual("arm64-v8a", importer.DEFAULT_STAGING.name)
        self.assertEqual("jniLibs", importer.DEFAULT_STAGING.parent.name)

    def test_import_and_verify_cli(self):
        self.assertEqual(0, self.cli(self.archive, "--staging-dir", self.stage)[0])
        self.assertEqual(0, self.cli("--verify-only", "--staging-dir", self.stage)[0])

    def test_import_failure_is_nonzero_without_traceback(self):
        self.archive.write_bytes(b"bad")
        status, _, err = self.cli(self.archive, "--staging-dir", self.stage)
        self.assertEqual(1, status)
        self.assertIn("mismatch", err)
        self.assertNotIn("Traceback", err)

    def test_interrupted_cli_returns_130_and_leaves_stage_invalid(self):
        with mock.patch.object(importer, "_stage_member", side_effect=KeyboardInterrupt):
            status, _, err = self.cli(self.archive, "--staging-dir", self.stage)
        self.assertEqual(130, status)
        self.assertIn("interrupted", err)
        self.assert_invalid("incomplete")

    def test_verify_failure_is_nonzero_without_mutation(self):
        status, _, err = self.cli("--verify-only", "--staging-dir", self.stage)
        self.assertEqual(1, status)
        self.assertIn("directory", err)
        self.assertFalse(self.stage.parent.exists())

    def test_verify_rejects_positional_directory(self):
        with self.assertRaises(SystemExit) as error:
            self.cli("--verify-only", self.stage)
        self.assertEqual(2, error.exception.code)


if __name__ == "__main__":
    unittest.main()
