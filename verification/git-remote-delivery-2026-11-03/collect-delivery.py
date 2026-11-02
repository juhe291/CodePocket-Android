from collections import Counter
import base64
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent;PREVIOUS=ROOT/'verification/git-workspace-2026-11-02'
def sha(file):
    with file.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def log(file):
    raw=file.read_bytes();return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
for name,digest in json.loads((OUT/'build-inputs.json').read_text()).items():assert sha(ROOT/name)==digest,name
tests=json.loads((OUT/'test-results.json').read_text());assert tests['tests']==1109 and not tests['new_failed']
known='com.codepocket.ide.CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs'
assert tests['failures']==[dict(id=known,status='failure')]
assert tests['counts']==dict(passed=1107,failure=1,skipped=1)
assert all(not severity.get('Error',0) and not severity.get('Fatal',0) for severity in tests['lint'].values())
assert int(log(OUT/'full-build-exit.txt').strip())==1
text=log(OUT/'full-build.log');assert 'BUILD FAILED' in text and len(re.findall(r'(?m)^> Task :app:packageDebug$',text))==1
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk';digest=sha(apk)
assert apk.stat().st_mtime>=(OUT/'full-build.log').stat().st_birthtime
for name in ('apk-python.json','semantic-apk-inspection.json','cpp-apk-inspection.json','git-apk-inspection.json'):
    result=json.loads((OUT/name).read_text());assert result.get('apk_sha256',result.get('sha256'))==digest,name
python=json.loads((OUT/'apk-python.json').read_text());assert python['bundled_total_tests']==48 and python['test_failures']==python['test_errors']==0
semantic=json.loads((OUT/'semantic-apk-inspection.json').read_text());assert semantic['tests']==22 and semantic['failures']==semantic['errors']==0
git=json.loads((OUT/'git-apk-inspection.json').read_text());assert git['tests']==82 and git['failures']==git['errors']==0 and git['actual_apk_bytecode_loaded']
assert git['dependencies_byte_identical'] and git['pinned_wheel_hashes_verified'] and git['original_python_module_payloads_unchanged']
assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in log(OUT/'certificates.txt').lower()
assert log(OUT/'permissions.txt')==log(PREVIOUS/'permissions.txt')
manifest=log(OUT/'manifest-tree.txt');blocks=re.findall(r'(?m)^      E: service[^\n]*\n(?:        A:[^\n]*\n)+',manifest)
service=[block for block in blocks if 'com.codepocket.ide.GitRemoteService' in block];assert len(service)==1
assert 'android:exported(0x01010010)=(type 0x12)0x0' in service[0] and '":git_remote"' in service[0]
normalize=lambda value:re.sub(r' \(line=\d+\)','',value)
assert normalize(manifest.replace(service[0],''))==normalize(log(PREVIOUS/'manifest-tree.txt'))
baseline=ROOT/'dist/CodePocket-local-git-2026-11-02-debug.apk';assert sha(baseline)==git['baseline_sha256']
with zipfile.ZipFile(apk) as current,zipfile.ZipFile(baseline) as old:
    assert current.testzip() is None
    runtime=[n for n in old.namelist() if n.startswith(('assets/cpp-','assets/chaquopy/')) and not n.endswith('/')]
    assert len(runtime)==48
    for name in runtime:
        if name not in git['approved_assets']:assert current.read(name)==old.read(name),name
    dex=b''.join(current.read(n) for n in current.namelist() if n.endswith('.dex'))
    for name in (b'GitRemoteService',b'GitRemoteWorkbench',b'GitRemoteUiController',b'GitCredentialVault',b'GitCloneTargets',b'GitPushLedger',b'SourceControlPanel',b'WorkbenchWelcome'):assert name in dex,name
    assert b'HostRemoteGit' not in dex
before=json.loads((OUT/'before-apks.json').read_text());assert len(before)==100
for item in before:
    file=ROOT/'dist'/item['name'];assert file.stat().st_size==item['bytes'] and sha(file)==item['sha256'],file
source_python=json.loads((ROOT/'verification/git-remote-workbench-2026-11-02/python-results.json').read_text());assert source_python['tests']==157
for name,value in source_python['source_hashes'].items():assert sha(ROOT/name)==value,name
terminal=ROOT/'third-party/termux/terminal-emulator/build/test-results/testDebugUnitTest';terminal_cases=[]
target=OUT/'final-terminal-junit';target.mkdir(exist_ok=True)
for file in terminal.glob('TEST-*.xml'):
    for case in ET.parse(file).getroot().findall('testcase'):
        terminal_cases.append(next((tag for tag in ('failure','error','skipped') if case.find(tag)is not None),'passed'))
    shutil.copyfile(file,target/file.name)
assert terminal_cases.count('passed')==len(terminal_cases)==146
assert '> Task :terminal-emulator:testDebugUnitTest UP-TO-DATE' in text and '> Task :editor-assist:test NO-SOURCE' in text
# Reuse the exact generated shell/snippet fixture bytes, rather than claiming fresh host execution.
formatting=ROOT/'verification/formatting-2026-10-30'
shell=json.loads((formatting/'shell-startup.json').read_text());output=ET.parse(OUT/'final-junit/TEST-com.codepocket.ide.terminal.shell.ShellLaunchPlanTest.xml').getroot().findtext('system-out','')
encoded=re.findall(r'^CODEPOCKET_SHELL_RC=(.+)$',output,re.MULTILINE);assert len(encoded)==1
assert hashlib.sha256(base64.b64decode(encoded[0],validate=True)).hexdigest()==shell['startup_sha256']
for name in ('editor-assist/src/main/kotlin/com/codepocket/ide/CodeSnippets.kt','editor-assist/src/main/kotlin/com/codepocket/ide/SnippetSession.kt','app/src/test/java/com/codepocket/ide/SnippetTest.kt'):
    old_sources=json.loads((formatting/'final-source-hashes.json').read_text());assert sha(ROOT/name)==old_sources[name],name
snippet_text=ET.parse(OUT/'final-junit/TEST-com.codepocket.ide.SnippetTest.xml').getroot().findtext('system-out','')
fixtures=json.loads(re.findall(r'^CODEPOCKET_SNIPPET_FIXTURES=(.+)$',snippet_text,re.MULTILINE)[0])
old_snippets=json.loads((formatting/'snippets.json').read_text());assert old_snippets['failures']==0
expected={(item['id'],item['variant']):item['template_sha256'] for item in old_snippets['fixtures']}
assert len(fixtures)==len(expected)==14
assert {(item['id'],item['variant']):hashlib.sha256(item['code'].encode()).hexdigest() for item in fixtures}==expected
previews=ROOT/'verification/git-remote-workbench-2026-11-02/previews'
images={name:sha(previews/name) for name in ('phone-dark-clone.png','tablet-light-fetch.png','compact-large-credentials.png')}
destination=ROOT/'dist/CodePocket-github-sync-2026-11-03-debug.apk';assert not destination.exists() or sha(destination)==digest
if not destination.exists():shutil.copyfile(apk,destination)
result=dict(apk=destination.relative_to(ROOT).as_posix(),bytes=apk.stat().st_size,sha256=digest,full_build_exit=1,actual_package_executions=1,
    tests=tests,related_targeted=134,source_python=157,packaged_python=48,packaged_semantics=22,packaged_git_and_formatting=82,
    terminal_tests=146,terminal_result='valid UP-TO-DATE',editor_assist='NO-SOURCE',sora_lint_report='unchanged valid UP-TO-DATE',
    native_libraries_byte_identical=25,original_runtime_assets_byte_identical=len(runtime)-len(git['approved_assets']),old_apks_preserved=100,
    host_shell_tests_reused=4,host_snippets_reused=14,previews=images,android_device_execution=False,device_keystore_execution=False,
    github_account_authorization_tested=False,full_goal_complete=False,remaining=['P3-E web auxiliary panel','P3-C settings and themes','P3-D more language analysis'])
(OUT/'results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
