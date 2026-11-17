"""Bounded transport/protocol primitives; never run ADB merely by importing this module."""
from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import time


class Failure(Exception):
    """Fixed public reason only: never include raw subprocess/provider diagnostics."""


class Budget:
    def __init__(self, seconds, reserve=0, clock=time.monotonic):
        self.clock = clock
        self.end = clock() + seconds
        self.reserve = reserve

    def deadline(self, cap, teardown=False):
        end = min(self.end - (0 if teardown else self.reserve), self.clock() + cap)
        if end <= self.clock():
            raise Failure("deadline expired")
        return end


class Command:
    """Nonblocking pipe polling: no reader thread to strand on inherited-open output."""
    def __init__(self, argv, limit=1024 * 1024):
        self.limit = limit
        self.count = 0
        try:
            # Fail closed on hosts without nonblocking anonymous pipes BEFORE spawning ADB.
            read, write = os.pipe()
            try: os.set_blocking(read, False)
            finally: os.close(read); os.close(write)
            self.proc = subprocess.Popen(argv, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                         stderr=subprocess.STDOUT, bufsize=0)
            os.set_blocking(self.proc.stdout.fileno(), False)
        except OSError:
            if getattr(self, "proc", None) is not None:
                self.proc.kill(); self.proc.wait(timeout=2); self.proc.stdout.close()
            raise Failure("cannot start bounded transport") from None

    def run(self, deadline, consume, until=lambda: False):
        eof = False
        while time.monotonic() < deadline:
            if not eof:
                try: data = os.read(self.proc.stdout.fileno(), 16384)
                except BlockingIOError: data = None
                except OSError: raise Failure("transport read failed") from None
                if data == b"": eof = True
                elif data:
                    self.count += len(data)
                    if self.count > self.limit: raise Failure("transport output limit")
                    consume(data)
            if until(): return None
            result = self.proc.poll()
            if eof and result is not None: return result
            time.sleep(min(.01, max(0, deadline - time.monotonic())))
        raise Failure("transport deadline expired")

    def close(self, deadline):
        try:
            if self.proc.poll() is None:
                self.proc.terminate()
                try: self.proc.wait(timeout=min(.5, max(0, deadline - time.monotonic())))
                except subprocess.TimeoutExpired:
                    self.proc.kill()
                    try: self.proc.wait(timeout=max(0, deadline - time.monotonic()))
                    except subprocess.TimeoutExpired: raise Failure("transport teardown expired") from None
            if self.proc.poll() is None: raise Failure("transport process survived")
        except OSError:
            raise Failure("transport teardown failed") from None
        finally:
            self.proc.stdout.close()


class Device:
    def __init__(self, serial, apks, command=Command):
        if not serial or not re.fullmatch(r"[A-Za-z0-9.:_-]{1,128}", serial):
            raise Failure("private device selection required")
        self.prefix = ["adb", "-s", serial]
        self.apks = apks
        self.command = command

    def call(self, args, budget, cap=15, limit=4096, teardown=False, consume=None):
        deadline = budget.deadline(cap, teardown)
        output = bytearray()
        cmd = self.command(self.prefix + args, limit)
        try:
            status = cmd.run(deadline, consume or output.extend)
            if status != 0: raise Failure("device command failed")
            return bytes(output)
        finally: cmd.close(min(budget.end, time.monotonic() + 2))

    def identity(self, budget):
        result = {}
        for package, path in self.apks.items():
            if path.is_symlink() or not path.is_file(): raise Failure("audited local APK missing")
            digest = hashlib.sha256()
            with path.open("rb") as source:
                before = os.fstat(source.fileno())
                while chunk := source.read(1024 * 1024):
                    budget.deadline(1); digest.update(chunk)
                after = os.fstat(source.fileno())
            if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
                raise Failure("local APK changed")
            uid_text = self.call(["shell", "cmd", "package", "list", "packages", "-U", package], budget)
            uids = []
            for line in uid_text.splitlines():
                row = re.fullmatch(rb"package:([A-Za-z0-9_.]+) uid:([0-9]+)", line)
                if not row: raise Failure("installed package UID mismatch")
                if row[1] == package.encode(): uids.append(int(row[2]))
            if len(uids) != 1 or not 10000 <= uids[0] <= 2147483647:
                raise Failure("installed package UID mismatch")
            location = self.call(["shell", "pm", "path", package], budget)
            match_path = re.fullmatch(rb"package:(/data/app/[-A-Za-z0-9_~+=./]+/base\.apk)\r?\n?", location)
            if not match_path: raise Failure("installed APK path mismatch")
            remote = match_path[1].decode("ascii")
            if any(part in {".", "..", ""} for part in remote.split("/")[1:]): raise Failure("unsafe installed APK path")
            installed = hashlib.sha256(); size = [0]
            def absorb(chunk): installed.update(chunk); size[0] += len(chunk)
            self.call(["exec-out", "cat", remote], budget, cap=120, limit=before.st_size, consume=absorb)
            if size[0] != before.st_size or installed.digest() != digest.digest(): raise Failure("installed APK bytes mismatch")
            result[package] = {"uid": uids[0], "sha256": digest.hexdigest(), "bytes": size[0]}
        if len({p["uid"] for p in result.values()}) != len(result): raise Failure("package UIDs overlap")
        return result

    def processes(self, baseline, budget, teardown=False):
        output = self.call(["shell", "ps", "-A", "-n", "-o", "PID,UID,NAME"], budget,
                           cap=8 if teardown else 15, limit=65536, teardown=teardown)
        try: lines = output.decode("ascii").splitlines()
        except UnicodeError: raise Failure("invalid process inventory") from None
        if not lines or lines[0].split() != ["PID", "UID", "NAME"]: raise Failure("invalid process header")
        found = []
        for line in lines[1:]:
            match = re.fullmatch(r"\s*([0-9]+)\s+([0-9]+)\s+(\S(?:.*\S)?)\s*", line)
            if not match: raise Failure("invalid process inventory")
            pid, uid, name = int(match[1]), int(match[2]), match[3]
            if not 0 < pid <= 2147483647: raise Failure("invalid process identity")
            for package, identity in baseline.items():
                if (name == package or name.startswith(package + ":")) and uid != identity["uid"]:
                    raise Failure("process UID mismatch")
            if uid in {p["uid"] for p in baseline.values()}:
                found.append((pid, uid, name))
                if len(found) > 16: raise Failure("test process count limit")
        return found

    def instrument_command(self, runner, test_filter, arguments, budget, cap):
        deadline = budget.deadline(cap)
        args = ["shell", "am", "instrument", "-w", "-r", "-e", "class", test_filter]
        for key, value in sorted(arguments.items()): args += ["-e", key, value]
        return self.command(self.prefix + args + [runner], 1024 * 1024), deadline

    def teardown(self, baseline, budget, teardown=True):
        failed = False
        for package in baseline:
            try:
                if self.call(["shell", "am", "force-stop", package], budget, cap=8, teardown=teardown).strip():
                    failed = True
            except Failure: failed = True
        try:
            if self.processes(baseline, budget, teardown=teardown): failed = True
        except Failure: failed = True
        if failed: raise Failure("device teardown incomplete")


class Instrumentation:
    def __init__(self, expected, correlation=None, custom_code=None, uid=None):
        self.correlation = correlation
        self.custom_code = custom_code
        self.uid = uid
        self.markers = {}
        self.expected = set(expected)
        self.started = set()
        self.finished = set()
        if not self.expected:
            raise Failure("empty test roster")
        self.buffer = bytearray()
        self.fields = {}
        self.active = None
        self.result = False
        self.ok = False
        self.total_bytes = 0

    def feed(self, data):
        self.total_bytes += len(data)
        if self.total_bytes > 1024 * 1024:
            raise Failure("instrumentation output limit")
        # Never append an arbitrary chunk before enforcing the per-line allocation bound.
        for part in data.splitlines(keepends=True):
            if len(self.buffer) + len(part) > 8192:
                raise Failure("instrumentation line limit")
            self.buffer.extend(part)
            if self.buffer.endswith(b"\n"):
                try:
                    line = self.buffer.decode("utf-8").rstrip("\r\n")
                except UnicodeError:
                    raise Failure("invalid instrumentation encoding") from None
                self.buffer.clear()
                self.line(line)

    def line(self, line):
        if line.startswith("INSTRUMENTATION_STATUS: "):
            if self.result:
                raise Failure("status after result")
            key, sep, value = line[24:].partition("=")
            if not sep or key in self.fields or len(self.fields) >= 16:
                raise Failure("invalid instrumentation frame")
            self.fields[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            try:
                code = int(line[29:])
            except ValueError:
                raise Failure("invalid instrumentation status") from None
            fields, self.fields = self.fields, {}
            if code in {1001, 1002}:
                self.custom(code, fields); return
            if self.custom_code == 1001 and code == 0: raise Failure("setup completed before controlled stop")
            case = (fields.get("class"), fields.get("test"))
            if case not in self.expected or fields.get("numtests") != str(len(self.expected)):
                raise Failure("unexpected test identity or count")
            if code == 1 and self.active is None and case not in self.started:
                self.active = case; self.started.add(case)
            elif code == 0 and self.active == case and case not in self.finished:
                self.active = None; self.finished.add(case)
            else:
                raise Failure("failed skipped or duplicate test")
        elif line.startswith("INSTRUMENTATION_CODE: "):
            if self.custom_code == 1001 or line != "INSTRUMENTATION_CODE: -1" or self.result:
                raise Failure("instrumentation result failed")
            self.result = True
        elif line.startswith("INSTRUMENTATION_FAILED") or line.startswith("FAILURES!!!") or line.startswith("INSTRUMENTATION_RESULT: shortMsg="):
            raise Failure("instrumentation failed")
        elif re.fullmatch(r"OK \(\d+ tests?\)", line):
            if self.ok or self.active is not None or self.finished != self.expected or line not in {f"OK ({len(self.expected)} tests)", f"OK ({len(self.expected)} test)"}:
                raise Failure("unexpected JUnit result count")
            self.ok = True

    def custom(self, code, fields):
        field = "model_import_checkpoint" if code == 1001 else "model_import_verified"
        if code != self.custom_code or self.correlation is None or self.active is None or code in self.markers or set(fields) != {field}:
            raise Failure("unexpected checkpoint frame")
        raw = fields[field]
        if len(raw.encode("utf-8")) > 1024: raise Failure("checkpoint size limit")
        def unique(pairs):
            result = {}
            for key, value in pairs:
                if key in result: raise Failure("duplicate checkpoint key")
                result[key] = value
            return result
        try: value = json.loads(raw, object_pairs_hook=unique)
        except (ValueError, TypeError): raise Failure("invalid checkpoint JSON") from None
        keys = {"v", "runId", "scenario", "checkpoint", "pid", "uid", "startedElapsedMs"}
        if not isinstance(value, dict) or set(value) != keys: raise Failure("checkpoint schema mismatch")
        if any(type(value[k]) is not int for k in ["v", "pid", "uid", "startedElapsedMs"]):
            raise Failure("checkpoint numeric grammar")
        if value["v"] != 1 or not 0 < value["pid"] <= 2147483647 or value["uid"] != self.uid or not 0 <= value["startedElapsedMs"] <= 9223372036854775807:
            raise Failure("checkpoint process identity mismatch")
        if any(value[k] != self.correlation[k] for k in ["runId", "scenario", "checkpoint"]):
            raise Failure("checkpoint correlation mismatch")
        self.markers[code] = value

    def complete(self):
        if self.custom_code == 1001 or (self.custom_code == 1002 and 1002 not in self.markers):
            raise Failure("required verification frame missing")
        if self.buffer or self.fields or self.active is not None or not self.result or not self.ok or self.finished != self.expected:
            raise Failure("incomplete test reporting")
        return {"tests": len(self.finished)}
