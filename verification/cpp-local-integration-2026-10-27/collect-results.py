"""Batch closure gates. Do not relabel host evidence as Android execution."""
import collections
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
def sha(file):
    with file.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def read_log(file):
    data=file.read_bytes()
    return data.decode('utf-16' if data.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
def junit(directory):
    cases=[]
    for file in sorted(directory.glob('TEST-*.xml')):
        for case in ET.parse(file).getroot().findall('testcase'):
            status=next((tag for tag in ('failure','error','skipped') if case.find(tag) is not None),'passed')
            cases.append(dict(id=case.get('classname')+'.'+case.get('name'),status=status))
    assert cases and len(cases)==len({case['id'] for case in cases})
    counts=collections.Counter(case['status'] for case in cases)
    return cases,dict(tests=len(cases),failures=counts['failure'],errors=counts['error'],skipped=counts['skipped'])

cases,tests=junit(PHASE/'final-junit')
targeted_cases,targeted=junit(PHASE/'compatibility-junit')
_,terminal=junit(PHASE/'final-terminal-junit')
old=json.loads((ROOT/'verification/cpp-semantic-integration-2026-10-27/test-case-manifest.json').read_text())
assert len(old)==821 and tests==dict(tests=855,failures=0,errors=0,skipped=1),tests
assert targeted['failures']==targeted['errors']==targeted['skipped']==0
assert terminal==dict(tests=146,failures=0,errors=0,skipped=0),terminal
assert {case['id'] for case in old}<={case['id'] for case in cases}
assert {case['id'] for case in old if case['status']=='skipped'}=={case['id'] for case in cases if case['status']=='skipped'}
(PHASE/'test-case-manifest.json').write_text(json.dumps(cases,indent=2)+'\n')
log=read_log(PHASE/'full-build.log')
assert 'BUILD SUCCESSFUL' in log and '> Task :app:packageDebug\n' in log
lint={}
for module in ('app','editor-sora'):
    issues=ET.parse(ROOT/module/'build/reports/lint-results-debug.xml').getroot().findall('issue')
    counts=collections.Counter(item.get('severity') for item in issues)
    assert counts['Error']==counts['Fatal']==0
    lint[module]=dict(severity=dict(counts),ids=dict(collections.Counter(item.get('id') for item in issues)))
    (PHASE/('lint-'+module+'.xml')).write_bytes((ROOT/module/'build/reports/lint-results-debug.xml').read_bytes())
before=json.loads((PHASE/'before-apks.json').read_text())
assert len(before)==90
for item in before:
    file=ROOT/'dist'/item['name'];assert file.stat().st_size==item['bytes'] and sha(file)==item['sha256'],item['name']
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
cpp=json.loads((PHASE/'cpp-apk-inspection.json').read_text())
assert sha(apk)==cpp['apk_sha256']
assert len(cpp['native_libraries'])==12 and not cpp['real_android_execution']
assert len(cpp['execution_images'])==2 and cpp['build_support']['entries']==4
assert len(cpp['original_native_unchanged'])==10 and len(cpp['original_python_assets_unchanged'])==20
previous=ROOT/'dist/CodePocket-cpp-semantics-2026-10-27-debug.apk'
with zipfile.ZipFile(apk) as archive, zipfile.ZipFile(previous) as baseline:
    assert len([name for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')])==25
    old_lock=json.loads(baseline.read('assets/cpp-source/cpp-runtime-lock.json'))
    for item in old_lock['libraries']:
        name='lib/arm64-v8a/'+item['packaged_name'];assert archive.read(name)==baseline.read(name)
    dex=b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
    for name in ('CppExecutionService','LocalCppRunClient','CppTerminalRunner','CppFaultLocation','NativeExecutionBridge','CppAnalysisService','CppRuntime'):
        assert ('Lcom/codepocket/ide/'+name+';').encode() in dex,name
manifest=read_log(PHASE/'manifest-tree.txt')
services=manifest.split('E: service ')
execution=next(value.split('E: service ')[0] for value in services if 'CppExecutionService' in value)
assert ':cpp_run' in execution and 'android:exported' in execution and '(type 0x12)0x0' in execution
assert 'CppAnalysisService' in manifest and ':cpp_analysis' in manifest and 'extractNativeLibs' in manifest
cert=read_log(PHASE/'certificates.txt').lower()
assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in cert
assert read_log(PHASE/'permissions.txt')==read_log(ROOT/'verification/cpp-semantic-integration-2026-10-27/permissions.txt')
semantics=json.loads((PHASE/'semantic-apk-inspection.json').read_text())
assert semantics['tests']==22 and semantics['failures']==semantics['errors']==0 and semantics['apk_sha256']==sha(apk)
assert '66 tests' in read_log(PHASE/'python-source.log')
previews=sorted((PHASE/'previews').glob('*.png'));assert len(previews)==7
result=dict(apk='dist/CodePocket-local-cpp-2026-10-27-debug.apk',bytes=apk.stat().st_size,sha256=sha(apk),
    apk_size_delta_bytes=apk.stat().st_size-previous.stat().st_size,junit=tests,targeted=targeted,terminal=terminal,
    old_cases_preserved=821,new_cases=34,no_new_skips=True,lint=lint,
    python_source_tests=66,actual_apk_semantic_tests=22,cpp_native_files=12,execution_images=2,
    cpp_native_bytes=cpp['native_bytes'],build_support=cpp['build_support'],
    old_cpp_native_unchanged=9,original_native_unchanged=10,chaquopy_assets_unchanged=20,old_apks_preserved=90,
    previews_viewed=7,android_execution=False,full_goal_complete=False)
destination=ROOT/result['apk'];assert not destination.exists() or sha(destination)==result['sha256']
destination.write_bytes(apk.read_bytes())
(PHASE/'results.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
