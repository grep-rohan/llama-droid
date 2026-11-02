#!/usr/bin/env python3
"""
Manifest-driven importer for the llama.cpp Android ARM64 release.

Reads third_party/llama.cpp.json, verifies the archive digest/size,
extracts only the allowlisted 14 payloads into a staging directory,
and optionally replaces the app jniLibs directory.

Usage:
    python scripts/import_llama_release.py <path-to-archive>
    python scripts/import_llama_release.py --verify-only
    python scripts/import_llama_release.py --verify-only <staging-dir>
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
import tarfile
import tempfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

MANIFEST_PATH = Path(__file__).resolve().parent.parent / "third_party" / "llama.cpp.json"
DEFAULT_STAGING = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "jniLibs"
RECEIPT_NAME = ".payload_receipt.json"


# ---------------------------------------------------------------------------
# Data helpers
# ---------------------------------------------------------------------------

@dataclass
class PayloadSpec:
    """One entry from the provenance manifest payloads array."""
    member: str
    apk_name: str
    description: str
    sha256: str


@dataclass
class Manifest:
    """Parsed third_party/llama.cpp.json."""
    archive_url: str
    archive_size: int
    archive_sha256: str
    payloads: list[PayloadSpec]
    excluded: list[str]


def load_manifest(path: Path = MANIFEST_PATH) -> Manifest:
    with open(path, "r", encoding="utf-8") as fh:
        raw: dict[str, Any] = json.load(fh)

    payloads = [
        PayloadSpec(
            member=p["member"],
            apk_name=p["apk_name"],
            description=p["description"],
            sha256=p["sha256"],
        )
        for p in raw["payloads"]
    ]
    return Manifest(
        archive_url=raw["archive_url"],
        archive_size=raw["archive_size"],
        archive_sha256=raw["archive_sha256"],
        payloads=payloads,
        excluded=raw.get("excluded", []),
    )


# ---------------------------------------------------------------------------
# Hashing
# ---------------------------------------------------------------------------

def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    buf_size = 1 << 16  # 64 KiB
    with open(path, "rb") as f:
        while True:
            chunk = f.read(buf_size)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def sha256_stream(tar_member: tarfile.TarFile, member: tarfile.TarInfo) -> bytes:
    """Stream a tar member and return its SHA-256 digest bytes."""
    h = hashlib.sha256()
    buf_size = 1 << 16
    with tar_member.extractfile(member) as fh:
        while True:
            chunk = fh.read(buf_size)
            if not chunk:
                break
            h.update(chunk)
    return h.digest()


# ---------------------------------------------------------------------------
# Archive verification
# ---------------------------------------------------------------------------

def verify_archive(archive_path: Path, manifest: Manifest) -> None:
    """Verify archive size and SHA-256 digest before any extraction."""
    actual_size = archive_path.stat().st_size
    if actual_size != manifest.archive_size:
        raise ValueError(
            f"Archive size mismatch: expected {manifest.archive_size}, "
            f"got {actual_size}"
        )

    actual_sha = sha256_file(archive_path)
    if actual_sha != manifest.archive_sha256:
        raise ValueError(
            f"Archive SHA-256 mismatch: expected {manifest.archive_sha256}, "
            f"got {actual_sha}"
        )


# ---------------------------------------------------------------------------
# Safety checks
# ---------------------------------------------------------------------------

def _is_safe_tar_path(member_name: str) -> bool:
    """Reject absolute paths, traversal sequences, and empty names."""
    if not member_name or member_name.startswith("/"):
        return False
    parts = member_name.replace("\\", "/").split("/")
    if ".." in parts:
        return False
    return True


def _reject_unsafe_member(member: tarfile.TarInfo) -> None:
    """Raise on absolute paths, traversal, symlinks to external targets,
    hard links to external targets, special files, and device nodes."""
    if not _is_safe_tar_path(member.name):
        raise ValueError(f"Unsafe member path: {member.name}")

    # Reject symlinks and hard links that point outside the archive
    if member.issym() or member.islnk():
        link_target = member.linkname
        if link_target and (link_target.startswith("/") or ".." in link_target):
            raise ValueError(f"Link target escapes archive: {member.name} -> {link_target}")
        # Even internal links are rejected to keep staging simple
        raise ValueError(f"Link not supported in archive member: {member.name}")

    if member.isblk() or member.ischr() or member.isfifo():
        raise ValueError(f"Special file not supported: {member.name}")


# ---------------------------------------------------------------------------
# Staging
# ---------------------------------------------------------------------------

def _stage_member(
    tar: tarfile.TarFile,
    member: tarfile.TarInfo,
    dest_dir: Path,
    expected_sha: str,
) -> None:
    """Extract one member, verify its SHA-256, write to dest_dir."""
    dest_path = dest_dir / member.name
    dest_path.parent.mkdir(parents=True, exist_ok=True)

    # Write to a temp file first to support rollback
    tmp_path = dest_path.with_suffix(".tmp")
    with tar.extractfile(member) as src, open(tmp_path, "wb") as dst:
        buf_size = 1 << 16
        h = hashlib.sha256()
        while True:
            chunk = src.read(buf_size)
            if not chunk:
                break
            dst.write(chunk)
            h.update(chunk)

    actual_sha = h.hexdigest()
    if actual_sha != expected_sha:
        tmp_path.unlink(missing_ok=True)
        raise ValueError(
            f"Payload SHA-256 mismatch for {member.name}: "
            f"expected {expected_sha}, got {actual_sha}"
        )

    tmp_path.rename(dest_path)


def stage_payloads(
    archive_path: Path,
    staging_dir: Path,
    manifest: Manifest,
) -> dict[str, str]:
    """
    Extract only the allowlisted payloads into *staging_dir*.

    Returns a dict mapping member name -> sha256 hex digest.
    On failure the staging directory is cleaned up (rollback).
    """
    # Use a temp staging area; only move it into place on success.
    with tempfile.TemporaryDirectory(
        prefix="llama-import-", dir=staging_dir.parent
    ) as tmp_root:
        tmp_staging = Path(tmp_root)

        with tarfile.open(archive_path, "r:gz") as tar:
            seen_members: set[str] = set()
            receipts: dict[str, str] = {}

            for spec in manifest.payloads:
                # Find the member in the archive
                member: tarfile.TarInfo | None = None
                for t in tar:
                    if t.name == spec.member:
                        member = t
                        break

                if member is None:
                    raise KeyError(f"Expected payload not in archive: {spec.member}")

                if member.name in seen_members:
                    raise ValueError(f"Duplicate member in archive: {member.name}")
                seen_members.add(member.name)

                _reject_unsafe_member(member)
                _stage_member(tar, member, tmp_staging, spec.sha256)
                receipts[member.name] = spec.sha256

        # All payloads verified — atomically replace the staging directory.
        if staging_dir.exists():
            shutil.rmtree(staging_dir)
        shutil.move(str(tmp_staging), str(staging_dir))

    # Write receipt after successful staging.
    write_receipt(staging_dir, receipts)
    return receipts


def write_receipt(staging_dir: Path, receipts: dict[str, str]) -> Path:
    """Write a JSON receipt alongside the staged payloads."""
    receipt_path = staging_dir / RECEIPT_NAME
    with open(receipt_path, "w", encoding="utf-8") as f:
        json.dump(receipts, f, indent=2)
    return receipt_path


# ---------------------------------------------------------------------------
# Verification-only mode
# ---------------------------------------------------------------------------

def verify_staging(staging_dir: Path, manifest: Manifest) -> list[str]:
    """
    Verify that the staged directory contains exactly the expected payloads
    with matching SHA-256 digests. Returns a list of error messages (empty
    if all OK).
    """
    errors: list[str] = []

    if not staging_dir.is_dir():
        errors.append(f"Staging directory does not exist: {staging_dir}")
        return errors

    # Check for expected payloads
    staged_files = {
        p.name for p in staging_dir.iterdir()
        if p.is_file() and p.name != RECEIPT_NAME
    }

    expected_members = {spec.member for spec in manifest.payloads}
    # Convert member paths to just filenames for comparison
    expected_names = {Path(spec.member).name for spec in manifest.payloads}

    for spec in manifest.payloads:
        payload_path = staging_dir / spec.member
        if not payload_path.exists():
            errors.append(f"Missing staged payload: {spec.member}")
            continue

        actual_sha = sha256_file(payload_path)
        if actual_sha != spec.sha256:
            errors.append(
                f"SHA-256 mismatch for {spec.member}: "
                f"expected {spec.sha256}, got {actual_sha}"
            )

    # Check for unexpected files
    unexpected = staged_files - expected_names
    for name in sorted(unexpected):
        errors.append(f"Unexpected staged file: {name}")

    return errors


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main() -> int:
    parser = argparse.ArgumentParser(
        description="Stage verified native payloads from the llama.cpp Android release."
    )
    parser.add_argument(
        "archive",
        nargs="?",
        default=None,
        help="Path to the llama-b12020-bin-android-arm64.tar.gz archive.",
    )
    parser.add_argument(
        "--staging-dir",
        type=Path,
        default=DEFAULT_STAGING,
        help=f"Staging directory (default: {DEFAULT_STAGING}).",
    )
    parser.add_argument(
        "--verify-only",
        action="store_true",
        help="Validate the staged directory without downloading or extracting.",
    )
    args = parser.parse_args()

    manifest = load_manifest()

    if args.verify_only:
        errors = verify_staging(args.staging_dir, manifest)
        if errors:
            print("Verification FAILED:", file=sys.stderr)
            for e in errors:
                print(f"  - {e}", file=sys.stderr)
            return 1
        print("Verification passed: staged payloads match the manifest.")
        return 0

    if not args.archive:
        print("Error: archive path required for import mode.", file=sys.stderr)
        print("Usage: python scripts/import_llama_release.py <archive.tar.gz>",
              file=sys.stderr)
        return 1

    archive_path = Path(args.archive).resolve()
    if not archive_path.is_file():
        print(f"Archive not found: {archive_path}", file=sys.stderr)
        return 1

    # Step 1: Verify archive integrity
    print(f"Verifying archive: {archive_path}")
    verify_archive(archive_path, manifest)
    print("  Archive digest OK.")

    # Step 2: Stage payloads
    print(f"Staging payloads to: {args.staging_dir}")
    receipts = stage_payloads(archive_path, args.staging_dir, manifest)
    receipt_path = write_receipt(args.staging_dir, receipts)

    print(f"  Staged {len(receipts)} payloads.")
    print(f"  Receipt written to: {args.staging_dir / RECEIPT_NAME}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
