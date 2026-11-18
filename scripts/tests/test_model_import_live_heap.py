"""Synthetic metadata only; does not claim any Android memory/copy result."""
import copy
import unittest

from model_import_device import Failure
from model_import_live_heap import verify_live_heap


class LiveHeapTest(unittest.TestCase):
    def setUp(self):
        self.expected = dict(process=dict(pid=12572, uid=10234, start_ticks=123456),
                             root_identity=dict(parent_inode=2, model_inode=3),
                             record_sha256='a'*64, payload=dict(inode=4, bytes=2**32, mtime=10),
                             candidate=dict(inode=5, name_sha256='b'*64, prefix_sha256='c'*64))
        self.before = copy.deepcopy(self.expected)
        self.before['candidate'].update(mode=0o100600, links=1, bytes=2**31+1)
        self.after = copy.deepcopy(self.before)
        self.after['candidate']['bytes'] += 1048576
        self.memory = dict(pid=12572, java_size_KiB=4096, java_alloc_KiB=2048,
                           native_size_KiB=24000, native_alloc_KiB=18000, total_PSS_KiB=55000)

    def check(self):
        return verify_live_heap(self.before, self.memory, self.after, self.expected, 2**32)

    def test_same_owned_partial_copy_brackets_memory(self):
        saved = copy.deepcopy((self.before, self.memory, self.after, self.expected))
        result = self.check()
        self.assertEqual(result['java_native_heap_size_KiB'], 28096)
        self.assertGreater(result['candidate_bytes_before'], 2**31)
        self.assertEqual((self.before, self.memory, self.after, self.expected), saved)
        self.assertTrue(result['partial_candidate_before_and_after'])

    def test_pid_uid_and_process_start_ticks_cannot_change(self):
        for point in [self.before, self.after]:
            for field in ['pid', 'uid', 'start_ticks']:
                with self.subTest(point=id(point), field=field):
                    old = point['process'][field]; point['process'][field] += 1
                    with self.assertRaises(Failure): self.check()
                    point['process'][field] = old

    def test_meminfo_must_be_same_process(self):
        self.memory['pid'] += 1
        with self.assertRaises(Failure): self.check()

    def test_candidate_owner_identity_cannot_change(self):
        for point in [self.before, self.after]:
            for field, value in [('inode', 99), ('name_sha256', 'd'*64), ('prefix_sha256', 'd'*64),
                                 ('mode', 0o120600), ('links', 2)]:
                with self.subTest(point=id(point), field=field):
                    old = point['candidate'][field]; point['candidate'][field] = value
                    with self.assertRaises(Failure): self.check()
                    point['candidate'][field] = old

    def test_unowned_or_completed_candidate_is_not_inflight(self):
        for point in [self.before, self.after]:
            for size in [0, 63, 2**32, 2**32+1, True]:
                with self.subTest(size=size):
                    old = point['candidate']['bytes']; point['candidate']['bytes'] = size
                    with self.assertRaises(Failure): self.check()
                    point['candidate']['bytes'] = old

    def test_decreasing_candidate_rejected(self):
        self.after['candidate']['bytes'] = self.before['candidate']['bytes']-1
        with self.assertRaises(Failure): self.check()

    def test_root_record_and_old_payload_preserved_at_both_ends(self):
        for point in [self.before, self.after]:
            for field, value in [('root_identity', {}), ('record_sha256', 'd'*64), ('payload', {})]:
                with self.subTest(field=field):
                    old = point[field]; point[field] = value
                    with self.assertRaises(Failure): self.check()
                    point[field] = old

    def test_bad_memory_missing_or_non_numeric_rejected(self):
        for field in ['java_size_KiB', 'java_alloc_KiB', 'native_size_KiB', 'native_alloc_KiB', 'total_PSS_KiB']:
            for value in [None, -1, True]:
                with self.subTest(field=field, value=value):
                    old = self.memory[field]; self.memory[field] = value
                    with self.assertRaises(Failure): self.check()
                    self.memory[field] = old
        del self.memory['pid']
        with self.assertRaises(Failure): self.check()

    def test_guard_is_sampled_not_peak_heap_or_progress_proof(self):
        result = self.check()
        self.assertTrue(result['sampled_not_peak_heap'])
        self.assertNotIn('numeric_progress_proven', result)


if __name__ == '__main__':
    unittest.main()
