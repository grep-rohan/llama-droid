"""Host-only protocol and fake-ADB tests. No physical device commands are allowed here."""
import importlib.util
from pathlib import Path
import sys
import unittest
import os
import time
import subprocess
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


if __name__ == "__main__":
    unittest.main()
