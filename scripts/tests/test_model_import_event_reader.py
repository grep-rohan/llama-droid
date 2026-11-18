import unittest
from types import SimpleNamespace
from unittest.mock import Mock

from model_import_device import Failure
from model_import_event_reader import EventReader


class ReaderTest(unittest.TestCase):
    def setUp(self):
        self.now = 0.0
        self.parts = []
        self.exit = None
        self.run_count = 0
        self.command = SimpleNamespace(run=self.transport_run, close=Mock(), proc=SimpleNamespace(poll=lambda: self.exit))
        self.factory = Mock(return_value=self.command)
        self.device = SimpleNamespace(prefix=['adb','-s','emulator-5554'], call=Mock(return_value=b'1\n'))

    def transport_run(self, deadline, consume, until=lambda: False):
        self.run_count += 1
        while self.parts:
            consume(self.parts.pop(0))
            if until(): return None
        if self.exit is not None: return self.exit
        self.now = deadline
        raise Failure('transport deadline expired')

    def reader(self):
        return EventReader(self.device, clock=lambda: self.now, command_factory=self.factory)

    def test_fixed_selector_refused_before_device_or_process_call(self):
        self.device.prefix[-1] = 'OTHER'
        with self.assertRaises(Failure): self.reader()
        self.device.call.assert_not_called(); self.factory.assert_not_called()

    def test_physical_qemu_property_refused(self):
        self.device.call.return_value = b'0\n'
        with self.assertRaises(Failure): self.reader()
        self.factory.assert_not_called()

    def test_remote_timeout_only_no_app_input_or_stop(self):
        self.reader()
        argv = self.factory.call_args.args[0]
        self.assertEqual(argv[:3], ['adb','-s','emulator-5554'])
        self.assertEqual(argv[3:], ['shell','timeout -s INT 10 uiautomator events'])
        self.assertEqual(self.factory.call_args.kwargs['limit'], 4*1024*1024)

    def test_fragmented_lines_are_delivered_complete_and_once(self):
        reader = self.reader(); self.parts = [b'abc',b'def\r\nnext\n']
        self.assertEqual(reader.read(20), b'abcdef\r\n')
        count = self.run_count
        self.assertEqual(reader.read(20), b'next\n')
        self.assertEqual(self.run_count, count)

    def test_bounded_empty_poll_not_listener_readiness(self):
        reader = self.reader()
        self.assertEqual(reader.read(20), b'')
        self.assertLessEqual(self.now, .2)

    def test_expired_lease_never_delivers_buffered_frame(self):
        reader = self.reader(); self.now = 11
        with self.assertRaises(Failure): reader.read(20)
        self.assertEqual(self.run_count, 0)

    def test_oversized_line_rejected(self):
        reader = self.reader(); self.parts = [b'x'*16385]
        with self.assertRaises(Failure): reader.read(20)

    def test_ended_observer_never_arms_from_stale_buffer(self):
        reader = self.reader(); self.exit = 124; self.parts = [b'old frame\n']
        with self.assertRaises(Failure): reader.read(20)

    def test_close_waits_for_expected_remote_timeout_and_is_once(self):
        reader = self.reader(); self.exit = 124
        reader.close(20); reader.close(20)
        self.command.close.assert_called_once()
        self.assertEqual(self.run_count, 1)

    def test_unexpected_exit_is_failure_even_when_local_pipe_closed(self):
        reader = self.reader(); self.exit = 0
        with self.assertRaises(Failure): reader.close(20)
        self.command.close.assert_called_once()


if __name__ == '__main__':
    unittest.main()
