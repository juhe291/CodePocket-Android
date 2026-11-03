"""Probe a collision-free, relocatable Android native bundle. Does not change the production App."""
import hashlib
import importlib.util
import json
from pathlib import Path
import struct
import zipfile

PHASE = Path(__file__).resolve().parent
ROOT = PHASE.parents[1]
CACHE = ROOT / '.tools/cpp-semantic-probe'
SYSTEM = {'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libandroid.so', 'libz.so'}
spec = importlib.util.spec_from_file_location('inspect_elf', PHASE / 'inspect-elf.py')
reader = importlib.util.module_from_spec(spec); spec.loader.exec_module(reader)


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


def main():
    inspection = json.loads((PHASE / 'elf-inspection.json').read_text())
    metadata = json.loads((PHASE / 'package-candidates.json').read_text())
    assert metadata['gpg_signature_verified']
    all_elfs = [item for package in inspection['packages'] for item in package['elfs']]
    libraries = {item['soname']: item for item in all_elfs if item['soname']}
    main = next(item for item in all_elfs if item['file'].endswith('/bin/clangd'))
    closure = {}

    def include(item):
        if item['file'] in closure: return
        assert item['aarch64'] and item['load_segments_16k_compatible'] and not item['text_relocations']
        assert all(note['api'] <= 24 for note in item['android_notes'])
        closure[item['file']] = item
        for needed in item['needed']:
            if needed not in SYSTEM:
                assert needed in libraries, 'Missing actual runtime dependency: ' + needed
                include(libraries[needed])
    include(main)
    names = sorted(item['soname'] for item in closure.values() if item['soname'])
    assert len(names) < 26
    aliases = {name: 'libcp' + chr(65 + i) + '_' * (len(name) - 9) + '.so' for i, name in enumerate(names)}
    assert all(len(old) == len(new) and len(old) >= 9 for old, new in aliases.items())
    destination = CACHE / 'staged-native/arm64-v8a'; destination.mkdir(parents=True, exist_ok=True)
    output = []
    with zipfile.ZipFile(ROOT / 'dist/CodePocket-python-project-context-2026-10-26-debug.apk') as apk:
        previous = {Path(name).name for name in apk.namelist() if name.startswith('lib/')}
    for item in closure.values():
        source = ROOT / item['file']
        name = aliases.get(item['soname'], 'libcodepocket_clangd.so')
        assert name not in previous
        target = destination / name
        patched, changes = patch_dynamic(source, aliases)
        target.write_bytes(patched)
        checked = reader.elf(target)
        assert checked['aarch64'] and checked['load_segments_16k_compatible']
        assert checked['needed'] == [aliases.get(name, name) for name in item['needed']]
        assert all(path == '$ORIGIN' for path in checked['runpaths'])
        assert checked['bytes'] == item['bytes'] and checked['loads'] == item['loads']
        output.append(dict(source=item['file'], source_sha256=item['sha256'], packaged_name=name,
                           sha256=checked['sha256'], bytes=checked['bytes'], dynamic_string_edits=changes,
                           needed=checked['needed'], min_api_notes=checked['android_notes']))
    bundle = CACHE / 'staged-native-probe.zip'
    with zipfile.ZipFile(bundle, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for item in output: archive.write(destination / item['packaged_name'], 'arm64-v8a/' + item['packaged_name'])
    result = dict(real_elf_inputs=True, source_archives_gpg_verified=True, native_count=len(output),
                  raw_bytes=sum(item['bytes'] for item in output), probe_zip_bytes=bundle.stat().st_size,
                  zip_is_not_an_apk=True, all_dependencies_resolved=True, existing_jni_libraries_untouched=True,
                  alias_map=aliases, files=output, android_execution=False, production_integrated=False)
    (PHASE / 'native-bundle-inspection.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({k: v for k, v in result.items() if k not in ('alias_map', 'files')}, indent=2))


if __name__ == '__main__': main()
