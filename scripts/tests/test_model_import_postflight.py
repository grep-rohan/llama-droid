"""Synthetic bounded selection records, never model weights or device results."""
import hashlib
import struct
import unittest

from model_import_device import Failure
from model_import_postflight import decode_active_selection


def sha(value):
    return hashlib.sha256(value).hexdigest()


def record(*, epoch=b'a'*32, model=b'c'*32, size=2**32, name=b'new.gguf', pending=False):
    return (b'LDMR\x01\x00'+epoch+(b'\x01'+b'f'*32 if pending else b'\x00')+
            b'\x01'+model+struct.pack('>qH',size,len(name))+name)


class SelectionTest(unittest.TestCase):
    def test_64bit_active_record_returns_only_redacted_fields(self):
        parsed=decode_active_selection(record())
        self.assertEqual(parsed['size'],2**32)
        self.assertEqual(parsed['name_sha256'],sha(b'new.gguf'))
        self.assertEqual(parsed['candidate_name_sha256'],sha(b'candidate-'+b'c'*32+b'.part'))
        self.assertEqual(parsed['payload_name_sha256'],sha(b'model-'+b'c'*32+b'.gguf'))
        self.assertFalse(parsed['pending'])
        self.assertNotIn('new.gguf',repr(parsed))

    def test_pending_decodes_but_is_not_a_final_state(self):
        self.assertTrue(decode_active_selection(record(pending=True))['pending'])

    def test_truncated_oversized_trailing_reset_or_unknown_version_rejected(self):
        for raw in [record()[:-1],record()+b'x',b'x'*2049,record().replace(b'LDMR\x01\x00',b'LDMR\x01\x01'),
                    record().replace(b'LDMR\x01\x00',b'LDMR\x02\x00')]:
            with self.subTest(size=len(raw)),self.assertRaises(Failure):decode_active_selection(raw)

    def test_invalid_flags_identifiers_sizes_or_absent_active_rejected(self):
        for raw in [record(epoch=b'A'*32),record(model=b'../'+b'c'*29),record(size=-1),record(size=23),
                    record()[:38]+b'\x02'+record()[39:],b'LDMR\x01\x00'+b'a'*32+b'\x00\x00']:
            with self.subTest(raw_size=len(raw)),self.assertRaises(Failure):decode_active_selection(raw)

    def test_names_follow_bounded_sanitized_record_contract(self):
        for name in [b'',b'\xff',b' leading',b'trailing ',b'line\nname',b'a'*257,
                     'hidden\u200bname'.encode(),'line\u2028name'.encode()]:
            with self.subTest(name_size=len(name)),self.assertRaises(Failure):decode_active_selection(record(name=name))
        self.assertEqual(decode_active_selection(record(name=('\U0001f600'*256).encode()))['size'],2**32)


if __name__=='__main__':unittest.main()
