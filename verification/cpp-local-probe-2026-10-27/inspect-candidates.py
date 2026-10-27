"""Inspect fixed Android compiler inputs without executing Android binaries or changing the App."""
import gzip
import hashlib
import io
import json
from pathlib import Path
import sys
import tarfile

ROOT = Path(__file__).resolve().parents[2]
PHASE = Path(__file__).resolve().parent
CACHE = ROOT / '.tools/cpp-local-probe'
sys.path.insert(0, str(ROOT / 'scripts'))
from cpp_runtime_elf import ar_members, elf, patch_dynamic


def sha(file):
    with file.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def main():
    old = ROOT / '.tools/cpp-semantic-probe'
    index = old / 'Packages.gz'
    assert sha(index) == '1ac9be7f2688cc4e549b217eceae9e536f35be770bec231970718afb2590c3df'
    entries = {}
    for block in gzip.decompress(index.read_bytes()).decode().split('\n\n'):
        values = dict(line.split(': ', 1) for line in block.splitlines() if ': ' in line and not line.startswith(' '))
        if values.get('Package') in {'clang', 'lld', 'libcompiler-rt', 'ndk-sysroot'}:
            entries[values['Package']] = {key: values.get(key) for key in ('Package','Version','Architecture','Filename','Size','SHA256','Depends')}
    assert len(entries) == 4
    record = dict(index_sha256=sha(index), repository='https://packages-cf.termux.dev/apt/termux-main',
        index_signature_evidence='../cpp-semantic-probe-2026-10-27/package-candidates.json', packages=entries,
        android_execution=False, production_integrated=False)
    (PHASE / 'package-candidates.json').write_text(json.dumps(record, indent=2) + '\n')
    lock = json.loads((ROOT / 'app/cpp-runtime-lock.json').read_text())
    aliases = lock['aliases']; files = []; missing = []; support = []
    originals = CACHE / 'original'; originals.mkdir(parents=True, exist_ok=True)
    staged = CACHE / 'staged-native'; staged.mkdir(exist_ok=True)
    wanted = {'bin/clang-21':'libcodepocket_clang.so','bin/lld':'libcodepocket_lld.so','bin/clang-format':'libcodepocket_format.so'}
    for package, values in entries.items():
        filename = Path(values['Filename']).name
        candidates = [old / 'debs' / filename, CACHE / 'debs' / filename]
        archive = next((file for file in candidates if file.is_file()), None)
        if archive is None:
            missing.append(dict(filename=filename, url=record['repository']+'/'+values['Filename'], sha256=values['SHA256'], bytes=int(values['Size'])))
            continue
        assert archive.stat().st_size == int(values['Size']) and sha(archive) == values['SHA256']
        for name, data in ar_members(archive):
            if not name.startswith('data.tar'): continue
            with tarfile.open(fileobj=io.BytesIO(data)) as tar:
                for member in tar:
                    prefix = './data/data/com.termux/files/usr/'
                    if not member.name.startswith(prefix) or not member.isfile(): continue
                    relative = member.name[len(prefix):]
                    if relative in wanted:
                        file = originals / Path(relative).name
                        with tar.extractfile(member) as source, file.open('wb') as out: out.write(source.read())
                        actual = elf(file)
                        assert actual['aarch64'] and actual['load_segments_16k_compatible'] and not actual['text_relocations']
                        assert all(note['api'] <= 24 for note in actual['android_notes'])
                        patched, changed = patch_dynamic(file, aliases); destination = staged / wanted[relative]; destination.write_bytes(patched)
                        inspected = elf(destination)
                        assert all(dep in aliases.values() or dep in {'libc.so','libm.so','libdl.so','liblog.so','libandroid.so','libz.so'} for dep in inspected['needed'])
                        files.append(dict(package=package, member=relative, bytes=file.stat().st_size, original_sha256=sha(file),
                            staged_name=destination.name, staged_sha256=sha(destination), needed=inspected['needed'], runpaths=inspected['runpaths'],
                            android_notes=inspected['android_notes'], dynamic_strings_changed=changed, alignment_16k=True))
                    if package in {'libcompiler-rt','ndk-sysroot'} and relative.startswith('lib/') and (relative.endswith(('.a','.o'))):
                        support.append(dict(package=package, member=relative, bytes=member.size))
                        target = CACHE / 'support' / relative
                        target.parent.mkdir(parents=True, exist_ok=True)
                        with tar.extractfile(member) as source: target.write_bytes(source.read())
    output = dict(native_files=files, support_files=support, missing=missing,
        compiler_found=any(item['member']=='bin/clang-21' for item in files),
        linker_found=any(item['member']=='bin/lld' for item in files),
        android_execution=False, production_integrated=False,
        current_apk_sha256=sha(ROOT/'dist/CodePocket-cpp-semantics-2026-10-27-debug.apk'))
    assert output['current_apk_sha256'] == 'd828aff66043ec8bfd9c877b8f7b53873f2a7205a66b657ff248e7f78694d4cc'
    (PHASE / 'candidate-inspection.json').write_text(json.dumps(output, indent=2) + '\n')
    print(json.dumps(dict(native_files=len(files),support_files=len(support),missing=missing,android_execution=False),indent=2))


if __name__ == '__main__': main()
