"""Host-only protocol and fake-ADB tests. No physical device commands are allowed here."""
import importlib.util
from pathlib import Path
import sys
import unittest
import os
import time
import subprocess
import tempfile
import hashlib
from unittest import mock
from types import SimpleNamespace

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import model_import_device as device


def frame(code, **values):
    return ("".join(f"INSTRUMENTATION_STATUS: {k}={v}\n" for k, v in values.items()) +
            f"INSTRUMENTATION_STATUS_CODE: {code}\n").encode()


def passed(cls="Example", method="testOne", total=1):
    fields = {"class": cls, "test": method, "numtests": str(total)}
    return frame(1, **fields) + frame(0, **fields) + f"INSTRUMENTATION_RESULT: stream=\nOK ({total} tests)\nINSTRUMENTATION_CODE: -1\n".encode()


class InstrumentationTests(unittest.TestCase):
    def test_complete_fragmented_reporting_is_required(self):
        p = device.Instrumentation({("Example", "testOne")})
        for byte in passed():
            p.feed(bytes([byte]))
        self.assertEqual({"tests": 1}, p.complete())

    def test_absent_zero_partial_and_wrong_cases_fail(self):
        for output in [b"", b"OK (0 tests)\nINSTRUMENTATION_CODE: -1\n", frame(1, **{"class": "Example", "test": "testOne"}), passed("Other"), passed(method="stale")]:
            with self.subTest(output=output), self.assertRaises(device.Failure):
                p = device.Instrumentation({("Example", "testOne")}); p.feed(output); p.complete()

    def test_skipped_failed_duplicate_and_noisy_substrings_do_not_pass(self):
        fields = {"class": "Example", "test": "testOne", "numtests": "1"}
        for output in [frame(1, **fields) + frame(-3, **fields), frame(-2, **fields), passed() + passed(), b"noise " + passed()]:
            with self.subTest(output=output), self.assertRaises(device.Failure):
                p = device.Instrumentation({("Example", "testOne")}); p.feed(output); p.complete()

    def test_newline_free_output_is_incrementally_bounded(self):
        with self.assertRaises(device.Failure):
            p = device.Instrumentation({("Example", "testOne")}); p.feed(b"secret-content://provider/" * 10000)

    def test_operation_budget_reserves_teardown_and_caps_each_phase(self):
        now = [10.0]; b = device.Budget(180, 30, lambda: now[0])
        self.assertEqual(25, b.deadline(15)); now[0] = 155
        self.assertEqual(160, b.deadline(90)); now[0] = 160
        with self.assertRaises(device.Failure): b.deadline(1)
        self.assertEqual(175, b.deadline(15, teardown=True))
        now[0] = 190
        with self.assertRaises(device.Failure): b.deadline(1, teardown=True)


class TransportTests(unittest.TestCase):
    def command(self, code, limit=1024):
        return device.Command([sys.executable, "-u", "-c", code], limit)

    def test_streams_fragmented_bytes_and_reaps_without_reader_threads(self):
        cmd = self.command("import sys; sys.stdout.buffer.write(b'abc'); sys.stdout.flush()")
        output = bytearray()
        try:
            self.assertEqual(0, cmd.run(time.monotonic() + 5, output.extend))
            self.assertEqual(b"abc", output)
            self.assertEqual(0, cmd.proc.poll())
        finally: cmd.close(time.monotonic() + 3)
        self.assertTrue(cmd.proc.stdout.closed)

    def test_flood_newline_free_output_and_timeout_fail_privately(self):
        for code, limit in [("print('content://secret/'*10000)", 100), ("import time; time.sleep(20)", 100)]:
            cmd = self.command(code, limit)
            try:
                with self.assertRaises(device.Failure) as caught:
                    cmd.run(time.monotonic() + .3, lambda data: None)
                self.assertNotIn("secret", str(caught.exception))
                self.assertNotIn("content:", str(caught.exception))
            finally: cmd.close(time.monotonic() + 3)
            self.assertIsNotNone(cmd.proc.poll()); self.assertTrue(cmd.proc.stdout.closed)

    def test_inherited_open_pipe_cannot_hold_reader_shutdown_forever(self):
        read, write = os.pipe()
        proc = SimpleNamespace(stdout=os.fdopen(read, "rb", buffering=0), poll=lambda: 0, wait=lambda timeout: 0)
        try:
            with mock.patch("subprocess.Popen", return_value=proc):
                cmd = self.command("never executed")
            with self.assertRaises(device.Failure):
                cmd.run(time.monotonic() + .05, lambda data: None)
            cmd.close(time.monotonic() + 1)
            self.assertTrue(proc.stdout.closed)
        finally:
            os.close(write)
            proc.stdout.close()

    def test_consumer_failure_still_allows_bounded_process_cleanup(self):
        cmd = self.command("print('secret'); import time; time.sleep(20)")
        def reject(data): raise device.Failure("parser rejected")
        try:
            with self.assertRaises(device.Failure): cmd.run(time.monotonic() + 1, reject)
        finally: cmd.close(time.monotonic() + 3)
        self.assertIsNotNone(cmd.proc.poll())


class FakeAdb:
    def __init__(self, replies):
        self.replies = list(replies); self.calls = []; self.closed = 0
    def __call__(self, argv, limit):
        self.calls.append(argv)
        expected, output, status = self.replies.pop(0)
        if argv[3:] != expected: raise AssertionError("Unexpected fake-ADB command")
        owner = self
        class Reply:
            proc = SimpleNamespace(poll=lambda: 0)
            def run(self, deadline, consume, until=lambda: False):
                if len(output) > limit: raise device.Failure("transport output limit")
                consume(output); return status
            def close(self, deadline): owner.closed += 1
        return Reply()


class IdentityTests(unittest.TestCase):
    package = "io.github.greprohan.llamadroid"
    def fixture(self, directory, change=None):
        path = Path(directory) / "app.apk"; path.write_bytes(b"apk")
        replies = [(["shell", "cmd", "package", "list", "packages", "-U", self.package], f"package:{self.package} uid:10001\n".encode(), 0),
                   (["shell", "pm", "path", self.package], b"package:/data/app/safe/base.apk\n", 0),
                   (["exec-out", "cat", "/data/app/safe/base.apk"], b"apk", 0)]
        if change: change(replies)
        fake = FakeAdb(replies)
        return device.Device("PRIVATE_SERIAL", {self.package: path}, fake), fake

    def test_installed_bytes_uid_and_local_apk_form_baseline(self):
        with tempfile.TemporaryDirectory() as tmp:
            d, fake = self.fixture(tmp)
            result = d.identity(device.Budget(120))
            self.assertEqual({self.package: {"uid": 10001, "sha256": hashlib.sha256(b"apk").hexdigest(), "bytes": 3}}, result)
            self.assertEqual(3, fake.closed); self.assertEqual([], fake.replies)
            self.assertNotIn("PRIVATE_SERIAL", str(result))

    def test_wrong_package_uid_apk_split_or_transport_failure_cannot_be_accepted(self):
        changes = [lambda r: r.__setitem__(0, (r[0][0], b"package:wrong uid:10001\n", 0)),
                   lambda r: r.__setitem__(0, (r[0][0], f"package:{self.package} uid:0\n".encode(), 0)),
                   lambda r: r.__setitem__(1, (r[1][0], b"package:/data/app/secret/base.apk\npackage:/data/app/secret/split.apk\n", 0)),
                   lambda r: r.__setitem__(2, (r[2][0], b"bad", 0)),
                   lambda r: r.__setitem__(0, (r[0][0], b"private output", 1))]
        for change in changes:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as tmp:
                d, fake = self.fixture(tmp, change)
                with self.assertRaises(device.Failure) as caught: d.identity(device.Budget(120))
                self.assertNotIn("secret", str(caught.exception)); self.assertNotIn("PRIVATE", str(caught.exception))

    def test_process_and_independent_package_teardown_are_checked(self):
        baseline = {self.package: {"uid": 10001}}
        replies = [(["shell", "ps", "-A", "-n", "-o", "PID,UID,NAME"], f"PID UID NAME\n42 10001 {self.package}\n".encode(), 0),
                   (["shell", "am", "force-stop", self.package], b"", 0),
                   (["shell", "ps", "-A", "-n", "-o", "PID,UID,NAME"], b"PID UID NAME\n", 0)]
        fake = FakeAdb(replies); d = device.Device("PRIVATE_SERIAL", {}, fake)
        self.assertEqual([(42, 10001, self.package)], d.processes(baseline, device.Budget(20)))
        d.teardown(baseline, device.Budget(30)); self.assertFalse(fake.replies)

    def test_wrong_uid_and_surviving_uid_process_fail_closed(self):
        baseline = {self.package: {"uid": 10001}}
        for uid, name in [(10002, self.package), (10001, "libllama-server.so")]:
            fake = FakeAdb([(["shell", "ps", "-A", "-n", "-o", "PID,UID,NAME"], f"PID UID NAME\n42 {uid} {name}\n".encode(), 0)])
            d = device.Device("PRIVATE_SERIAL", {}, fake)
            if uid == 10002:
                with self.assertRaises(device.Failure): d.processes(baseline, device.Budget(15))
            else: self.assertEqual([(42, uid, name)], d.processes(baseline, device.Budget(15)))


if __name__ == "__main__":
    unittest.main()
