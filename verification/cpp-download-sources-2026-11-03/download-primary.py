"""Fetch the exact locked public packages from the documented official primary host."""
from concurrent.futures import ThreadPoolExecutor,as_completed
import hashlib
import json
from pathlib import Path,PurePosixPath
import urllib.request
ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
CACHE=ROOT/'.tools/cpp-download-source-probe';CACHE.mkdir(parents=True,exist_ok=True)
SOURCE='https://packages.termux.dev/apt/termux-main'
lock=json.loads((ROOT/'app/cpp-runtime-lock.json').read_text())
def fetch(item):
    package,meta=item;name=PurePosixPath(meta['Filename']).name;target=CACHE/name
    assert target.resolve().is_relative_to(CACHE.resolve())
    expected=int(meta['Size']);url=SOURCE+'/'+meta['Filename']
    if target.exists():
        assert target.stat().st_size==expected
        with target.open('rb') as stream:assert hashlib.file_digest(stream,'sha256').hexdigest()==meta['SHA256']
        return dict(package=package,filename=name,source=url,bytes=expected,sha256=meta['SHA256'],downloaded=False)
    temporary=target.with_suffix(target.suffix+'.partial');count=0;digest=hashlib.sha256()
    with urllib.request.urlopen(url,timeout=60) as response,temporary.open('wb') as stream:
        assert response.status==200 and response.geturl()==url
        while data:=response.read(1024*1024):
            count+=len(data);assert count<=expected
            digest.update(data);stream.write(data)
    assert count==expected and digest.hexdigest()==meta['SHA256'],name
    temporary.replace(target)
    return dict(package=package,filename=name,source=url,bytes=count,sha256=digest.hexdigest(),downloaded=True)
results=[]
with ThreadPoolExecutor(max_workers=3) as executor:
    for future in as_completed([executor.submit(fetch,item) for item in lock['packages'].items()]):
        result=future.result();results.append(result)
        (OUT/'primary-downloads.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8')
        print(result['filename'],result['bytes'],'SHA256_VERIFIED',flush=True)
assert len(results)==len(lock['packages'])==12
print('ALL_12_ORIGINAL_LOCKED_PACKAGES_VERIFIED_FROM_OFFICIAL_PRIMARY',flush=True)
