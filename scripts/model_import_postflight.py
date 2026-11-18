"""Read-only committed-copy evidence; never imports, launches or deletes anything."""
import hashlib
import re
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
