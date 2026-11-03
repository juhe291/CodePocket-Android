"""Cache two fixed pure Python distributions from official PyPI, verifying every byte."""
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
CACHE=ROOT/'.tools/formatting-probe'
SITE=CACHE/'site'
SITE.mkdir(parents=True,exist_ok=True)
WHEELS=CACHE/'wheels'
WHEELS.mkdir(exist_ok=True)
rows=[]
for name,version in [('autopep8','2.3.2'),('pycodestyle','2.14.0')]:
    source=f'https://pypi.org/pypi/{name}/{version}/json'
    with urllib.request.urlopen(source,timeout=30) as response: metadata=json.load(response)
    assert metadata['info']['version']==version
    wheel=next(item for item in metadata['urls'] if item['filename'].endswith('-py2.py3-none-any.whl'))
    assert not wheel['yanked'] and wheel['url'].startswith('https://files.pythonhosted.org/packages/')
    path=WHEELS/wheel['filename']
    if path.exists(): data=path.read_bytes()
    else:
        with urllib.request.urlopen(wheel['url'],timeout=30) as response: data=response.read(1_000_001)
        assert len(data)<=1_000_000
        assert hashlib.sha256(data).hexdigest()==wheel['digests']['sha256']
        path.write_bytes(data)
    assert len(data)==wheel['size'] and hashlib.sha256(data).hexdigest()==wheel['digests']['sha256']
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        assert archive.testzip() is None
        for member in archive.infolist():
            assert (SITE/member.filename).resolve().is_relative_to(SITE.resolve())
        license_path=next(n for n in archive.namelist() if n.upper().endswith('/LICENSE') or n.upper().endswith('/COPYING'))
        license_text=archive.read(license_path).decode('utf-8')
        assert 'Permission is hereby granted' in license_text
        archive.extractall(SITE)
        (PHASE/(name+'-LICENSE.txt')).write_text(license_text,encoding='utf-8')
    rows.append(dict(name=name,version=version,metadata=source,url=wheel['url'],filename=path.name,
        sha256=hashlib.sha256(data).hexdigest(),bytes=len(data),requires_python=metadata['info']['requires_python'],
        requires_dist=metadata['info']['requires_dist'],license='MIT',universal_pure_python=True))
(PHASE/'python-dependencies.json').write_text(json.dumps(rows,indent=2)+'\n')
print(json.dumps(rows,indent=2))
