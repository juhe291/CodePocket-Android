"""Collect this local Bug APK, keeping full-suite failure and packaging success distinct."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
OLD=ROOT/'verification/git-directory-status-fix-2026-11-03'
def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def data(name):return json.loads((OUT/name).read_text())
def read(path):
    raw=path.read_bytes();return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
for name,digest in data('build-inputs.json').items():assert sha(ROOT/name)==digest,name
tests=data('test-results.json')
assert tests['tests']==tests['previous_identities_retained']==1173 and tests['new_tests']==0
assert tests['counts']==dict(passed=1171,failure=1,skipped=1) and not tests['new_failed']
assert tests['failures']==[dict(id='com.codepocket.ide.CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs',status='failure')]
assert all(not value.get('Error',0) and not value.get('Fatal',0) for value in tests['lint'].values())
assert int(read(OUT/'full-build-exit.txt'))==1 and 'BUILD FAILED in 7m 54s' in read(OUT/'full-build.log')
assert int(read(OUT/'repackage-exit.txt'))==0 and 'BUILD SUCCESSFUL in 21s' in read(OUT/'repackage.log')
assert not re.findall(r'(?m)^> Task :app:packageDebug$',read(OUT/'full-build.log'))
assert len(re.findall(r'(?m)^> Task :app:packageDebug$',read(OUT/'repackage.log')))==1
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk';digest=sha(apk)
assert apk.stat().st_mtime>=(OUT/'repackage.log').stat().st_birthtime
certificate='d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408'
assert certificate in read(OUT/'certificates.txt').lower()
assert "versionCode='3' versionName='0.2.1'" in read(OUT/'badging.txt')
assert read(OUT/'permissions.txt')==read(OLD/'permissions.txt')
assert read(OUT/'manifest-tree.txt')==read(OLD/'manifest-tree.txt')
python=data('apk-python.json');assert python['sha256']==digest and python['bundled_total_tests']==48 and python['test_failures']==python['test_errors']==0
semantics=data('semantic-apk-inspection.json');assert semantics['apk_sha256']==digest and semantics['tests']==22 and semantics['failures']==semantics['errors']==0
git=data('git-apk-inspection.json');assert git['apk_sha256']==digest and git['tests']==84 and git['failures']==git['errors']==0
cpp=data('cpp-apk-inspection.json');assert cpp['apk_sha256']==digest and cpp['zip_crc'] and len(cpp['native_libraries'])==25
delta=data('package-delta.json');assert delta['apk_sha256']==digest and not delta['changed_entry_payloads']
source=json.loads((ROOT/'verification/cpp-source-download-fix-2026-11-03/python-results.json').read_text())
assert source['tests']==172 and source['failures']==source['errors']==source['skipped']==0
for name,digest_source in source['source_hashes'].items():assert sha(ROOT/name)==digest_source,name
before=data('before-apks.json');assert len(before)==104
for item in before:
    path=ROOT/'dist'/item['name'];assert path.stat().st_size==item['bytes'] and sha(path)==item['sha256'],path
for test,marker in [('terminal.shell.ShellLaunchPlanTest','CODEPOCKET_SHELL_RC'),('SnippetTest','CODEPOCKET_SNIPPET_FIXTURES')]:
    name='TEST-com.codepocket.ide.'+test+'.xml'
    current=ET.parse(OUT/'final-junit'/name).getroot().findtext('system-out','')
    old=ET.parse(OLD/'final-junit'/name).getroot().findtext('system-out','')
    assert marker in current and re.findall(r'^'+marker+r'=(.+)$',current,re.M)==re.findall(r'^'+marker+r'=(.+)$',old,re.M)
terminal=[]
for path in (ROOT/'third-party/termux/terminal-emulator/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    terminal.extend(next((key for key in ('failure','error','skipped') if case.find(key) is not None),'passed') for case in ET.parse(path).getroot().findall('testcase'))
assert len(terminal)==terminal.count('passed')==146 and '> Task :terminal-emulator:testDebugUnitTest UP-TO-DATE' in read(OUT/'full-build.log')
visual=[]
for name in ('verification/git-workspace-2026-11-02/previews/phone-dark-staged.png',
             'verification/workbench-settings-chrome-batch-2026-10-26/previews/tablet-light-editor-groups.png',
             'verification/cpp-header-completion-2026-10-27/previews/phone-light-large-header.png'):
    path=ROOT/name;assert path.stat().st_mtime>=(OUT/'full-build.log').stat().st_birthtime
    visual.append(dict(path=name,sha256=sha(path),host_native_rendering=True,android_device=False))
destination=ROOT/'dist/CodePocket-bugfix-2026-11-03-debug.apk'
assert not destination.exists() or sha(destination)==digest
if not destination.exists():shutil.copyfile(apk,destination)
result=dict(apk=destination.relative_to(ROOT).as_posix(),bytes=destination.stat().st_size,sha256=digest,
    version_name='0.2.1',version_code=3,certificate_sha256=certificate,full_build_exit=1,package_build_exit=0,
    full_build_seconds=474,package_build_seconds=21,actual_package_executions=1,tests=tests,
    source_python=172,packaged_python=48,packaged_semantics=22,packaged_git_formatting=84,
    cpp_libraries=25,old_apks_preserved=len(before),frozen_inputs=len(data('build-inputs.json')),
    package_payload_identical_to_previous=True,previous_apk_sha256=delta['previous_sha256'],
    host_shell_tests_reused=4,host_snippets_reused=14,terminal_tests=146,terminal_result='valid UP-TO-DATE',
    visual_inspection=visual,android_device_execution=False,deferred_features_in_apk=False,
    deferred_owner='DeepSeek',github_push_stopped=True,original_full_plan_completed=False)
(OUT/'results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps({key:value for key,value in result.items() if key not in ('tests','visual_inspection')}))
