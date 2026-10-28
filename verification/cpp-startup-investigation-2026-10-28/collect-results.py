"""Record the whole checkout honestly, including unfinished and host-limited tests."""
import collections
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
PREVIOUS=ROOT/'verification/settings-touch-fix-2026-10-28'

def sha(file):
    with file.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def log(file):
    data=file.read_bytes()
    return data.decode('utf-16' if data.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')

def junit(folder):
    rows=[]
    for file in sorted(folder.glob('TEST-*.xml')):
        for case in ET.parse(file).getroot().findall('testcase'):
            rows.append(dict(id=case.get('classname')+'.'+case.get('name'),status=next((t for t in ('failure','error','skipped') if case.find(t) is not None),'passed')))
    assert rows and len(rows)==len({r['id'] for r in rows})
    counts=collections.Counter(r['status'] for r in rows)
    return rows,dict(tests=len(rows),passed=counts['passed'],failures=counts['failure'],errors=counts['error'],skipped=counts['skipped'])

cases,totals=junit(PHASE/'full-junit')
assert totals==dict(tests=884,passed=878,failures=5,errors=0,skipped=1),totals
previous=json.loads((PREVIOUS/'test-case-manifest.json').read_text())
old={r['id']:r['status'] for r in previous};current={r['id']:r['status'] for r in cases}
assert len(old)==874 and old.keys()<=current.keys()
regressions={key:current[key] for key in old if current[key]!=old[key]}
assert not regressions,regressions
assert {r['id'] for r in cases if r['status']=='skipped'}=={r['id'] for r in previous if r['status']=='skipped'}
failures=[r['id'] for r in cases if r['status'] in ('failure','error')]
host_case='com.codepocket.ide.CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs'
unfinished={
    'com.codepocket.ide.PythonRunProviderTest.'+name for name in (
        'newModulesAndBinaryResourcesAppearWithoutReplacingTheEditorMirror',
        'deletedProviderFilesAreNotReadFromTheStaleMirror',
        'aProviderCycleAddedAfterOpeningIsRejectedAndOnlyTheOwnedJobIsRemoved',
        'providerFailureRemovesThePartialJobWithoutTouchingTheMirror')}
assert set(failures)==unfinished|{host_case},failures
full=log(PHASE/'full-build.log')
assert 'BUILD FAILED' in full and '> Task :app:testDebugUnitTest FAILED' in full
assert '> Task :app:packageDebug\n' in full and '> Task :app:assembleDebug' in full
terminal_source=ROOT/'third-party/termux/terminal-emulator/build/test-results/testDebugUnitTest'
_,terminal=junit(terminal_source)
assert terminal['tests']==146 and terminal['failures']==terminal['errors']==terminal['skipped']==0
terminal_dir=PHASE/'full-terminal-junit';terminal_dir.mkdir(exist_ok=True)
for file in terminal_source.glob('TEST-*.xml'):shutil.copy2(file,terminal_dir/file.name)
lint={}
for module in ('app','editor-sora'):
    file=ROOT/module/'build/reports/lint-results-debug.xml'
    counts=collections.Counter(i.get('severity') for i in ET.parse(file).getroot().findall('issue'))
    assert counts['Error']==counts['Fatal']==0
    lint[module]=dict(counts);shutil.copy2(file,PHASE/('lint-'+module+'.xml'))
source=json.loads((PHASE/'full-source-hashes.json').read_text())
for name,digest in source.items():assert sha(ROOT/name)==digest,name
before=json.loads((PHASE/'before-apks.json').read_text());assert len(before)==93
for item in before:
    file=ROOT/'dist'/item['name'];assert file.stat().st_size==item['bytes'] and sha(file)==item['sha256']
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
baseline=ROOT/'dist/CodePocket-settings-touch-fix-2026-10-28-debug.apk'
with zipfile.ZipFile(apk) as archive,zipfile.ZipFile(baseline) as old_apk:
    assert archive.testzip() is None
    names=[n for n in old_apk.namelist() if n.startswith('lib/') and n.endswith('.so')]
    assets=[n for n in old_apk.namelist() if n.startswith(('assets/chaquopy/','assets/cpp-')) and not n.endswith('/')]
    assert len(names)==25 and len(assets)==48
    for name in names+assets:assert archive.read(name)==old_apk.read(name),name
assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in log(PHASE/'certificates.txt').lower()
assert log(PHASE/'manifest-tree.txt')==log(PREVIOUS/'manifest-tree.txt')
assert log(PHASE/'permissions.txt')==log(PREVIOUS/'permissions.txt')
packaged=json.loads((PHASE/'apk-python.json').read_text())
semantics=json.loads((PHASE/'apk-semantics-workspace-temp.json').read_text())
cpp=json.loads((PHASE/'apk-cpp-static.json').read_text())
assert packaged['sha256']==semantics['apk_sha256']==cpp['apk_sha256']==sha(apk)
assert packaged['bundled_total_tests']==39 and packaged['test_failures']==packaged['test_errors']==0
assert semantics['tests']==22 and semantics['failures']==semantics['errors']==0
assert cpp['real_android_execution'] is False
result=dict(apk='dist/CodePocket-cpp-startup-diagnostic-2026-10-28-debug.apk',bytes=apk.stat().st_size,sha256=sha(apk),
    artifact_role='diagnostic_candidate_not_all_tests_green',full_build_exit_success=False,junit=totals,
    previous_cases_preserved=874,previous_case_regressions=regressions,new_skips=False,
    host_filesystem_failure=host_case,unfinished_python_tests=sorted(unfinished),terminal=terminal,lint=lint,
    native_files_unchanged=25,runtime_assets_unchanged=48,historical_apks_preserved=93,
    packaged_python_tests=39,packaged_semantic_tests=22,cpp_apk_static_check=True,
    semantic_first_attempt_host_temp_errors=30,semantic_workspace_temp_retry_passed=True,previews_viewed=1,
    editor_assist='NO-SOURCE; contracts tested in app',
    android_device_execution=False,user_device_root_cause_confirmed=False,full_goal_complete=False)
destination=ROOT/result['apk'];assert not destination.exists() or sha(destination)==result['sha256']
if not destination.exists():shutil.copy2(apk,destination)
(PHASE/'results.json').write_text(json.dumps(result,indent=2)+'\n')
(PHASE/'test-case-manifest.json').write_text(json.dumps(cases,indent=2)+'\n')
print(json.dumps(result,indent=2))
