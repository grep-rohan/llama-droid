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
import io
import json
from contextlib import redirect_stdout, redirect_stderr
from unittest import mock
from types import SimpleNamespace

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import model_import_device as device
import check_model_import_recovery as driver


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

    def test_premature_result_and_missing_count_are_rejected(self):
        fields = {"class": "Example", "test": "testOne"}
        for output in [b"OK (1 tests)\n" + passed(), frame(1, **fields) + frame(0, **fields) + b"OK (1 tests)\nINSTRUMENTATION_CODE: -1\n"]:
            with self.subTest(output=output), self.assertRaises(device.Failure):
                p = device.Instrumentation({("Example", "testOne")}); p.feed(output); p.complete()

    def test_operation_budget_reserves_teardown_and_caps_each_phase(self):
        now = [10.0]; b = device.Budget(180, 30, lambda: now[0])
        self.assertEqual(25, b.deadline(15)); now[0] = 155
        self.assertEqual(160, b.deadline(90)); now[0] = 160
        with self.assertRaises(device.Failure): b.deadline(1)
        self.assertEqual(175, b.deadline(15, teardown=True))
        now[0] = 190
        with self.assertRaises(device.Failure): b.deadline(1, teardown=True)


class CheckpointTests(unittest.TestCase):
    correlation = {"runId": "a" * 32, "scenario": "first", "checkpoint": "mid-copy"}
    expected = {("Setup", "stage")}
    def marker(self, **changes):
        value = {"v": 1, **self.correlation, "pid": 42, "uid": 10001, "startedElapsedMs": 123}
        value.update(changes); return value
    def parser(self, code=1001):
        return device.Instrumentation(self.expected, self.correlation, code, 10001)
    def start(self): return frame(1, **{"class": "Setup", "test": "stage", "numtests": "1"})
    def status(self, value, code=1001):
        return frame(code, **{("model_import_checkpoint" if code == 1001 else "model_import_verified"): json.dumps(value)})

    def test_ready_is_accepted_only_after_the_complete_correlated_custom_frame(self):
        p = self.parser(); p.feed(self.start())
        data = self.status(self.marker())
        p.feed(data[:-1]); self.assertFalse(p.markers)
        p.feed(data[-1:]); self.assertEqual({1001: self.marker()}, p.markers)
        with self.assertRaises(device.Failure): p.complete()

    def test_wrong_identity_schema_duplicate_oversize_and_unframed_markers_fail(self):
        wrong = [{"runId": "b"*32}, {"scenario": "reset"}, {"checkpoint": "stale"}, {"uid": 10002},
                 {"pid": True}, {"pid": 0}, {"startedElapsedMs": "123"}, {"startedElapsedMs": -1}, {"v": True}, {"URI": "content://private"}]
        for changes in wrong:
            with self.subTest(changes=changes), self.assertRaises(device.Failure):
                p = self.parser(); p.feed(self.start()); p.feed(self.status(self.marker(**changes)))
        for output in [self.status(self.marker()) * 2,
                       frame(1001, model_import_checkpoint="x" * 1025),
                       frame(1001, model_import_checkpoint='{"v":1,"v":1}')]:
            with self.subTest(output=output), self.assertRaises(device.Failure):
                p = self.parser(); p.feed(self.start()); p.feed(output)
        with self.assertRaises(device.Failure):
            p = self.parser(); p.feed(self.status(self.marker())) # No started setup.

    def test_ready_setup_must_stay_paused_and_verification_requires_marker_plus_success(self):
        with self.assertRaises(device.Failure):
            p = self.parser(); p.feed(self.start() + self.status(self.marker()) + frame(0, **{"class": "Setup", "test": "stage", "numtests": "1"}))
        p = self.parser(1002)
        p.feed(self.start() + self.status(self.marker(pid=43, startedElapsedMs=124), 1002) +
               frame(0, **{"class": "Setup", "test": "stage", "numtests": "1"}) + b"OK (1 tests)\nINSTRUMENTATION_CODE: -1\n")
        self.assertEqual({"tests": 1}, p.complete())
        with self.assertRaises(device.Failure):
            p = self.parser(1002); p.feed(passed("Setup", "stage")); p.complete()
        with self.assertRaises(device.Failure):
            p = device.Instrumentation(self.expected); p.feed(self.start() + self.status(self.marker()))


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
            alive = False
            @property
            def proc(self): return SimpleNamespace(poll=lambda: None if self.alive else status)
            def run(self, deadline, consume, until=lambda: False):
                if len(output) > limit: raise device.Failure("transport output limit")
                if status == "timeout": raise device.Failure("transport deadline expired")
                consume(output)
                if until(): self.alive = True; return None
                return status
            def close(self, deadline): self.alive = False; owner.closed += 1
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
                   lambda r: r.__setitem__(0, (r[0][0], f"package:{self.package.replace('.', 'X')} uid:10001\n".encode(), 0)),
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

    def test_large_system_process_inventory_stays_bounded_and_finds_target(self):
        baseline = {self.package: {"uid": 10001}}
        command = ["shell", "ps", "-A", "-n", "-o", "PID,UID,NAME"]
        for count in (1500, 3000):
            output = b"PID UID NAME\n" + b"123 1000 system-service\n" * count
            output += f"42 10001 {self.package}\n".encode()
            self.assertGreater(len(output), 32768)
            fake = FakeAdb([(command, output, 0)])
            d = device.Device("PRIVATE_SERIAL", {}, fake)
            if len(output) <= 65536:
                self.assertEqual([(42, 10001, self.package)], d.processes(baseline, device.Budget(20)))
            else:
                with self.assertRaisesRegex(device.Failure, "transport output limit"):
                    d.processes(baseline, device.Budget(20))
            self.assertEqual(1, fake.closed)

    def test_process_names_with_spaces_do_not_hide_target_uid_children(self):
        baseline = {self.package: {"uid": 10001}}
        output = b"PID UID NAME\n41 1000 system worker name\n42 10001 native worker name\n"
        fake = FakeAdb([(["shell", "ps", "-A", "-n", "-o", "PID,UID,NAME"], output, 0)])
        d = device.Device("PRIVATE_SERIAL", {}, fake)
        self.assertEqual([(42, 10001, "native worker name")], d.processes(baseline, device.Budget(20)))
        self.assertEqual(1, fake.closed)

    def test_space_names_preserve_uid_checks_and_malformed_rows_fail_closed(self):
        baseline = {self.package: {"uid": 10001}}
        for row in (f"42 10002 {self.package}:worker name", "42 10001", "42 invalid worker name"):
            with self.subTest(row=row):
                output = ("PID UID NAME\n" + row + "\n").encode()
                fake = FakeAdb([(["shell", "ps", "-A", "-n", "-o", "PID,UID,NAME"], output, 0)])
                d = device.Device("PRIVATE_SERIAL", {}, fake)
                with self.assertRaises(device.Failure): d.processes(baseline, device.Budget(20))
                self.assertEqual(1, fake.closed)

    def test_wrong_uid_and_surviving_uid_process_fail_closed(self):
        baseline = {self.package: {"uid": 10001}}
        for uid, name in [(10002, self.package), (10001, "libllama-server.so")]:
            fake = FakeAdb([(["shell", "ps", "-A", "-n", "-o", "PID,UID,NAME"], f"PID UID NAME\n42 {uid} {name}\n".encode(), 0)])
            d = device.Device("PRIVATE_SERIAL", {}, fake)
            if uid == 10002:
                with self.assertRaises(device.Failure): d.processes(baseline, device.Budget(15))
            else: self.assertEqual([(42, uid, name)], d.processes(baseline, device.Budget(15)))


class DriverTests(unittest.TestCase):
    def test_help_and_invalid_or_unconsented_modes_never_contact_adb(self):
        with mock.patch("subprocess.Popen", side_effect=AssertionError("No ADB")), redirect_stdout(io.StringIO()), redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as result: driver.main(["--help"], {})
            self.assertEqual(0, result.exception.code)
            for args, env in [(["--suite", "isolated", "--evidence-dir", "unused"], {}),
                              (["--suite", "native", "--evidence-dir", "unused", "--allow-device-writes"], {}),
                              (["--class", "content://private"], {})]:
                self.assertEqual(1, driver.main(args, env))

    def fake_session(self, tmp, mode, output=None, post_uid=10001, survivors=b"PID UID NAME\n"):
        paths = {}; replies = []
        def identities(uid):
            for index, pkg in enumerate([driver.TARGET, driver.TEST]):
                replies.extend([(["shell", "cmd", "package", "list", "packages", "-U", pkg], f"package:{pkg} uid:{uid+index}\n".encode(), 0),
                    (["shell", "pm", "path", pkg], b"package:/data/app/safe/base.apk\n", 0),
                    (["exec-out", "cat", "/data/app/safe/base.apk"], b"apk", 0)])
        for index, pkg in enumerate([driver.TARGET, driver.TEST]):
            path = Path(tmp) / f"{index}.apk"; path.write_bytes(b"apk"); paths[pkg] = path
        identities(10001)
        replies.append((["shell", "ps", "-A", "-n", "-o", "PID,UID,NAME"], b"PID UID NAME\n", 0))
        if output is None:
            total = len(driver.ROSTERS[mode]); output = b""
            for cls, method in sorted(driver.ROSTERS[mode]):
                fields = {"class": cls, "test": method, "numtests": str(total)}
                output += frame(1, **fields) + frame(0, **fields)
            output += f"OK ({total} tests)\nINSTRUMENTATION_CODE: -1\n".encode()
        replies.append((["shell", "am", "instrument", "-w", "-r", "-e", "class", driver.FILTERS[mode], driver.RUNNER], output, 0))
        for pkg in [driver.TARGET, driver.TEST]: replies.append((["shell", "am", "force-stop", pkg], b"", 0))
        replies.append((["shell", "ps", "-A", "-n", "-o", "PID,UID,NAME"], survivors, 0))
        identities(post_uid)
        fake = FakeAdb(replies)
        return device.Device("PRIVATE_SERIAL", paths, fake), fake

    def test_fixed_suite_rosters_complete_and_postflight_retained(self):
        self.assertEqual(26, len(driver.ROSTERS["isolated"]))
        for mode in ["native", "isolated"]:
            with tempfile.TemporaryDirectory() as tmp:
                session, fake = self.fake_session(tmp, mode)
                result = driver.run_suite(session, mode)
                self.assertTrue(result["passed"]); self.assertEqual(len(driver.ROSTERS[mode]), result["tests"])
                self.assertFalse(fake.replies); self.assertNotIn("PRIVATE_SERIAL", str(result))

    def test_incomplete_skipped_changed_uid_or_surviving_process_never_passes(self):
        cases = [{"output": b"OK (0 tests)\nINSTRUMENTATION_CODE: -1\n"}, {"post_uid": 11001},
                 {"survivors": b"PID UID NAME\n42 10001 libllama-server.so\n"}]
        for options in cases:
            with tempfile.TemporaryDirectory() as tmp:
                session, fake = self.fake_session(tmp, "native", **options)
                result = driver.run_suite(session, "native")
                self.assertFalse(result["passed"]); self.assertFalse(fake.replies)

    def test_fixed_roster_matches_compiled_source_and_excludes_crash_hang_native(self):
        import re
        base = SCRIPTS.parent / "app/src/androidTest/java/io/github/greprohan/llamadroid/modelimport"
        self.assertEqual(4, len(driver.METHODS))
        for cls, methods in driver.METHODS.items():
            self.assertEqual(set(methods), set(re.findall(r"@Test fun (\w+)\(", (base / (cls + ".kt")).read_text())))
        self.assertIn((driver.MODEL + "ModelImportScreenTest", "unknownSizeProviderRendersCopiedBytesWithoutPercent"), driver.ROSTERS["isolated"])
        self.assertFalse(any("Crash" in cls or "CancellationDevice" in cls or "Native" in cls for cls, method in driver.ROSTERS["isolated"]))

    def test_phase_and_teardown_expiry_remain_failures_without_extra_commands(self):
        fake = FakeAdb([]); session = device.Device("PRIVATE_SERIAL", {}, fake)
        baseline = {driver.TARGET: {"uid": 10001}, driver.TEST: {"uid": 10002}}
        b = device.Budget(-1)
        with self.assertRaises(device.Failure): session.teardown(baseline, b)
        self.assertFalse(fake.calls)
        for pkg in baseline:
            self.assertNotIn(pkg, str(b))

    def test_success_cannot_mask_failed_control_teardown_or_postflight_apk(self):
        for mutate in ["stop", "hash", "transport"]:
            with tempfile.TemporaryDirectory() as tmp:
                session, fake = self.fake_session(tmp, "native")
                if mutate == "stop":
                    index = next(i for i, r in enumerate(fake.replies) if "force-stop" in r[0])
                    args, data, status = fake.replies[index]; fake.replies[index] = (args, b"private failure", 1)
                elif mutate == "hash":
                    args, data, status = fake.replies[-1]; fake.replies[-1] = (args, b"bad", 0)
                else:
                    index = next(i for i, r in enumerate(fake.replies) if "instrument" in r[0])
                    args, data, status = fake.replies[index]; fake.replies[index] = (args, data, 1)
                result = driver.run_suite(session, "native")
                self.assertFalse(result["passed"]); self.assertFalse(fake.replies)
                self.assertNotIn("private failure", str(result))

    def test_cli_retains_only_bounded_parsed_private_evidence(self):
        import json
        with tempfile.TemporaryDirectory() as tmp:
            session, fake = self.fake_session(tmp, "native")
            destination = Path(tmp) / "fresh-evidence"
            stdout = io.StringIO(); stderr = io.StringIO()
            with redirect_stdout(stdout), redirect_stderr(stderr):
                result = driver.main(["--suite", "native", "--evidence-dir", str(destination), "--allow-device-writes"],
                    {"ANDROID_SERIAL": "PRIVATE_SERIAL"}, lambda serial, paths: session)
            self.assertEqual(0, result)
            text = (destination / "report.json").read_text()
            self.assertTrue(json.loads(text)["passed"]); self.assertLessEqual(len(text.encode()), 65536)
            self.assertNotIn("PRIVATE_SERIAL", text + stdout.getvalue() + stderr.getvalue())
            self.assertFalse(fake.replies)
            for args in fake.calls:
                self.assertFalse({"install", "uninstall", "clear", "kill-server"}.intersection(args))

    def test_existing_evidence_directory_cannot_be_overwritten(self):
        with tempfile.TemporaryDirectory() as tmp:
            keep = Path(tmp) / "report.json"; keep.write_text("owner")
            with redirect_stderr(io.StringIO()), mock.patch.object(driver, "run_suite", side_effect=AssertionError("No execution")):
                self.assertEqual(1, driver.main(["--suite", "native", "--evidence-dir", tmp, "--allow-device-writes"], {"ANDROID_SERIAL": "PRIVATE_SERIAL"}))
            self.assertEqual("owner", keep.read_text())


class CrashDriverTests(unittest.TestCase):
    run_id = "a" * 32
    def fixture(self, tmp, scenario="first", checkpoint="mid-copy", mutation=None):
        paths = {}; replies = []
        def identities():
            for index, pkg in enumerate([driver.TARGET, driver.TEST]):
                path = Path(tmp) / f"{index}.apk"; path.write_bytes(b"apk"); paths[pkg] = path
                replies.extend([(["shell", "cmd", "package", "list", "packages", "-U", pkg], f"package:{pkg} uid:{10001+index}\n".encode(), 0),
                    (["shell", "pm", "path", pkg], b"package:/data/app/safe/base.apk\n", 0),
                    (["exec-out", "cat", "/data/app/safe/base.apk"], b"apk", 0)])
        ps = ["shell", "ps", "-A", "-n", "-o", "PID,UID,NAME"]
        identities(); replies.append((ps, b"PID UID NAME\n", 0))
        args = {"orchestrated": "true", "runId": self.run_id, "scenario": scenario, "checkpoint": checkpoint}
        def command(cls, method):
            result = ["shell", "am", "instrument", "-w", "-r", "-e", "class", cls + "#" + method]
            for key, value in sorted(args.items()): result += ["-e", key, value]
            return result + [driver.RUNNER]
        ready = {"v": 1, "runId": self.run_id, "scenario": scenario, "checkpoint": checkpoint, "pid": 42, "uid": 10001, "startedElapsedMs": 100}
        setup_class = driver.MODEL + "ModelImportCancellationDeviceTest" if scenario == "cancel-hang" else driver.CRASH_SETUP
        verify_class = setup_class if scenario == "cancel-hang" else driver.CRASH_VERIFY
        setup = frame(1, **{"class": setup_class, "test": "stageUntilCheckpoint", "numtests": "1"})
        setup += frame(1001, model_import_checkpoint=json.dumps(ready))
        replies.append((command(setup_class, "stageUntilCheckpoint"), setup, 0))
        replies.append((ps, f"PID UID NAME\n42 10001 {driver.TARGET}\n".encode(), 0))
        def stops():
            for pkg in paths: replies.append((["shell", "am", "force-stop", pkg], b"", 0))
            replies.append((ps, b"PID UID NAME\n", 0))
        stops()
        verified = dict(ready, pid=43, startedElapsedMs=200)
        fields = {"class": verify_class, "test": "verifyAfterRestart", "numtests": "1"}
        output = frame(1, **fields) + frame(1002, model_import_verified=json.dumps(verified)) + frame(0, **fields) + b"OK (1 tests)\nINSTRUMENTATION_CODE: -1\n"
        replies.append((command(verify_class, "verifyAfterRestart"), output, 0))
        stops(); identities()
        if mutation: mutation(replies)
        fake = FakeAdb(replies)
        return device.Device("PRIVATE_SERIAL", paths, fake), fake

    def run_case(self, session, scenario="first", checkpoint="mid-copy"):
        with mock.patch.object(driver.uuid, "uuid4", return_value=SimpleNamespace(hex=self.run_id)):
            return driver.run_case(session, scenario, checkpoint)

    def test_every_explicit_first_replace_reset_pair_runs_separate_stop_and_verify(self):
        pairs = driver.expand_pairs("all", "all"); self.assertEqual(27, len(pairs))
        for scenario, checkpoint in pairs:
            with self.subTest(scenario=scenario, checkpoint=checkpoint), tempfile.TemporaryDirectory() as tmp:
                session, fake = self.fixture(tmp, scenario, checkpoint)
                report = self.run_case(session, scenario, checkpoint)
                self.assertTrue(report["passed"]); self.assertEqual(1, report["tests"])
                self.assertFalse(fake.replies)
                self.assertNotEqual(report["ready"]["pid"], report["verified"]["pid"])
                self.assertEqual(2, sum(args[-1] == driver.TARGET and "force-stop" in args for args in fake.calls))

    def test_cancel_hang_pairs_use_separate_filters_and_stop_provider_process(self):
        self.assertIn("cancel-hang", driver.PAIRS)
        self.assertEqual({"query-hung", "open-hung", "read-hung", "close-hung"}, set(driver.PAIRS["cancel-hang"]))
        def provider(replies):
            args, data, status = replies[8]
            replies[8] = (args, data + f"44 10002 {driver.TEST}:model_import_fixture\n".encode(), status)
        for checkpoint in driver.PAIRS["cancel-hang"]:
            with self.subTest(checkpoint=checkpoint), tempfile.TemporaryDirectory() as tmp:
                session, fake = self.fixture(tmp, "cancel-hang", checkpoint, provider)
                report = self.run_case(session, "cancel-hang", checkpoint)
                self.assertTrue(report["passed"]); self.assertFalse(fake.replies)
                self.assertEqual(2, sum("force-stop" in args and args[-1] == driver.TEST for args in fake.calls))
        self.assertFalse(any("CancellationDevice" in cls for cls, method in driver.ROSTERS["isolated"]))

    def test_fault_at_each_state_keeps_failure_and_independent_final_teardown(self):
        def change(index, transform):
            def apply(replies):
                args, data, status = replies[index]; replies[index] = (args, transform(data), status)
            return apply
        mutations = []
        # Failure before ready/process validation: verification must not start.
        for transform in [lambda b: b"", lambda b: b.replace(b'"runId": "' + b'a'*32, b'"runId": "' + b'b'*32),
                          lambda b: b + b"INSTRUMENTATION_CODE: -1\n"]:
            def before_ready(replies, transform=transform):
                change(7, transform)(replies); del replies[8:13]
            mutations.append(before_ready)
        def wrong_process(replies):
            change(8, lambda b: b.replace(b"42 10001", b"99 10001"))(replies); del replies[9:13]
        mutations.append(wrong_process)
        def failed_stop(replies):
            args, data, status = replies[9]; replies[9] = (args, data, 1); del replies[12]
        mutations.append(failed_stop)
        # Verification framing/count/process identity and timeout must never be masked by cleanup.
        for transform in [lambda b: b.replace(b'"pid": 43', b'"pid": 42').replace(b'"startedElapsedMs": 200', b'"startedElapsedMs": 100'),
                          lambda b: b.replace(b"model_import_verified", b"unrecognized_verified"),
                          lambda b: b.replace(b'"uid": 10001', b'"uid": 10002'),
                          lambda b: b.replace(b"OK (1 tests)\nINSTRUMENTATION_CODE: -1\n", b""),
                          lambda b: b.replace(b"INSTRUMENTATION_STATUS_CODE: 0", b"INSTRUMENTATION_STATUS_CODE: -3")]:
            mutations.append(change(12, transform))
        def timed_out(replies):
            args, data, status = replies[12]; replies[12] = (args, data, "timeout")
        mutations.append(timed_out)
        for mutate in mutations:
            with self.subTest(mutation=mutate), tempfile.TemporaryDirectory() as tmp:
                session, fake = self.fixture(tmp, mutation=mutate)
                report = self.run_case(session)
                self.assertFalse(report["passed"]); self.assertFalse(fake.replies)
                self.assertTrue(report["teardown"]); self.assertTrue(report["retained"])
                self.assertFalse(report["fixture_cleanup"])

    def test_partial_extra_frame_after_ready_cannot_be_treated_as_still_paused(self):
        def malformed(replies):
            args, data, status = replies[7]; replies[7] = (args, data + b"INSTRUMENTATION_STATUS: class=unfinished", status)
            del replies[8:13]
        with tempfile.TemporaryDirectory() as tmp:
            session, fake = self.fixture(tmp, mutation=malformed)
            report = self.run_case(session)
            self.assertFalse(report["passed"]); self.assertFalse(fake.replies)

    def test_final_teardown_or_postflight_failure_cannot_become_a_case_pass(self):
        def failed_final_stop(replies):
            args, data, status = replies[13]; replies[13] = (args, data, 1)
        def changed_post_uid(replies):
            args, data, status = replies[16]; replies[16] = (args, data.replace(b"10001", b"10003"), status)
        for mutate in [failed_final_stop, changed_post_uid]:
            with tempfile.TemporaryDirectory() as tmp:
                session, fake = self.fixture(tmp, mutation=mutate)
                report = self.run_case(session)
                self.assertFalse(report["passed"]); self.assertFalse(fake.replies)
                self.assertTrue(report["fixture_cleanup"])

    def test_pid_reuse_is_allowed_only_with_distinct_process_start_identity(self):
        def reuse(replies):
            args, data, status = replies[12]; replies[12] = (args, data.replace(b'"pid": 43', b'"pid": 42'), status)
        with tempfile.TemporaryDirectory() as tmp:
            session, fake = self.fixture(tmp, mutation=reuse)
            self.assertTrue(self.run_case(session)["passed"]); self.assertFalse(fake.replies)

    def test_matrix_keeps_original_baseline_and_marks_unrun_pairs_after_failure(self):
        pairs = [("first", "mid-copy"), ("replace", "mid-copy"), ("reset", "reset-delete")]
        baseline = {driver.TARGET: {"uid": 10001}}
        replies = [{"passed": True, "tests": 1, "baseline": baseline}, {"passed": False, "tests": 0, "baseline": baseline}]
        with mock.patch.object(driver, "run_case", side_effect=replies) as run:
            result = driver.run_matrix(object(), pairs)
        self.assertFalse(result["passed"]); self.assertEqual(3, result["requested"])
        self.assertEqual("prior case failed", result["cases"][2]["not_run"])
        self.assertEqual(baseline, run.call_args_list[1].args[3])
        with self.assertRaises(device.Failure): driver.run_matrix(object(), [pairs[0], pairs[0]])

    def test_cli_rejects_incompatible_modes_before_device_or_evidence_access(self):
        invalid = [["--suite", "native", "--checkpoint", "all"], ["--scenario", "first"],
                   ["--scenario", "first", "--checkpoint", "reset-cleared"],
                   ["--scenario", "all", "--checkpoint", "mid-copy"],
                   ["--suite", "native", "--scenario", "first"]]
        with mock.patch("subprocess.Popen", side_effect=AssertionError("No ADB")), redirect_stderr(io.StringIO()):
            for args in invalid:
                self.assertEqual(1, driver.main(args + ["--evidence-dir", "never-created", "--allow-device-writes"], {"ANDROID_SERIAL": "PRIVATE_SERIAL"}))

    def test_invalid_pairs_fail_before_any_session_or_device_mutation(self):
        for scenario, checkpoint in [("first", "reset-cleared"), ("reset", "mid-copy"), ("all", "mid-copy"), ("unknown", "all")]:
            with self.assertRaises(device.Failure): driver.expand_pairs(scenario, checkpoint)


if __name__ == "__main__":
    unittest.main()
