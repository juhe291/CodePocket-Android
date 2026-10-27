"""Verify actual packaged C++ ELF closure, headers, notices and LGPL source. Never executes Android code."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import tarfile
import tempfile
import zipfile

from cpp_runtime_elf import elf

ROOT = Path(__file__).resolve().parents[1]


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    lock = json.loads((ROOT / 'app/cpp-runtime-lock.json').read_text())
    with zipfile.ZipFile(args.apk) as apk, zipfile.ZipFile(args.baseline) as baseline:
        assert apk.testzip() is None and len(apk.namelist()) == len(set(apk.namelist()))
        manifest = json.loads(apk.read('assets/cpp-runtime/manifest.json'))
        assert manifest['engine'] == 'clangd 21.1.8' and manifest['version'] == '21.1.8-3'
        actual_lock = json.loads(apk.read('assets/cpp-source/cpp-runtime-lock.json'))
        assert actual_lock == lock
        assert manifest['target'] == 'aarch64-linux-android24'
        natives = []
        with tempfile.TemporaryDirectory(dir=ROOT / '.tools', prefix='cpp-apk-inspect-') as directory:
            for item in manifest['libraries']:
                name = item['name']; data = apk.read('lib/arm64-v8a/' + name)
                pinned = next(value for value in lock['libraries'] if value['packaged_name'] == name)
                assert len(data) == item['bytes'] and digest(data) == item['sha256'] == pinned['sha256']
                target = Path(directory) / name; target.write_bytes(data)
                inspected = elf(target)
                assert inspected['aarch64'] and inspected['load_segments_16k_compatible'] and not inspected['text_relocations']
                # Some upstream system-only libraries have no RUNPATH. Existing paths are
                # rewritten to ORIGIN; the controlled process also sets its native-only LD path.
                assert inspected['runpaths'] in ([], ['$ORIGIN'])
                assert all(note['api'] <= 24 for note in inspected['android_notes'])
                assert all(value in lock['aliases'].values() or value in {'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libandroid.so', 'libz.so'} for value in inspected['needed'])
                if name == manifest['executable']: assert inspected['interpreter'] == '/system/bin/linker64' and inspected['entry'] > 0
                natives.append(dict(name=name, sha256=item['sha256'], bytes=len(data), needed=inspected['needed'], alignment_16k=True))
            bridge = apk.read('lib/arm64-v8a/libcodepocket_analysis.so')
            target = Path(directory) / 'libcodepocket_analysis.so'; target.write_bytes(bridge)
            inspected = elf(target)
            assert inspected['aarch64'] and inspected['load_segments_16k_compatible'] and not inspected['text_relocations']
            assert all(note['api'] <= 24 for note in inspected['android_notes'])
        spec = manifest['headers']; data = apk.read('assets/' + spec['asset'])
        assert len(data) == spec['bytes'] and digest(data) == spec['sha256']
        with zipfile.ZipFile(io.BytesIO(data)) as headers:
            assert headers.testzip() is None and len(headers.namelist()) == len(set(headers.namelist())) == spec['entries']
            assert sum(entry.file_size for entry in headers.infolist()) == spec['uncompressed_bytes'] <= 64 * 1024 * 1024
            assert max(entry.file_size for entry in headers.infolist()) <= 4 * 1024 * 1024
            for name in ('sysroot/include/c++/v1/vector', 'sysroot/include/c++/v1/algorithm', 'clang/include/stddef.h', 'sysroot/include/aarch64-linux-android/asm/types.h'):
                assert headers.read(name)
        source = manifest['corresponding_source']; data = apk.read('assets/' + source['asset'])
        assert not source['user_modified'] and len(data) == source['bytes'] and digest(data) == source['sha256'] == lock['source']['sha256']
        with tarfile.open(fileobj=io.BytesIO(data)) as archive:
            names = archive.getnames()
            assert all('libiconv-1.19/' + name in names for name in ('COPYING.LIB', 'configure', 'lib/iconv.c', 'libcharset/lib/localcharset.c'))
            assert len(names) > 1000
        notices = []
        source_notices = ROOT / 'app/src/main/assets/cpp-licenses'
        for file in sorted(source_notices.iterdir()):
            actual = apk.read('assets/cpp-licenses/' + file.name)
            assert actual == file.read_bytes()
            notices.append(dict(name=file.name, bytes=len(actual), sha256=digest(actual)))
        for name in ('cpp_runtime_elf.py', 'libiconv-build.sh', 'properties.sh'):
            assert apk.read('assets/cpp-source/' + name)
        original_native = [name for name in baseline.namelist() if name.startswith('lib/') and name.endswith('.so')]
        original_python = [name for name in baseline.namelist() if name.startswith('assets/chaquopy/')]
        for name in original_native + original_python:
            assert digest(apk.read(name)) == digest(baseline.read(name)), 'Original runtime changed: ' + name
        assert len(original_native) == 10 and len(original_python) == 20
    report = dict(apk_sha256=digest(args.apk.read_bytes()), apk_bytes=args.apk.stat().st_size,
        baseline_sha256=digest(args.baseline.read_bytes()), baseline_bytes=args.baseline.stat().st_size,
        native_libraries=natives, native_bytes=sum(item['bytes'] for item in natives), bridge_bytes=len(bridge),
        headers=spec, corresponding_source=source, notices=notices,
        original_native_unchanged=original_native, original_python_assets_unchanged=original_python,
        abi='arm64-v8a', target_api=24, zip_crc=True, real_android_execution=False)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Actual APK C++ closure, headers, notices, complete source and all original runtimes verified; Android execution not tested')


if __name__ == '__main__':
    main()
