"""Synthetic bounded selection records, never model weights or device results."""
import copy
import hashlib
import struct
import unittest

from model_import_device import Failure
from model_import_postflight import decode_active_selection, verify_committed


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


class CommitTest(unittest.TestCase):
    def setUp(self):
        self.raw=record();self.prefix=b'x'*64
        oldraw=record(model=b'b'*32,name=b'old.gguf');old=decode_active_selection(oldraw)
        def entry(kind,inode,size,name_sha,**extra):
            return dict(mode=0o100600,links=1,inode=inode,bytes=size,mtime=10,kind=kind,name_sha256=name_sha,**extra)
        def store(raw,inode,model):
            return dict(parent_identity=dict(mode=0o40771,inode=1),models=dict(mode=0o40700,links=2,inode=2,bytes=4096,mtime=10),
                        legacy={'files/models':None,'files/model.gguf':None},entries=[
                        entry('record',inode+1,len(raw),sha(b'selection'),record='selection',sha256=sha(raw)),
                        entry('payload',inode,2**32,sha(b'model-'+model+b'.gguf'))])
        before=store(oldraw,10,b'b'*32);old['payload']=copy.deepcopy(before['entries'][1])
        self.expected=dict(before_active=old,before_store=before,
            candidate=dict(inode=20,name_sha256=sha(b'candidate-'+b'c'*32+b'.part'),prefix_sha256=sha(self.prefix)),
            source=dict(size=2**32,name_sha256=sha(b'new.gguf'),prefix_sha256=sha(self.prefix)),
            process=dict(pid=111,uid=10000,start_ticks=42),
            identity={'io.github.greprohan.llamadroid':dict(uid=10000,sha256='d'*64,bytes=79332034)})
        self.observed=dict(store=store(self.raw,20,b'c'*32),record_bytes=self.raw,payload_prefix=self.prefix,
            process=copy.deepcopy(self.expected['process']),identity=copy.deepcopy(self.expected['identity']),
            ui=dict(target_focused=True,keyguard=False,phase='IMPORTED',replace_enabled=True,
                    pending_visible=False,cancel_visible=False,reset_visible=False,
                    active_name_sha256=sha(b'new.gguf'),active_size=2**32))

    def check(self):return verify_committed(self.expected,self.observed)

    def test_new_record_references_same_promoted_candidate(self):
        before=copy.deepcopy((self.expected,self.observed));result=self.check()
        self.assertEqual(result['status'],'COMMITTED_COPY_METADATA_VERIFIED')
        self.assertEqual(result['active']['size'],2**32)
        self.assertTrue(result['same_candidate_promoted'])
        self.assertFalse(result['body_integrity_proven'])
        self.assertFalse(result['external_source_cleanup_verified'])
        self.assertEqual((self.expected,self.observed),before)

    def test_unknown_candidate_extra_payload_or_record_sidecar_rejected(self):
        for kind in ['candidate','payload','record','unknown']:
            self.observed['store']['entries'].append(dict(self.observed['store']['entries'][1],kind=kind))
            with self.subTest(kind=kind),self.assertRaises(Failure):self.check()
            self.observed['store']['entries'].pop()

    def test_wrong_candidate_inode_name_or_prefix_rejected(self):
        for key,value in [('inode',21),('name_sha256','e'*64),('prefix_sha256','e'*64)]:
            old=self.expected['candidate'][key];self.expected['candidate'][key]=value
            with self.subTest(key=key),self.assertRaises(Failure):self.check()
            self.expected['candidate'][key]=old
        self.observed['payload_prefix']=b'y'*64
        with self.assertRaises(Failure):self.check()

    def test_source_size_name_or_record_reference_rejected(self):
        for key,value in [('size',2**32+1),('name_sha256','e'*64),('prefix_sha256','e'*64)]:
            old=self.expected['source'][key];self.expected['source'][key]=value
            with self.subTest(key=key),self.assertRaises(Failure):self.check()
            self.expected['source'][key]=old
        self.observed['store']['entries'][1]['name_sha256']='e'*64
        with self.assertRaises(Failure):self.check()

    def test_pending_changed_epoch_or_unchanged_old_selection_rejected(self):
        for raw in [record(pending=True),record(epoch=b'f'*32),record(model=b'b'*32,name=b'old.gguf')]:
            self.observed['record_bytes']=raw
            self.observed['store']['entries'][0].update(bytes=len(raw),sha256=sha(raw))
            with self.subTest(raw_size=len(raw)),self.assertRaises(Failure):self.check()

    def test_record_digest_size_type_and_payload_metadata_rejected(self):
        for index,key,value in [(0,'sha256','e'*64),(0,'bytes',999),(0,'record','selection.next'),
                                (1,'mode',0o120600),(1,'links',2),(1,'bytes',2**32-1)]:
            old=self.observed['store']['entries'][index][key];self.observed['store']['entries'][index][key]=value
            with self.subTest(index=index,key=key),self.assertRaises(Failure):self.check()
            self.observed['store']['entries'][index][key]=old

    def test_root_legacy_identity_and_process_epoch_changes_rejected(self):
        for section,key,value in [('process','start_ticks',43),('process','uid',10001),('identity','extra',{} )]:
            old=copy.deepcopy(self.observed[section]);self.observed[section][key]=value
            with self.subTest(section=section,key=key),self.assertRaises(Failure):self.check()
            self.observed[section]=old
        for section,key in [('parent_identity','inode'),('models','inode'),('legacy','files/model.gguf')]:
            old=self.observed['store'][section][key];self.observed['store'][section][key]=99
            with self.subTest(section=section),self.assertRaises(Failure):self.check()
            self.observed['store'][section][key]=old

    def test_busy_mismatched_or_unfocused_ui_rejected(self):
        for key,value in [('target_focused',False),('keyguard',True),('phase','COPYING'),('replace_enabled',False),
                          ('pending_visible',True),('cancel_visible',True),('reset_visible',True),
                          ('active_size',2**32-1),('active_name_sha256','e'*64)]:
            old=self.observed['ui'][key];self.observed['ui'][key]=value
            with self.subTest(key=key),self.assertRaises(Failure):self.check()
            self.observed['ui'][key]=old

    def test_ready_phase_and_legal_directory_record_stat_changes_accepted(self):
        self.observed['ui']['phase']='READY'
        self.observed['store']['models']['mtime']+=1
        self.observed['store']['entries'][0]['inode']+=1
        self.assertTrue(self.check()['same_candidate_promoted'])


if __name__=='__main__':unittest.main()
