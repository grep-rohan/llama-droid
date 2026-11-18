"""Read-only live-copy callbacks; no device selection, source import or app input."""
import copy
import hashlib
import math
import re
import shlex
import stat
import time

from model_import_device import Budget, Failure

TARGET='io.github.greprohan.llamadroid'


def parse_process_epoch(raw, pid, uid):
    if (type(raw) is not bytes or len(raw)>4096 or type(pid) is not int or not 0<pid<2**31
            or type(uid) is not int or not 0<uid<2**31):
        raise Failure('bounded process metadata required')
    head,sep,tail=raw.strip().rpartition(b') ')
    fields=tail.split()
    if (not sep or not head.startswith(str(pid).encode()+b' (') or len(fields)<20
            or fields[0] not in [b'R',b'S',b'D',b'T',b't',b'I'] or not re.fullmatch(rb'[0-9]{1,19}',fields[19])
            or not 0<int(fields[19])<2**63):
        raise Failure('expected live process epoch required')
    return dict(pid=pid,uid=uid,start_ticks=int(fields[19]))


def parse_meminfo(raw, pid):
    if type(raw) is not bytes or len(raw)>65536 or type(pid) is not int or not 0<pid<2**31:
        raise Failure('bounded memory metadata required')
    try: text=raw.decode('utf-8').replace(chr(13),'')
    except UnicodeError: raise Failure('invalid meminfo encoding') from None
    headers=re.findall(r'\*\* MEMINFO in pid ([0-9]+) \[io\.github\.greprohan\.llamadroid\] \*\*',text)
    if headers!=[str(pid)]: raise Failure('same unique meminfo PID required')
    result={'pid':pid}
    for label,key in [('Native Heap','native'),('Dalvik Heap','java')]:
        rows=[line.strip()[len(label):].split() for line in text.splitlines() if line.strip().startswith(label+' ')]
        if len(rows)!=1 or len(rows[0])!=8 or not all(re.fullmatch('[0-9]{1,18}',v) for v in rows[0]):
            raise Failure('unique eight-column heap row required')
        values=list(map(int,rows[0]))
        if values[6]>values[5]: raise Failure('invalid heap allocation metadata')
        result[key+'_size_KiB']=values[5];result[key+'_alloc_KiB']=values[6]
    totals=re.findall(r'TOTAL PSS:\s*([0-9]{1,18})\b',text)
    if len(totals)!=1: raise Failure('unique total PSS required')
    result['total_PSS_KiB']=int(totals[0])
    return result


class LiveCopyCallbacks:
    """Serial, read-only callbacks around a validated inventory(device) function.

    The same Emulator object is retained; its calls are temporarily deadline-
    clamped during each callback. Do not use the object concurrently elsewhere.
    Prefix binding proves only the known 64-byte fixture prefix, not its body.
    """
    def __init__(self, device, inventory, baseline, process, total, *, source_prefix_sha256=None, clock=time.monotonic):
        self.device=device;self.inventory=inventory;self.baseline=copy.deepcopy(baseline)
        self.process=copy.deepcopy(process);self.total=total;self.prefix=source_prefix_sha256;self.clock=clock
        self._expected=None;self._checked=False;self._busy=False;self._last_size=0
        self._selector()
        if (not callable(inventory) or type(total) is not int or not 65<=total<2**63
                or not isinstance(baseline,dict) or not isinstance(baseline.get('entries'),list)
                or not all(isinstance(v,dict) for v in baseline['entries'])
                or not isinstance(process,dict) or set(process)!={'pid','uid','start_ticks'}
                or any(type(v) is not int or not 0<v<2**63 for v in process.values())
                or not process['pid']<2**31 or not process['uid']<2**31
                or (source_prefix_sha256 is not None and (not isinstance(source_prefix_sha256,str)
                    or not re.fullmatch('[0-9a-f]{64}',source_prefix_sha256)))):
            raise Failure('validated process, inventory and fixture baseline required')
        records=[v for v in self.baseline['entries'] if v.get('kind')=='record']
        payloads=[v for v in self.baseline['entries'] if v.get('kind')=='payload']
        if len(records)!=1 or len(payloads)!=1 or len(self.baseline['entries'])!=2:
            raise Failure('idle selection/payload baseline required')
        self.record=records[0];self.payload=payloads[0]
        self.root=self._root(self.baseline)
        if self._view(self.baseline) is not None: raise Failure('idle baseline required')

    def _selector(self):
        if len(self.device.prefix)!=3 or self.device.prefix[-2:]!=['-s','emulator-5554']:
            raise Failure('fixed emulator callbacks required')

    @staticmethod
    def _root(store):
        try:
            parent=store['parent_identity'];models=store['models']
            if not stat.S_ISDIR(parent['mode']) or not stat.S_ISDIR(models['mode']):
                raise Failure('safe model root required')
            return dict(parent_identity=copy.deepcopy(parent),models={k:models[k] for k in ['mode','inode','links']})
        except (KeyError,TypeError): raise Failure('complete model root required') from None

    def _view(self,store):
        try:
            if self._root(store)!=self.root or store['legacy']!=self.baseline['legacy']:
                raise Failure('root or legacy state changed')
            entries=store['entries'];kinds=[v['kind'] for v in entries]
            if sorted(kinds) not in [['payload','record'],['candidate','payload','record']]:
                raise Failure('unexpected model inventory entries')
            names=[v['name_sha256'] for v in entries]
            if len(set(names))!=len(names) or not all(re.fullmatch('[0-9a-f]{64}',v) for v in names):
                raise Failure('unique model inventory identities required')
            for v in entries:
                if (any(type(v[k]) is not int or not 0<=v[k]<2**63 for k in ['mode','links','inode','bytes','mtime'])
                        or not stat.S_ISREG(v['mode']) or v['links']!=1 or v['inode']==0):
                    raise Failure('regular single-link model entries required')
            record=next(v for v in entries if v['kind']=='record')
            stable=lambda v:{k:x for k,x in v.items() if k not in ['inode','mtime']}
            if (stable(record)!=stable(self.record) or record['record']!='selection'
                    or not 0<record['bytes']<=2048 or not re.fullmatch('[0-9a-f]{64}',record['sha256'])
                    or next(v for v in entries if v['kind']=='payload')!=self.payload):
                raise Failure('old selected record or payload changed')
            candidates=[v for v in entries if v['kind']=='candidate']
            if candidates and not 64<=candidates[0]['bytes']<self.total:
                raise Failure('strictly partial candidate with complete fixture prefix required')
            return candidates[0] if candidates else None
        except (KeyError,TypeError,StopIteration): raise Failure('invalid model inventory') from None

    def _bound(self,deadline,action):
        if self._busy: raise Failure('callbacks require serial access to emulator')
        self._selector();original=self.device.call
        def bounded(args,budget,*a,**kw):
            self._selector();remaining=deadline-self.clock()
            if remaining<=0: raise Failure('live callback deadline expired')
            kw['cap']=min(kw.get('cap',10),remaining)
            result=original(args,Budget(remaining,clock=self.clock),*a,**kw)
            if self.clock()>=deadline: raise Failure('live callback command overran deadline')
            return result
        if type(deadline) not in [int,float] or not math.isfinite(deadline) or not 0<deadline-self.clock()<=90:
            raise Failure('bounded live callback deadline required')
        self._busy=True;self.device.call=bounded
        try:
            if not self._checked:
                if self.device.call(['shell','getprop','ro.kernel.qemu'],Budget(5),cap=5).strip()!=b'1':
                    raise Failure('qemu callbacks required')
                self._checked=True
            result=action()
            if self.clock()>=deadline: raise Failure('live callback overran deadline')
            return result
        finally:
            self.device.call=original;self._busy=False

    @property
    def expected(self):
        return copy.deepcopy(self._expected)

    def _epoch(self):
        pid=self.process['pid']
        script=f'stat -c %u /proc/{pid}; head -c 4097 /proc/{pid}/stat'
        raw=self.device.call(['shell','run-as '+TARGET+' sh -c '+shlex.quote(script)],Budget(5),limit=8192)
        owner,sep,process=raw.strip().partition(b'\n')
        owner=owner.removesuffix(b'\r')  # Observed Android shell CRLF framing.
        if not sep or not re.fullmatch(rb'[0-9]{1,10}',owner) or int(owner)!=self.process['uid']:
            raise Failure('expected process UID required')
        epoch=parse_process_epoch(process,pid,int(owner))
        if epoch!=self.process: raise Failure('process incarnation changed')
        return epoch

    def snapshot(self, deadline):
        def capture():
            process=self._epoch();first=self.inventory(self.device);candidate=self._view(first)
            digest=None
            if candidate is not None:
                if self.prefix is None: raise Failure('no scoped source prefix; candidate rejected')
                script='find no_backup/models -mindepth 1 -maxdepth 1 -type f -name "candidate-*.part"'
                paths=self.device.call(['shell','run-as '+TARGET+' sh -c '+shlex.quote(script)],Budget(5),limit=8192).splitlines()
                if (len(paths)!=1 or not re.fullmatch(rb'no_backup/models/candidate-[0-9a-f]{32}\.part',paths[0])
                        or hashlib.sha256(paths[0].rsplit(b'/',1)[-1]).hexdigest()!=candidate['name_sha256']):
                    raise Failure('candidate path does not match scoped inventory identity')
                raw=self.device.call(['exec-out','run-as',TARGET,'head','-c','64',paths[0].decode('ascii')],Budget(5),limit=64)
                digest=hashlib.sha256(raw).hexdigest()
                if len(raw)!=64 or digest!=self.prefix: raise Failure('candidate fixture prefix mismatch')
            second=self.inventory(self.device);after=self._view(second)
            if self._epoch()!=process: raise Failure('process changed during snapshot')
            if candidate is None:
                if after is not None: raise Failure('candidate appeared during idle observation')
                return None
            if (after is None or any(after[k]!=candidate[k] for k in ['mode','links','inode','name_sha256'])
                    or candidate['bytes']<self._last_size or after['bytes']<candidate['bytes']):
                raise Failure('candidate identity or counter changed during snapshot')
            frame=dict(process=process,root_identity=self._root(second),record_sha256=self.record['sha256'],
                       payload=copy.deepcopy(self.payload),candidate=dict(after,prefix_sha256=digest))
            owner={k:frame['candidate'][k] for k in ['inode','name_sha256','prefix_sha256']}
            if self._expected is not None and owner!=self._expected['candidate']:
                raise Failure('different candidate after ownership binding')
            if self._expected is None:
                self._expected=dict(process=copy.deepcopy(process),root_identity=self._root(second),
                                    record_sha256=self.record['sha256'],payload=copy.deepcopy(self.payload),candidate=owner)
            self._last_size=after['bytes']
            return frame
        return self._bound(deadline,capture)

    def meminfo(self, deadline):
        def capture():
            process=self._epoch()
            raw=self.device.call(['shell','dumpsys','meminfo',TARGET],Budget(10),limit=65536)
            memory=parse_meminfo(raw,process['pid'])
            if self._epoch()!=process: raise Failure('process changed during meminfo')
            return memory
        return self._bound(deadline,capture)
