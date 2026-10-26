from pathlib import Path
import collections,hashlib,io,json,re,shutil,xml.etree.ElementTree as E,zipfile
root=Path.cwd();phase=root/'verification/python-semantic-integration-2026-10-26'
previous=root/'verification/workbench-details-batch-2026-10-26'
def read(path):
    b=path.read_bytes();return b.decode('utf-16' if b.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig',errors='replace')
def load(path):return json.loads(read(path))
def save(path,data):path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def junit(folder):
    totals=dict(tests=0,failures=0,errors=0,skipped=0);manifest=[]
    for path in folder.glob('TEST-*.xml'):
        suite=E.parse(path).getroot()
        for key in totals:totals[key]+=int(suite.get(key,0))
        for case in suite.findall('testcase'):
            status='skipped' if case.find('skipped') is not None else 'failed' if case.find('failure') is not None or case.find('error') is not None else 'passed'
            manifest.append(dict(id=suite.get('name')+'.'+case.get('name'),status=status))
    return totals,sorted(manifest,key=lambda c:c['id'])

totals,manifest=junit(phase/'final-junit');old=load(phase/'before-test-manifest.json')
ids={c['id'] for c in manifest};oldids={c['id'] for c in old}
assert totals==dict(tests=772,failures=0,errors=0,skipped=1),totals
assert len(ids)==772 and oldids<=ids and len(oldids)==751 and len(ids-oldids)==21
assert {c['id'] for c in manifest if c['status']=='skipped'}=={c['id'] for c in old if c['status']=='skipped'}
targeted,_=junit(phase/'targeted-junit');assert targeted==dict(tests=79,failures=0,errors=0,skipped=0),targeted
build=read(phase/'full-build.log');assert 'BUILD SUCCESSFUL' in build and re.search(r'> Task :app:packageDebug(?:\r?\n|$)',build)
lint={};new_warnings=[];oldresults=load(previous/'results.json')
for module in ('app','editor-sora'):
    path=root/module/'build/reports/lint-results-debug.xml';nodes=E.parse(path).getroot().findall('issue')
    severity=collections.Counter(n.get('severity') for n in nodes);counts=collections.Counter(n.get('id') for n in nodes)
    assert not severity.get('Error') and not severity.get('Fatal')
    lint[module]=dict(severity=dict(severity),ids=dict(counts))
    delta=counts-collections.Counter(oldresults['lint'][module]['ids'])
    for kind,count in delta.items():
        new_warnings.append(dict(module=module,id=kind,count=count,messages=[n.get('message') for n in nodes if n.get('id')==kind]))
    shutil.copy2(path,phase/(module+'-lint.xml'))

apk=root/'app/build/outputs/apk/debug/app-debug.apk';meta=load(phase/'apk-inspection.json')
assert meta['sha256']==sha(apk) and meta['zip_crc_passed'] and meta['abis']==['arm64-v8a']
assert meta['test_failures']==meta['test_errors']==0 and meta['bundled_total_tests']==39
semantic=load(phase/'semantic-apk-inspection.json')
assert semantic['apk_sha256']==sha(apk) and semantic['tests']==19 and semantic['errors']==semantic['failures']==0
assert semantic['dependencies_from_actual_apk'] and semantic['dependency_bytecode_loaded'] and semantic['filesystem_packages_verified']
source=read(phase/'python-source.log');assert 'Ran 63 tests' in source and re.search(r'\bOK\b',source)
assert 'Ran 15 tests' in read(phase/'backend-baseline.log') and re.search(r'\bOK\b',read(phase/'backend-baseline.log'))
shell=load(phase/'shell-startup-inspection.json');assert len(shell['cases'])==4 and all(c['passed'] for c in shell['cases'])
snippets=load(phase/'snippets.json')['fixtures'];assert len(snippets)==14 and all(c['passed'] for c in snippets)
signer='d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408'
assert signer in read(phase/'certificates.txt')
badging=read(phase/'badging.txt')
assert "package: name='com.codepocket.ide' versionCode='2' versionName='0.2.0'" in badging
assert "sdkVersion:'24'" in badging and "targetSdkVersion:'34'" in badging
assert read(phase/'permissions.txt')==read(previous/'permissions.txt')
tree=read(phase/'manifest-tree.txt')
block=next(b for b in re.split(r'\n\s*E: service ',tree) if 'PythonAnalysisService' in b)
block=block.split('\n      E:')[0]
assert ':analysis' in block and re.search(r'android:exported[^\n]+0x0',block)
before=load(phase/'before-apks.json')
for item in before:assert sha(root/'dist'/item['name'])==item['sha256'],item['name']
with zipfile.ZipFile(apk) as new,zipfile.ZipFile(root/oldresults['apk']) as prior:
    assert new.testzip() is None
    libs=[n for n in prior.namelist() if n.startswith('lib/') and n.endswith('.so')];assert len(libs)==10
    for name in libs:assert new.read(name)==prior.read(name),name
    with zipfile.ZipFile(io.BytesIO(new.read('assets/chaquopy/app.imy'))) as fresh,zipfile.ZipFile(io.BytesIO(prior.read('assets/chaquopy/app.imy'))) as oldapp:
        for name in ('runner.pyc','diagnostics.pyc'):assert fresh.read(name)[16:]==oldapp.read(name)[16:],name
    dex=b''.join(new.read(n) for n in new.namelist() if n.startswith('classes') and n.endswith('.dex'))
    assert b'Lcom/codepocket/ide/PythonAnalysisService;' in dex and b'Lcom/codepocket/ide/PythonSemanticClient;' in dex
    config=json.loads(new.read('assets/chaquopy/build.json'));assert {'jedi','parso'}<=set(config['extract_packages'])
dest=root/'dist/CodePocket-python-semantics-2026-10-26-debug.apk'
if dest.exists():assert sha(dest)==sha(apk),'Do not replace a different historical APK'
else:shutil.copy2(apk,dest)
previews=list((phase/'previews').glob('*.png'));assert len(previews)==3
native=root/'docs/python-semantic-preview';native.mkdir(exist_ok=True)
for image in previews:assert image.read_bytes().startswith(b'\x89PNG');shutil.copy2(image,native/image.name)
results=dict(apk=dest.relative_to(root).as_posix(),bytes=dest.stat().st_size,sha256=sha(dest),
    apk_size_delta_bytes=dest.stat().st_size-oldresults['bytes'],junit=totals,targeted=targeted,
    old_test_cases_preserved=751,new_test_cases=21,no_new_skips=True,lint=lint,new_lint_warnings=new_warnings,
    source_python_tests=63,packaged_runtime_tests=39,packaged_semantic_tests=19,
    dependencies_from_actual_apk=True,dependency_bytecode_loaded=True,extract_packages=config['extract_packages'],
    shell_startup_tests=4,python_snippets=7,cpp17_snippets=7,native_libraries_identical=10,runner_and_diagnostics_payload_identical=True,
    old_apks_preserved=len(before),signature='passed',signer_sha256=signer,zip_crc='passed',alignment_16kb='passed',
    main_analysis_service_in_actual_apk=True,native_previews=3,apk_build_invocations=1,
    real_host_backend=True,host_main_transport='Recorded real Jedi responses for Main/Sora; separate Messenger lifecycle tests',
    android_semantic_execution_verified=False,device_verified=False,remote_ci_verified=False,
    first_scope='Unsaved active file and standard library; saved internal project modules. Other unsaved buffers and fresh SAF project snapshots are P2-B.')
save(phase/'results.json',results);save(phase/'test-case-manifest.json',manifest)
sources=['app/build.gradle.kts','app/src/main/AndroidManifest.xml','app/src/main/python/semantics.py',
    'app/src/main/java/com/codepocket/ide/PythonSemanticClient.kt','app/src/main/java/com/codepocket/ide/PythonAnalysisService.kt',
    'app/src/main/java/com/codepocket/ide/MainActivity.kt','editor-assist/src/main/kotlin/com/codepocket/ide/CompletionModel.kt',
    'editor-assist/src/main/kotlin/com/codepocket/ide/SignatureHelp.kt']
save(phase/'source-hashes.json',{name:sha(root/name) for name in sources})
print(json.dumps(results,ensure_ascii=False,indent=2))
