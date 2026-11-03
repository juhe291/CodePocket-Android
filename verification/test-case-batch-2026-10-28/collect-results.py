"""Verify the exact batch artifact and retained failures, then copy a single candidate APK."""
import collections,hashlib,json,re,shutil,zipfile
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2];PHASE=Path(__file__).resolve().parent
PREVIOUS=ROOT/'verification/cpp-signal6-fix-2026-10-28'
def sha(file):
    with file.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def log(file):
    raw=file.read_bytes();return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
def junit(folder):
    rows=[]
    for file in sorted(folder.glob('TEST-*.xml')):
        for case in ET.parse(file).getroot().findall('testcase'):
            rows.append(dict(id=case.get('classname')+'.'+case.get('name'),status=next((tag for tag in ('failure','error','skipped') if case.find(tag) is not None),'passed')))
    assert rows and len(rows)==len({r['id'] for r in rows})
    counts=collections.Counter(r['status'] for r in rows)
    return rows,dict(tests=len(rows),passed=counts['passed'],failures=counts['failure'],errors=counts['error'],skipped=counts['skipped'])
cases,tests=junit(PHASE/'final-junit')
assert tests==dict(tests=946,passed=944,failures=1,errors=0,skipped=1),tests
_,first_full=junit(PHASE/'full-first-junit')
assert first_full==dict(tests=946,passed=943,failures=2,errors=0,skipped=1),first_full
old=json.loads((PREVIOUS/'test-case-manifest.json').read_text());assert len(old)==911
current={r['id']:r['status'] for r in cases}
assert all(current.get(r['id'])==r['status'] for r in old)
assert {r['id'] for r in cases if r['status']=='skipped'}=={r['id'] for r in old if r['status']=='skipped'}
assert {r['id'] for r in cases if r['status']=='failure'}=={'com.codepocket.ide.CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs'}
targeted={}
for phase,expected in [('controller-first',31),('ui-first',44),('reported-bugs-baseline',55),('reported-bugs-fixed',68),('closing-targeted',19),('reader-final-targeted',8),('command-surface-fixed',24)]:
    _,value=junit(PHASE/(phase+'-junit'));assert value['tests']==expected
    assert value['errors']==value['skipped']==0
    assert value['failures']==({'ui-first':1,'reported-bugs-baseline':6}.get(phase,0)),(phase,value)
    targeted[phase]=value
source=json.loads((PHASE/'final-source-hashes.json').read_text(encoding='utf-8-sig'))
for name,digest in source.items():assert sha(ROOT/name)==digest,name
build=log(PHASE/'full-build.log')
assert 'BUILD FAILED' in build and '> Task :app:testDebugUnitTest FAILED' in build
assert '> Task :app:assembleDebug' in build and '> Task :app:packageDebug' in build
assert (PHASE/'full-build-exit.txt').read_text(encoding='utf-8-sig').strip()=='1'
lint={}
for module in ('app','editor-sora'):
    file=ROOT/module/'build/reports/lint-results-debug.xml'
    severity=collections.Counter(i.get('severity') for i in ET.parse(file).getroot().findall('issue'))
    assert severity['Error']==severity['Fatal']==0;lint[module]=dict(severity);shutil.copy2(file,PHASE/('lint-'+module+'.xml'))
terminal_source=ROOT/'third-party/termux/terminal-emulator/build/test-results/testDebugUnitTest'
_,terminal=junit(terminal_source);assert terminal==dict(tests=146,passed=146,failures=0,errors=0,skipped=0)
folder=PHASE/'final-terminal-junit';folder.mkdir(exist_ok=True)
for file in terminal_source.glob('TEST-*.xml'):shutil.copy2(file,folder/file.name)
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk';previous=ROOT/'dist/CodePocket-cpp-signal6-fix-2026-10-28-debug.apk'
assert sha(previous)=='7e8959a94c8a5cff06a76abdf217c9ce95e4e5dbf1ca08566de38d7b2ca48503'
with zipfile.ZipFile(apk) as current_apk,zipfile.ZipFile(previous) as old_apk:
    assert current_apk.testzip() is None
    natives=[n for n in old_apk.namelist() if n.startswith('lib/') and n.endswith('.so')];assert len(natives)==25
    assets=[n for n in old_apk.namelist() if n.startswith(('assets/cpp-','assets/chaquopy/')) and not n.endswith('/')];assert len(assets)==48
    for name in natives+assets:assert current_apk.read(name)==old_apk.read(name),name
    dex=b''.join(current_apk.read(n) for n in current_apk.namelist() if n.endswith('.dex'))
    for name in (b'CaseRunSession',b'TestCasesDialog',b'CaseBatch',b'SyntaxThemePreview'):assert name in dex,name
python=json.loads((PHASE/'apk-python.json').read_text());cpp=json.loads((PHASE/'cpp-apk-inspection.json').read_text());semantic=json.loads((PHASE/'semantic-apk-inspection.json').read_text())
assert python['sha256']==cpp['apk_sha256']==semantic['apk_sha256']==sha(apk)
assert python['bundled_total_tests']==48 and python['test_failures']==python['test_errors']==0
assert semantic['tests']==22 and semantic['failures']==semantic['errors']==0
for name,count in [('python-source.log',75)]:
    text=log(PHASE/name);assert re.search(r'Ran '+str(count)+r' tests? in ',text) and '\nOK\n' in text
shell=json.loads((PHASE/'shell-startup-inspection.json').read_text())
assert len(shell['cases'])==4 and all(case['passed'] for case in shell['cases']) and not shell['android_mksh_pty_executed']
snippets=json.loads((PHASE/'snippets.json').read_text())
assert snippets['python_passed']==snippets['cpp_passed']==7 and snippets['failures']==0 and not snippets['device_execution']
assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in log(PHASE/'certificates.txt').lower()
assert log(PHASE/'permissions.txt')==log(PREVIOUS/'permissions.txt') and log(PHASE/'manifest-tree.txt')==log(PREVIOUS/'manifest-tree.txt')
before=json.loads((PHASE/'before-apks.json').read_text());assert len(before)==96
for item in before:
    file=ROOT/'dist'/item['name'];assert file.stat().st_size==item['bytes'] and sha(file)==item['sha256']
result=dict(apk='dist/CodePocket-test-cases-and-run-input-2026-10-28-debug.apk',sha256=sha(apk),bytes=apk.stat().st_size,
    size_delta_bytes=apk.stat().st_size-previous.stat().st_size,artifact_role='case_batch_candidate_with_retained_host_failure',
    full_build_exit_success=False,junit=tests,targeted=targeted,first_full=first_full,concentrated_build_attempts=2,previous_cases_preserved=911,new_cases=35,new_skips=False,
    retained_host_failure='CppRuntimePreparationTest: Windows filesystem case collision',terminal=terminal,lint=lint,
    native_unchanged=25,runtime_assets_unchanged=48,python_source_tests=75,packaged_python_tests=48,packaged_semantic_tests=22,
    runtime_special_checks_reexecuted=True,real_host_batch_tests=2,real_host_batch_cases=dict(cpp=5,python=6),
    old_apks_preserved=96,previews_viewed=8,android_device_execution=False,device_cpp_signal6_fix_confirmed=False,
    case_batch_ui_implemented=True,search_explorer_and_welcome_followup_pending=True,full_goal_complete=False)
destination=ROOT/result['apk'];assert not destination.exists() or sha(destination)==result['sha256']
if not destination.exists():shutil.copy2(apk,destination)
(PHASE/'test-case-manifest.json').write_text(json.dumps(cases,indent=2)+'\n')
(PHASE/'results.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
