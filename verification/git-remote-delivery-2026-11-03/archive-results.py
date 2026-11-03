"""Freeze this build's JUnit/Lint without accepting stale or removed test results."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
def sha(file):
    with file.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def log(file):
    raw=file.read_bytes();return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
for name,digest in json.loads((OUT/'build-inputs.json').read_text()).items():assert sha(ROOT/name)==digest,name
text=log(OUT/'full-build.log');start=(OUT/'full-build.log').stat().st_birthtime
assert 'BUILD FAILED' in text or 'BUILD SUCCESSFUL' in text
assert '> Task :app:testDebugUnitTest' in text and '> Task :app:assembleDebug' in text
cases=[];folder=OUT/'final-junit';folder.mkdir()
for source in sorted((ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
    assert source.stat().st_mtime>=start,source
    root=ET.parse(source).getroot()
    for case in root.findall('testcase'):
        cases.append(dict(id=case.get('classname')+'.'+case.get('name'),status=next((tag for tag in ('failure','error','skipped') if case.find(tag)is not None),'passed')))
    shutil.copyfile(source,folder/source.name)
old=json.loads((ROOT/'verification/git-workspace-2026-11-02/test-case-manifest.json').read_text())
current={case['id']:case['status'] for case in cases};assert len(current)==len(cases)==1109
assert len(old)==1033 and all(case['id'] in current for case in old)
assert {case['id'] for case in cases if case['status']=='skipped'}=={case['id'] for case in old if case['status']=='skipped'}
new=[case for case in cases if case['id'] not in {item['id'] for item in old}];assert len(new)==76
target=json.loads((ROOT/'verification/git-remote-workbench-2026-11-02/broad-results.json').read_text())
assert len(target['identities'])==134 and set(target['identities'])<=set(current)
stats=Counter(case['status'] for case in cases)
lint={}
for module in ('app','editor-sora','workspace-core'):
    source=ROOT/module/'build/reports/lint-results-debug.xml'
    if source.stat().st_mtime<start:
        assert f'> Task :{module}:lintReportDebug UP-TO-DATE' in text,source
        previous=ROOT/'verification/git-workspace-2026-11-02'
        assert sha(source)==sha(previous/('lint-'+module+'.xml'))
        old_sources=json.loads((previous/'final-source-hashes.json').read_text())
        for name,digest in json.loads((OUT/'build-inputs.json').read_text()).items():
            if name.startswith(module+'/src/main/'):assert old_sources[name]==digest,name
    lint[module]=dict(Counter(issue.get('severity') for issue in ET.parse(source).getroot().findall('issue')))
    shutil.copyfile(source,OUT/('lint-'+module+'.xml'))
(OUT/'test-case-manifest.json').write_text(json.dumps(cases,indent=2)+'\n',encoding='utf-8')
(OUT/'test-results.json').write_text(json.dumps(dict(tests=len(cases),counts=dict(stats),previous_identities_retained=len(old),new_tests=len(new),
    new_failed=[case for case in new if case['status']!='passed'],failures=[case for case in cases if case['status'] in ('failure','error')],lint=lint),indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(tests=len(cases),counts=dict(stats),new_failed=[case for case in new if case['status']!='passed'],lint=lint)))
