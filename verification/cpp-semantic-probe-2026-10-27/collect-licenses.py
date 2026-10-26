"""Collect candidate notices and matching libiconv source provenance. Not a release-readiness claim."""
import hashlib
import importlib.util
import io
import json
from pathlib import Path, PurePosixPath
import shutil
import tarfile

PHASE = Path(__file__).resolve().parent
ROOT = PHASE.parents[1]
CACHE = ROOT / '.tools/cpp-semantic-probe'
LICENSES = PHASE / 'licenses'
metadata = json.loads((PHASE / 'package-candidates.json').read_text())
spec = importlib.util.spec_from_file_location('elf_reader', PHASE / 'inspect-elf.py')
reader = importlib.util.module_from_spec(spec); spec.loader.exec_module(reader)
item = metadata['packages']['termux-licenses']
archive = CACHE / 'debs' / PurePosixPath(item['Filename']).name
assert hashlib.sha256(archive.read_bytes()).hexdigest() == item['SHA256']
for name, compressed in reader.ar_members(archive):
    if not name.startswith('data.tar'): continue
    with tarfile.open(fileobj=io.BytesIO(compressed), mode='r|*') as tar:
        for entry in tar:
            basename = PurePosixPath(entry.name).name
            if entry.isfile() and basename in ('NCSA.txt', 'Apache-2.0.txt', 'LGPL-2.1.txt', 'GPL-3.0.txt'):
                assert entry.size <= 100000
                (LICENSES / basename).write_bytes(tar.extractfile(entry).read())
for package, source, target in [
    ('libllvm', 'LICENSE.TXT', 'LLVM-LICENSE.txt'),
    ('libffi', 'copyright', 'LIBFFI-COPYRIGHT.txt'),
    ('libxml2', 'copyright', 'LIBXML2-COPYRIGHT.txt'),
    ('zlib', 'copyright', 'ZLIB-COPYRIGHT.txt')]:
    file = CACHE / 'extracted' / package / 'share/doc' / package / source
    assert file.is_file()
    shutil.copy2(file, LICENSES / target)
source = CACHE / 'sources/libiconv-1.19.tar.gz'
assert hashlib.sha256(source.read_bytes()).hexdigest() == '88dd96a8c0464eca144fc791ae60cd31cd8ee78321e67397e25fc095c4a19aa6'
with tarfile.open(source) as tar:
    for name in ('COPYING.LIB', 'COPYING', 'README'):
        (LICENSES / ('LIBICONV-' + name + '.txt')).write_bytes(tar.extractfile('libiconv-1.19/' + name).read())
key = CACHE / 'termux-autobuilds.gpg'
shutil.copy2(key, PHASE / 'upstream/termux-autobuilds.gpg')
notice_map = {
    'clangd/libclang-cpp/libLLVM': ['LLVM-LICENSE.txt', 'NCSA.txt'],
    'libc++_shared and C++ headers': ['LLVM-LICENSE.txt', 'NCSA.txt'],
    'libffi': ['LIBFFI-COPYRIGHT.txt'], 'libxml2': ['LIBXML2-COPYRIGHT.txt'],
    'zlib': ['ZLIB-COPYRIGHT.txt'], 'libzstd library (not CLI)': ['ZSTD-LICENSE.txt'],
    'libiconv library (not CLI)': ['LIBICONV-COPYING.LIB.txt', 'LIBICONV-README.txt'],
}
for files in notice_map.values():
    assert all((LICENSES / file).is_file() for file in files)
report = dict(notice_files={file.name: hashlib.sha256(file.read_bytes().replace(b'\r\n', b'\n')).hexdigest() for file in sorted(LICENSES.iterdir())}, text_hashes_normalized_lf=True,
              map=notice_map, termux_key_sha256=hashlib.sha256(key.read_bytes()).hexdigest(),
              libiconv_source=dict(version='1.19', bytes=source.stat().st_size,
                  sha256=hashlib.sha256(source.read_bytes()).hexdigest(),
                  url='https://mirrors.kernel.org/gnu/libiconv/libiconv-1.19.tar.gz'),
              command_line_tools_excluded=True, production_notice_ui_integrated=False,
              corresponding_source_delivery_pending=True, relink_instructions_pending=True,
              full_ndk_r30_notice_verification_pending=True, release_ready=False)
(PHASE / 'license-inspection.json').write_text(json.dumps(report, indent=2) + '\n')
print('Collected', len(report['notice_files']), 'notice texts and matching libiconv source provenance; production delivery remains pending.')
