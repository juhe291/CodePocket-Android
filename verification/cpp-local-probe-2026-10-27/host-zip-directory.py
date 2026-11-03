"""Read the official NDK Windows ZIP directory; fetch/extract selected entries separately."""
import json
from pathlib import Path
import struct

ROOT=Path(__file__).resolve().parents[2]
CACHE=ROOT/'.tools/cpp-local-probe'
data=(CACHE/'ndk30-windows-central.bin').read_bytes()
entries=[];at=0
while data[at:at+4]==b'PK\x01\x02':
    fields=struct.unpack_from('<4s6H3L5H2L',data,at)
    name=data[at+46:at+46+fields[10]].decode('utf-8')
    entries.append(dict(name=name,method=fields[4],crc32=fields[7],compressed=fields[8],uncompressed=fields[9],offset=fields[16]))
    at+=46+fields[10]+fields[11]+fields[12]
assert len(entries)==9108
wanted=[]
for entry in entries:
    name=entry['name']
    if name.endswith(('/bin/clang.exe','/bin/ld.lld.exe','/source.properties')) or name.endswith('.dll') and '/toolchains/llvm/prebuilt/' in name:
        entry['range_end']=entry['offset']+30+len(name.encode())+65536+entry['compressed']-1
        wanted.append(entry)
record=dict(url='https://dl.google.com/android/repository/android-ndk-r30-windows.zip',archive_bytes=727980788,
    https_ranges=True,full_archive_sha256_verified=False,selected=wanted)
(Path(__file__).resolve().parent/'host-tool-directory.json').write_text(json.dumps(record,indent=2)+'\n')
print(json.dumps(wanted,indent=2))
