"""Gate the touch hotfix and separately record hash-based reuse of unchanged runtime validation."""
import collections
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2];PHASE=Path(__file__).resolve().parent
PREVIOUS=ROOT/'verification/cpp-header-completion-2026-10-27'
def sha(file):
    with file.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def read_log(file):
    data=file.read_bytes()
    return data.decode('utf-16' if data.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
def junit(folder):
    cases=[]
    for file in sorted(folder.glob('TEST-*.xml')):
        for case in ET.parse(file).getroot().findall('testcase'):
            cases.append(dict(id=case.get('classname')+'.'+case.get('name'),status=next((tag for tag in ('failure','error','skipped') if case.find(tag) is not None),'passed')))
    assert cases and len(cases)==len({case['id'] for case in cases})
    counts=collections.Counter(case['status'] for case in cases)
    return cases,dict(tests=len(cases),failures=counts['failure'],errors=counts['error'],skipped=counts['skipped'])

cases,tests=junit(PHASE/'final-junit');targeted_cases,targeted=junit(PHASE/'targeted-junit')
baseline_cases,baseline=junit(PHASE/'baseline-junit');_,terminal=junit(PHASE/'final-terminal-junit')
assert tests==dict(tests=874,failures=0,errors=0,skipped=1),tests
assert targeted==dict(tests=109,failures=0,errors=0,skipped=0),targeted
assert baseline==dict(tests=5,failures=4,errors=0,skipped=0),baseline
assert terminal==dict(tests=146,failures=0,errors=0,skipped=0),terminal
assert {c['id'] for c in baseline_cases}<={c['id'] for c in targeted_cases}
old=json.loads((PREVIOUS/'test-case-manifest.json').read_text());assert len(old)==869
assert {c['id'] for c in old}<={c['id'] for c in cases}
assert {c['id'] for c in old if c['status']=='skipped'}=={c['id'] for c in cases if c['status']=='skipped'}
log=read_log(PHASE/'full-build.log');assert 'BUILD SUCCESSFUL' in log and '> Task :app:packageDebug\n' in log
current_hashes=json.loads((PHASE/'final-source-hashes.json').read_text())
for name,digest in current_hashes.items():assert sha(ROOT/name)==digest,name
before_hashes=json.loads((PREVIOUS/'verified-source-hashes.json').read_text())
production={name for name in before_hashes if name.startswith('app/src/main/')}
assert production=={name for name in current_hashes if name.startswith('app/src/main/')}
changed={name for name in production if before_hashes[name]!=current_hashes[name]}
assert changed=={'app/src/main/java/com/codepocket/ide/ui/IdeControls.kt','app/src/main/java/com/codepocket/ide/ui/WorkbenchChoiceAdapter.kt','app/src/main/java/com/codepocket/ide/ui/CompletionCandidateRow.kt'},changed
lint={}
for module in ('app','editor-sora'):
    issues=ET.parse(ROOT/module/'build/reports/lint-results-debug.xml').getroot().findall('issue')
    counts=collections.Counter(item.get('severity') for item in issues);assert counts['Error']==counts['Fatal']==0
    lint[module]=dict(counts);(PHASE/('lint-'+module+'.xml')).write_bytes((ROOT/module/'build/reports/lint-results-debug.xml').read_bytes())
before=json.loads((PHASE/'before-apks.json').read_text());assert len(before)==92
for item in before:
    file=ROOT/'dist'/item['name'];assert file.stat().st_size==item['bytes'] and sha(file)==item['sha256'],item['name']
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk';previous=ROOT/'dist/CodePocket-header-tab-2026-10-27-debug.apk'
assert sha(previous)=='47ea9f1d1f59d056f05fcc6be0564d2f536cecff41b5a53d4081dc1c73e8a0bd'
preserved={}
with zipfile.ZipFile(apk) as archive,zipfile.ZipFile(previous) as old_apk:
    assert archive.testzip() is None
    native=[name for name in old_apk.namelist() if name.startswith('lib/') and name.endswith('.so')];assert len(native)==25
    assets=[name for name in old_apk.namelist() if name.startswith(('assets/cpp-','assets/chaquopy/')) and not name.endswith('/')];assert len(assets)==48
    for name in native+assets:
        actual=archive.read(name);assert actual==old_apk.read(name),name
        preserved[name]=hashlib.sha256(actual).hexdigest()
    dex=b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
    old_dex=b''.join(old_apk.read(name) for name in old_apk.namelist() if name.endswith('.dex'))
    assert dex!=old_dex
    for name in ('IdeControls','WorkbenchChoiceAdapter','CompletionCandidateRow'):assert ('Lcom/codepocket/ide/ui/'+name+';').encode() in dex
assert read_log(PHASE/'permissions.txt')==read_log(PREVIOUS/'permissions.txt')
assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in read_log(PHASE/'certificates.txt').lower()
assert 'sdkVersion:' in read_log(PHASE/'badging.txt') and "native-code: 'arm64-v8a'" in read_log(PHASE/'badging.txt')
assert read_log(PHASE/'manifest-tree.txt')==read_log(PREVIOUS/'manifest-tree.txt')
previous_results=json.loads((PREVIOUS/'results.json').read_text());assert previous_results['sha256']==sha(previous)
assert previous_results['python_source_tests']==66 and previous_results['packaged_python_tests']==39 and previous_results['packaged_semantic_tests']==22
assert len(list((PHASE/'previews').glob('*.png')))==2
reuse=dict(source='verification/cpp-header-completion-2026-10-27',apk_sha256=sha(previous),
    justification='Only the three UI input classes changed; all runtime sources, 25 native images and 48 C++/Chaquopy assets are identical.',
    python_source_tests=66,packaged_python_tests=39,packaged_semantic_tests=22,shell_cases=4,snippet_cases=14,reexecuted_this_batch=False,
    native_and_asset_sha256=preserved)
(PHASE/'runtime-validation-reuse.json').write_text(json.dumps(reuse,indent=2)+'\n')
result=dict(apk='dist/CodePocket-settings-touch-fix-2026-10-28-debug.apk',bytes=apk.stat().st_size,sha256=sha(apk),size_delta_bytes=apk.stat().st_size-previous.stat().st_size,
    baseline=baseline,junit=tests,targeted=targeted,terminal=terminal,old_cases_preserved=869,new_cases=5,no_new_skips=True,lint=lint,
    old_apks_preserved=92,native_files_unchanged=25,runtime_assets_unchanged=48,production_changed_files=sorted(changed),previews_viewed=2,
    runtime_validation_reused=True,android_device_execution=False,full_goal_complete=False)
destination=ROOT/result['apk'];assert not destination.exists() or sha(destination)==result['sha256']
if not destination.exists():destination.write_bytes(apk.read_bytes())
(PHASE/'test-case-manifest.json').write_text(json.dumps(cases,indent=2)+'\n');(PHASE/'results.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
