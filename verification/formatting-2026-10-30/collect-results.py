"""Validate actual concentrated results and artifact checks, then copy exactly one formatting candidate."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
PREVIOUS=ROOT/'verification/workbench-behavior-2026-10-28'
KNOWN='com.codepocket.ide.CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs'

def sha(file):
    with file.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def log(file):
    raw=file.read_bytes()
    return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
def junit(folder):
    cases=[]
    for file in sorted(folder.glob('TEST-*.xml')):
        for case in ET.parse(file).getroot().findall('testcase'):
            cases.append(dict(id=case.get('classname')+'.'+case.get('name'),status=next((tag for tag in ('failure','error','skipped') if case.find(tag) is not None),'passed')))
    assert cases and len(cases)==len({case['id'] for case in cases})
    counts=Counter(c['status'] for c in cases)
    return cases,dict(tests=len(cases),passed=counts['passed'],failures=counts['failure'],errors=counts['error'],skipped=counts['skipped'])

cases,counts=junit(PHASE/'final-junit');assert counts['tests']==995 and counts['errors']==0,counts
old=json.loads((PREVIOUS/'test-case-manifest.json').read_text());assert len(old)==968
current={case['id']:case['status'] for case in cases}
assert all(case['id'] in current for case in old),'An original test was lost'
assert {c['id'] for c in cases if c['status']=='skipped'}=={c['id'] for c in old if c['status']=='skipped'}
assert {c['id'] for c in cases if c['status']=='failure'}<={KNOWN}
assert all(current[c['id']]==c['status'] or c['id']==KNOWN and current[c['id']]=='passed' for c in old)
new=[c for c in cases if c['id'] not in {v['id'] for v in old}];assert len(new)==27 and all(c['status']=='passed' for c in new)
baseline=json.loads((PHASE/'entry-baseline-identities.json').read_text())['cases']
assert len(baseline)==2 and all(current[c['id']]=='passed' for c in baseline)
_,targeted=junit(PHASE/'transactions-complete-junit');assert targeted==dict(tests=37,passed=37,failures=0,errors=0,skipped=0)
second_cases,second_counts=junit(PHASE/'full-second-junit')
assert second_counts==dict(tests=995,passed=991,failures=3,errors=0,skipped=1)
version_tests={'com.codepocket.ide.CompletionTest.legacyExplicitModeMigratesButOffAndOtherSettingsArePreserved',
    'com.codepocket.ide.SharedAssistContractTest.appPersistenceRemainsVersionNineAndKeepsNonEditorPreferences'}
assert {c['id'] for c in second_cases if c['status']=='failure'}==version_tests|{KNOWN}
assert all(current[name]=='passed' for name in version_tests)
for name,digest in json.loads((PHASE/'final-source-hashes.json').read_text()).items():assert sha(ROOT/name)==digest,name
earlier=json.loads((PHASE/'full-second-source-hashes.json').read_text())
version_files={'app/src/test/java/com/codepocket/ide/CompletionTest.kt','app/src/test/java/com/codepocket/ide/SharedAssistContractTest.kt'}
portability_file='app/src/test/java/com/codepocket/ide/CodeFormattingTest.kt'
before_portability=PHASE/'CodeFormattingTest-before-portability.kt'
assert sha(before_portability)==earlier[portability_file]
assert before_portability.read_bytes().split(b'class CodeFormattingTest {',1)[1]==(ROOT/portability_file).read_bytes().split(b'class CodeFormattingTest {',1)[1]
verifier_file='scripts/verify_formatting_apk.py'
before_loader=PHASE/'verify_formatting_apk-before-loader-fix.py'
assert sha(before_loader)==earlier[verifier_file]
loader_fix=b'        # autopep8 reloads pycodestyle during import. Its actual APK .pyc must\n        # be discoverable by the import machinery as well as by our loader.\n        sys.path.insert(0, str(target))\n'
assert (ROOT/verifier_file).read_bytes().replace(loader_fix,b'',1)==before_loader.read_bytes()
for name,digest in earlier.items():
    if name not in version_files|{portability_file,verifier_file}:assert sha(ROOT/name)==digest,'Host fixture or production source changed: '+name
third_cases,third_counts=junit(PHASE/'full-third-junit')
assert third_counts==dict(tests=995,passed=993,failures=1,errors=0,skipped=1)
assert {c['id'] for c in third_cases if c['status']=='failure'}=={KNOWN}
build=log(PHASE/'full-build.log');build_exit=int(log(PHASE/'full-build-exit.txt').strip())
assert '> Task :app:assembleDebug' in build and '> Task :app:packageDebug' in build
assert ('BUILD FAILED' in build and build_exit==1) if counts['failures'] else ('BUILD SUCCESSFUL' in build and build_exit==0)
lint={}
for module in ('app','editor-sora','workspace-core'):
    source=ROOT/module/'build/reports/lint-results-debug.xml'
    severity=Counter(i.get('severity') for i in ET.parse(source).getroot().findall('issue'))
    assert severity['Error']==severity['Fatal']==0,(module,severity)
    lint[module]=dict(severity);shutil.copy2(source,PHASE/('lint-'+module+'.xml'))
terminal_source=ROOT/'third-party/termux/terminal-emulator/build/test-results/testDebugUnitTest'
_,terminal=junit(terminal_source);assert terminal==dict(tests=146,passed=146,failures=0,errors=0,skipped=0)
terminal_target=PHASE/'final-terminal-junit';terminal_target.mkdir(exist_ok=True)
for source in terminal_source.glob('TEST-*.xml'):shutil.copy2(source,terminal_target/source.name)
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
previous_apk=ROOT/'dist/CodePocket-workbench-behavior-2026-10-30-debug.apk'
assert sha(previous_apk)=='9bb61d2451b5ce80ca3b0ccd0b49d3f43c143e4e268b9bc683fa26ab3c44d499'
first_status=json.loads((PHASE/'full-first-status.json').read_text())
assert first_status['apk_sha256']==sha(apk) and not first_status['app_tests_executed']
package_executions=sum(len(re.findall(r'(?m)^> Task :app:packageDebug$',log(PHASE/name))) for name in ('full-first.log','full-second.log','full-third.log','full-build.log'))
assert package_executions==1
python=json.loads((PHASE/'apk-python.json').read_text());cpp=json.loads((PHASE/'cpp-apk-inspection.json').read_text())
semantics=json.loads((PHASE/'semantic-apk-inspection.json').read_text());formatting=json.loads((PHASE/'formatting-apk-inspection.json').read_text())
assert python['sha256']==cpp['apk_sha256']==semantics['apk_sha256']==formatting['apk_sha256']==sha(apk)
assert python['bundled_total_tests']==48 and python['test_failures']==python['test_errors']==0
assert semantics['tests']==22 and semantics['failures']==semantics['errors']==0
assert formatting['tests']==20 and formatting['failures']==formatting['errors']==0 and formatting['actual_apk_bytecode_loaded']
assert len(formatting['original_native_unchanged'])==25
assert formatting['original_python_module_payloads_unchanged'] and formatting['pinned_wheel_sources_verified']
with zipfile.ZipFile(apk) as archive,zipfile.ZipFile(previous_apk) as previous:
    assert archive.testzip() is None
    runtime=[n for n in previous.namelist() if n.startswith(('assets/cpp-','assets/chaquopy/')) and not n.endswith('/')]
    assert len(runtime)==48
    exceptions={'assets/chaquopy/app.imy','assets/chaquopy/requirements-common.imy','assets/chaquopy/build.json'}
    for name in runtime:
        if name not in exceptions:assert archive.read(name)==previous.read(name),name
    dex=b''.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
    for name in (b'WorkbenchWelcome',b'CodeFormattingService',b'formatOnSave',b'FORMAT_DOCUMENT',b'CaseRunSession',b'SyntaxThemePreview'):assert name in dex,name
assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in log(PHASE/'certificates.txt').lower()
assert log(PHASE/'permissions.txt')==log(PREVIOUS/'permissions.txt')
manifest=log(PHASE/'manifest-tree.txt')
blocks=re.findall(r'(?m)^      E: service[^\n]*\n(?:        A:[^\n]*\n)+',manifest)
format_services=[block for block in blocks if 'com.codepocket.ide.CodeFormattingService' in block]
assert len(format_services)==1
assert 'android:exported(0x01010010)=(type 0x12)0x0' in format_services[0] and '":formatting"' in format_services[0]
normalize=lambda text:re.sub(r' \(line=\d+\)','',text)
assert normalize(manifest.replace(format_services[0],''))==normalize(log(PREVIOUS/'manifest-tree.txt'))
before=json.loads((PHASE/'before-apks.json').read_text());assert len(before)==98
for item in before:
    file=ROOT/'dist'/item['name'];assert file.stat().st_size==item['bytes'] and sha(file)==item['sha256'],file
assert 'Ran 95 tests' in log(PHASE/'python-source-all.log') and log(PHASE/'python-source-all.log').rstrip().endswith('OK')
assert 'Ran 4 tests' in log(PHASE/'build-asset-delta-verified.log') and log(PHASE/'build-asset-delta-verified.log').rstrip().endswith('OK')
shell=json.loads((PHASE/'shell-startup.json').read_text());assert len(shell['cases'])==4 and all(c['passed'] for c in shell['cases'])
snippets=json.loads((PHASE/'snippets.json').read_text());assert snippets['failures']==0 and snippets['python_passed']==snippets['cpp_passed']==7
previews=['phone-format-edit-menu.png','phone-large-format-setting.png','tablet-light-format-setting.png']
assert all((PHASE/'previews'/name).is_file() for name in previews)
result=dict(apk='dist/CodePocket-formatting-2026-10-30-debug.apk',bytes=apk.stat().st_size,sha256=sha(apk),
    size_delta_bytes=apk.stat().st_size-previous_apk.stat().st_size,full_build_exit=build_exit,junit=counts,
    previous_test_identities_preserved=968,new_tests=27,new_skips=False,entry_baseline_fixed=2,targeted=targeted,terminal=terminal,lint=lint,
    source_python_tests=95,packaged_python_tests=48,packaged_semantics_tests=22,packaged_formatting_tests=20,
    host_shell_tests=4,host_snippets=14,native_libraries_byte_identical=25,original_runtime_assets_byte_identical=45,
    approved_runtime_asset_updates=sorted(exceptions),old_apks_preserved=98,concentrated_build_attempts=4,actual_package_executions=package_executions,
    first_full_app_tests_executed=False,second_full=second_counts,
    previews=previews,android_device_execution=False,full_goal_complete=False,remaining_plan=['original P3'],
    retained_failure=KNOWN if counts['failures'] else None)
destination=ROOT/result['apk'];assert not destination.exists() or sha(destination)==result['sha256']
if not destination.exists():shutil.copy2(apk,destination)
(PHASE/'test-case-manifest.json').write_text(json.dumps(cases,indent=2)+'\n')
(PHASE/'results.json').write_text(json.dumps(result,indent=2)+'\n')
(PHASE/'previews/manifest.json').write_text(json.dumps({n:sha(PHASE/'previews'/n) for n in previews},indent=2)+'\n')
print(json.dumps(result,indent=2))
