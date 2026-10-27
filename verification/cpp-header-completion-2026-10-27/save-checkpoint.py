"""Save exact test identities, source hashes and shared editor inputs for this batch."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
name=sys.argv[1];assert name.replace('-','').isalnum()
target=PHASE/(name+'-junit');target.mkdir(exist_ok=True)
cases=[]
for file in sorted((ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
    shutil.copy2(file,target/file.name)
    for case in ET.parse(file).getroot().findall('testcase'):
        cases.append(dict(id=case.get('classname')+'.'+case.get('name'),status=next((tag for tag in ('failure','error','skipped') if case.find(tag) is not None),'passed')))
assert cases and len(cases)==len({case['id'] for case in cases})
totals=dict(tests=len(cases),failures=sum(c['status']=='failure' for c in cases),errors=sum(c['status']=='error' for c in cases),skipped=sum(c['status']=='skipped' for c in cases))
(PHASE/(name+'-identities.json')).write_text(json.dumps(dict(totals=totals,cases=cases),indent=2)+'\n')
hashes={}
for base in ['app/src/main','app/src/test/java','editor-assist/src/main','editor-assist/src/test']:
    for file in sorted((ROOT/base).rglob('*')):
        if file.is_file():hashes[file.relative_to(ROOT).as_posix()]=hashlib.sha256(file.read_bytes()).hexdigest()
for file in [ROOT/'app/build.gradle.kts',ROOT/'app/cpp-runtime-lock.json']:
    hashes[file.relative_to(ROOT).as_posix()]=hashlib.sha256(file.read_bytes()).hexdigest()
(PHASE/(name+'-source-hashes.json')).write_text(json.dumps(hashes,indent=2)+'\n')
print(json.dumps(totals))
