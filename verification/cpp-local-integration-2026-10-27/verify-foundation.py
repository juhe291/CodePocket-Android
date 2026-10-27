"""Actual production runner on the host and current Android ELF/input checks. No APK build."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PHASE = Path(__file__).resolve().parent
CACHE = ROOT / '.tools/cpp-local-integration'
sys.path.insert(0, str(ROOT / 'scripts'))
from cpp_runtime_elf import elf


def sha(file):
    with file.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    CACHE.mkdir(exist_ok=True)
    windows = os.name == 'nt'
    cc = Path('C:/mingw64/bin/gcc.exe') if windows else Path('/usr/bin/gcc')
    cpp = cc.with_name('g++.exe' if windows else 'g++')
    environment = dict(os.environ)
    environment['PATH'] = str(cc.parent) + os.pathsep + environment['PATH']
    environment['TEMP'] = environment['TMP'] = str(ROOT / '.tools/semantics-tmp')
    records = {}

    def run(name, arguments, stdin=None, expected=0):
        result = subprocess.run(list(map(str, arguments)), cwd=CACHE, env=environment,
            input=stdin, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=30)
        records[name] = dict(arguments=list(map(str, arguments)), returncode=result.returncode,
            stdout=result.stdout, stderr=result.stderr)
        (PHASE / (name + '.json')).write_text(json.dumps(records[name], indent=2) + '\n', encoding='utf-8')
        assert result.returncode == expected, records[name]
        return result

    runner = CACHE / ('runner.exe' if windows else 'runner')
    run('host-production-runner-build', [cc, '-std=c11', '-Wall', '-Wextra', ROOT / 'app/src/main/jni/cpp_runner.c',
        '-o', runner] + ([] if windows else ['-ldl']))
    fixtures = ROOT / 'verification/cpp-local-probe-2026-10-27/fixtures'
    extension = '.dll' if windows else '.so'
    flags = ['-std=c++17', '-shared'] + (['-Wl,--export-all-symbols'] if windows else ['-fPIC'])
    for name, sources in [('program', ['main.cpp', 'helper.cpp']), ('argv', ['argv-main.cpp']), ('no-main', ['helper.cpp'])]:
        library = CACHE / (name + extension)
        if library.exists(): library.chmod(0o644)
        run('host-production-build-' + name, [cpp, *flags, *(fixtures / item for item in sources), '-o', library])
        if not windows: library.chmod(0o444)
    def argument(name):
        return './' + name + extension if windows else str(CACHE / (name + extension))
    normal = run('host-production-runner-normal', [runner, argument('program')], '9 2\n')
    assert normal.stdout.replace('\r\n', '\n') == 'ready\n2:9:2\ncaught\nbye\n'
    invalid = run('host-production-runner-invalid-input', [runner, argument('program')], 'bad\n', 2)
    assert invalid.stdout.replace('\r\n', '\n') == 'ready\nbye\n'
    assert run('host-production-runner-argv', [runner, argument('argv')]).stdout.replace('\r\n', '\n') == '1:program\n'
    assert 'missing main entry' in run('host-production-runner-no-main', [runner, argument('no-main')], expected=127).stderr
    run('host-production-runner-no-argument', [runner], expected=125)

    native = ROOT / '.tools/cpp-execution-jni/libs/arm64-v8a'
    images = [elf(native / name) for name in ('libcodepocket_execute.so', 'codepocket_cpp_runner')]
    for image in images:
        assert image['aarch64'] and image['load_segments_16k_compatible'] and not image['text_relocations']
        assert all(note['api'] <= 24 for note in image['android_notes'])
    old = ROOT / 'dist/CodePocket-cpp-semantics-2026-10-27-debug.apk'
    assert sha(old) == 'd828aff66043ec8bfd9c877b8f7b53873f2a7205a66b657ff248e7f78694d4cc'
    lock = json.loads((ROOT / 'app/cpp-runtime-lock.json').read_text())
    with zipfile.ZipFile(old) as archive:
        for item in lock['libraries'][:9]:
            original = archive.read('lib/arm64-v8a/' + item['packaged_name'])
            assert hashlib.sha256(original).hexdigest() == sha(ROOT / '.tools/cpp-runtime/jni/arm64-v8a' / item['packaged_name'])
        assert archive.read('assets/cpp-runtime/headers.zip') == (ROOT / '.tools/cpp-runtime/assets/cpp-runtime/headers.zip').read_bytes()
    result = dict(host_production_runner_cases=5, host_production_runner_source_sha256=sha(ROOT/'app/src/main/jni/cpp_runner.c'),
        android_images=images, previous_nine_libraries_and_headers_unchanged=True,
        latest_validated_apk=str(old.relative_to(ROOT)), apk_sha256=sha(old), new_apk_built=False,
        android_execution=False, native_jni_execution=False, full_batch_complete=False)
    (PHASE / 'foundation-results.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__': main()
