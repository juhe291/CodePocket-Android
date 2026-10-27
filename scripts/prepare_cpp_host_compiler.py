"""Pinned official host Clang21 for actual Android command-array tests, never an APK input."""
import hashlib
import json
import os
from pathlib import Path
import struct
import sys
import urllib.request
import zlib

ROOT = Path(__file__).resolve().parents[1]


def digest(file):
    with file.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def prepare(fetch=False, platform=None):
    platform = platform or ('windows' if os.name == 'nt' else 'linux' if sys.platform.startswith('linux') else '')
    lock = json.loads((ROOT / 'scripts/cpp_host_compiler_lock.json').read_text())[platform]
    target = ROOT / '.tools/cpp-runtime/host-compiler' / platform / 'bin'
    target.mkdir(parents=True, exist_ok=True)
    fallback = ROOT / '.tools/cpp-local-probe' / ('host-ndk30' if platform == 'windows' else 'host-ndk30-linux') / 'bin'
    for item in lock['files']:
        file = target / item['name']
        if file.is_file():
            assert file.stat().st_size == item['uncompressed'] and digest(file) == item['sha256']
        elif (fallback / file.name).is_file():
            data = (fallback / file.name).read_bytes()
            assert len(data) == item['uncompressed'] and hashlib.sha256(data).hexdigest() == item['sha256']
            file.write_bytes(data)
        else:
            if not fetch: raise FileNotFoundError(f'Missing pinned host compiler: {file}; prepare_cpp_runtime.py --fetch --fetch-host')
            start = item['offset']; end = start + 30 + len(item['member'].encode()) + 65536 + item['compressed'] - 1
            request = urllib.request.Request(lock['url'], headers={'Range': f'bytes={start}-{end}'})
            with urllib.request.urlopen(request, timeout=90) as response:
                assert response.status == 206 and response.headers['Content-Range'] == f'bytes {start}-{end}/{lock["archive_bytes"]}'
                data = response.read(end - start + 2); assert len(data) == end - start + 1
            fields = struct.unpack_from('<4s5H3L2H', data)
            assert fields[0] == b'PK\x03\x04' and fields[3] == 8
            name_length, extra_length = fields[-2:]
            assert data[30:30+name_length].decode() == item['member']
            offset = 30 + name_length + extra_length
            data = zlib.decompress(data[offset:offset+item['compressed']], -15)
            assert len(data) == item['uncompressed'] and zlib.crc32(data) == item['crc32']
            assert hashlib.sha256(data).hexdigest() == item['sha256']
            pending = file.with_suffix('.pending'); pending.write_bytes(data); pending.replace(file)
        file.chmod(0o755)
    print(f'Prepared pinned {platform} host Clang21; selected files verified, whole archive hash not asserted')
    return target


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fetch', action='store_true')
    parser.add_argument('--platform', choices=('windows', 'linux'))
    args = parser.parse_args(); prepare(args.fetch, args.platform)
