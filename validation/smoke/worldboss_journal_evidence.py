"""Read-only bounded journal framing checks for native crash-test evidence."""
import hashlib
import struct


def last_complete(data, head):
    if len(data)>80*1024*1024+104 or len(head)!=92 or head[:4]!=struct.pack('>I',0x57425231):
        raise ValueError("Invalid recovery evidence size/head")
    if hashlib.sha256(head[:60]).digest()!=head[60:]:raise ValueError("Head checksum mismatch")
    anchored=struct.unpack('>Q',head[20:28])[0]
    offset=0;sequence=0;previous=bytes(32);identity=None;seen=False;payload=None
    while offset<len(data):
        if len(data)-offset<68:break
        header=data[offset:offset+68]
        magic,version,size,number=struct.unpack('>IIIQ',header[:20])
        if magic!=0x57424a31 or version!=1 or not 0<size<=16*1024*1024 or number<=0:
            raise ValueError("Invalid frame header")
        end=offset+68+size
        if len(data)-offset<68+size+36:break
        digest=hashlib.sha256(data[offset:end]).digest()
        if data[end:end+32]!=digest or data[end+32:end+36]!=struct.pack('>I',0x57424531):
            raise ValueError("Complete frame checksum/marker mismatch")
        if identity is None:
            identity=header[20:36]
            if number>1:
                if anchored<number:raise ValueError("Compacted prefix is not anchored")
                sequence=number-1;previous=header[36:68]
        if header[20:36]!=identity or number!=sequence+1 or header[36:68]!=previous:
            raise ValueError("Journal identity/order/chain mismatch")
        if number==anchored:
            if head[4:20]!=identity or head[28:60]!=digest:raise ValueError("Head contradicts journal")
            seen=True
        payload=data[offset+68:end];previous=digest;sequence=number;offset=end+36
    if payload is None or not seen:raise ValueError("Acknowledged frame is unavailable")
    return {"sequence":sequence,"digest":previous.hex(),"installation":identity.hex(),
            "valid_bytes":offset,"incomplete_tail_bytes":len(data)-offset,"payload":payload}
