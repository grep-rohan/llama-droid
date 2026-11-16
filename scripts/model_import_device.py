"""Bounded transport/protocol primitives; never run ADB merely by importing this module."""
from __future__ import annotations

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


class Instrumentation:
    def __init__(self, expected):
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
            case = (fields.get("class"), fields.get("test"))
            if case not in self.expected or fields.get("numtests", str(len(self.expected))) != str(len(self.expected)):
                raise Failure("unexpected test identity or count")
            if code == 1 and self.active is None and case not in self.started:
                self.active = case; self.started.add(case)
            elif code == 0 and self.active == case and case not in self.finished:
                self.active = None; self.finished.add(case)
            else:
                raise Failure("failed skipped or duplicate test")
        elif line.startswith("INSTRUMENTATION_CODE: "):
            if line != "INSTRUMENTATION_CODE: -1" or self.result:
                raise Failure("instrumentation result failed")
            self.result = True
        elif line.startswith("INSTRUMENTATION_FAILED") or line.startswith("FAILURES!!!") or line.startswith("INSTRUMENTATION_RESULT: shortMsg="):
            raise Failure("instrumentation failed")
        elif re.fullmatch(r"OK \(\d+ tests?\)", line):
            if self.ok or line not in {f"OK ({len(self.expected)} tests)", f"OK ({len(self.expected)} test)"}:
                raise Failure("unexpected JUnit result count")
            self.ok = True

    def complete(self):
        if self.buffer or self.fields or self.active is not None or not self.result or not self.ok or self.finished != self.expected:
            raise Failure("incomplete test reporting")
        return {"tests": len(self.finished)}
