"""Collect this build only after fresh JUnit, strict package delta and actual bytecode checks."""
import base64
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent;OLD=ROOT/'verification/app-updates-2026-11-03'
def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def read(path):
    raw=path.read_bytes();return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
def data(name):return json.loads((OUT/name).read_text())
inputs=data('build-inputs.json')
verification_changes=data('verification-inputs.json')
assert set(verification_changes)=={'scripts/verify_git_status_apk.py'}
for name,digest in inputs.items():
    if name in verification_changes:
        assert verification_changes[name]['before']==digest
        digest=verification_changes[name]['after']
    assert sha(ROOT/name)==digest,name
tests=data('test-results.json');assert tests['tests']==1173 and tests['counts']==dict(passed=1171,failure=1,skipped=1)
assert tests['previous_identities_retained']==1173 and tests['new_tests']==0 and not tests['new_failed']
assert tests['failures']==[dict(id='com.codepocket.ide.CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs',status='failure')]
assert all(not value.get('Error',0) and not value.get('Fatal',0) for value in tests['lint'].values())
log=read(OUT/'full-build.log');assert int(read(OUT/'full-build-exit.txt').strip())==1 and 'BUILD FAILED' in log
assert len(re.findall(r'(?m)^> Task :app:packageDebug$',log))==1
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk';digest=sha(apk)
assert apk.stat().st_mtime>=(OUT/'full-build.log').stat().st_birthtime
assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in read(OUT/'certificates.txt').lower()
assert read(OUT/'permissions.txt')==read(OLD/'permissions.txt')
assert read(OUT/'manifest-tree.txt')==read(OLD/'manifest-tree.txt')
assert "versionCode='3' versionName='0.2.1'" in read(OUT/'badging.txt')
python=data('apk-python.json');assert python['sha256']==digest and python['bundled_total_tests']==48 and python['test_failures']==python['test_errors']==0
semantics=data('semantic-apk-inspection.json');assert semantics['apk_sha256']==digest and semantics['tests']==22 and semantics['failures']==semantics['errors']==0 and semantics['dependency_bytecode_loaded']
git=data('git-apk-inspection.json');assert git['apk_sha256']==digest and git['baseline_sha256']=='4715b84bb9454c5136f823ea07cf2e09d34e3cfc4c21b24a35e688502e44bd91'
assert git['tests']==84 and git['failures']==git['errors']==0 and git['actual_apk_bytecode_loaded'] and git['dex_resources_manifest_licenses_byte_identical']
assert git['other_python_payloads_identical'] and len(git['python_timestamp_headers'])==6
assert len(git['original_native_unchanged'])==25 and len(git['original_python_assets_unchanged'])==18
cpp=data('cpp-apk-inspection.json');assert cpp['apk_sha256']==digest and cpp['git_update']['changed_module']=='git_workspace.pyc'
source=data('python-results.json');assert source['tests']==159 and source['failures']==source['errors']==source['skipped']==0
for name,digest_source in source['source_hashes'].items():assert sha(ROOT/name)==digest_source,name
before=data('before-apks.json');assert len(before)==103
for item in before:
    file=ROOT/'dist'/item['name'];assert file.stat().st_size==item['bytes'] and sha(file)==item['sha256'],file
# Unchanged actual generated shell/snippet bytes justify reusing host executions.
shell_output=ET.parse(OUT/'final-junit/TEST-com.codepocket.ide.terminal.shell.ShellLaunchPlanTest.xml').getroot().findtext('system-out','')
old_shell=ET.parse(OLD/'final-junit/TEST-com.codepocket.ide.terminal.shell.ShellLaunchPlanTest.xml').getroot().findtext('system-out','')
assert re.findall(r'^CODEPOCKET_SHELL_RC=(.+)$',shell_output,re.M)==re.findall(r'^CODEPOCKET_SHELL_RC=(.+)$',old_shell,re.M) and 'CODEPOCKET_SHELL_RC=' in shell_output
snippet_output=ET.parse(OUT/'final-junit/TEST-com.codepocket.ide.SnippetTest.xml').getroot().findtext('system-out','')
old_snippet=ET.parse(OLD/'final-junit/TEST-com.codepocket.ide.SnippetTest.xml').getroot().findtext('system-out','')
assert re.findall(r'^CODEPOCKET_SNIPPET_FIXTURES=(.+)$',snippet_output,re.M)==re.findall(r'^CODEPOCKET_SNIPPET_FIXTURES=(.+)$',old_snippet,re.M) and 'CODEPOCKET_SNIPPET_FIXTURES=' in snippet_output
old_inputs=json.loads((OLD/'build-inputs.json').read_text())
for name in ('editor-assist/src/main/kotlin/com/codepocket/ide/CodeSnippets.kt','editor-assist/src/main/kotlin/com/codepocket/ide/SnippetSession.kt','app/src/test/java/com/codepocket/ide/SnippetTest.kt'):
    assert sha(ROOT/name)==old_inputs[name],name
terminal=[];target=OUT/'final-terminal-junit';target.mkdir(exist_ok=True)
for file in (ROOT/'third-party/termux/terminal-emulator/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    for case in ET.parse(file).getroot().findall('testcase'):terminal.append(next((tag for tag in ('failure','error','skipped') if case.find(tag)is not None),'passed'))
    shutil.copyfile(file,target/file.name)
assert len(terminal)==terminal.count('passed')==146 and '> Task :terminal-emulator:testDebugUnitTest UP-TO-DATE' in log
assert '> Task :editor-assist:test NO-SOURCE' in log
destination=ROOT/'dist/CodePocket-git-directory-status-fix-2026-11-03-debug.apk'
assert not destination.exists() or sha(destination)==digest
if not destination.exists():shutil.copyfile(apk,destination)
result=dict(apk=destination.relative_to(ROOT).as_posix(),bytes=apk.stat().st_size,sha256=digest,version_name='0.2.1',version_code=3,
    full_build_exit=1,actual_package_executions=1,tests=tests,source_python=159,packaged_python=48,packaged_semantics=22,packaged_git_formatting=84,
    native_libraries_byte_identical=25,all_other_package_entries_byte_identical=True,changed_runtime_module='git_workspace.pyc',other_python_timestamp_headers=git['python_timestamp_headers'],old_apks_preserved=103,
    host_shell_tests_reused=4,host_snippets_reused=14,vector_tests_rerun=5,terminal_tests=146,terminal_result='valid UP-TO-DATE',
    preview_reused_from='verification/app-updates-2026-11-03/viewed-previews.json',dex_resources_manifest_byte_identical=True,android_device_execution=False,
    full_goal_complete=False,remaining=['P3-C settings and themes','P3-D additional language analysis'])
(OUT/'results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
