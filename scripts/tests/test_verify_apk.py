#!/usr/bin/env python3
"""Adversarial ZIP fixtures only: no upstream native binaries, models or SDK needed."""
from contextlib import redirect_stderr, redirect_stdout
from dataclasses import replace
import hashlib
import io
from pathlib import Path
import stat
import struct
import sys
import tempfile
import unittest
from unittest import mock
import warnings
import zipfile

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import verify_apk as auditor
from import_llama_release import Manifest, PayloadSpec


def elf(machine=183):
    header = bytearray(64)
    header[:7] = b"\x7fELF\x02\x01\x01"
    struct.pack_into("<HHI", header, 16, 3, machine, 1)
    struct.pack_into("<H", header, 52, 64)
    return bytes(header) + b"structural fixture, never executed"


class ApkAuditTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="apk-audit-test-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.apk = self.root / "fixture.apk"
        self.attribution = self.root / "third_party"
        self.attribution.mkdir()
        self.license = b"fixture license\n"
        self.notice = b"fixture notice\n"
        (self.attribution / "llama.cpp-LICENSE").write_bytes(self.license)
        (self.attribution / "NOTICE.md").write_bytes(self.notice)
        self.native = {"libllama-server.so": elf(), "libbase.so": elf() + b"base"}
        self.manifest = Manifest(
            "https://example.invalid/archive.tar.gz", 123, "a" * 64,
            [PayloadSpec("release/" + n, n, "fixture", hashlib.sha256(b).hexdigest())
             for n, b in self.native.items()],
            ["libggml-rpc.so", "llama-cli", "ggml-rpc-server"], "fixture", "b" * 40,
        )
        self.files = [("lib/arm64-v8a/" + n, b) for n, b in self.native.items()] + [
            # Deliberately not UTF-8. ZIP audit must not decode binary XML.
            ("AndroidManifest.xml", b"\x03\x00\x08\x00\xff\xb0binary XML fixture"),
            ("classes.dex", b"dex\n035\x00fixture"),
            ("assets/THIRD_PARTY_LICENSE.txt", self.license),
            ("assets/THIRD_PARTY_NOTICES.txt", self.notice),
        ]

    def make(self, files=None):
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)  # Intentional duplicate ZIP entries.
            with zipfile.ZipFile(self.apk, "w", compression=zipfile.ZIP_DEFLATED) as z:
                for name, data in self.files if files is None else files:
                    z.writestr(name, data)

    def audit(self):
        return auditor.audit_apk(self.apk, self.manifest, self.attribution)

    def rejected(self, detail):
        result = self.audit()
        self.assertFalse(result.passed)
        self.assertIn(detail.lower(), "\n".join(result.errors).lower())
        return result

    def changing(self, name, data):
        return [(n, data if n == name else b) for n, b in self.files]

    def central_offset(self, raw, filename):
        offset = 0
        while True:
            offset = raw.find(b"PK\x01\x02", offset)
            self.assertNotEqual(-1, offset)
            length = struct.unpack_from("<H", raw, offset + 28)[0]
            if raw[offset + 46:offset + 46 + length] == filename.encode():
                return offset
            offset += 4

    def test_accepts_binary_manifest_but_does_not_claim_sdk_validation(self):
        self.make()
        result = self.audit()
        self.assertTrue(result.passed, result.errors)
        self.assertTrue(any("SDK" in warning for warning in result.warnings))
        self.assertEqual(hashlib.sha256(self.apk.read_bytes()).hexdigest(), result.apk_sha256)
        self.assertEqual(self.apk.stat().st_size, result.apk_size)

    def test_missing_apk(self):
        self.rejected("APK")

    def test_directory_instead_of_apk(self):
        self.apk.mkdir()
        self.rejected("APK")

    def test_non_zip_and_truncated_zip(self):
        self.apk.write_bytes(b"not a ZIP")
        self.rejected("ZIP")
        self.make()
        self.apk.write_bytes(self.apk.read_bytes()[:40])
        self.rejected("ZIP")

    def test_self_extracting_prefix_rejected(self):
        self.make()
        self.apk.write_bytes(b"\x7fELFextra-prefix" + self.apk.read_bytes())
        self.rejected("ZIP")

    def test_missing_native(self):
        self.make([(n, b) for n, b in self.files if not n.endswith("libbase.so")])
        self.rejected("Missing native")

    def test_extra_native(self):
        self.make(self.files + [("lib/arm64-v8a/libggml-rpc.so", elf())])
        self.rejected("Unexpected native")

    def test_wrong_abi(self):
        self.make(self.files + [("lib/x86_64/libbase.so", elf(62))])
        self.rejected("ABI")

    def test_empty_wrong_abi_directory(self):
        self.make(self.files + [("lib/x86_64/", b"")])
        self.rejected("ABI")

    def test_single_native_never_enables_monolithic_mode(self):
        self.make([(n, b) for n, b in self.files if not n.endswith("libbase.so")])
        self.rejected("libbase.so")

    def test_changed_native_digest(self):
        self.make(self.changing("lib/arm64-v8a/libbase.so", elf() + b"changed"))
        self.rejected("SHA-256")

    def test_wrong_elf_even_when_fixture_digest_matches(self):
        path = "lib/arm64-v8a/libbase.so"
        bad = elf(62)
        self.make(self.changing(path, bad))
        self.manifest = replace(self.manifest, payloads=[
            replace(p, sha256=hashlib.sha256(bad).hexdigest()) if p.apk_name == "libbase.so" else p
            for p in self.manifest.payloads])
        self.rejected("ELF")

    def test_native_must_retain_legacy_compression(self):
        name = "lib/arm64-v8a/libbase.so"
        stored = zipfile.ZipInfo(name)
        stored.compress_type = zipfile.ZIP_STORED
        self.make([(stored if n == name else n, b) for n, b in self.files])
        self.rejected("compression")

    def test_missing_and_empty_manifest(self):
        self.make([(n, b) for n, b in self.files if n != "AndroidManifest.xml"])
        self.rejected("AndroidManifest.xml")
        self.make(self.changing("AndroidManifest.xml", b""))
        self.rejected("AndroidManifest.xml")

    def test_missing_attribution_asset(self):
        self.make([(n, b) for n, b in self.files if n != "assets/THIRD_PARTY_LICENSE.txt"])
        self.rejected("attribution")

    def test_changed_or_empty_attribution_asset(self):
        for value in (b"changed license", b""):
            with self.subTest(value=value):
                self.make(self.changing("assets/THIRD_PARTY_LICENSE.txt", value))
                self.rejected("attribution")

    def test_missing_or_empty_attribution_source(self):
        self.make()
        source = self.attribution / "NOTICE.md"
        source.unlink()
        self.rejected("attribution")
        source.write_bytes(b"")
        self.rejected("attribution")

    def test_gguf_extension_case_insensitive(self):
        self.make(self.files + [("assets/MODEL.GGUF", b"not even real weights")])
        self.rejected("GGUF")

    def test_renamed_gguf_magic_rejected(self):
        self.make(self.files + [("assets/renamed.bin", b"GGUF\x03\x00fixture")])
        self.rejected("GGUF")

    def test_renamed_elf_and_shared_library_outside_allowlist(self):
        for name, data in (("assets/renamed.bin", elf()), ("assets/libbackend.so", b"not ELF")):
            with self.subTest(name=name):
                self.make(self.files + [(name, data)])
                self.rejected("native")

    def test_excluded_utility_names_rejected_without_elf_magic(self):
        for name in ("assets/llama-cli", "ggml-rpc-server", "assets/llama-server"):
            with self.subTest(name=name):
                self.make(self.files + [(name, b"utility fixture")])
                self.rejected("utility")

    def test_stage_receipt_and_transaction_controls_rejected(self):
        for name in ("assets/.payload_receipt.json", "assets/.arm64-v8a.importing",
                     "assets/.arm64-v8a.previous/file", "assets/.arm64-v8a.lock",
                     "assets/.arm64-v8a.candidate-stale/file"):
            with self.subTest(name=name):
                self.make(self.files + [(name, b"stage fixture")])
                self.rejected("staging")

    def test_duplicate_native_even_when_bytes_identical(self):
        self.make(self.files + [self.files[0]])
        self.rejected("Duplicate")

    def test_duplicate_manifest_rejected(self):
        self.make(self.files + [("AndroidManifest.xml", b"other manifest")])
        self.rejected("Duplicate")

    def test_case_alias_duplicate_rejected(self):
        self.make(self.files + [("assets/Case.txt", b"one"), ("assets/case.txt", b"two")])
        self.rejected("Duplicate")

    def test_unsafe_member_names_rejected(self):
        for name in ("../escape", "/absolute", "C:/escape", "assets\\escape", "assets//double",
                     "assets/./dot", "assets/file:stream", "assets/trailing."):
            with self.subTest(name=name):
                self.make(self.files + [(name, b"unsafe fixture")])
                if "\\" in name:
                    # Windows ZipInfo normalizes separators when writing. Patch
                    # the actual headers so this really tests a backslash ZIP name.
                    self.apk.write_bytes(self.apk.read_bytes().replace(
                        name.replace("\\", "/").encode(), name.encode()))
                self.rejected("Unsafe")

    def test_nul_name_alias_rejected(self):
        self.make(self.files + [("assets/nulXmember", b"fixture")])
        self.apk.write_bytes(self.apk.read_bytes().replace(b"assets/nulXmember", b"assets/nul\x00member"))
        self.rejected("Unsafe")

    def test_links_and_special_entries_rejected(self):
        for kind in (stat.S_IFLNK, stat.S_IFIFO, stat.S_IFCHR, stat.S_IFBLK, stat.S_IFSOCK):
            with self.subTest(kind=kind):
                info = zipfile.ZipInfo("assets/special")
                info.create_system = 3
                info.external_attr = (kind | 0o600) << 16
                self.make(self.files + [(info, b"special fixture")])
                self.rejected("special")

    def test_valid_empty_directory_entries(self):
        self.make(self.files + [("lib/", b""), ("lib/arm64-v8a/", b""), ("assets/", b"")])
        result = self.audit()
        self.assertTrue(result.passed, result.errors)

    def test_nonempty_directory_rejected(self):
        self.make(self.files + [("assets/nonempty/", b"not a directory body")])
        self.rejected("directory")

    def test_directory_type_disagreement_rejected(self):
        for name, kind in (("assets/not-dir", stat.S_IFDIR), ("assets/not-file/", stat.S_IFREG)):
            with self.subTest(name=name):
                info = zipfile.ZipInfo(name)
                info.external_attr = (kind | 0o700) << 16
                self.make(self.files + [(info, b"")])
                self.rejected("directory")

    def test_bad_directory_crc_rejected_without_inflation(self):
        self.make(self.files + [("assets/empty/", b"")])
        with zipfile.ZipFile(self.apk) as z:
            local = z.getinfo("assets/empty/").header_offset
        raw = bytearray(self.apk.read_bytes())
        central = self.central_offset(raw, "assets/empty/")
        struct.pack_into("<I", raw, local + 14, 1)
        struct.pack_into("<I", raw, central + 16, 1)
        self.apk.write_bytes(raw)
        self.rejected("directory")

    def test_multidisk_and_zip64_metadata_rejected(self):
        self.make()
        original = self.apk.read_bytes()
        for zip64 in (False, True):
            with self.subTest(zip64=zip64):
                raw = bytearray(original)
                end = raw.rfind(b"PK\x05\x06")
                if zip64:
                    struct.pack_into("<HH", raw, end + 8, 0xFFFF, 0xFFFF)
                else:
                    struct.pack_into("<H", raw, end + 4, 1)
                self.apk.write_bytes(raw)
                self.rejected("ZIP")

    def test_zip64_locator_with_maximum_comment_rejected_before_zip_parsing(self):
        self.make()
        with zipfile.ZipFile(self.apk, "a") as z:
            z.comment = b"x" * 65_535
        valid = self.audit()
        self.assertTrue(valid.passed, valid.errors)
        raw = bytearray(self.apk.read_bytes())
        end = raw.rfind(b"PK\x05\x06")
        raw[end - 20:end - 16] = b"PK\x06\x07"
        self.apk.write_bytes(raw)
        with mock.patch.object(zipfile, "ZipFile", side_effect=AssertionError("Must reject in preflight")):
            self.rejected("ZIP64")

    def test_encrypted_entry_flag_rejected(self):
        self.make()
        with zipfile.ZipFile(self.apk) as z:
            local = z.getinfo("classes.dex").header_offset
        raw = bytearray(self.apk.read_bytes())
        central = self.central_offset(raw, "classes.dex")
        struct.pack_into("<H", raw, local + 6, 1)
        struct.pack_into("<H", raw, central + 8, 1)
        self.apk.write_bytes(raw)
        self.rejected("encrypted")

    def test_unsupported_compression_rejected(self):
        self.make()
        with zipfile.ZipFile(self.apk) as z:
            local = z.getinfo("classes.dex").header_offset
        raw = bytearray(self.apk.read_bytes())
        central = self.central_offset(raw, "classes.dex")
        struct.pack_into("<H", raw, local + 8, 99)
        struct.pack_into("<H", raw, central + 10, 99)
        self.apk.write_bytes(raw)
        self.rejected("compression")

    def test_corrupt_crc_in_non_native_entry_rejected(self):
        info = zipfile.ZipInfo("assets/crc.bin")
        info.compress_type = zipfile.ZIP_STORED
        self.make(self.files + [(info, b"non-native fixture")])
        with zipfile.ZipFile(self.apk) as z:
            local = z.getinfo("assets/crc.bin").header_offset
        raw = bytearray(self.apk.read_bytes())
        lengths = struct.unpack_from("<HH", raw, local + 26)
        raw[local + 30 + sum(lengths)] ^= 1
        self.apk.write_bytes(raw)
        self.rejected("CRC")

    def test_false_declared_uncompressed_size_rejected(self):
        self.make()
        raw = bytearray(self.apk.read_bytes())
        central = self.central_offset(raw, "classes.dex")
        size = struct.unpack_from("<I", raw, central + 24)[0]
        struct.pack_into("<I", raw, central + 24, size + 1)
        self.apk.write_bytes(raw)
        self.rejected("size")

    def test_resource_limits_reject_before_inflation(self):
        self.make()
        for constant in ("MAX_APK_SIZE", "MAX_ENTRIES", "MAX_CENTRAL_DIRECTORY",
                         "MAX_MEMBER_SIZE", "MAX_TOTAL_UNCOMPRESSED"):
            with self.subTest(constant=constant), mock.patch.object(auditor, constant, 1), \
                    mock.patch.object(zipfile.ZipFile, "open", side_effect=AssertionError("Must not inflate")):
                self.rejected("limit")
        self.make(self.files + [(f"/unsafe-{n}", b"") for n in range(110)])
        findings = self.rejected("Unsafe").errors
        self.assertEqual(auditor.MAX_ERRORS, len(findings))
        self.assertIn("capped", findings[-1])

    def test_invalid_manifest_not_accepted(self):
        self.make()
        self.manifest = replace(self.manifest, archive_sha256="bad")
        self.rejected("provenance")

    def test_audit_is_read_only_and_never_extracts(self):
        self.make()
        before = self.apk.read_bytes(), self.apk.stat().st_mtime_ns
        with mock.patch.object(zipfile.ZipFile, "extract", side_effect=AssertionError("No extraction")), \
                mock.patch.object(zipfile.ZipFile, "extractall", side_effect=AssertionError("No extraction")), \
                mock.patch.object(Path, "write_bytes", side_effect=AssertionError("No writes")):
            result = self.audit()
        self.assertTrue(result.passed, result.errors)
        self.assertEqual(before, (self.apk.read_bytes(), self.apk.stat().st_mtime_ns))

    def cli(self, *, bad_manifest=False):
        out, err = io.StringIO(), io.StringIO()
        options = {"side_effect": ValueError("bad provenance")} if bad_manifest else {"return_value": self.manifest}
        with mock.patch.object(sys, "argv", ["verify_apk.py", str(self.apk)]), \
                mock.patch.object(auditor, "load_manifest", **options), \
                mock.patch.object(auditor, "ATTRIBUTION_DIR", self.attribution), \
                redirect_stdout(out), redirect_stderr(err):
            status = auditor.main()
        return status, out.getvalue(), err.getvalue()

    def test_cli_success_is_explicit_about_scope_and_hash(self):
        self.make()
        status, out, err = self.cli()
        self.assertEqual(0, status, err)
        self.assertIn("ZIP/payload audit passed", out)
        self.assertIn(hashlib.sha256(self.apk.read_bytes()).hexdigest(), out)
        self.assertIn("SDK", out + err)
        self.assertIn("not", (out + err).lower())

    def test_cli_bad_apk_fails_without_traceback(self):
        status, _, err = self.cli()
        self.assertEqual(1, status)
        self.assertIn("APK", err)
        self.assertNotIn("Traceback", err)

    def test_cli_bad_manifest_fails_without_traceback(self):
        self.make()
        status, _, err = self.cli(bad_manifest=True)
        self.assertEqual(1, status)
        self.assertIn("provenance", err)
        self.assertNotIn("Traceback", err)


if __name__ == "__main__":
    unittest.main()
