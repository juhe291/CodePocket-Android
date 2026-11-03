"""Audit this exact signal-6 build, retaining the real Windows-only failing test."""
import collections,hashlib,json,re,shutil
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile
ROOT=Path(__file__).resolve().parents[2];PHASE=Path(__file__).resolve().parent
PREVIOUS=ROOT/'verification/python-project-run-2026-10-28'
def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def log(path):
    raw=path.read_bytes();return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
def junit(folder):
    rows=[]
    for file in folder.glob('TEST-*.xml'):
        for case in ET.parse(file).getroot().findall('testcase'):
            rows.append(dict(id=case.get('classname')+'.'+case.get('name'),status=next((tag for tag in ('failure','error','skipped') if case.find(tag) is not None),'passed')))
    assert rows and len(rows)==len({r['id'] for r in rows})
    counts=collections.Counter(r['status'] for r in rows)
    return sorted(rows,key=lambda r:r['id']),dict(tests=len(rows),passed=counts['passed'],failures=counts['failure'],errors=counts['error'],skipped=counts['skipped'])
cases,tests=junit(PHASE/'final-junit');_,targeted=junit(PHASE/'targeted-junit');_,baseline=junit(PHASE/'abort-baseline-junit')
assert tests==dict(tests=911,passed=909,failures=1,errors=0,skipped=1),tests
assert targeted==dict(tests=46,passed=46,failures=0,errors=0,skipped=0)
assert baseline==dict(tests=1,passed=0,failures=1,errors=0,skipped=0)
old=json.loads((PREVIOUS/'test-case-manifest.json').read_text());assert len(old)==896
current={r['id']:r['status'] for r in cases}
assert all(current.get(r['id'])==r['status'] for r in old)
assert {r['id'] for r in cases if r['status']=='skipped'}=={r['id'] for r in old if r['status']=='skipped'}
assert {r['id'] for r in cases if r['status']=='failure'}=={'com.codepocket.ide.CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs'}
source=json.loads((PHASE/'final-source-hashes.json').read_text())
for name,digest in source.items():assert sha(ROOT/name)==digest,name
build=log(PHASE/'full-build.log')
assert 'BUILD FAILED' in build and '> Task :app:testDebugUnitTest FAILED' in build
assert '> Task :app:packageDebug\n' in build and '> Task :app:assembleDebug' in build and '> Task :app:buildNdkBuildDebug[arm64-v8a]' in build
lint={}
for module in ('app','editor-sora'):
    file=ROOT/module/'build/reports/lint-results-debug.xml'
    severity=collections.Counter(i.get('severity') for i in ET.parse(file).getroot().findall('issue'))
    assert severity['Error']==severity['Fatal']==0;lint[module]=dict(severity);shutil.copy2(file,PHASE/('lint-'+module+'.xml'))
terminal_source=ROOT/'third-party/termux/terminal-emulator/build/test-results/testDebugUnitTest'
_,terminal=junit(terminal_source);assert terminal==dict(tests=146,passed=146,failures=0,errors=0,skipped=0)
folder=PHASE/'final-terminal-junit';folder.mkdir(exist_ok=True)
for file in terminal_source.glob('TEST-*.xml'):shutil.copy2(file,folder/file.name)
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk';previous_apk=ROOT/'dist/CodePocket-python-project-run-candidate-2026-10-28-debug.apk'
assert sha(previous_apk)=='911b923dad60bb750dc9fea5ac28a135cd98603fb5b0ea0303c785c5a0edca7a'
changed_native=[]
with zipfile.ZipFile(apk) as current_apk,zipfile.ZipFile(previous_apk) as previous:
    assert current_apk.testzip() is None
    natives=[name for name in previous.namelist() if name.startswith('lib/') and name.endswith('.so')];assert len(natives)==25
    for name in natives:
        if current_apk.read(name)!=previous.read(name):changed_native.append(name)
    assert set(changed_native)=={'lib/arm64-v8a/libcodepocket_execute.so','lib/arm64-v8a/libcodepocket_cpp_runner.so'}
    assets=[name for name in previous.namelist() if name.startswith(('assets/cpp-','assets/chaquopy/')) and not name.endswith('/')];assert len(assets)==48
    for name in assets:assert current_apk.read(name)==previous.read(name),name
    image=ROOT/'app/build/intermediates/stripped_native_libs/debug/stripDebugDebugSymbols/out/lib/arm64-v8a/libcodepocket_execute.so'
    assert current_apk.read('lib/arm64-v8a/libcodepocket_execute.so')==image.read_bytes()
    assert b'/statm' in image.read_bytes()
    dex=b''.join(current_apk.read(n) for n in current_apk.namelist() if n.endswith('.dex'))
    assert b'memoryExceeded' in dex
assembly=log(PHASE/'execution-spawn-disassembly.log').splitlines();limits=[]
for index,line in enumerate(assembly):
    if re.search(r'\bbl\s+.*<setrlimit@plt>',line):
        context='\n'.join(assembly[max(0,index-4):index])
        matches=list(re.finditer(r'\bmov\s+w0,\s*(wzr|#0x[0-9a-f]+)',context));assert matches,context
        value=matches[-1].group(1);limits.append(0 if value=='wzr' else int(value[1:],16))
assert limits==[0,1,4],limits
memory=json.loads((PHASE/'memory-policy.json').read_text());assert memory['actual_c_parser_executed'] and not memory['android_proc_or_signal_execution']
assert memory['production_header_sha256']==sha(ROOT/'app/src/main/jni/execution_memory.h')
cpp=json.loads((PHASE/'cpp-apk-inspection.json').read_text());python=json.loads((PHASE/'apk-python.json').read_text())
assert cpp['apk_sha256']==python['sha256']==sha(apk)
assert python['bundled_total_tests']==48 and python['test_failures']==python['test_errors']==0
assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in log(PHASE/'certificates.txt').lower()
assert log(PHASE/'permissions.txt')==log(PREVIOUS/'permissions.txt') and log(PHASE/'manifest-tree.txt')==log(PREVIOUS/'manifest-tree.txt')
before=json.loads((PHASE/'before-apks.json').read_text());assert len(before)==95
for item in before:
    file=ROOT/'dist'/item['name'];assert file.stat().st_size==item['bytes'] and sha(file)==item['sha256']
old_sources=json.loads((PREVIOUS/'verified-source-hashes.json').read_text())
python_sources={name for name in old_sources if name.startswith('app/src/main/python/') and '/__pycache__/' not in name}
for name in python_sources:assert source[name]==old_sources[name]
reuse=dict(source_batch='verification/python-project-run-2026-10-28',python_source_tests=75,packaged_semantic_tests=22,
    shell_cases=4,snippet_cases=14,reexecuted_this_batch=False,reason='Python sources, all 48 runtime assets, and shell/snippet implementations are unchanged; packaged Python 48 was rerun.')
(PHASE/'runtime-validation-reuse.json').write_text(json.dumps(reuse,indent=2)+'\n')
result=dict(apk='dist/CodePocket-cpp-signal6-fix-2026-10-28-debug.apk',sha256=sha(apk),bytes=apk.stat().st_size,
    size_delta_bytes=apk.stat().st_size-previous_apk.stat().st_size,artifact_role='signal6_fix_candidate_with_retained_host_failure',
    full_build_exit_success=False,junit=tests,targeted=targeted,abort_baseline=baseline,previous_cases_preserved=896,new_cases=15,new_skips=False,
    retained_host_failure='CppRuntimePreparationTest: Windows filesystem case collision',terminal=terminal,lint=lint,
    changed_native=changed_native,runtime_assets_unchanged=48,other_native_unchanged=23,packaged_python_tests=48,
    spawned_binary_setrlimit_resources=limits,pre_exec_rlimit_as_present=False,production_rss_parser_host_verified=True,
    old_apks_preserved=95,previews_viewed=1,android_device_execution=False,device_root_cause_confirmed=False,
    case_batch_ui_complete=False,full_goal_complete=False)
destination=ROOT/result['apk'];assert not destination.exists() or sha(destination)==result['sha256']
if not destination.exists():shutil.copy2(apk,destination)
(PHASE/'test-case-manifest.json').write_text(json.dumps(cases,indent=2)+'\n');(PHASE/'results.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
