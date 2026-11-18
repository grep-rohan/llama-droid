"""Synthetic callback integration only; no Android events/memory are fabricated."""
import copy
import unittest

from model_import_device import Failure
from model_import_live_collector import LiveCollector
from test_model_import_progress_events import event


class CollectorTest(unittest.TestCase):
    def setUp(self):
        self.now = 0.0
        self.calls = []
        self.chunks = [event(package='other.app'), event(100, 2001),
                       event(2**31+1, 2002), event(3_000_000_000, 2003)]
        self.expected = dict(process=dict(pid=111, uid=10000, start_ticks=42),
                             root_identity={'inode': 2}, record_sha256='a'*64,
                             payload={'inode': 3, 'bytes': 2**32},
                             candidate=dict(inode=4, name_sha256='b'*64, prefix_sha256='c'*64))
        self.frames = []
        for size in [100, 2**31+1, 3_000_000_000]:
            for n in [size, size+50]:
                frame = copy.deepcopy(self.expected)
                frame['candidate'].update(mode=0o100600, links=1, bytes=n)
                self.frames.append(frame)
        self.closed = []
        self.collector = LiveCollector(self.read, self.close, 2**32, 20, clock=lambda: self.now)

    def read(self, deadline):
        self.calls.append('read'); self.now += .01
        self.assertEqual(deadline, 20)
        return self.chunks.pop(0) if self.chunks else b''

    def close(self, deadline):
        self.closed.append(deadline)

    def snapshot(self, deadline):
        self.calls.append('snapshot'); self.now += .01
        self.assertEqual(deadline, 20)
        return self.frames.pop(0)

    def memory(self, deadline):
        self.calls.append('memory'); self.now += .01
        return dict(pid=111, java_size_KiB=4000, java_alloc_KiB=2000,
                    native_size_KiB=24000, native_alloc_KiB=18000, total_PSS_KiB=55000)

    def collect(self):
        return self.collector.collect(self.snapshot, self.memory, self.expected, start_event_uptime_ms=2000)

    def test_fresh_arm_then_ordered_brackets_and_progress(self):
        self.collector.arm()
        result = self.collect()
        self.assertEqual(result['status'], 'OBSERVED_PARTIAL_COPY_ONLY')
        self.assertEqual(len(result['heap_samples']), 3)
        self.assertEqual([s['bytes'] for s in result['progress_samples']], [100, 2**31+1, 3000000000])
        self.assertEqual(self.calls, ['read']+['read', 'snapshot', 'memory', 'snapshot']*3)
        self.assertEqual(self.closed, [22])
        self.assertTrue(self.collector.closed)

    def test_no_arm_means_no_snapshot_or_memory(self):
        with self.assertRaises(Failure): self.collect()
        self.assertEqual(self.calls, [])

    def test_previous_probe_flag_or_banner_does_not_arm(self):
        self.chunks = [b'LD_ARMED\n']+[b'']*128
        with self.assertRaises(Failure): self.collector.arm()
        self.assertEqual(self.closed, [22])
        self.assertNotIn('memory', self.calls)

    def test_pre_copy_numeric_feedback_is_not_carried_into_collect(self):
        self.chunks[0] += event(2**32, 1000)
        self.collector.arm()
        self.chunks[0] = event(2**32, 1000)+self.chunks[0]
        result = self.collect()
        self.assertEqual(result['progress_samples'][0]['bytes'], 100)

    def test_expired_deadline_has_no_next_io(self):
        self.now = 21
        with self.assertRaises(Failure): self.collector.arm()
        self.assertEqual(self.calls, [])
        self.assertEqual(self.closed, [22])

    def test_callback_overrun_rejects_before_memory(self):
        self.collector.arm()
        def late(deadline):
            self.now = deadline+1
            return self.frames.pop(0)
        with self.assertRaises(Failure):
            self.collector.collect(late, self.memory, self.expected, start_event_uptime_ms=2000)
        self.assertNotIn('memory', self.calls)
        self.assertEqual(self.closed, [22])

    def test_process_change_during_meminfo_rejects(self):
        self.collector.arm(); self.frames[1]['process']['start_ticks'] += 1
        with self.assertRaises(Failure): self.collect()
        self.assertEqual(self.closed, [22])

    def test_copy_end_during_memory_does_not_accept_heap_sample(self):
        self.collector.arm(); self.frames[1] = None
        with self.assertRaises(Failure): self.collect()
        self.assertEqual(self.closed, [22])

    def test_eof_and_truncated_events_block(self):
        self.chunks = [event()[:-2], None]
        with self.assertRaises(Failure): self.collector.arm()
        self.assertEqual(self.closed, [22])

    def test_one_shot_no_rearm_or_recollect(self):
        self.collector.arm(); self.collect(); count = len(self.calls)
        with self.assertRaises(Failure): self.collector.arm()
        with self.assertRaises(Failure): self.collect()
        self.assertEqual(len(self.calls), count)
        self.assertEqual(len(self.closed), 1)

    def test_teardown_failure_prevents_success(self):
        self.collector.arm()
        self.collector.close_events = lambda end: (_ for _ in ()).throw(RuntimeError('private diagnostic'))
        with self.assertRaises(Failure) as error: self.collect()
        self.assertNotIn('private', str(error.exception))

    def test_context_closes_if_caller_fails_between_arm_and_collect(self):
        with self.assertRaises(RuntimeError):
            with self.collector:
                self.collector.arm()
                raise RuntimeError('caller failed before source selection')
        self.assertEqual(self.closed, [22])

    def test_invalid_event_chunk_rejects_and_closes(self):
        self.chunks = [b'x'*16385]
        with self.assertRaises(Failure): self.collector.arm()
        self.assertEqual(self.closed, [22])

    def test_counter_rewind_between_heap_intervals_rejected(self):
        self.collector.arm()
        self.frames[2]['candidate']['bytes'] = 99
        with self.assertRaises(Failure): self.collect()
        self.assertEqual(self.closed, [22])

    def test_no_numeric_feedback_cannot_pass_or_poll_forever(self):
        self.collector.arm(); self.chunks = []
        self.frames = []
        for size in range(100, 3300, 100):
            for delta in [0, 50]:
                frame = copy.deepcopy(self.expected)
                frame['candidate'].update(mode=0o100600, links=1, bytes=size+delta)
                self.frames.append(frame)
        with self.assertRaisesRegex(Failure, 'sample bound'): self.collect()
        self.assertEqual(self.calls.count('memory'), 32)
        self.assertEqual(self.closed, [22])

    def test_invalid_deadline_rejected_without_callbacks(self):
        for end in [True, -1, float('inf'), 100]:
            with self.subTest(end=end), self.assertRaises(Failure):
                LiveCollector(self.read, self.close, 2**32, end, clock=lambda: 0)
        self.assertEqual(self.calls, [])


if __name__ == '__main__':
    unittest.main()
