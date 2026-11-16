"""Bounded transport/protocol primitives; never run ADB merely by importing this module."""
from __future__ import annotations

import re
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
