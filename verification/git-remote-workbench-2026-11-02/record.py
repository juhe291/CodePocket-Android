"""Archive only fresh results from this phase, preserving every previous related test."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
label=sys.argv[1]
assert label in ('first','fixed','ui','broad')
log=OUT/f'targeted-{label}.log';raw=log.read_bytes()
text=raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig',errors='replace')
assert 'BUILD SUCCESSFUL' in text or 'BUILD FAILED' in text
assert '> Task :app:testDebugUnitTest' in text,'Compilation failure: do not archive old JUnit'
old=json.loads((ROOT/'verification/git-remote-service-2026-11-02/broad-results.json').read_text())
classes=sorted(set(key.rsplit('.',1)[0] for key in old['identities'])|{'com.codepocket.ide.GitRemoteWorkbenchTest'})
if label in ('ui','broad'):classes.append('com.codepocket.ide.GitRemoteUiIntegrationTest')
folder=OUT/f'{label}-junit';folder.mkdir();stats={};ids=[]
for name in classes:
    source=ROOT/f'app/build/test-results/testDebugUnitTest/TEST-{name}.xml'
    assert source.stat().st_mtime>=log.stat().st_birthtime,name
    root=ET.parse(source).getroot();assert root.get('name')==name
    stats[name]={key:int(root.get(key)) for key in ('tests','failures','errors','skipped')}
    ids.extend(case.get('classname')+'.'+case.get('name') for case in root.findall('testcase'))
    shutil.copyfile(source,folder/source.name)
assert len(set(ids))==len(ids) and set(old['identities'])<=set(ids)
sources={}
for base in ('app/src','workspace-core/src','tests'):
    for path in sorted((ROOT/base).rglob('*')):
        if path.is_file() and path.suffix in ('.kt','.java','.py','.xml'):
            sources[path.relative_to(ROOT).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
result=dict(tests=len(ids),stats=stats,identities=sorted(ids),old_related_test_identities_retained=len(old['identities']),
    new_apk_built=False,android_execution=False)
(OUT/f'{label}-sources.json').write_text(json.dumps(sources,indent=2)+'\n',encoding='utf-8')
(OUT/f'{label}-results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(label=label,tests=len(ids),stats=stats)))
