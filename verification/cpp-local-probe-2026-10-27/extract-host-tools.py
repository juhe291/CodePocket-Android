"""Extract only selected official HTTPS ZIP ranges and verify every member's length and CRC."""
import binascii
import hashlib
import json
from pathlib import Path
import struct
import zlib

ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
CACHE=ROOT/'.tools/cpp-local-probe'
metadata=json.loads((PHASE/'host-tool-directory.json').read_text())
wanted={'clang.exe','libwinpthread-1.dll','libxml2.dll','source.properties'}
output=[]
for item in metadata['selected']:
    name=Path(item['name']).name
    if name not in wanted or name.endswith('.dll') and '/bin/' not in item['name']:continue
    data=(CACHE/'host-ranges'/(name+'.range')).read_bytes()
    fields=struct.unpack_from('<4s5H3L2H',data)
    assert fields[0]==b'PK\x03\x04' and fields[3]==item['method']==8
    archived=data[30:30+fields[9]].decode('utf-8');assert archived==item['name']
    at=30+fields[9]+fields[10]
    content=zlib.decompress(data[at:at+item['compressed']],-15)
    assert len(content)==item['uncompressed'] and binascii.crc32(content)&0xffffffff==item['crc32']
    target=CACHE/'host-ndk30'/('bin/'+name if name!='source.properties' else name)
    target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(content)
    output.append(dict(name=item['name'],bytes=len(content),sha256=hashlib.sha256(content).hexdigest(),zip_crc_verified=True))
assert len(output)==4
record=dict(url=metadata['url'],https_byte_ranges=True,full_archive_sha256_verified=False,files=output,
    extracted_for_host_cross_build_only=True,android_runtime_tools=False)
(PHASE/'host-tool-provenance.json').write_text(json.dumps(record,indent=2)+'\n')
print('Four official host members extracted and CRC/length checked; full NDK archive digest not verified')
