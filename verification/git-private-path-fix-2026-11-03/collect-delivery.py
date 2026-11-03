from collections import Counter
import base64
import hashlib
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent;PREVIOUS=ROOT/'verification/git-remote-delivery-2026-11-03'
def sha(file):
    with file.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def log(file):
    raw=file.read_bytes();return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
for name,digest in json.loads((OUT/'build-inputs.json').read_text()).items():assert sha(ROOT/name)==digest,name
tests=json.loads((OUT/'test-results.json').read_text());assert tests['tests']==1149 and not tests['new_failed']
known='com.codepocket.ide.CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs'
assert tests['failures']==[dict(id=known,status='failure')]
assert tests['counts']==dict(passed=1147,failure=1,skipped=1)
assert all(not severity.get('Error',0) and not severity.get('Fatal',0) for severity in tests['lint'].values())
assert int(log(OUT/'full-build-exit.txt').strip())==1
text=log(OUT/'full-build.log');assert 'BUILD FAILED' in text and len(re.findall(r'(?m)^> Task :app:packageDebug$',text))==1
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk';digest=sha(apk)
assert apk.stat().st_mtime>=(OUT/'full-build.log').stat().st_birthtime
for name in ('cpp-apk-inspection.json','web-apk-inspection.json'):
    result=json.loads((OUT/name).read_text());assert result['apk_sha256']==digest,name
web=json.loads((OUT/'web-apk-inspection.json').read_text());assert web['baseline_sha256']=='c63e8df1d0f6b89df2f972fe2a58117c43a8fd7b0da982eb30c1e68571492c1c'
assert len(web['original_natives_byte_identical'])==25 and web['unchanged_runtime_source_hashes']
assert not web['android_chromium_execution'] and not web['device_execution']
assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in log(OUT/'certificates.txt').lower()
assert log(OUT/'permissions.txt')==log(PREVIOUS/'permissions.txt')
normalize=lambda value:re.sub(r' \(line=\d+\)','',value)
manifest=log(OUT/'manifest-tree.txt')
factory=[line for line in manifest.splitlines(keepends=True) if 'A: android:appComponentFactory(' in line]
assert len(factory)==1 and '="androidx.core.app.CoreComponentFactory" (Raw: "androidx.core.app.CoreComponentFactory")' in factory[0]
dependencies=json.loads((OUT/'actual-dependencies.json').read_text())
core=[item for item in dependencies['debugRuntimeClasspath'] if item['coordinate']=='androidx.core:core:1.1.0']
assert len(core)==1 and core[0]['sha256']=='76c7cfbe596fe3c09a6983bf1c89e889299c08ac9a3b52ce5182a088d056647e'
assert sha(Path(core[0]['file']))==core[0]['sha256']
with zipfile.ZipFile(core[0]['file']) as component:
    assert b'android:appComponentFactory="androidx.core.app.CoreComponentFactory"' in component.read('AndroidManifest.xml')
def named_bindings(manifest,resources):
    for attribute,expected in [('theme','style/Theme.CodePocket'),('icon','drawable/ic_app'),('roundIcon','drawable/ic_app')]:
        pattern=r'(A: android:'+attribute+r'\(0x[0-9a-f]+\)=)@(0x[0-9a-f]+)'
        found=list(re.finditer(pattern,manifest));assert len(found)==1
        identity=found[0].group(2)
        bindings=re.findall(r'(?m)^\s+spec resource '+identity+r' com[.]codepocket[.]ide:([^:]+):',resources)
        assert bindings==[expected],(attribute,identity,bindings)
        manifest=re.sub(pattern,lambda m:m.group(1)+'@com.codepocket.ide:'+expected,manifest,count=1)
    return manifest
assert normalize(named_bindings(manifest.replace(factory[0],''),log(OUT/'resources.txt')))==normalize(named_bindings(log(PREVIOUS/'manifest-tree.txt'),log(OUT/'baseline-resources.txt')))
baseline=ROOT/'dist/CodePocket-github-sync-2026-11-03-debug.apk';assert sha(baseline)==web['baseline_sha256']
before=json.loads((OUT/'before-apks.json').read_text());assert len(before)==101
for item in before:
    file=ROOT/'dist'/item['name'];assert file.stat().st_size==item['bytes'] and sha(file)==item['sha256'],file
source_python=json.loads((ROOT/'verification/git-remote-workbench-2026-11-02/python-results.json').read_text());assert source_python['tests']==157
for name,value in source_python['source_hashes'].items():assert sha(ROOT/name)==value,name
vectors=json.loads((ROOT/'verification/web-sidebar-preflight-2026-11-03/vectors.json').read_text());assert vectors['tests']==vectors['passed']==5
assert set(vectors['identities'])<set(source_python['identities'])
for name,value in vectors['source_hashes'].items():assert sha(ROOT/name)==value,name
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
previews=ROOT/'verification/web-sidebar-integration-2026-11-03/previews'
images={name:sha(previews/name) for name in ('tablet-dark-left-browser.png','tablet-light-native-markdown.png','phone-large-bing-focus.png')}
destination=ROOT/'dist/CodePocket-git-private-path-fix-2026-11-03-debug.apk';assert not destination.exists() or sha(destination)==digest
if not destination.exists():shutil.copyfile(apk,destination)
result=dict(apk=destination.relative_to(ROOT).as_posix(),bytes=apk.stat().st_size,sha256=digest,full_build_exit=1,actual_package_executions=1,
    tests=tests,related_targeted=len(json.loads((ROOT/'verification/web-sidebar-preflight-2026-11-03/related-results.json').read_text())['identities']),source_python_reused=152,packaged_python_reused=48,packaged_semantics_reused=22,packaged_git_and_formatting_reused=82,vector_tests_rerun=5,
    terminal_tests=146,terminal_result='valid UP-TO-DATE',editor_assist='NO-SOURCE',sora_lint_report='unchanged valid UP-TO-DATE',
    native_libraries_byte_identical=25,original_runtime_assets_byte_identical=len(web['original_runtime_assets_byte_identical']),old_apks_preserved=101,
    host_shell_tests_reused=4,host_snippets_reused=14,previews=images,android_device_execution=False,device_keystore_execution=False,
    github_account_authorization_tested=False,full_goal_complete=False,git_device_bug_tests=11,git_related_tests=124,batch_actual_package_executions=2,remaining=['P3-C settings and themes','P3-D more language analysis'])
(OUT/'results.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
