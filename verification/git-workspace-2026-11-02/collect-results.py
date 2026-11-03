"""Require current test/artifact evidence, preserve old identities and copy one local Git candidate."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2];PHASE=Path(__file__).resolve().parent
PREVIOUS=ROOT/'verification/formatting-2026-10-30'
KNOWN='com.codepocket.ide.CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs'
def sha(file):
    with file.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def log(file):
    raw=file.read_bytes();return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
def junit(folder):
    cases=[]
    for file in sorted(folder.glob('TEST-*.xml')):
        for case in ET.parse(file).getroot().findall('testcase'):
            cases.append(dict(id=case.get('classname')+'.'+case.get('name'),status=next((tag for tag in ('failure','error','skipped') if case.find(tag)is not None),'passed')))
    assert cases and len(cases)==len({c['id'] for c in cases})
    count=Counter(c['status'] for c in cases)
    return cases,dict(tests=len(cases),passed=count['passed'],failures=count['failure'],errors=count['error'],skipped=count['skipped'])

cases,counts=junit(PHASE/'final-junit');old=json.loads((PREVIOUS/'test-case-manifest.json').read_text())
assert len(old)==995 and counts['tests']==1033 and counts['errors']==0,counts
current={c['id']:c['status'] for c in cases};assert all(c['id'] in current for c in old)
assert {c['id'] for c in cases if c['status']=='skipped'}=={c['id'] for c in old if c['status']=='skipped'}
assert {c['id'] for c in cases if c['status']=='failure'}<={KNOWN}
assert all(current[c['id']]==c['status'] or c['id']==KNOWN and current[c['id']]=='passed' for c in old)
new=[c for c in cases if c['id'] not in {v['id'] for v in old}];assert len(new)==38 and all(c['status']=='passed' for c in new)
_,targeted=junit(PHASE/'ui-integration-current-junit');assert targeted==dict(tests=52,passed=52,failures=0,errors=0,skipped=0)
_,layout_targeted=junit(PHASE/'layout-gate-targeted-junit');assert layout_targeted==dict(tests=85,passed=85,failures=0,errors=0,skipped=0)
first,first_counts=junit(PHASE/'full-first-junit');assert first_counts==dict(tests=1032,passed=1029,failures=2,errors=0,skipped=1)
assert {c['id'] for c in first if c['status']=='failure'}=={KNOWN,'com.codepocket.ide.CompletionInteractionTest.fontZoomDoesNotDestroyTheNativeUndoHistory'}
assert current['com.codepocket.ide.CompletionInteractionTest.fontZoomDoesNotDestroyTheNativeUndoHistory']=='passed'
for name,digest in json.loads((PHASE/'final-source-hashes.json').read_text()).items():assert sha(ROOT/name)==digest,name
build=log(PHASE/'full-build.log');build_exit=int(log(PHASE/'full-build-exit.txt').strip())
assert '> Task :app:assembleDebug' in build
package_count=sum(len(re.findall(r'(?m)^> Task :app:packageDebug$',log(PHASE/name))) for name in ('full-first.log','full-build.log'));assert package_count==2
assert ('BUILD FAILED' in build and build_exit==1) if counts['failures'] else ('BUILD SUCCESSFUL' in build and build_exit==0)
lint={}
for module in ('app','editor-sora','workspace-core'):
    source=ROOT/module/'build/reports/lint-results-debug.xml'
    severity=Counter(i.get('severity') for i in ET.parse(source).getroot().findall('issue'))
    assert severity['Error']==severity['Fatal']==0,(module,severity)
    lint[module]=dict(severity);shutil.copy2(source,PHASE/('lint-'+module+'.xml'))
terminal_source=ROOT/'third-party/termux/terminal-emulator/build/test-results/testDebugUnitTest'
_,terminal=junit(terminal_source);assert terminal==dict(tests=146,passed=146,failures=0,errors=0,skipped=0)
target=PHASE/'final-terminal-junit';target.mkdir(exist_ok=True)
for source in terminal_source.glob('TEST-*.xml'):shutil.copy2(source,target/source.name)
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk';previous_apk=ROOT/'dist/CodePocket-formatting-2026-10-30-debug.apk'
assert sha(previous_apk)=='59e13ca581f0806ba25df937772228e3802c5ee1896aebb091cf80e174756668'
python=json.loads((PHASE/'apk-python.json').read_text());cpp=json.loads((PHASE/'cpp-apk-inspection.json').read_text())
semantics=json.loads((PHASE/'semantic-apk-inspection.json').read_text());git=json.loads((PHASE/'git-apk-inspection.json').read_text())
assert python['sha256']==cpp['apk_sha256']==semantics['apk_sha256']==git['apk_sha256']==sha(apk)
assert python['bundled_total_tests']==48 and python['test_failures']==python['test_errors']==0
assert semantics['tests']==22 and semantics['failures']==semantics['errors']==0
assert git['tests']==19 and git['failures']==git['errors']==0 and git['actual_apk_bytecode_loaded']
assert git['formatting_tests']==20 and git['formatting_failures']==git['formatting_errors']==0
assert len(git['original_native_unchanged'])==25 and git['original_python_module_payloads_unchanged'] and git['pinned_wheel_sources_verified']
with zipfile.ZipFile(apk) as archive,zipfile.ZipFile(previous_apk) as previous:
    assert archive.testzip() is None
    runtime=[n for n in previous.namelist() if n.startswith(('assets/cpp-','assets/chaquopy/')) and not n.endswith('/')]
    assert len(runtime)==48
    exceptions={'assets/chaquopy/app.imy','assets/chaquopy/requirements-common.imy','assets/chaquopy/build.json'}
    for name in runtime:
        if name not in exceptions:assert archive.read(name)==previous.read(name),name
    dex=b''.join(archive.read(n) for n in archive.namelist() if n.endswith('.dex'))
    for name in (b'GitOperationService',b'GitUiController',b'SourceControlPanel',b'CodeFormattingService',b'WorkbenchWelcome',b'CaseRunSession'):
        assert name in dex,name
assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in log(PHASE/'certificates.txt').lower()
assert log(PHASE/'permissions.txt')==log(PREVIOUS/'permissions.txt')
manifest=log(PHASE/'manifest-tree.txt')
blocks=re.findall(r'(?m)^      E: service[^\n]*\n(?:        A:[^\n]*\n)+',manifest)
services=[block for block in blocks if 'com.codepocket.ide.GitOperationService' in block];assert len(services)==1
assert 'android:exported(0x01010010)=(type 0x12)0x0' in services[0] and '":git"' in services[0]
normalize=lambda text:re.sub(r' \(line=\d+\)','',text)
assert normalize(manifest.replace(services[0],''))==normalize(log(PREVIOUS/'manifest-tree.txt'))
before=json.loads((PHASE/'before-apks.json').read_text());assert len(before)==99
for item in before:
    file=ROOT/'dist'/item['name'];assert file.stat().st_size==item['bytes'] and sha(file)==item['sha256'],file
assert 'Ran 114 tests' in log(PHASE/'python-source-all.log') and log(PHASE/'python-source-all.log').rstrip().endswith('OK')
for name,digest in json.loads((PHASE/'python-114-source-hashes.json').read_text()).items():assert sha(ROOT/name)==digest,name
shell=json.loads((PREVIOUS/'shell-startup.json').read_text());assert len(shell['cases'])==4 and all(c['passed'] for c in shell['cases'])
snippets=json.loads((PREVIOUS/'snippets.json').read_text());assert snippets['failures']==0 and snippets['python_passed']==snippets['cpp_passed']==7
previews=['phone-dark-staged.png','phone-dark-commit.png','tablet-light-untracked-diff.png','phone-dark-no-project.png','phone-light-large-commit.png']
assert all((PHASE/'previews'/name).is_file() for name in previews)
result=dict(apk='dist/CodePocket-local-git-2026-11-02-debug.apk',bytes=apk.stat().st_size,sha256=sha(apk),
    size_delta_bytes=apk.stat().st_size-previous_apk.stat().st_size,full_build_exit=build_exit,junit=counts,
    previous_test_identities_preserved=995,new_tests=38,new_skips=False,targeted=targeted,layout_targeted=layout_targeted,terminal=terminal,lint=lint,
    source_python_tests=114,packaged_python_tests=48,packaged_semantics_tests=22,packaged_git_tests=19,packaged_formatting_tests=20,
    host_shell_tests_reused=4,host_snippets_reused=14,native_libraries_byte_identical=25,original_runtime_assets_byte_identical=45,
    approved_runtime_asset_updates=sorted(exceptions),old_apks_preserved=99,actual_package_executions=package_count,
    first_full=first_counts,first_apk_delivered=False,layout_history_regression_fixed=True,
    previews=previews,android_device_execution=False,full_goal_complete=False,remaining_plan=['P3-B GitHub','P3-C settings and themes','P3-D more language analysis'],
    retained_failure=KNOWN if counts['failures'] else None)
destination=ROOT/result['apk'];assert not destination.exists() or sha(destination)==result['sha256']
if not destination.exists():shutil.copy2(apk,destination)
(PHASE/'test-case-manifest.json').write_text(json.dumps(cases,indent=2)+'\n')
(PHASE/'results.json').write_text(json.dumps(result,indent=2)+'\n')
(PHASE/'previews/manifest.json').write_text(json.dumps({n:sha(PHASE/'previews'/n) for n in previews},indent=2)+'\n')
print(json.dumps(result,indent=2))
