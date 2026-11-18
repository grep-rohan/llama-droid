"""Synthetic transport fixtures only; actual runtime results live in evidence docs."""
import copy
import hashlib
import unittest
from types import SimpleNamespace
from unittest.mock import Mock

from model_import_device import Failure
from model_import_live_heap import verify_live_heap
from model_import_live_callbacks import LiveCopyCallbacks, parse_meminfo, parse_process_epoch


def proc(pid=111, start=42):
    # Fields after comm start at state(field3); starttime is field22.
    return f'{pid} (app name) S '.encode()+b' '.join([b'0']*18+[str(start).encode()])+b'\n'


def mem(pid=111):
    return (f'Applications Memory Usage (in Kilobytes):\r\nUptime: 2000 Realtime: 2000\r\n'
            f'** MEMINFO in pid {pid} [io.github.greprohan.llamadroid] **\r\n'
            ' Native Heap 100 90 0 0 120 24000 18000 6000\r\n'
            ' Dalvik Heap 10 9 0 0 80 4000 2000 2000\r\n'
            ' TOTAL PSS: 55000 TOTAL RSS: 190000 TOTAL SWAP (KB): 0\r\n').encode()


class MetadataTest(unittest.TestCase):
    def test_process_start_ticks_and_uid(self):
        self.assertEqual(parse_process_epoch(proc(),111,10000), dict(pid=111,uid=10000,start_ticks=42))

    def test_wrong_pid_truncation_dead_or_invalid_epoch_rejected(self):
        for raw in [proc(112), b'111 (short) S 0', proc(start=0), proc().replace(b') S ',b') Z ')]:
            with self.subTest(raw=raw),self.assertRaises(Failure): parse_process_epoch(raw,111,10000)

    def test_meminfo_exact_pid_and_heap_columns(self):
        self.assertEqual(parse_meminfo(mem(),111), dict(pid=111,java_size_KiB=4000,
                         java_alloc_KiB=2000,native_size_KiB=24000,native_alloc_KiB=18000,total_PSS_KiB=55000))

    def test_meminfo_wrong_pid_duplicate_or_missing_rows_rejected(self):
        for raw in [mem(112), mem()+mem(), mem().replace(b'Dalvik Heap',b'Other Heap'),b'No process found']:
            with self.subTest(size=len(raw)),self.assertRaises(Failure): parse_meminfo(raw,111)

    def test_oversized_or_invalid_output_rejected_without_raw_error(self):
        for parser,raw,args in [(parse_meminfo,b'x'*65537,(111,)),
                                (parse_process_epoch,b'x'*4097,(111,10000))]:
            with self.assertRaises(Failure) as error: parser(raw,*args)
            self.assertNotIn('xxxx',str(error.exception))


class CallbacksTest(unittest.TestCase):
    def setUp(self):
        self.now=0.;self.calls=[];self.prefix=b'x'*64
        self.path='no_backup/models/candidate-'+'c'*32+'.part'
        self.record=dict(mode=0o100600,links=1,inode=4,bytes=144,mtime=10,kind='record',record='selection',
                         name_sha256=hashlib.sha256(b'selection').hexdigest(),sha256='a'*64)
        self.payload=dict(mode=0o100600,links=1,inode=3,bytes=2**32,mtime=10,kind='payload',name_sha256='d'*64)
        self.baseline=dict(parent_identity=dict(mode=0o40771,inode=1),models=dict(mode=0o40700,links=2,inode=2,bytes=4096,mtime=10),
                           entries=[self.record,self.payload],legacy={'files/models':None,'files/model.gguf':None})
        self.candidate=dict(mode=0o100600,links=1,inode=5,bytes=100,mtime=10,kind='candidate',
                            name_sha256=hashlib.sha256(self.path.rsplit('/',1)[-1].encode()).hexdigest())
        self.store=copy.deepcopy(self.baseline);self.store['entries'].append(self.candidate)
        self.device=SimpleNamespace(prefix=['adb','-s','emulator-5554'],call=self.call)
        self.inventory=Mock(side_effect=lambda d:copy.deepcopy(self.store))
        self.process=dict(pid=111,uid=10000,start_ticks=42)
        self.callback=self.make()

    def call(self,args,budget,**kw):
        self.calls.append((args,kw));self.assertLessEqual(budget.end,10)
        if args[:3]==['shell','getprop','ro.kernel.qemu']:return b'1\n'
        if args[:3]==['shell','dumpsys','meminfo']:return mem()
        if args[0]=='exec-out':return self.prefix
        if 'find no_backup/models' in args[-1]:return (self.path+'\n').encode()
        return b'10000\n'+proc()

    def make(self,**kw):
        return LiveCopyCallbacks(self.device,self.inventory,self.baseline,self.process,2**32,
                  source_prefix_sha256=kw.get('source_prefix_sha256',hashlib.sha256(self.prefix).hexdigest()),clock=lambda:self.now)

    def test_real_transport_ports_bind_candidate_and_read_memory(self):
        frame=self.callback.snapshot(10)
        self.assertEqual(frame['candidate']['inode'],5)
        self.assertEqual(self.callback.expected['candidate']['prefix_sha256'],hashlib.sha256(self.prefix).hexdigest())
        memory=self.callback.meminfo(10)
        self.assertEqual(memory['native_size_KiB'],24000)
        proof=verify_live_heap(frame,memory,self.callback.snapshot(10),self.callback.expected,2**32)
        self.assertTrue(proof['partial_candidate_before_and_after'])
        self.assertEqual(self.device.call,self.call)
        expected=self.callback.expected;expected['candidate']['inode']=999
        self.assertEqual(self.callback.expected['candidate']['inode'],5)

    def test_idle_is_none_and_does_not_read_any_payload_prefix(self):
        self.store=copy.deepcopy(self.baseline)
        self.assertIsNone(self.callback.snapshot(10))
        self.assertIsNone(self.callback.expected)
        self.assertFalse(any(args[0]=='exec-out' for args,_ in self.calls))

    def test_unscoped_or_changed_candidate_is_never_bound(self):
        for prefix in [None,'f'*64]:
            callback=self.make(source_prefix_sha256=prefix)
            with self.subTest(prefix=prefix),self.assertRaises(Failure):callback.snapshot(10)
            self.assertIsNone(callback.expected)

    def test_replaced_candidate_after_binding_rejected(self):
        self.callback.snapshot(10);self.candidate['inode']+=1
        with self.assertRaises(Failure):self.callback.snapshot(10)

    def test_partial_candidate_must_remain_partial_and_regular(self):
        for key,value in [('mode',0o120600),('links',2),('bytes',63),('bytes',2**32)]:
            old=self.candidate[key];self.candidate[key]=value
            with self.subTest(key=key,value=value),self.assertRaises(Failure):self.callback.snapshot(10)
            self.candidate[key]=old

    def test_unknown_extra_changed_payload_record_and_root_reject(self):
        for section in ['extra','payload','record','root','legacy']:
            self.store=copy.deepcopy(self.baseline);self.store['entries'].append(copy.deepcopy(self.candidate))
            if section=='extra':self.store['entries'].append(dict(self.candidate,kind='unknown'))
            if section=='payload':self.store['entries'][1]['mtime']+=1
            if section=='record':self.store['entries'][0]['sha256']='f'*64
            if section=='root':self.store['models']['inode']+=1
            if section=='legacy':self.store['legacy']['files/model.gguf']={}
            with self.subTest(section=section),self.assertRaises(Failure):self.callback.snapshot(10)

    def test_legal_record_inode_and_directory_mtime_changes_allowed(self):
        self.store['entries'][0]['inode']+=1;self.store['models']['mtime']+=1
        self.assertEqual(self.callback.snapshot(10)['candidate']['inode'],5)

    def test_deadline_and_selector_refused_before_io(self):
        self.now=11
        with self.assertRaises(Failure):self.callback.snapshot(10)
        self.assertEqual(self.calls,[])
        self.now=0;self.device.prefix[-1]='OTHER'
        with self.assertRaises(Failure):self.callback.snapshot(10)
        self.assertEqual(self.calls,[])

    def test_inventory_calls_clamped_and_method_restored_on_failure(self):
        def failing(device):
            device.call(['shell','safe-read'],SimpleNamespace(end=999),cap=50)
            raise Failure('inventory failed')
        self.callback.inventory=failing
        with self.assertRaises(Failure):self.callback.snapshot(10)
        self.assertEqual(self.device.call,self.call)
        self.assertTrue(any(args==['shell','safe-read'] for args,_ in self.calls))

    def test_observed_crlf_uid_line_is_normalized_without_relaxing_identity(self):
        def framed(args,budget,**kw):
            raw=self.call(args,budget,**kw)
            return raw.replace(b'\n',b'\r\n') if 'stat -c %u' in args[-1] else raw
        self.device.call=framed
        self.assertEqual(self.callback.snapshot(10)['process'],self.process)
        self.assertEqual(self.callback.meminfo(10)['pid'],111)
        self.assertEqual(self.device.call,framed)

    def test_qemu_refused_before_inventory(self):
        self.device.call=Mock(return_value=b'0\n');original=self.device.call
        with self.assertRaises(Failure):self.callback.snapshot(10)
        self.inventory.assert_not_called()
        self.assertIs(self.device.call,original)

    def test_candidate_completion_or_swap_during_snapshot_rejects(self):
        for mode in ['complete','swap','disappear']:
            first=copy.deepcopy(self.store);last=copy.deepcopy(self.store)
            if mode=='complete':last['entries'][-1]['bytes']=2**32
            if mode=='swap':last['entries'][-1]['inode']+=1
            if mode=='disappear':last['entries'].pop()
            self.inventory.side_effect=[first,last]
            callback=self.make()
            with self.subTest(mode=mode),self.assertRaises(Failure):callback.snapshot(10)
            self.assertIsNone(callback.expected)

    def test_counter_pre_stat_cannot_rewind_then_recover(self):
        first=copy.deepcopy(self.store);last=copy.deepcopy(self.store);last['entries'][-1]['bytes']=300
        self.inventory.side_effect=[first,last]
        self.callback.snapshot(10)
        first['entries'][-1]['bytes']=250;last['entries'][-1]['bytes']=350
        self.inventory.side_effect=[first,last]
        with self.assertRaises(Failure):self.callback.snapshot(10)

    def test_memory_uid_or_epoch_change_rejected(self):
        count=[0]
        def changed(args,budget,**kw):
            if 'stat -c %u' in args[-1]:
                count[0]+=1
                return b'10000\n'+proc(start=42 if count[0]==1 else 43)
            return self.call(args,budget,**kw)
        self.device.call=changed
        with self.assertRaises(Failure):self.callback.meminfo(10)
        self.assertEqual(self.device.call,changed)

    def test_invalid_baseline_or_prefix_rejected_before_io(self):
        for baseline,prefix in [({},None),(self.baseline,True)]:
            with self.assertRaises(Failure):
                LiveCopyCallbacks(self.device,self.inventory,baseline,self.process,2**32,source_prefix_sha256=prefix)
        self.assertEqual(self.calls,[])

    def test_actual_prefix_path_is_strict_and_bound_to_inventory_name(self):
        self.path='no_backup/models/candidate-owner-unapproved.part'
        with self.assertRaises(Failure):self.callback.snapshot(10)
        self.assertFalse(any(args[0]=='exec-out' for args,_ in self.calls))


if __name__=='__main__': unittest.main()
