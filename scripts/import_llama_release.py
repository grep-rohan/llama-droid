#!/usr/bin/env python3
"""Stage pinned, byte-preserved Android ARM64 payloads; never download or relink.

    python scripts/import_llama_release.py <archive.tar.gz>
    python scripts/import_llama_release.py --verify-only [--staging-dir DIR]

An import intent marker survives failure/interruption and invalidates even a
previous complete stage until a successful retry. An OS lock excludes concurrent
writers and is released on process exit. Verification only reads existing files.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import stat
import struct
import sys
import tarfile
import tempfile
from typing import BinaryIO

ROOT = Path(__file__).resolve().parent.parent
MANIFEST_PATH = ROOT / "third_party" / "llama.cpp.json"
DEFAULT_STAGING = ROOT / "app" / "src" / "main" / "jniLibs" / "arm64-v8a"
RECEIPT_NAME = ".payload_receipt.json"
BUFFER_SIZE = 1 << 16


@dataclass(frozen=True)
class PayloadSpec:
    member: str
    apk_name: str
    description: str
    sha256: str


@dataclass(frozen=True)
class Manifest:
    archive_url: str
    archive_size: int
    archive_sha256: str
    payloads: list[PayloadSpec]
    excluded: list[str]
    release: str
    source_commit: str


def _unique_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate JSON key: {key}")
        result[key] = value
    return result


def _read_json(path: Path):
    with path.open(encoding="utf-8") as stream:
        return json.load(stream, object_pairs_hook=_unique_keys)


def _is_safe_tar_path(name: str) -> bool:
    if not isinstance(name, str) or not name or any(c in name for c in "\\:\x00"):
        return False
    for part in name.split("/"):
        if part in ("", ".", "..") or part.endswith((".", " ")) or any(ord(c) < 32 for c in part):
            return False
        if re.match(r"^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|$)", part, re.I):
            return False
    return True


def _digest(value) -> bool:
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None


def validate_manifest(manifest: Manifest) -> None:
    if type(manifest.archive_size) is not int or manifest.archive_size <= 0 or not _digest(manifest.archive_sha256):
        raise ValueError("Invalid archive size/SHA-256 in provenance manifest")
    if not isinstance(manifest.source_commit, str) or not re.fullmatch(r"[0-9a-f]{40}", manifest.source_commit):
        raise ValueError("Provenance source_commit must be a full 40-character SHA-1")
    if not isinstance(manifest.release, str) or not manifest.release:
        raise ValueError("Missing provenance release")
    if not isinstance(manifest.archive_url, str) or not manifest.archive_url.startswith("https://"):
        raise ValueError("Provenance archive_url must use HTTPS (no download is performed)")
    if not isinstance(manifest.payloads, list) or not manifest.payloads:
        raise ValueError("Provenance payload allowlist is empty")
    if not isinstance(manifest.excluded, list) or not all(isinstance(x, str) for x in manifest.excluded):
        raise ValueError("Invalid provenance exclusions")
    members, names = set(), set()
    for spec in manifest.payloads:
        if not _is_safe_tar_path(spec.member):
            raise ValueError(f"Unsafe provenance member path: {spec.member!r}")
        if not isinstance(spec.apk_name, str) or not re.fullmatch(r"lib[A-Za-z0-9_.+-]+\.so", spec.apk_name):
            raise ValueError(f"Unsafe APK payload name: {spec.apk_name!r}")
        if spec.member.casefold() in members or spec.apk_name.casefold() in names:
            raise ValueError("Duplicate provenance member/APK mapping")
        if not _digest(spec.sha256):
            raise ValueError(f"Invalid payload SHA-256: {spec.apk_name}")
        if spec.apk_name in manifest.excluded or spec.member.split("/")[-1] in manifest.excluded:
            raise ValueError(f"Selected payload is also excluded: {spec.member}")
        members.add(spec.member.casefold())
        names.add(spec.apk_name.casefold())


def load_manifest(path: Path | None = None) -> Manifest:
    try:
        raw = _read_json(path or MANIFEST_PATH)
        manifest = Manifest(
            raw["archive_url"], raw["archive_size"], raw["archive_sha256"],
            [PayloadSpec(p["member"], p["apk_name"], p.get("description", ""), p["sha256"])
             for p in raw["payloads"]],
            raw.get("excluded", []), raw["release"], raw["source_commit"],
        )
        validate_manifest(manifest)
        return manifest
    except (KeyError, TypeError) as error:
        raise ValueError(f"Invalid provenance manifest: {error}") from error


def sha256_file(path: Path) -> str:
    with path.open("rb") as stream:
        return _hash_stream(stream)


def _hash_stream(stream: BinaryIO) -> str:
    digest = hashlib.sha256()
    while chunk := stream.read(BUFFER_SIZE):
        digest.update(chunk)
    return digest.hexdigest()


def _verify_archive_stream(stream: BinaryIO, manifest: Manifest) -> None:
    stream.seek(0, os.SEEK_END)
    size = stream.tell()
    if size != manifest.archive_size:
        raise ValueError(f"Archive size mismatch: expected {manifest.archive_size}, got {size}")
    stream.seek(0)
    digest = _hash_stream(stream)
    if digest != manifest.archive_sha256:
        raise ValueError(f"Archive SHA-256 mismatch: expected {manifest.archive_sha256}, got {digest}")
    stream.seek(0)


def verify_archive(archive_path: Path, manifest: Manifest) -> None:
    validate_manifest(manifest)
    with archive_path.open("rb") as stream:
        _verify_archive_stream(stream, manifest)


def _scan_archive(tar: tarfile.TarFile) -> dict[str, tarfile.TarInfo]:
    """Scan *all* entries before copying; never use tar.extract/extractall."""
    members, seen = {}, set()
    for member in tar:
        name = member.name[:-1] if member.isdir() and member.name.endswith("/") else member.name
        if not _is_safe_tar_path(name):
            raise ValueError(f"Unsafe archive member path: {member.name!r}")
        if name.casefold() in seen:
            raise ValueError(f"Duplicate archive member: {name}")
        seen.add(name.casefold())
        if member.type not in (tarfile.REGTYPE, tarfile.AREGTYPE, tarfile.DIRTYPE) or member.sparse is not None:
            raise ValueError(f"Link/special/sparse archive member is unsupported: {name}")
        members[name] = member
    return members


def _verify_elf(header: bytes, name: str) -> None:
    # Structural sanity only, not proof of linker/CPU/device compatibility.
    if (len(header) != 64 or header[:7] != b"\x7fELF\x02\x01\x01"
            or struct.unpack_from("<HHI", header, 16) != (3, 183, 1)
            or struct.unpack_from("<H", header, 52)[0] != 64):
        raise ValueError(f"Invalid ELF header for {name}: expected ELF64 little-endian AArch64 ET_DYN")


def _stage_member(tar: tarfile.TarFile, member: tarfile.TarInfo, directory: Path, spec: PayloadSpec) -> None:
    destination = directory / spec.apk_name
    digest, size = hashlib.sha256(), 0
    with tar.extractfile(member) as source, destination.open("xb") as output:
        while chunk := source.read(BUFFER_SIZE):
            output.write(chunk)
            digest.update(chunk)
            size += len(chunk)
        output.flush()
        os.fsync(output.fileno())
    if size != member.size or digest.hexdigest() != spec.sha256:
        raise ValueError(f"Payload SHA-256 mismatch or incomplete copy: {spec.member}")
    with destination.open("rb") as stream:
        _verify_elf(stream.read(64), spec.apk_name)


def _receipt(manifest: Manifest) -> dict:
    return {
        "schema_version": 1,
        "release": manifest.release,
        "source_commit": manifest.source_commit,
        "archive_size": manifest.archive_size,
        "archive_sha256": manifest.archive_sha256,
        "payloads": {p.apk_name: {"member": p.member, "sha256": p.sha256} for p in manifest.payloads},
    }


def _write_synced(path: Path, data: bytes) -> None:
    with path.open("wb") as stream:
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())


def write_receipt(staging_dir: Path, manifest: Manifest) -> Path:
    path = staging_dir / RECEIPT_NAME
    _write_synced(path, (json.dumps(_receipt(manifest), indent=2, sort_keys=True) + "\n").encode())
    return path


def _linked(path: Path) -> bool:
    if path.is_symlink():
        return True
    try:
        # lstat attributes cover Windows junctions/reparse points even on
        # Python versions predating Path.is_junction(). Never follow them.
        attributes = getattr(path.lstat(), "st_file_attributes", 0)
        return bool(attributes & stat.FILE_ATTRIBUTE_REPARSE_POINT)
    except FileNotFoundError:
        return False


def _exists(path: Path) -> bool:
    return os.path.lexists(path)


def _paths(stage: Path) -> tuple[Path, Path, Path]:
    return tuple(stage.parent / f".{stage.name}.{suffix}" for suffix in ("importing", "previous", "lock"))


@contextmanager
def _import_lock(stage: Path):
    """Persistent lock file, transient OS lock; never unlink a possibly locked inode."""
    stage.parent.mkdir(parents=True, exist_ok=True)
    lock = _paths(stage)[2]
    if _linked(lock):
        raise ValueError(f"Refusing linked import lock: {lock}")
    with lock.open("a+b") as stream:
        stream.seek(0, os.SEEK_END)
        if stream.tell() == 0:
            stream.write(b"0")
            stream.flush()
        stream.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise ValueError(f"Another import is in progress or the stage cannot be locked: {stage}") from error
        try:
            yield
        finally:
            stream.seek(0)
            if os.name == "nt":
                msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(stream.fileno(), fcntl.LOCK_UN)


def _publish(candidate: Path, stage: Path, backup: Path) -> None:
    # A prior hard interruption may leave both a complete stage and its backup.
    # Discard that older backup only after a fresh candidate is fully verified.
    if backup.exists():
        shutil.rmtree(backup)
    had_stage = stage.exists()
    if had_stage:
        stage.rename(backup)
    try:
        candidate.rename(stage)
    except BaseException as publication_error:
        try:
            if backup.exists():
                if stage.exists():
                    shutil.rmtree(stage)
                backup.rename(stage)
        except OSError as rollback_error:
            raise ValueError(
                f"Native publication failed ({publication_error}); rollback also failed "
                f"({rollback_error}). Saved stage: {backup}; retry a complete import."
            ) from rollback_error
        raise
    if backup.exists():
        shutil.rmtree(backup)


def stage_payloads(archive_path: Path, staging_dir: Path, manifest: Manifest) -> dict[str, str]:
    """Verify archive, prepare complete candidate+receipt, then publish with rollback.

    Failed attempts leave the intent marker. Old bytes may be retained for
    rollback, but verify-only will reject them until a complete successful retry.
    """
    validate_manifest(manifest)
    stage = Path(os.path.abspath(staging_dir))
    marker, backup, _ = _paths(stage)
    if stage == stage.parent or _linked(stage) or _linked(marker) or _linked(backup):
        raise ValueError(f"Refusing root/linked staging or transaction paths: {stage}")
    if Path(os.path.abspath(archive_path)).is_relative_to(stage):
        raise ValueError("Archive must be outside the generated staging directory")
    with _import_lock(stage):
        _write_synced(marker, b"Incomplete native import; retry the importer before building.\n")
        if backup.exists() and not stage.exists():
            backup.rename(stage)  # Recover an interrupted old-stage rename; marker remains.
        if stage.exists():
            if not stage.is_dir():
                raise ValueError(f"Stage is not a directory: {stage}")
            if any(stage.iterdir()) and not (stage / RECEIPT_NAME).is_file():
                raise ValueError(f"Refusing to replace non-stage directory without a receipt: {stage}")
        with tempfile.TemporaryDirectory(prefix=f".{stage.name}.candidate-", dir=stage.parent) as temporary:
            candidate = Path(temporary)
            # Hash and parse the same open file, not a separately reopened pathname.
            with archive_path.open("rb") as source:
                _verify_archive_stream(source, manifest)
                with tarfile.open(fileobj=source, mode="r:gz") as tar:
                    members = _scan_archive(tar)
                    for spec in manifest.payloads:
                        member = members.get(spec.member)
                        if member is None or not member.isfile():
                            raise ValueError(f"Missing regular archive payload: {spec.member}")
                        _stage_member(tar, member, candidate, spec)
            write_receipt(candidate, manifest)
            errors = _verify_contents(candidate, manifest)
            if errors:
                raise ValueError("Candidate verification failed: " + "; ".join(errors))
            _publish(candidate, stage, backup)
        marker.unlink()  # Last step: never remove on failure, including KeyboardInterrupt.
    return {spec.apk_name: spec.sha256 for spec in manifest.payloads}


def _verify_contents(stage: Path, manifest: Manifest) -> list[str]:
    errors = []
    if _linked(stage):
        return [f"Staging directory is a link/junction: {stage}"]
    if not stage.is_dir():
        return [f"Staging directory does not exist or is not a directory: {stage}"]
    expected = {spec.apk_name for spec in manifest.payloads} | {RECEIPT_NAME}
    for entry in stage.iterdir():
        if entry.name not in expected:
            errors.append(f"Unexpected staged entry: {entry.name}")
        if _linked(entry):
            errors.append(f"Staged entry is a link/junction: {entry.name}")
    for spec in manifest.payloads:
        path = stage / spec.apk_name
        if _linked(path):
            continue  # Never read a linked file.
        if not path.is_file():
            errors.append(f"Missing staged payload or not a regular file: {spec.apk_name}")
            continue
        try:
            if sha256_file(path) != spec.sha256:
                errors.append(f"SHA-256 mismatch for staged payload: {spec.apk_name}")
            with path.open("rb") as stream:
                _verify_elf(stream.read(64), spec.apk_name)
        except (OSError, ValueError) as error:
            errors.append(f"Payload verification failed: {error}")
    path = stage / RECEIPT_NAME
    if not _linked(path):
        try:
            if not path.is_file() or path.stat().st_size > 1 << 20:
                raise ValueError("Missing, non-regular or oversized receipt")
            actual = _read_json(path)
            # Canonical serialization prevents bool/int or float/int equality aliases.
            if json.dumps(actual, sort_keys=True) != json.dumps(_receipt(manifest), sort_keys=True):
                raise ValueError("Receipt provenance/payload mapping differs from manifest")
        except (OSError, ValueError) as error:
            errors.append(f"Invalid stage receipt: {error}")
    return errors


def verify_staging(staging_dir: Path, manifest: Manifest) -> list[str]:
    """Read-only, fail-closed verification. No creation, locking, extraction or repair."""
    stage = Path(os.path.abspath(staging_dir))
    marker, backup, _ = _paths(stage)
    try:
        validate_manifest(manifest)
        if _exists(marker) or _exists(backup):
            return [f"Incomplete native import for {stage}; rerun a complete import before building"]
        errors = _verify_contents(stage, manifest)
        if _exists(marker) or _exists(backup):
            errors.append(f"Incomplete native import appeared during verification: {stage}")
        return errors
    except (OSError, ValueError) as error:
        return [f"Native stage verification failed: {error}"]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", nargs="?", help="Pinned upstream archive (no network access)")
    parser.add_argument("--staging-dir", type=Path, default=DEFAULT_STAGING)
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    if args.verify_only and args.archive:
        parser.error("--verify-only takes no archive/positional directory; use --staging-dir DIR")
    if not args.verify_only and not args.archive:
        parser.error("archive path required for import mode")
    try:
        manifest = load_manifest()
        if args.verify_only:
            errors = verify_staging(args.staging_dir, manifest)
            if errors:
                raise ValueError("; ".join(errors))
            print("Verification passed: exact payloads, ELF headers, receipt and transaction state match.")
        else:
            payloads = stage_payloads(Path(args.archive), args.staging_dir, manifest)
            print(f"Staged {len(payloads)} byte-preserved payloads to {args.staging_dir}")
        return 0
    except (OSError, ValueError, tarfile.TarError, EOFError) as error:
        print(f"Native {'verification' if args.verify_only else 'import'} FAILED: {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("Native import interrupted; complete a successful retry before building.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
