"""Inspect official aarch64 candidates, without installing or claiming Android execution."""
import gzip
import hashlib
import json
from pathlib import Path

phase = Path(__file__).resolve().parent
root = phase.parents[1]
cache = root / '.tools/cpp-semantic-probe'
raw = (cache / 'Packages.gz').read_bytes()
release = (cache / 'InRelease').read_text()
sha = hashlib.sha256(raw).hexdigest()
expected = [line.split() for line in release.split('SHA256:\n', 1)[1].split('SHA512:', 1)[0].splitlines()
            if line.strip().endswith('main/binary-aarch64/Packages.gz')]
assert len(expected) == 1 and expected[0][0] == sha and int(expected[0][1]) == len(raw), expected

packages = {}
for section in gzip.decompress(raw).decode().split('\n\n'):
    data = {}
    last = None
    for line in section.splitlines():
        if line.startswith(' ') and last:
            data[last] += '\n' + line[1:]
        elif ': ' in line:
            last, value = line.split(': ', 1); data[last] = value
    if 'Package' in data:
        packages[data['Package']] = data
names = ['clang', 'clangd', 'libllvm', 'libc++', 'ndk-sysroot', 'zlib', 'libxml2', 'libffi', 'ncurses', 'zstd',
         'liblzma', 'libiconv', 'libandroid-support', 'lld', 'llvm', 'libcompiler-rt', 'termux-licenses']
candidates = {name: {key: packages[name].get(key) for key in
                    ('Package', 'Version', 'Architecture', 'Filename', 'Size', 'Installed-Size', 'SHA256', 'Depends')}
              for name in names if name in packages}
signature_file = phase / 'signature-verification.log'
signature = signature_file.read_text(encoding='utf-8-sig') if signature_file.exists() else ''
fingerprint = 'CC72CF8BA7DBFA0182877D045A897D96E57CF20C'
verified = '[GNUPG:] VALIDSIG ' + fingerprint + ' ' in signature and '[GNUPG:] GOODSIG ' in signature
report = dict(checked_client_date='2026-10-27', repository='https://packages-cf.termux.dev/apt/termux-main',
              index_path='dists/stable/main/binary-aarch64/Packages.gz', index_sha256=sha, index_bytes=len(raw),
              index_matches_inrelease_sha256=True, gpg_signature_verified=verified, signature_fingerprint=fingerprint if verified else None,
              android_execution=False, elf_compatibility_proven=False, production_backend_selected=False,
              packages=candidates)
(phase / 'package-candidates.json').write_text(json.dumps(report, indent=2) + '\n')
(phase / 'InRelease').write_text(release)
index = {file.name: dict(sha256=hashlib.sha256(file.read_bytes() if file.suffix == '.gpg' else file.read_bytes().replace(b'\r\n', b'\n')).hexdigest(), bytes=file.stat().st_size,
                        normalized_lf=file.suffix != '.gpg')
         for file in sorted((phase / 'upstream').iterdir())}
(phase / 'upstream-source-hashes.json').write_text(json.dumps(index, indent=2) + '\n')
for name, item in candidates.items():
    print(name, item['Version'], item['Architecture'], 'download bytes=' + item['Size'], 'installed KiB=' + item['Installed-Size'])
print('SHA256 matches InRelease; GPG verified=', verified, '; actual Android execution remains unproven.')
