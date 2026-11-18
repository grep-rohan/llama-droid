"""No adb/cache dependencies: fail closed on any unproven stopped-store change."""
import copy
import unittest

from model_import_stopped_store import StoppedStoreError, stopped_record_view


class StoppedStoreTest(unittest.TestCase):
    def setUp(self):
        stat = dict(mode=0o100600, links=1, inode=11, bytes=144, mtime=10)
        record = dict(stat, kind='record', record='selection', name_sha256='a'*64, sha256='b'*64)
        payload = dict(stat, inode=12, bytes=2**32, kind='payload', name_sha256='c'*64)
        self.before = dict(parent_identity=dict(mode=0o40771, inode=8),
                           models=dict(mode=0o40700, links=2, inode=9, bytes=4096, mtime=10),
                           entries=[record, payload], legacy={'files/models': None, 'files/model.gguf': None})
        candidate = dict(stat, inode=13, bytes=23068696, kind='candidate', name_sha256='d'*64)
        self.captured = {k: candidate[k] for k in ['name_sha256', 'inode', 'bytes']}
        self.stopped = copy.deepcopy(self.before)
        self.stopped['entries'].append(candidate)
        self.stopped['models']['mtime'] += 1

    def check(self, **kw):
        return stopped_record_view(self.before, self.stopped, self.captured, 2**32,
                                   process_pids=kw.get('process_pids', []))

    def test_exact_candidate_projection_is_nonmutating(self):
        saved = copy.deepcopy((self.before, self.stopped, self.captured))
        view = self.check()
        self.assertEqual(view['entries'], self.stopped['entries'][:2])
        self.assertEqual((self.before, self.stopped, self.captured), saved)
        view['entries'][0]['bytes'] = 0
        self.assertEqual(self.stopped['entries'][0]['bytes'], 144)

    def test_checked_record_replacement_may_change_inode_and_mtime(self):
        self.stopped['entries'][0].update(inode=20, mtime=30)
        self.assertEqual(self.check()['entries'][0]['inode'], 20)

    def test_payload_stat_must_be_exact(self):
        for key in ['inode', 'mtime', 'bytes', 'mode', 'links']:
            with self.subTest(key=key):
                original = self.stopped['entries'][1][key]
                self.stopped['entries'][1][key] += 1
                with self.assertRaises(StoppedStoreError): self.check()
                self.stopped['entries'][1][key] = original

    def test_record_changes_rejected(self):
        for key, value in [('sha256', 'e'*64), ('bytes', 145), ('record', 'selection.next'),
                           ('name_sha256', 'e'*64), ('mode', 0o120600), ('links', 2)]:
            with self.subTest(key=key):
                original = self.stopped['entries'][0][key]
                self.stopped['entries'][0][key] = value
                with self.assertRaises(StoppedStoreError): self.check()
                self.stopped['entries'][0][key] = original

    def test_candidate_identity_type_links_and_length_exact(self):
        for key, value in [('inode', 99), ('name_sha256', 'e'*64), ('bytes', 1),
                           ('mode', 0o120600), ('links', 2)]:
            with self.subTest(key=key):
                original = self.stopped['entries'][2][key]
                self.stopped['entries'][2][key] = value
                with self.assertRaises(StoppedStoreError): self.check()
                self.stopped['entries'][2][key] = original

    def test_candidate_must_be_strictly_partial(self):
        for size in [0, 2**32, 2**32+1, -1]:
            with self.subTest(size=size):
                self.captured['bytes'] = self.stopped['entries'][2]['bytes'] = size
                with self.assertRaises(StoppedStoreError): self.check()

    def test_unknown_or_duplicate_extras_rejected(self):
        for kind in ['candidate', 'record', 'payload', 'unknown']:
            with self.subTest(kind=kind):
                self.stopped['entries'].append(dict(self.stopped['entries'][2], kind=kind))
                with self.assertRaises(StoppedStoreError): self.check()
                self.stopped['entries'].pop()

    def test_missing_candidate_rejected(self):
        self.stopped['entries'].pop()
        with self.assertRaises(StoppedStoreError): self.check()

    def test_root_and_legacy_identity_changes_rejected(self):
        for section, key in [('parent_identity', 'inode'), ('models', 'inode'), ('models', 'mode'),
                             ('models', 'links'), ('legacy', 'files/model.gguf')]:
            with self.subTest(section=section, key=key):
                old = self.stopped[section][key]
                self.stopped[section][key] = 77
                with self.assertRaises(StoppedStoreError): self.check()
                self.stopped[section][key] = old

    def test_live_or_unknown_process_state_rejected(self):
        for pids in [[123], None, False, '']:
            with self.subTest(pids=pids), self.assertRaises(StoppedStoreError):
                self.check(process_pids=pids)

    def test_baseline_must_itself_be_idle(self):
        self.before['entries'].append(copy.deepcopy(self.stopped['entries'][2]))
        with self.assertRaises(StoppedStoreError): self.check()


if __name__ == '__main__':
    unittest.main()
