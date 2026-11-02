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


def verify_runner_update(current_data, old_data):
    """Only runner code may change; other shipped modules may change their pyc mtime only."""
    with zipfile.ZipFile(io.BytesIO(current_data)) as current, zipfile.ZipFile(io.BytesIO(old_data)) as old:
        names=current.namelist()
        assert current.testzip() is None and len(names)==len(set(names)) and set(names)==set(old.namelist())
        changed, timestamps=[],[]
        for name in names:
            before,after=old.read(name),current.read(name)
            if before==after:
                continue
            if name=='runner.pyc':
                assert len(before)>=16 and len(after)>=16 and before[:8]==after[:8] and before[16:]!=after[16:]
                changed.append(name)
            else:
                # Chaquopy recompiles all three modules when its source merge runs. The source
                # size, flags, magic and complete marshalled code must remain byte identical.
                assert name in ('diagnostics.pyc','semantics.pyc') and len(before)>=16
                assert before[:8]==after[:8] and before[12:]==after[12:], 'Unapproved Python code/header change: '+name
                timestamps.append(dict(module=name,before_mtime=int.from_bytes(before[8:12],'little'),
                    after_mtime=int.from_bytes(after[8:12],'little'),unchanged_payload_sha256=digest(after[16:])))
        assert changed==['runner.pyc'], 'Expected only runner.pyc code to change: '+repr(changed)
        return dict(modules=changed,timestamp_only_updates=timestamps,
            before_sha256=digest(old.read('runner.pyc')),after_sha256=digest(current.read('runner.pyc')))


def verify_build_update(current_data, old_data, current_app, old_app):
    current,old=json.loads(current_data),json.loads(old_data)
    expected=hashlib.sha1(current_app).hexdigest()
    assert current['assets']['app.imy']==expected
    assert old['assets']['app.imy']==hashlib.sha1(old_app).hexdigest()
    current['assets'].pop('app.imy');old['assets'].pop('app.imy')
    assert current==old, 'Unapproved Chaquopy build metadata change'
    return dict(asset='assets/chaquopy/build.json',field='assets.app.imy',matches_actual_archive_sha1=expected)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--allow-runner-update', action='store_true',
                        help='Allow only runner.pyc code to change; other shipped modules may change their pyc mtime only. All runtime assets must match.')
    parser.add_argument('--allow-formatting-update', action='store_true',
                        help='Verify only the pinned formatting additions against the workbench candidate; all original runtime payloads stay unchanged.')
    parser.add_argument('--allow-git-update', action='store_true',
                        help='Verify only pinned local Git additions against the formatting candidate; preserve original runtimes.')
    args = parser.parse_args()
    assert sum((args.allow_runner_update,args.allow_formatting_update,args.allow_git_update))<=1
    lock = json.loads((ROOT / 'app/cpp-runtime-lock.json').read_text())
    with zipfile.ZipFile(args.apk) as apk, zipfile.ZipFile(args.baseline) as baseline:
        assert apk.testzip() is None and len(apk.namelist()) == len(set(apk.namelist()))
        manifest = json.loads(apk.read('assets/cpp-runtime/manifest.json'))
        assert manifest['engine'] == 'clangd 21.1.8' and manifest['version'] == '21.1.8-3'
        actual_lock = json.loads(apk.read('assets/cpp-source/cpp-runtime-lock.json'))
        assert actual_lock == lock
        assert manifest['target'] == 'aarch64-linux-android24'
        assert len(manifest['libraries']) == len(lock['libraries'])
        assert {item['name'] for item in manifest['libraries']} == {item['packaged_name'] for item in lock['libraries']}
        natives = []
        execution_images = []
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
                if name in {manifest[key] for key in ('executable','compiler','linker','symbolizer')}:
                    assert inspected['interpreter'] == '/system/bin/linker64' and inspected['entry'] > 0
                natives.append(dict(name=name, sha256=item['sha256'], bytes=len(data), needed=inspected['needed'], alignment_16k=True))
            bridge = apk.read('lib/arm64-v8a/libcodepocket_analysis.so')
            target = Path(directory) / 'libcodepocket_analysis.so'; target.write_bytes(bridge)
            inspected = elf(target)
            assert inspected['aarch64'] and inspected['load_segments_16k_compatible'] and not inspected['text_relocations']
            assert all(note['api'] <= 24 for note in inspected['android_notes'])
            for name in ('libcodepocket_execute.so','libcodepocket_cpp_runner.so'):
                data=apk.read('lib/arm64-v8a/'+name)
                target=Path(directory)/name;target.write_bytes(data);inspected=elf(target)
                assert inspected['aarch64'] and inspected['load_segments_16k_compatible'] and not inspected['text_relocations']
                assert all(note['api']<=24 for note in inspected['android_notes'])
                assert set(inspected['needed'])<=set(('libc.so','libm.so','libdl.so'))
                if name=='libcodepocket_cpp_runner.so':
                    assert inspected['type']==3 and inspected['interpreter']=='/system/bin/linker64' and inspected['entry']>0
                    staged=ROOT/'app/build/cpp-runner/jni/arm64-v8a'/name
                    assert data==staged.read_bytes(), 'Installed runner differs from current build output'
                execution_images.append(dict(name=name,sha256=digest(data),bytes=len(data),needed=inspected['needed'],alignment_16k=True))
        support=manifest['build_support'];data=apk.read('assets/'+support['asset'])
        assert len(data)==support['bytes'] and digest(data)==support['sha256']
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            assert archive.testzip() is None and len(archive.namelist())==len(set(archive.namelist()))==support['entries']==4
            assert set(archive.namelist())=={item['name'] for item in lock['build_support']}
            assert sum(item.file_size for item in archive.infolist())==support['uncompressed_bytes']<=2*1024*1024
            for item in lock['build_support']:
                value=archive.read(item['name'])
                assert len(value)==item['bytes'] and digest(value)==item['sha256']
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
        changed_python = []
        metadata_updates=[]
        formatting_update = None
        git_update = None
        if args.allow_formatting_update:
            assert not args.allow_runner_update
            from verify_formatting_apk import verify_delta
            formatting_update = verify_delta(apk, baseline)
            original_native = formatting_update['original_native_unchanged']
            original_python = formatting_update['original_python_assets_unchanged']
        if args.allow_git_update:
            from verify_git_apk import verify_delta, BASELINE
            assert digest(args.baseline.read_bytes())==BASELINE
            git_update = verify_delta(apk, baseline)
            original_native = git_update['original_native_unchanged']
            original_python = git_update['original_python_assets_unchanged']
        for name in original_native + original_python:
            if args.allow_runner_update and name == 'assets/chaquopy/app.imy':
                changed_python.append(dict(asset=name,**verify_runner_update(apk.read(name),baseline.read(name))))
                continue
            if args.allow_runner_update and name == 'assets/chaquopy/build.json':
                metadata_updates.append(verify_build_update(apk.read(name),baseline.read(name),
                    apk.read('assets/chaquopy/app.imy'),baseline.read('assets/chaquopy/app.imy')))
                continue
            assert digest(apk.read(name)) == digest(baseline.read(name)), 'Original runtime changed: ' + name
        if not args.allow_formatting_update and not args.allow_git_update:
            assert len(original_native) == 10 and len(original_python) == 20
    report = dict(apk_sha256=digest(args.apk.read_bytes()), apk_bytes=args.apk.stat().st_size,
        baseline_sha256=digest(args.baseline.read_bytes()), baseline_bytes=args.baseline.stat().st_size,
        native_libraries=natives, native_bytes=sum(item['bytes'] for item in natives), bridge_bytes=len(bridge),
        headers=spec, build_support=support, execution_images=execution_images, corresponding_source=source, notices=notices,
        original_native_unchanged=original_native,
        original_python_assets_unchanged=[name for name in original_python if name not in {item['asset'] for item in changed_python+metadata_updates}],
        approved_python_changes=changed_python,build_metadata_updates=metadata_updates,formatting_update=formatting_update,git_update=git_update,
        abi='arm64-v8a', target_api=24, zip_crc=True, real_android_execution=False)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Actual APK C++ closure, headers, notices, source and original runtimes verified; approved Python module changes recorded; Android execution not tested')


if __name__ == '__main__':
    main()
