"""Archive fresh targeted JUnit and exact inputs; never substitute older results."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
label=sys.argv[1];assert label in ('first','saf','fixed','broad')
log=OUT/('broad.log' if label=='broad' else 'targeted-'+label+'.log');raw=log.read_bytes()
text=raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig',errors='replace')
assert 'BUILD SUCCESSFUL' in text or 'BUILD FAILED' in text
assert '> Task :app:testDebugUnitTest' in text,'No fresh test execution; do not copy older XML'
classes=['GitRemoteJournalTest','GitRemotePlanTest','GitWorkspaceStoreTest','SafGitBinaryIntegrationTest']
if label!='first':classes.append('SafGitRemoteJournalIntegrationTest')
if label=='broad':classes+=['GitWorkbenchTest','GitOperationServiceTest','GitWorkbenchIntegrationTest','ShellUiTest']
folder=OUT/(label+'-junit');folder.mkdir();stats={};ids=[]
for name in classes:
    source=ROOT/('app/build/test-results/testDebugUnitTest/TEST-com.codepocket.ide.'+name+'.xml')
    assert source.stat().st_mtime>=log.stat().st_birthtime
    root=ET.parse(source).getroot();assert root.get('name')=='com.codepocket.ide.'+name
    stats[name]={key:int(root.get(key)) for key in ('tests','failures','errors','skipped')}
    ids.extend(case.get('classname')+'.'+case.get('name') for case in root.findall('testcase'))
    shutil.copyfile(source,folder/source.name)
assert len(ids)==(57 if label=='first' else 92 if label=='broad' else 62) and len(set(ids))==len(ids)
old=json.loads((ROOT/'verification/git-remote-workspace-2026-11-02/native-engine-results.json').read_text())
assert len(old['identities'])==40 and set(old['identities'])<=set(ids)
sources={}
for base in ('app/src','workspace-core/src','tests'):
    for path in sorted((ROOT/base).rglob('*')):
        if path.is_file() and path.suffix in ('.kt','.java','.py','.xml'):
            sources[path.relative_to(ROOT).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
previous=json.loads((ROOT/'verification/git-remote-workspace-2026-11-02/native-engine-sources.json').read_text())
reused={p:sha for p,sha in previous.items() if p.endswith('.py') and (p.startswith('app/src/main/python/') or p.startswith('tests/'))}
assert reused and all(sources[p]==sha for p,sha in reused.items())
result=dict(tests=len(ids),stats=stats,identities=sorted(ids),old_targeted_identities_retained=40,
    python_tests_reused=151,python_source_hashes=reused,baseline_commit='5b13a7b',new_apk_built=False,android_execution=False)
if label=='broad':
    old_local=[]
    for name in ('GitWorkspaceStoreTest','SafGitBinaryIntegrationTest','GitWorkbenchTest','GitOperationServiceTest','GitWorkbenchIntegrationTest','ShellUiTest'):
        source=ROOT/('verification/git-workspace-2026-11-02/final-junit/TEST-com.codepocket.ide.'+name+'.xml')
        root=ET.parse(source).getroot();old_local.extend(t.get('classname')+'.'+t.get('name') for t in root.findall('testcase'))
    assert len(old_local)==52 and set(old_local)<=set(ids)
    result['old_local_workbench_identities_retained']=52
(OUT/(label+'-sources.json')).write_text(json.dumps(sources,indent=2)+'\n',encoding='utf-8')
(OUT/(label+'-results.json')).write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(label=label,tests=len(ids),stats=stats)))
