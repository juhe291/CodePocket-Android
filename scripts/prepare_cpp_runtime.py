"""Prepare pinned Android clangd inputs locally. Network is opt-in; no binaries are executed."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import tarfile
import urllib.request
import zipfile

from cpp_runtime_elf import ar_members, elf, patch_dynamic

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / '.tools/cpp-runtime'


def sha(file):
    digest = hashlib.sha256()
    with file.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''): digest.update(chunk)
    return digest.hexdigest()


def obtain(filename, expected, url, fetch, fallbacks=()):
    for file in [CACHE / 'downloads' / filename] + list(fallbacks):
        if file.is_file():
            assert sha(file) == expected, f'Cached digest mismatch: {file}'
            return file
    if not fetch: raise FileNotFoundError(f'Missing pinned C++ input {filename}; run scripts/prepare_cpp_runtime.py --fetch')
    target = CACHE / 'downloads' / filename; target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_name(target.name + '.partial')
    with urllib.request.urlopen(url, timeout=60) as response, partial.open('wb') as stream:
        shutil.copyfileobj(response, stream, 1024 * 1024)
    assert sha(partial) == expected, f'Download digest mismatch: {filename}'
    partial.replace(target)
    return target


def usr_path(name):
    parts = PurePosixPath(name).parts
    assert not name.startswith('/') and '..' not in parts
    if 'usr' not in parts: return None
    return '/'.join(parts[parts.index('usr') + 1:])


def deterministic_zip(file, entries):
    file.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(file, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            archive.writestr(info, data)


def prepare(args):
    lock = json.loads((ROOT / 'app/cpp-runtime-lock.json').read_text())
    raw = CACHE / 'original'; raw.mkdir(parents=True, exist_ok=True)
    native = CACHE / 'jni/arm64-v8a'; native.mkdir(parents=True, exist_ok=True)
    assets = CACHE / 'assets'; assets.mkdir(exist_ok=True)
    headers = {}; header_links = []; originals = {}
    for package, meta in lock['packages'].items():
        filename = PurePosixPath(meta['Filename']).name
        archive = obtain(filename, meta['SHA256'], lock['repository'] + '/' + meta['Filename'], args.fetch,
                         [ROOT / '.tools/cpp-semantic-probe/debs' / filename])
        needed = {item['member']: item for item in lock['libraries'] if item['package'] == package}
        prefix = 'lib/clang/21/include/' if package == 'clang' else 'include/' if package == 'ndk-sysroot' else None
        for name, compressed in ar_members(archive):
            if not name.startswith('data.tar'): continue
            with tarfile.open(fileobj=io.BytesIO(compressed), mode='r|*') as tar:
                for entry in tar:
                    member = usr_path(entry.name)
                    if member is None: continue
                    if member in needed and entry.isfile():
                        item = needed[member]; target = raw / item['packaged_name']
                        with tar.extractfile(entry) as source, target.open('wb') as stream: shutil.copyfileobj(source, stream, 1024 * 1024)
                        assert sha(target) == item['source_sha256'] and target.stat().st_size == item['bytes']
                        originals[member] = target
                    if prefix and member.startswith(prefix):
                        destination = ('clang/include/' if package == 'clang' else 'sysroot/include/') + member[len(prefix):]
                        if entry.isfile():
                            # Pinned LLVM arm_neon.h is 3,188,137 bytes; retain a per-file bound.
                            assert entry.size <= 4 * 1024 * 1024
                            headers[destination] = tar.extractfile(entry).read()
                        elif entry.issym() or entry.islnk():
                            header_links.append((destination, member, entry.linkname, prefix, package))
    # Resolve only archive-internal header links; do not create filesystem symbolic links.
    for destination, member, link, prefix, package in header_links:
        target = os.path.normpath(str(PurePosixPath(member).parent / link)).replace('\\', '/')
        assert not target.startswith('../') and target.startswith(prefix)
        key = ('clang/include/' if package == 'clang' else 'sysroot/include/') + target[len(prefix):]
        assert key in headers, f'Unresolved trusted header link: {member}'
        headers[destination] = headers[key]
    assert len(headers) <= 20000 and sum(map(len, headers.values())) <= 64 * 1024 * 1024
    assert all(key in headers for key in ('sysroot/include/c++/v1/vector', 'sysroot/include/aarch64-linux-android/asm/types.h', 'clang/include/stddef.h'))
    libraries = []
    for item in lock['libraries']:
        source = originals[item['member']]
        modified = item['member'] == 'lib/libiconv.so' and args.iconv_library is not None
        if modified: source = args.iconv_library.resolve()
        inspected = elf(source)
        assert inspected['aarch64'] and inspected['load_segments_16k_compatible'] and not inspected['text_relocations']
        if modified:
            assert inspected['soname'] == 'libiconv.so' and set(inspected['needed']) <= {'libc.so', 'libm.so', 'libdl.so'}
        patched, _ = patch_dynamic(source, lock['aliases'])
        target = native / item['packaged_name']; target.write_bytes(patched)
        if not modified: assert sha(target) == item['sha256']
        checked = elf(target)
        assert all(name in lock['aliases'].values() or name in ('libc.so','libm.so','libdl.so','liblog.so','libandroid.so','libz.so') for name in checked['needed'])
        libraries.append(dict(name=item['packaged_name'], sha256=sha(target), bytes=target.stat().st_size))
    expected_names = {item['name'] for item in libraries}
    assert {f.name for f in native.iterdir()} == expected_names, 'Stale native output: clear only .tools/cpp-runtime/jni before changing engine versions'
    header_zip = assets / 'cpp-runtime/headers.zip'; deterministic_zip(header_zip, headers)
    original = lock['source']
    source = args.iconv_source.resolve() if args.iconv_source else obtain(original['filename'], original['sha256'], original['url'], args.fetch,
        [ROOT / '.tools/cpp-semantic-probe/sources' / original['filename']])
    assert source.stat().st_size <= 16 * 1024 * 1024
    with tarfile.open(source) as tar: assert any(entry.name.endswith('/COPYING.LIB') for entry in tar)
    # Android's asset merger transparently inflates *.gz and strips its suffix. A .tgz name
    # preserves the exact pinned gzip archive and keeps the runtime manifest truthful.
    source_asset = assets / 'cpp-source' / 'libiconv-1.19-source.tgz'; source_asset.parent.mkdir(exist_ok=True)
    shutil.copy2(source, source_asset)
    stale = assets / 'cpp-source' / original['filename']
    assert stale.resolve().is_relative_to(CACHE.resolve()) and stale.name == 'libiconv-1.19.tar.gz'
    if stale.is_file(): stale.unlink()  # Only this script's obsolete generated asset, not the download.
    for input_file in [ROOT / 'scripts/cpp_runtime_elf.py', ROOT / 'app/cpp-runtime-lock.json',
                       ROOT / 'verification/cpp-semantic-probe-2026-10-27/upstream/libiconv-build.sh',
                       ROOT / 'verification/cpp-semantic-probe-2026-10-27/upstream/properties.sh']:
        shutil.copy2(input_file, source_asset.parent / input_file.name)
    manifest = dict(engine=lock['engine'], version=lock['version'], target=lock['target'], libraries=libraries,
        executable='libcodepocket_clangd.so', headers=dict(asset='cpp-runtime/headers.zip', sha256=sha(header_zip), bytes=header_zip.stat().st_size,
            entries=len(headers), uncompressed_bytes=sum(map(len, headers.values()))),
        corresponding_source=dict(asset='cpp-source/' + source_asset.name, sha256=sha(source_asset), bytes=source_asset.stat().st_size,
            user_modified=bool(args.iconv_source)))
    (assets / 'cpp-runtime/manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (CACHE / 'prepared.json').write_text(json.dumps(dict(lock_sha256=sha(ROOT / 'app/cpp-runtime-lock.json'), manifest=manifest), indent=2) + '\n')
    print('Prepared', len(libraries), 'native files,', len(headers), 'headers and complete libiconv corresponding source; no APK built')
    if args.fetch_host:
        host_headers = CACHE / 'headers'
        for name, data in headers.items():
            target = host_headers / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        windows = os.name == 'nt'
        filename = 'clangd-windows-21.1.8.zip' if windows else 'clangd-linux-21.1.8.zip'
        expected = 'aeb5a68414c4d10b0867a479c9f21593043cc6d122e39de61b3801c89ebd57fc' if windows else 'e0b214a59906dfceef33406873c4379fb8e768fb4345d8afa7f307bd2ec899de'
        host = obtain(filename, expected, 'https://github.com/clangd/clangd/releases/download/21.1.8/' + filename, args.fetch,
                      [ROOT / '.tools/cpp-semantic-probe' / filename])
        with zipfile.ZipFile(host) as archive:
            for entry in archive.infolist():
                path = PurePosixPath(entry.filename); assert not path.is_absolute() and '..' not in path.parts
            archive.extractall(CACHE / 'host')
        executable = CACHE / 'host/clangd_21.1.8/bin' / ('clangd.exe' if windows else 'clangd')
        executable.chmod(0o755)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fetch', action='store_true', help='Explicitly allow pinned public downloads')
    parser.add_argument('--fetch-host', action='store_true', help='Prepare the pinned host comparison tool too')
    parser.add_argument('--iconv-library', type=Path, help='User relink: replacement API24/16KiB libiconv.so')
    parser.add_argument('--iconv-source', type=Path, help='User relink: complete corresponding modified source archive')
    args = parser.parse_args()
    if bool(args.iconv_library) != bool(args.iconv_source): parser.error('Relinking requires both the library and its complete corresponding source')
    prepare(args)
