import hashlib
import json
from pathlib import Path
import shutil
import sys
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
label=sys.argv[1];assert label in ('first','cross','broad')
log=OUT/('targeted-'+label+'.log');raw=log.read_bytes()
text=raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig',errors='replace')
assert 'BUILD SUCCESSFUL' in text or 'BUILD FAILED' in text
classes=['GitRemoteSecurityTest','GitRemoteServiceTest','PythonAnalysisServiceTest','GitOperationServiceTest','GitWorkbenchTest']
if label=='broad':classes+=['GitRemoteJournalTest','GitRemotePlanTest','GitWorkspaceStoreTest','SafGitBinaryIntegrationTest',
    'SafGitRemoteJournalIntegrationTest','GitWorkbenchIntegrationTest','ShellUiTest']
folder=OUT/(label+'-junit');folder.mkdir();stats={};ids=[]
assert '> Task :app:testDebugUnitTest' in text,'Do not archive older JUnit after a compilation failure'
for name in classes:
    source=ROOT/('app/build/test-results/testDebugUnitTest/TEST-com.codepocket.ide.'+name+'.xml')
    assert source.stat().st_mtime>=log.stat().st_birthtime
    root=ET.parse(source).getroot();assert root.get('name')=='com.codepocket.ide.'+name
    stats[name]={key:int(root.get(key)) for key in ('tests','failures','errors','skipped')}
    ids.extend(case.get('classname')+'.'+case.get('name') for case in root.findall('testcase'))
    shutil.copyfile(source,folder/source.name)
assert len(ids)==(36 if label=='first' else 120 if label=='broad' else 37) and len(set(ids))==len(ids)
sources={}
for base in ('app/src','workspace-core/src','tests'):
    for path in sorted((ROOT/base).rglob('*')):
        if path.is_file() and path.suffix in ('.kt','.java','.py','.xml'):
            sources[path.relative_to(ROOT).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
result=dict(tests=len(ids),stats=stats,identities=sorted(ids),new_apk_built=False,android_execution=False,keystore_device_execution=False)
if label=='broad':
    old=json.loads((ROOT/'verification/git-remote-journal-2026-11-02/broad-results.json').read_text())
    assert len(old['identities'])==92 and set(old['identities'])<=set(ids)
    result['old_related_test_identities_retained']=92
(OUT/(label+'-sources.json')).write_text(json.dumps(sources,indent=2)+'\n',encoding='utf-8')
(OUT/(label+'-results.json')).write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(label=label,tests=len(ids),stats=stats)))
