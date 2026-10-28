"""Audit project-run changes without hiding the retained Windows header extraction failure."""
import collections
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2];PHASE=Path(__file__).resolve().parent
PREVIOUS=ROOT/'verification/cpp-startup-investigation-2026-10-28'
def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def log(path):
    data=path.read_bytes();return data.decode('utf-16' if data.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
def junit(folder):
    rows=[]
    for file in folder.glob('TEST-*.xml'):
        for case in ET.parse(file).getroot().findall('testcase'):
            rows.append(dict(id=case.get('classname')+'.'+case.get('name'),status=next((t for t in ('failure','error','skipped') if case.find(t) is not None),'passed')))
    assert rows and len(rows)==len({r['id'] for r in rows})
    c=collections.Counter(r['status'] for r in rows)
    return sorted(rows,key=lambda r:r['id']),dict(tests=len(rows),passed=c['passed'],failures=c['failure'],errors=c['error'],skipped=c['skipped'])

cases,totals=junit(PHASE/'final-junit');targeted_cases,targeted=junit(PHASE/'targeted-junit')
assert totals==dict(tests=896,passed=894,failures=1,errors=0,skipped=1),totals
assert targeted==dict(tests=61,passed=61,failures=0,errors=0,skipped=0),targeted
old=json.loads((PREVIOUS/'test-case-manifest.json').read_text())
assert len(old)==884
before={r['id']:r['status'] for r in old};after={r['id']:r['status'] for r in cases}
assert before.keys()<=after.keys()
fixed={r['id'] for r in old if '.PythonRunProviderTest.' in r['id'] and r['status']=='failure'}
assert len(fixed)==4 and all(after[key]=='passed' for key in fixed)
assert all(after[key]==value for key,value in before.items() if key not in fixed)
host='com.codepocket.ide.CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs'
assert {r['id'] for r in cases if r['status'] in ('failure','error')}=={host}
assert {r['id'] for r in cases if r['status']=='skipped'}=={r['id'] for r in old if r['status']=='skipped'}
full=log(PHASE/'full-build.log')
assert 'BUILD FAILED' in full and '> Task :app:testDebugUnitTest FAILED' in full
assert '> Task :app:packageDebug\n' in full and '> Task :app:assembleDebug' in full
hashes=json.loads((PHASE/'final-source-hashes.json').read_text())
changed_after_build={name for name,digest in hashes.items() if sha(ROOT/name)!=digest}
assert changed_after_build=={'scripts/verify_cpp_apk.py'},changed_after_build
hashes['scripts/verify_cpp_apk.py']=sha(ROOT/'scripts/verify_cpp_apk.py')
hashes['verification/python-project-run-2026-10-28/test_cpp_python_archive_guard.py']=sha(PHASE/'test_cpp_python_archive_guard.py')
(PHASE/'verified-source-hashes.json').write_text(json.dumps(hashes,indent=2)+'\n')
for name,digest in hashes.items():assert sha(ROOT/name)==digest,name
old_sources={n:h for n,h in json.loads((PREVIOUS/'full-source-hashes.json').read_text()).items() if '/__pycache__/' not in n}
changed={n for n in old_sources if n.startswith('app/src/main/') and old_sources[n]!=hashes[n]}
assert changed=={
    'app/src/main/java/com/codepocket/ide/ExecutionEngines.kt','app/src/main/java/com/codepocket/ide/MainActivity.kt',
    'app/src/main/java/com/codepocket/ide/PythonExecutionService.kt','app/src/main/java/com/codepocket/ide/RunSession.kt',
    'app/src/main/python/runner.py'},changed
assert {n for n in hashes if n.startswith('app/src/main/')} - {n for n in old_sources if n.startswith('app/src/main/')}=={'app/src/main/java/com/codepocket/ide/PythonRunSnapshot.kt'}
terminal_source=ROOT/'third-party/termux/terminal-emulator/build/test-results/testDebugUnitTest'
_,terminal=junit(terminal_source);assert terminal==dict(tests=146,passed=146,failures=0,errors=0,skipped=0)
terminal_dir=PHASE/'final-terminal-junit';terminal_dir.mkdir(exist_ok=True)
for file in terminal_source.glob('TEST-*.xml'):shutil.copy2(file,terminal_dir/file.name)
lint={}
for module in ('app','editor-sora'):
    file=ROOT/module/'build/reports/lint-results-debug.xml'
    counts=collections.Counter(i.get('severity') for i in ET.parse(file).getroot().findall('issue'));assert counts['Error']==counts['Fatal']==0
    lint[module]=dict(counts);shutil.copy2(file,PHASE/('lint-'+module+'.xml'))
assert 'Ran 75 tests' in log(PHASE/'python-source-offline-site.log') and log(PHASE/'python-source-offline-site.log').rstrip().endswith('OK')
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
baseline=ROOT/'dist/CodePocket-cpp-startup-diagnostic-2026-10-28-debug.apk'
python=json.loads((PHASE/'apk-inspection.json').read_text());semantics=json.loads((PHASE/'semantic-apk-inspection.json').read_text());cpp=json.loads((PHASE/'cpp-apk-inspection.json').read_text())
assert python['sha256']==semantics['apk_sha256']==cpp['apk_sha256']==sha(apk)
assert python['bundled_total_tests']==48 and python['bundled_project_tests']==9 and python['test_failures']==python['test_errors']==0
assert semantics['tests']==22 and semantics['failures']==semantics['errors']==0
assert cpp['real_android_execution'] is False and len(cpp['approved_python_changes'])==1
assert cpp['approved_python_changes'][0]['modules']==['runner.pyc']
assert len(cpp['approved_python_changes'][0]['timestamp_only_updates'])==2
assert len(cpp['build_metadata_updates'])==1
assert 'Ran 4 tests' in log(PHASE/'cpp-python-guard-final.log') and log(PHASE/'cpp-python-guard-final.log').rstrip().endswith('OK')
shell=json.loads((PHASE/'shell-startup-inspection.json').read_text());snippets=json.loads((PHASE/'snippets.json').read_text())
assert len(shell['cases'])==4 and all(case['passed'] for case in shell['cases'])
assert len(snippets['fixtures'])==14 and all(case['passed'] for case in snippets['fixtures'])
with zipfile.ZipFile(apk) as current,zipfile.ZipFile(baseline) as previous:
    assert current.testzip() is None
    native=[n for n in previous.namelist() if n.startswith('lib/') and n.endswith('.so')]
    assets=[n for n in previous.namelist() if n.startswith(('assets/cpp-','assets/chaquopy/')) and not n.endswith('/') and n not in ('assets/chaquopy/app.imy','assets/chaquopy/build.json')]
    assert len(native)==25 and len(assets)==46
    for name in native+assets:assert current.read(name)==previous.read(name),name
    dex=b''.join(current.read(n) for n in current.namelist() if n.endswith('.dex'))
    old_dex=b''.join(previous.read(n) for n in previous.namelist() if n.endswith('.dex'))
    assert dex!=old_dex and b'Lcom/codepocket/ide/PythonRunSnapshot;' in dex and b'Lcom/codepocket/ide/PythonRunSnapshot;' not in old_dex
assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in log(PHASE/'certificates.txt').lower()
assert log(PHASE/'permissions.txt')==log(PREVIOUS/'permissions.txt')
assert log(PHASE/'manifest-tree.txt')==log(PREVIOUS/'manifest-tree.txt')
old_apks=json.loads((PHASE/'before-apks.json').read_text());assert len(old_apks)==94
for item in old_apks:
    file=ROOT/'dist'/item['name'];assert file.stat().st_size==item['bytes'] and sha(file)==item['sha256'],file.name
result=dict(apk='dist/CodePocket-python-project-run-candidate-2026-10-28-debug.apk',bytes=apk.stat().st_size,sha256=sha(apk),
    size_delta_bytes=apk.stat().st_size-baseline.stat().st_size,artifact_role='candidate_with_retained_host_only_failure',full_build_exit_success=False,
    junit=totals,targeted=targeted,previous_cases_preserved=884,previously_failing_provider_cases_fixed=sorted(fixed),new_cases=12,new_skips=False,
    retained_host_failure=host,terminal=terminal,lint=lint,python_source_tests=75,packaged_python_tests=48,packaged_semantic_tests=22,shell_cases=4,snippet_cases=14,
    native_files_unchanged=25,runtime_assets_unchanged=46,approved_python_module_changes=['runner.pyc'],python_timestamp_only_updates=2,
    chaquopy_build_metadata_hash_verified=True,artifact_guard_tests=4,old_apks_preserved=94,previews_viewed=1,
    android_device_execution=False,cpp_device_failure_root_cause_confirmed=False,full_goal_complete=False)
destination=ROOT/result['apk'];assert not destination.exists() or sha(destination)==result['sha256']
if not destination.exists():shutil.copy2(apk,destination)
(PHASE/'results.json').write_text(json.dumps(result,indent=2)+'\n')
(PHASE/'test-case-manifest.json').write_text(json.dumps(cases,indent=2)+'\n')
print(json.dumps(result,indent=2))
