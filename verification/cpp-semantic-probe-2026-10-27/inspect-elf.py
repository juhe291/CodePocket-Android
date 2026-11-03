"""Read actual candidate archive/ELF metadata. Does not install or execute Android binaries."""
import argparse
import hashlib
import io
import json
import mmap
from pathlib import Path, PurePosixPath
import shutil
import struct
import subprocess
import tarfile

PHASE = Path(__file__).resolve().parent
ROOT = PHASE.parents[1]
CACHE = ROOT / '.tools/cpp-semantic-probe'


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
        return dict(file=str(file.relative_to(ROOT)).replace('\\', '/'), bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                    elf_class=64, endian='little', machine=machine, aarch64=machine == 183, type=kind, entry=entry,
                    interpreter=interpreter, soname=soname, needed=needed, runpaths=paths, android_notes=notes,
                    loads=[dict(offset=h[2], vaddr=h[3], bytes=h[5], align=h[7]) for h in loads],
                    load_segments_16k_compatible=all(h[7] >= 16384 and h[2] % 16384 == h[3] % 16384 for h in loads),
                    text_relocations=any(t == 22 or t == 30 and v & 4 for t, v in values),
                    contains_termux_prefix=data.find(b'/data/data/com.termux/') >= 0,
                    android_execution=False)


def inspect(name, metadata):
    item = metadata['packages'][name]
    archive = CACHE / 'debs' / PurePosixPath(item['Filename']).name
    assert archive.stat().st_size == int(item['Size'])
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == item['SHA256']
    target = CACHE / 'extracted' / name
    records, extracted = [], []
    for member_name, compressed in ar_members(archive):
        if not member_name.startswith('data.tar'):
            continue
        with tarfile.open(fileobj=io.BytesIO(compressed), mode='r|*') as tar:
            for member in tar:
                path = PurePosixPath(member.name)
                assert not path.is_absolute() and '..' not in path.parts
                filename = path.name
                selected = filename in ('clangd', 'libclang-cpp.so') or name not in ('clang', 'ndk-sysroot') and filename.startswith('lib') and '.so' in filename
                license_file = 'share/doc/' in member.name and filename in ('copyright', 'LICENSE', 'LICENSE.TXT', 'LICENSE.txt')
                header_file = '/include/' in member.name and (name == 'ndk-sysroot' or '/lib/clang/' in member.name)
                if not member.isfile() or not (selected or license_file or header_file):
                    continue
                assert 'usr' in path.parts
                relative = PurePosixPath(*path.parts[path.parts.index('usr') + 1:])
                output = target.joinpath(*relative.parts)
                output.parent.mkdir(parents=True, exist_ok=True)
                with tar.extractfile(member) as source, output.open('wb') as destination:
                    shutil.copyfileobj(source, destination, 1024 * 1024)
                extracted.append(str(relative))
                if selected:
                    records.append(elf(output))
                    readelf = ROOT / '.tools/android-sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe'
                    checked = subprocess.run([str(readelf), '-h', '-l', '-d', '-n', '--version-info', str(output)], capture_output=True, check=True)
                    (PHASE / ('elf-' + filename + '.txt')).write_bytes(checked.stdout)
    assert records or name == 'ndk-sysroot', f'No actual requested ELF found in {name}'
    return dict(package=name, version=item['Version'], archive_sha256=item['SHA256'], extracted=extracted, elfs=records)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('names', nargs='*', default=['clang', 'libllvm'])
    args = parser.parse_args()
    metadata = json.loads((PHASE / 'package-candidates.json').read_text())
    records = [inspect(name, metadata) for name in args.names]
    result = dict(packages=records, actual_archives_verified=True, actual_elf_metadata=True,
                  android_execution=False, production_backend_selected=False)
    (PHASE / 'elf-inspection.json').write_text(json.dumps(result, indent=2) + '\n')
    for record in records:
        for item in record['elfs']:
            print(record['package'], item['file'], item['bytes'], 'bytes; AArch64=', item['aarch64'],
                  '; 16KiB=', item['load_segments_16k_compatible'], '; needs=', item['needed'], '; runpaths=', item['runpaths'])


if __name__ == '__main__':
    main()
