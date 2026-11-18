"""Read-only committed-copy evidence; never imports, launches or deletes anything."""
import copy
import hashlib
import re
import stat
import struct
import unicodedata

from model_import_device import Failure


def decode_active_selection(raw):
    """Decode the bounded active-selection subset of ModelRecordCodec V1."""
    def require(condition):
        if not condition:raise Failure('invalid bounded active selection')
    require(type(raw) is bytes and len(raw)<=2048 and raw[:6]==b'LDMR\x01\x00')
    pos=6
    def take(n):
        nonlocal pos
        value=raw[pos:pos+n];pos+=n
        require(len(value)==n)
        return value
    def ident():
        value=take(32);require(re.fullmatch(rb'[0-9a-f]{32}',value) is not None)
        return value
    def flag():
        value=take(1);require(value in [b'\x00',b'\x01'])
        return value==b'\x01'
    epoch=ident();pending=flag()
    if pending:ident()
    require(flag());start=pos;model=ident()
    size=struct.unpack('>q',take(8))[0];length=struct.unpack('>H',take(2))[0]
    require(size>=24 and 1<=length<=1024)
    name=take(length)
    try:text=name.decode('utf-8')
    except UnicodeError:raise Failure('invalid bounded active selection') from None
    require(pos==len(raw) and 1<=len(text)<=256 and text==text.strip()
            and all(unicodedata.category(c) not in ['Cc','Cf','Cs','Zl','Zp'] for c in text))
    digest=lambda data:hashlib.sha256(data).hexdigest()
    return dict(epoch_sha256=digest(epoch),active_sha256=digest(raw[start:]),name_sha256=digest(name),
                size=size,pending=pending,payload_name_sha256=digest(b'model-'+model+b'.gguf'),
                candidate_name_sha256=digest(b'candidate-'+model+b'.part'))


def _require(condition):
    if not condition:raise Failure('unproven committed-copy metadata')


def _digest(value):
    return isinstance(value,str) and re.fullmatch('[0-9a-f]{64}',value) is not None


def _root(store):
    parent=store['parent_identity'];model=store['models']
    for node in [parent,model]:
        _require(type(node['mode']) is int and stat.S_ISDIR(node['mode']))
        _require(type(node['inode']) is int and 0<node['inode']<2**63)
    _require(type(model['links']) is int and model['links']>=2)
    return dict(parent_identity={k:parent[k] for k in ['mode','inode']},
                models={k:model[k] for k in ['mode','inode','links']})


def _selected(store):
    entries=store['entries']
    _require(isinstance(entries,list) and len(entries)==2)
    _require(sorted([e['kind'] for e in entries])==['payload','record'])
    for e in entries:
        _require(all(type(e[k]) is int and 0<=e[k]<2**63 for k in ['mode','links','inode','bytes','mtime']))
        _require(stat.S_ISREG(e['mode']) and e['links']==1 and e['inode']>0 and _digest(e['name_sha256']))
    rec=next(e for e in entries if e['kind']=='record');payload=next(e for e in entries if e['kind']=='payload')
    _require(rec['record']=='selection' and rec['name_sha256']==hashlib.sha256(b'selection').hexdigest())
    _require(0<rec['bytes']<=2048 and _digest(rec['sha256']) and payload['bytes']>=24)
    return rec,payload


def verify_committed(expected, observed):
    """Validate final metadata against a prior selection and captured candidate.

    This accepts evidence only; it grants no source cleanup or other mutations.
    The expected baseline/candidate must come from the separately scoped copy.
    """
    try:
        old=expected['before_active'];before=expected['before_store'];store=observed['store']
        old_record,old_payload=_selected(before);rec,payload=_selected(store)
        _require(_root(before)==_root(store) and before['legacy']==store['legacy'])
        _require(old['payload']==old_payload and old['size']==old_payload['bytes'] and old['pending'] is False)
        _require(all(_digest(old[k]) for k in ['epoch_sha256','active_sha256','name_sha256']))
        raw=observed['record_bytes'];active=decode_active_selection(raw)
        _require(rec['bytes']==len(raw) and rec['sha256']==hashlib.sha256(raw).hexdigest())
        source=expected['source'];candidate=expected['candidate']
        _require(type(source['size']) is int and 65<=source['size']<2**63)
        _require(all(_digest(source[k]) for k in ['name_sha256','prefix_sha256']))
        _require(type(candidate['inode']) is int and 0<candidate['inode']<2**63)
        _require(all(_digest(candidate[k]) for k in ['name_sha256','prefix_sha256']))
        _require(active['pending'] is False and active['epoch_sha256']==old['epoch_sha256'])
        _require(active['active_sha256']!=old['active_sha256'] and rec['sha256']!=old_record['sha256'])
        _require(active['size']==source['size']==payload['bytes'] and active['name_sha256']==source['name_sha256'])
        _require(active['payload_name_sha256']==payload['name_sha256']!=old_payload['name_sha256'])
        _require(active['candidate_name_sha256']==candidate['name_sha256'])
        _require(payload['inode']==candidate['inode']!=old_payload['inode'])
        prefix=observed['payload_prefix']
        _require(type(prefix) is bytes and len(prefix)==64)
        _require(hashlib.sha256(prefix).hexdigest()==source['prefix_sha256']==candidate['prefix_sha256'])
        for process in [expected['process'],observed['process']]:
            _require(set(process)=={'pid','uid','start_ticks'})
            _require(all(type(v) is int and 0<v<2**63 for v in process.values()))
        _require(observed['process']==expected['process'])
        for identity in [expected['identity'],observed['identity']]:
            _require(set(identity)=={'io.github.greprohan.llamadroid'})
            apk=identity['io.github.greprohan.llamadroid']
            _require(type(apk['uid']) is int and apk['uid']==expected['process']['uid'])
            _require(type(apk['bytes']) is int and 0<apk['bytes']<2**63 and _digest(apk['sha256']))
        _require(observed['identity']==expected['identity'])
        ui=observed['ui']
        _require(ui['target_focused'] is True and ui['keyguard'] is False and ui['phase'] in ['READY','IMPORTED'])
        _require(ui['replace_enabled'] is True and all(ui[k] is False for k in ['pending_visible','cancel_visible','reset_visible']))
        _require(type(ui['active_size']) is int and ui['active_size']==source['size'] and ui['active_name_sha256']==source['name_sha256'])
        return dict(status='COMMITTED_COPY_METADATA_VERIFIED',active=dict(active,payload=copy.deepcopy(payload)),
                    same_candidate_promoted=True,single_selected_payload_no_store_debt=True,
                    body_integrity_proven=False,inflight_progress_or_heap_proven=False,external_source_cleanup_verified=False)
    except (KeyError,TypeError,ValueError,AttributeError,StopIteration):
        raise Failure('incomplete committed-copy evidence') from None
