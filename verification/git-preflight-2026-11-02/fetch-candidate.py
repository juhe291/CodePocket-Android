"""Cache official pure wheels for a Git feasibility probe, without changing app dependencies."""
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PHASE = Path(__file__).resolve().parent
CACHE = ROOT / '.tools/git-probe'
SITE = CACHE / 'site'
WHEELS = CACHE / 'wheels'
SITE.mkdir(parents=True, exist_ok=True)
WHEELS.mkdir(exist_ok=True)


def read(url, limit):
    assert url.startswith(('https://pypi.org/pypi/', 'https://files.pythonhosted.org/packages/'))
    with urllib.request.urlopen(url, timeout=30) as response:
        data = response.read(limit + 1)
    assert len(data) <= limit
    return data


metadata = json.loads((PHASE / 'dulwich-1.2.20-metadata.json').read_bytes())
urllib_version = '2.8.0'
urllib_data = read(f'https://pypi.org/pypi/urllib3/{urllib_version}/json', 4_000_000)
urllib_metadata = json.loads(urllib_data)
assert urllib_metadata['info']['name'] == 'urllib3' and urllib_metadata['info']['version'] == urllib_version
version = urllib_metadata['info']['version']
(PHASE / f'urllib3-{version}-metadata.json').write_bytes(urllib_data)
rows = []
for info in (metadata, urllib_metadata):
    name = info['info']['name']
    wheels = [item for item in info['urls'] if item['filename'].endswith('-py3-none-any.whl') and not item['yanked']]
    assert len(wheels) == 1
    wheel = wheels[0]
    path = WHEELS / wheel['filename']
    data = path.read_bytes() if path.exists() else read(wheel['url'], 2_000_000)
    assert len(data) == wheel['size'] and hashlib.sha256(data).hexdigest() == wheel['digests']['sha256']
    if not path.exists():
        path.write_bytes(data)
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None
        assert len(archive.namelist()) == len(set(archive.namelist()))
        assert not any(n.endswith(('.so', '.pyd', '.dll', '.exe')) for n in archive.namelist())
        licenses = []
        for member in archive.infolist():
            target = SITE / member.filename
            assert target.resolve().is_relative_to(SITE.resolve())
            assert member.file_size <= 2_000_000
            if not member.is_dir():
                target.parent.mkdir(parents=True, exist_ok=True)
                payload = archive.read(member)
                if target.exists():
                    assert target.read_bytes() == payload
                else:
                    target.write_bytes(payload)
                if 'LICENSE' in member.filename.upper() or 'COPYING' in member.filename.upper():
                    notice = PHASE / 'licenses' / name / member.filename
                    notice.parent.mkdir(parents=True, exist_ok=True)
                    notice.write_bytes(payload)
                    licenses.append(dict(member=member.filename, sha256=hashlib.sha256(payload).hexdigest()))
        assert licenses
    rows.append(dict(name=name, version=info['info']['version'], filename=path.name, bytes=len(data),
                     sha256=hashlib.sha256(data).hexdigest(), requires_python=info['info']['requires_python'],
                     requires_dist=info['info']['requires_dist'], source=wheel['url'], licenses=licenses))
(PHASE / 'candidate-inputs.json').write_text(json.dumps(rows, indent=2) + '\n', encoding='utf-8')
print(json.dumps(rows, indent=2))
