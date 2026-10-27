"""Verify full identity preservation and the actual APK before writing a final distribution copy."""
import collections
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
PREVIOUS=ROOT/'verification/cpp-local-integration-2026-10-27'
def sha(file):
    with file.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def read_log(file):
    data=file.read_bytes()
    return data.decode('utf-16' if data.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig').replace('\r\n','\n')
def junit(folder):
    cases=[]
    for file in sorted(folder.glob('TEST-*.xml')):
        for case in ET.parse(file).getroot().findall('testcase'):
            cases.append(dict(id=case.get('classname')+'.'+case.get('name'),status=next((tag for tag in ('failure','error','skipped') if case.find(tag) is not None),'passed')))
    assert cases and len(cases)==len({case['id'] for case in cases})
    counts=collections.Counter(case['status'] for case in cases)
    return cases,dict(tests=len(cases),failures=counts['failure'],errors=counts['error'],skipped=counts['skipped'])

cases,tests=junit(PHASE/'final-junit');_,targeted=junit(PHASE/'targeted-final-junit')
_,terminal=junit(PHASE/'final-terminal-junit')
assert tests==dict(tests=869,failures=0,errors=0,skipped=1),tests
assert targeted==dict(tests=90,failures=0,errors=0,skipped=0),targeted
assert terminal==dict(tests=146,failures=0,errors=0,skipped=0),terminal
old=json.loads((PREVIOUS/'test-case-manifest.json').read_text());assert len(old)==855
assert {c['id'] for c in old}<={c['id'] for c in cases}
assert {c['id'] for c in old if c['status']=='skipped'}=={c['id'] for c in cases if c['status']=='skipped'}
assert 'BUILD SUCCESSFUL' in read_log(PHASE/'full-build.log') and '> Task :app:packageDebug\n' in read_log(PHASE/'full-build.log')
assert '> Task :editor-assist:test NO-SOURCE' in read_log(PHASE/'full-build.log')
verified_hashes=json.loads((PHASE/'final-source-hashes.json').read_text())
_,host_path=junit(PHASE/'host-path-pass-junit')
assert host_path==dict(tests=3,failures=0,errors=0,skipped=0),host_path
# Only test tool selection changed after the full build. No production input may differ.
later=json.loads((PHASE/'host-path-pass-source-hashes.json').read_text())
portable_tests={'app/src/test/java/com/codepocket/ide/CppHeaderEngineTest.kt','app/src/test/java/com/codepocket/ide/CppLspEngineTest.kt'}
assert {name for name,digest in verified_hashes.items() if later[name]!=digest}==portable_tests
for name in portable_tests:verified_hashes[name]=later[name]
for name,digest in verified_hashes.items():assert sha(ROOT/name)==digest,name
(PHASE/'verified-source-hashes.json').write_text(json.dumps(verified_hashes,indent=2)+'\n')
lint={}
for module in ('app','editor-sora'):
    issues=ET.parse(ROOT/module/'build/reports/lint-results-debug.xml').getroot().findall('issue')
    counts=collections.Counter(item.get('severity') for item in issues);assert counts['Error']==counts['Fatal']==0
    lint[module]=dict(counts);(PHASE/('lint-'+module+'.xml')).write_bytes((ROOT/module/'build/reports/lint-results-debug.xml').read_bytes())
before=json.loads((PHASE/'before-apks.json').read_text());assert len(before)==91
for item in before:
    file=ROOT/'dist'/item['name'];assert file.stat().st_size==item['bytes'] and sha(file)==item['sha256'],item['name']
apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk';baseline=ROOT/'dist/CodePocket-local-cpp-2026-10-27-debug.apk'
assert sha(baseline)=='07b06aa03f393d35e8bb88ebd6f7a0c51f5ad2749aa6e98090f2c54dedd2bd26'
with zipfile.ZipFile(apk) as archive,zipfile.ZipFile(baseline) as previous:
    assert archive.testzip() is None
    native=[name for name in previous.namelist() if name.startswith('lib/') and name.endswith('.so')];assert len(native)==25
    assets=[name for name in previous.namelist() if name.startswith(('assets/cpp-','assets/chaquopy/')) and not name.endswith('/')]
    for name in native+assets:assert archive.read(name)==previous.read(name),name
    dex=b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
    assert b'Lcom/codepocket/ide/CppHeaderCompletion;' in dex and b'DIRECTORY' in dex and b'HEADER' in dex
    assert 'lib/arm64-v8a/libcodepocket_cpp_runner.so' in archive.namelist()
cpp=json.loads((PHASE/'cpp-apk-inspection.json').read_text());assert cpp['apk_sha256']==sha(apk) and not cpp['real_android_execution']
assert len(cpp['native_libraries'])==12 and len(cpp['execution_images'])==2 and cpp['build_support']['entries']==4
assert read_log(PHASE/'permissions.txt')==read_log(PREVIOUS/'permissions.txt')
cert=read_log(PHASE/'certificates.txt').lower();assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in cert
manifest=read_log(PHASE/'manifest-tree.txt');assert 'CppExecutionService' in manifest and ':cpp_run' in manifest and 'CppAnalysisService' in manifest
python=json.loads((PHASE/'apk-inspection.json').read_text());assert python['sha256']==sha(apk) and python['bundled_total_tests']==39 and python['test_failures']==python['test_errors']==0
semantic=json.loads((PHASE/'semantic-apk-inspection.json').read_text());assert semantic['apk_sha256']==sha(apk) and semantic['tests']==22 and semantic['failures']==semantic['errors']==0
assert '66 tests' in read_log(PHASE/'python-source.log')
previews=sorted((PHASE/'previews').glob('*.png'));assert len(previews)==3
result=dict(apk='dist/CodePocket-header-tab-2026-10-27-debug.apk',bytes=apk.stat().st_size,sha256=sha(apk),
    size_delta_bytes=apk.stat().st_size-baseline.stat().st_size,junit=tests,targeted=targeted,terminal=terminal,editor_assist='NO-SOURCE; contracts tested in app',
    old_cases_preserved=855,new_cases=14,no_new_skips=True,lint=lint,old_apks_preserved=91,native_files_unchanged=25,
    runtime_assets_unchanged=len(assets),real_clangd_cases=10,previews_viewed=3,python_source_tests=66,packaged_python_tests=39,
    packaged_semantic_tests=22,host_path_tests=host_path,android_execution=False,full_goal_complete=False)
destination=ROOT/result['apk'];assert not destination.exists() or sha(destination)==result['sha256']
destination.write_bytes(apk.read_bytes())
(PHASE/'test-case-manifest.json').write_text(json.dumps(cases,indent=2)+'\n')
(PHASE/'results.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
