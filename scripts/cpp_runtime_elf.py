import mmap
import struct
import hashlib
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def ar_members(file):
    with file.open('rb') as stream:
        assert stream.read(8) == b'!<arch>\n'
        while header := stream.read(60):
            assert len(header) == 60 and header[58:] == b'`\n'
            name = header[:16].decode('ascii').strip().rstrip('/')
            size = int(header[48:58]); data = stream.read(size)
            assert len(data) == size
            if size % 2:
                stream.read(1)
            yield name, data

def elf(file):
    with file.open('rb') as stream, mmap.mmap(stream.fileno(), 0, access=mmap.ACCESS_READ) as data:
        assert data[:6] == b'\x7fELF\x02\x01', file
        kind, machine = struct.unpack_from('<HH', data, 16)
        entry, phoff = struct.unpack_from('<QQ', data, 24)
        phsize, phnum = struct.unpack_from('<HH', data, 54)
        assert phsize == 56 and phnum < 100
        headers = [struct.unpack_from('<IIQQQQQQ', data, phoff + i * phsize) for i in range(phnum)]
        loads = [h for h in headers if h[0] == 1]
        dynamic = next((h for h in headers if h[0] == 2), None)
        values = []
        if dynamic:
            for offset in range(dynamic[2], dynamic[2] + dynamic[5], 16):
                tag, value = struct.unpack_from('<QQ', data, offset)
                if tag == 0:
                    break
                values.append((tag, value))
        table_address = next((v for t, v in values if t == 5), None)
        table = None
        if table_address:
            table = next(h[2] + table_address - h[3] for h in loads if h[3] <= table_address < h[3] + h[5])

        def text(offset):
            end = data.find(b'\0', offset, min(offset + 4096, len(data)))
            assert end >= 0
            return data[offset:end].decode('utf-8', errors='replace')

        needed = [text(table + v) for t, v in values if t == 1]
        paths = [text(table + v) for t, v in values if t in (15, 29)]
        interpreter = next((text(h[2]) for h in headers if h[0] == 3), None)
        soname = next((text(table + v) for t, v in values if t == 14), None)
        notes = []
        for h in headers:
            if h[0] != 4:
                continue
            at, end = h[2], h[2] + h[5]
            while at + 12 <= end:
                namesize, descsize, type_ = struct.unpack_from('<III', data, at); at += 12
                name = data[at:at + namesize].rstrip(b'\0'); at += (namesize + 3) // 4 * 4
                desc = data[at:at + descsize]; at += (descsize + 3) // 4 * 4
                if name == b'Android' and type_ == 1 and len(desc) >= 4:
                    notes.append(dict(api=struct.unpack_from('<I', desc)[0], ndk=desc[4:68].rstrip(b'\0').decode(errors='replace')))
        return dict(file=str(file).replace('\\', '/'), bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                    elf_class=64, endian='little', machine=machine, aarch64=machine == 183, type=kind, entry=entry,
                    interpreter=interpreter, soname=soname, needed=needed, runpaths=paths, android_notes=notes,
                    loads=[dict(offset=h[2], vaddr=h[3], bytes=h[5], align=h[7]) for h in loads],
                    load_segments_16k_compatible=all(h[7] >= 16384 and h[2] % 16384 == h[3] % 16384 for h in loads),
                    text_relocations=any(t == 22 or t == 30 and v & 4 for t, v in values),
                    contains_termux_prefix=data.find(b'/data/data/com.termux/') >= 0,
                    android_execution=False)

def patch_dynamic(file, aliases):
    data = bytearray(file.read_bytes())
    phoff = struct.unpack_from('<Q', data, 32)[0]
    phsize, phnum = struct.unpack_from('<HH', data, 54)
    headers = [struct.unpack_from('<IIQQQQQQ', data, phoff + i * phsize) for i in range(phnum)]
    dynamic = next(h for h in headers if h[0] == 2)
    entries = []
    for offset in range(dynamic[2], dynamic[2] + dynamic[5], 16):
        tag, value = struct.unpack_from('<QQ', data, offset)
        if not tag: break
        entries.append((tag, value))
    address = next(v for t, v in entries if t == 5)
    table = next(h[2] + address - h[3] for h in headers if h[0] == 1 and h[3] <= address < h[3] + h[5])
    edits = {}
    for tag, value in entries:
        if tag not in (1, 14, 15, 29): continue
        at = table + value; end = data.index(0, at)
        old = bytes(data[at:end]).decode('ascii')
        replacement = '$ORIGIN' if tag in (15, 29) and old else aliases.get(old, old)
        if replacement != old:
            assert len(replacement) <= end - at
            encoded = replacement.encode() + b'\0' * (end - at - len(replacement))
            assert at not in edits or edits[at] == encoded
            edits[at] = encoded
    for at, encoded in edits.items(): data[at:at + len(encoded)] = encoded
    assert len(data) == file.stat().st_size
    return data, len(edits)

