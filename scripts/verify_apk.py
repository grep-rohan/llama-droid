#!/usr/bin/env python3
"""Read-only ZIP/payload audit of the pinned native-runtime debug APK.

    python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk

No extraction, downloads, ELF execution, size-only merge mode or binary-XML
text decoding. SDK manifest inspection, APK signing and device execution are
separate requirements. Do not modify the APK while auditing it.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass, field
import hashlib
import os
from pathlib import Path
import stat
import struct
import sys
import zipfile
import zlib

import import_llama_release as staging
from import_llama_release import Manifest, load_manifest, validate_manifest

ROOT = Path(__file__).resolve().parent.parent
ATTRIBUTION_DIR = ROOT / "third_party"
MAX_APK_SIZE = 512 << 20
MAX_ENTRIES = 10_000
MAX_CENTRAL_DIRECTORY = 8 << 20
MAX_MEMBER_SIZE = 256 << 20
MAX_TOTAL_UNCOMPRESSED = 1 << 30
MAX_ATTRIBUTION_SIZE = 1 << 20
MAX_ERRORS = 100
BUFFER_SIZE = 1 << 16
SCOPE_WARNING = (
    "SDK binary-manifest/extraction/export/permission checks, APK signing and device "
    "execution are not verified by this ZIP/payload audit. Run SDK apkanalyzer manifest print separately."
)


@dataclass
class AuditResult:
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=lambda: [SCOPE_WARNING])
    apk_sha256: str | None = None
    apk_size: int | None = None

    @property
    def passed(self) -> bool:
        return not self.errors

    def reject(self, message: str) -> None:
        if len(self.errors) < MAX_ERRORS - 1:
            self.errors.append(message)
        elif len(self.errors) == MAX_ERRORS - 1:
            self.errors.append(f"Audit findings capped at {MAX_ERRORS}; additional errors may be omitted.")


def _label(name: str) -> str:
    return repr(name if len(name) <= 200 else name[:200] + "...")


def _attribution(directory: Path, result: AuditResult) -> dict[str, tuple[int, str]]:
    expected = {}
    for source, destination in (("llama.cpp-LICENSE", "THIRD_PARTY_LICENSE.txt"),
                                ("NOTICE.md", "THIRD_PARTY_NOTICES.txt")):
        path = directory / source
        try:
            if not path.is_file() or not 0 < path.stat().st_size <= MAX_ATTRIBUTION_SIZE:
                raise ValueError("missing, empty or oversized source")
            expected["assets/" + destination] = (path.stat().st_size, staging.sha256_file(path))
        except (OSError, ValueError) as error:
            result.reject(f"Invalid attribution source {_label(str(path))}: {error}")
    return expected


def _preflight(source, size: int) -> None:
    # Bound central-directory allocation before ZipFile constructs ZipInfo objects.
    # The pinned APK needs neither ZIP64 nor a multi-disk archive.
    length = min(size, 65_577)  # Maximum EOCD comment + record + ZIP64 locator.
    source.seek(size - length)
    tail = source.read(length)
    offset = tail.rfind(b"PK\x05\x06")
    if offset < 0 or offset + 22 > len(tail):
        raise ValueError("Invalid/truncated ZIP end record")
    _, disk, cd_disk, on_disk, entries, cd_size, cd_offset, comment = struct.unpack_from("<4s4H2LH", tail, offset)
    if offset + 22 + comment != len(tail):
        raise ValueError("Invalid ZIP end record/comment or trailing data")
    if disk or cd_disk or on_disk != entries:
        raise ValueError("Multi-disk ZIP is unsupported")
    if (entries == 0xFFFF or cd_size == 0xFFFFFFFF or cd_offset == 0xFFFFFFFF
            or (offset >= 20 and tail[offset - 20:offset - 16] == b"PK\x06\x07")):
        raise ValueError("ZIP64 is unsupported by this pinned-APK audit")
    if entries > MAX_ENTRIES or cd_size > MAX_CENTRAL_DIRECTORY:
        raise ValueError("ZIP central-directory count/size exceeds audit limit")
    if cd_offset + cd_size != size - length + offset:
        raise ValueError("Inconsistent ZIP central-directory offset/size")
    source.seek(0)


def _inventory(infos: list[zipfile.ZipInfo], expected_native: dict, excluded: set[str],
               attribution: dict, result: AuditResult) -> list[zipfile.ZipInfo]:
    if len(infos) > MAX_ENTRIES:
        result.reject(f"ZIP entry count exceeds audit limit {MAX_ENTRIES}")
        return []
    names, files, seen, total = set(), [], set(), 0
    for info in infos:
        name = info.filename
        directory = info.is_dir()
        canonical = name[:-1] if directory else name
        label = _label(info.orig_filename)
        if info.orig_filename != name or not staging._is_safe_tar_path(canonical):
            result.reject(f"Unsafe ZIP member name: {label}")
            continue
        if canonical.casefold() in seen:
            result.reject(f"Duplicate/case-aliased ZIP member: {label}")
        seen.add(canonical.casefold())
        mode = stat.S_IFMT(info.external_attr >> 16)
        if mode not in (0, stat.S_IFREG, stat.S_IFDIR):
            result.reject(f"Link/special ZIP entry is unsupported: {label}")
        if (mode == stat.S_IFDIR and not directory) or (mode == stat.S_IFREG and directory):
            result.reject(f"ZIP directory/type mismatch: {label}")
        if directory and (info.file_size or info.CRC):
            result.reject(f"Nonempty ZIP directory or invalid directory CRC: {label}")
        if info.flag_bits & (1 | 0x40 | 0x2000):
            result.reject(f"Encrypted ZIP entry is unsupported: {label}")
        if info.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED):
            result.reject(f"Unsupported ZIP compression: {label}")
        if not 0 <= info.file_size <= MAX_MEMBER_SIZE:
            result.reject(f"ZIP member size exceeds audit limit {MAX_MEMBER_SIZE}: {label}")
        total += info.file_size
        if total > MAX_TOTAL_UNCOMPRESSED:
            result.reject(f"ZIP declared inflation exceeds audit limit {MAX_TOTAL_UNCOMPRESSED}")
        parts = canonical.split("/")
        if any(part.casefold().endswith(".gguf") for part in parts):
            result.reject(f"Forbidden GGUF member: {label}")
        if any(part == staging.RECEIPT_NAME or part.startswith(".arm64-v8a.") for part in parts):
            result.reject(f"Forbidden staging/transaction content: {label}")
        if not directory and parts[-1].casefold() in excluded:
            result.reject(f"Forbidden upstream utility/backend: {label}")
        if parts[0].casefold() == "lib":
            if len(parts) > 1 and parts[1] != "arm64-v8a":
                result.reject(f"Unexpected native ABI: {label}")
            elif directory and name not in ("lib/", "lib/arm64-v8a/"):
                result.reject(f"Unexpected native directory: {label}")
            elif not directory and name not in expected_native:
                result.reject(f"Unexpected native payload: {label}")
        elif not directory and name.casefold().endswith((".so", ".dll", ".dylib", ".exe", ".a")):
            result.reject(f"Unexpected native file outside allowlist: {label}")
        if not directory:
            names.add(name)
            files.append(info)
            if name in expected_native and info.compress_type != zipfile.ZIP_DEFLATED:
                result.reject(f"Native compression differs from legacy extraction policy: {label}")
    for name in sorted(expected_native.keys() - names):
        result.reject(f"Missing native payload: {_label(name)}")
    for name in sorted(attribution.keys() - names):
        result.reject(f"Missing attribution asset: {_label(name)}")
    manifests = [i for i in files if i.filename == "AndroidManifest.xml"]
    if not manifests or manifests[0].file_size == 0:
        result.reject("Missing or empty AndroidManifest.xml (SDK decoding still required)")
    return files


def _read_member(archive: zipfile.ZipFile, info: zipfile.ZipInfo, remaining: int):
    digest, header, size = hashlib.sha256(), b"", 0
    with archive.open(info) as stream:
        while chunk := stream.read(BUFFER_SIZE):
            size += len(chunk)
            if size > MAX_MEMBER_SIZE or size > remaining:
                raise ValueError("Actual ZIP inflation exceeds audit size limit")
            header += chunk[:max(0, 64 - len(header))]
            digest.update(chunk)
    if size != info.file_size:
        raise ValueError(f"ZIP entry size mismatch: declared {info.file_size}, read {size}")
    return header, digest.hexdigest(), size


def audit_apk(apk_path: Path, manifest: Manifest | None = None,
              attribution_dir: Path | None = None) -> AuditResult:
    """ZIP/payload scope only; even a passed result does not verify SDK/device policy."""
    result = AuditResult()
    try:
        manifest = manifest if manifest is not None else load_manifest()
        validate_manifest(manifest)
    except (OSError, ValueError) as error:
        result.reject(f"Invalid provenance manifest: {error}")
        return result
    attribution = _attribution(attribution_dir if attribution_dir is not None else ATTRIBUTION_DIR, result)
    expected_native = {"lib/arm64-v8a/" + p.apk_name: p for p in manifest.payloads}
    excluded = {name.casefold() for name in manifest.excluded} | {"llama-server"}
    try:
        if not apk_path.is_file():
            result.reject(f"APK not found or not a regular file: {_label(str(apk_path))}")
            return result
        with apk_path.open("rb") as source:
            result.apk_size = os.fstat(source.fileno()).st_size
            if result.apk_size > MAX_APK_SIZE:
                result.reject(f"APK size exceeds audit limit {MAX_APK_SIZE}")
                return result
            if source.read(4) != b"PK\x03\x04":
                result.reject("APK is not a standard ZIP container (missing local header or executable prefix)")
                return result
            _preflight(source, result.apk_size)
            with zipfile.ZipFile(source) as archive:
                files = _inventory(archive.infolist(), expected_native, excluded, attribution, result)
                if result.errors:
                    return result  # Reject invalid metadata before inflating any member.
                total = 0
                for info in files:
                    name = info.filename
                    try:
                        header, digest, size = _read_member(archive, info, MAX_TOTAL_UNCOMPRESSED - total)
                        total += size
                        if name in expected_native:
                            if digest != expected_native[name].sha256:
                                result.reject(f"Native SHA-256 mismatch: {_label(name)}")
                            staging._verify_elf(header, name)
                        elif header.startswith(b"\x7fELF"):
                            result.reject(f"Unexpected native ELF outside allowlist: {_label(name)}")
                        if header.startswith(b"GGUF"):
                            result.reject(f"Forbidden GGUF magic in renamed member: {_label(name)}")
                        if name in attribution and (size, digest) != attribution[name]:
                            result.reject(f"Attribution bytes differ from source: {_label(name)}")
                    except (OSError, ValueError, RuntimeError, EOFError, zipfile.BadZipFile, zlib.error) as error:
                        result.reject(f"Cannot verify ZIP entry {_label(name)}: {error}")
                        break
            if result.passed:
                source.seek(0)
                result.apk_sha256 = staging._hash_stream(source)  # Same open artifact, bounded reads.
    except (OSError, ValueError, RuntimeError, EOFError, zipfile.BadZipFile, zipfile.LargeZipFile, zlib.error) as error:
        result.reject(f"Invalid/unreadable APK ZIP: {error}")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    args = parser.parse_args()
    result = audit_apk(args.apk)
    if not result.passed:
        print("APK ZIP/payload audit FAILED:", file=sys.stderr)
        for error in result.errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print("APK ZIP/payload audit passed.")
    print(f"Size: {result.apk_size} bytes; SHA-256: {result.apk_sha256}")
    for warning in result.warnings:
        print("Not verified: " + warning)
    return 0


if __name__ == "__main__":
    sys.exit(main())
