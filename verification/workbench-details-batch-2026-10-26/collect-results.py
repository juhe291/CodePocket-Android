from pathlib import Path
import collections, hashlib, json, re, shutil, xml.etree.ElementTree as E, zipfile

root = Path.cwd()
phase = root / 'verification/workbench-details-batch-2026-10-26'
previous = root / 'verification/workbench-settings-chrome-batch-2026-10-26'
def read(path):
    b = path.read_bytes()
    return b.decode('utf-16' if b.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig')
def load(path): return json.loads(read(path))
def save(path, data): path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def junit(folder):
    manifest = []
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in folder.glob('TEST-*.xml'):
        suite = E.parse(path).getroot()
        for key in totals: totals[key] += int(suite.get(key, 0))
        for case in suite.findall('testcase'):
            status = 'skipped' if case.find('skipped') is not None else 'failed' if case.find('failure') is not None or case.find('error') is not None else 'passed'
            manifest.append(dict(id=suite.get('name')+'.'+case.get('name'), status=status))
    return totals, sorted(manifest, key=lambda c:c['id'])

before = load(phase/'before-apks.json')
for item in before: assert sha(root/'dist'/item['name']) == item['sha256'], item['name']
totals, manifest = junit(phase/'final-junit')
old = load(phase/'before-test-manifest.json')
oldids = {c['id'] for c in old}
ids = {c['id'] for c in manifest}
assert oldids <= ids and len(ids) == len(manifest)
assert totals['tests'] == len(manifest) and totals['tests'] >= 751
assert totals['errors'] == totals['failures'] == 0 and totals['skipped'] == 1
assert {c['id'] for c in manifest if c['status']=='skipped'} == {c['id'] for c in old if c['status']=='skipped'}
build = read(phase/'full-build.log')
assert 'BUILD SUCCESSFUL' in build and re.search(r'> Task :app:packageDebug(?:\r?\n|$)', build)
lint = {}
for module in ('app', 'editor-sora'):
    path = root/module/'build/reports/lint-results-debug.xml'
    nodes = E.parse(path).getroot().findall('issue')
    severity = collections.Counter(n.get('severity') for n in nodes)
    assert not severity.get('Error') and not severity.get('Fatal')
    lint[module] = dict(severity=dict(severity), ids=dict(collections.Counter(n.get('id') for n in nodes)))
    expected = load(previous/'results.json')['lint'][module]
    if module == 'app':
        expected['severity']['Warning'] += 1
        expected['ids']['UnusedResources'] += 1
        assert sum(n.get('id')=='UnusedResources' and 'R.string.shell_failure_close' in n.get('message','') for n in nodes) == 1
    assert lint[module] == expected, (module, lint[module])
    shutil.copy2(path, phase/(module+'-lint.xml'))
apk = root/'app/build/outputs/apk/debug/app-debug.apk'
meta = load(phase/'apk-inspection.json')
assert meta['sha256'] == sha(apk) and meta['test_errors'] == meta['test_failures'] == 0 and meta['bundled_total_tests'] == 39
assert meta['zip_crc_passed'] and meta['abis'] == ['arm64-v8a']
badging = read(phase/'badging.txt')
assert "package: name='com.codepocket.ide' versionCode='2' versionName='0.2.0'" in badging
assert "sdkVersion:'24'" in badging and "targetSdkVersion:'34'" in badging
assert read(phase/'permissions.txt') == read(previous/'permissions.txt')
assert 'Ran 44 tests' in read(phase/'python-source.log') and re.search(r'\bOK\b',read(phase/'python-source.log'))
shell = load(phase/'shell-startup-inspection.json')
assert len(shell['cases']) == 4 and all(c['passed'] for c in shell['cases'])
snippets = load(phase/'snippets.json')['fixtures']
assert len(snippets) == 14 and all(c['passed'] for c in snippets)
signer = 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408'
assert signer in read(phase/'certificates.txt')
with zipfile.ZipFile(apk) as new, zipfile.ZipFile(root/'dist/CodePocket-settings-chrome-batch-2026-10-26-debug.apk') as oldapk:
    assert new.testzip() is None
    libs = [n for n in oldapk.namelist() if n.startswith('lib/') and n.endswith('.so')]
    assert len(libs) == 10
    for name in libs: assert new.read(name) == oldapk.read(name), name
    assert new.read('assets/chaquopy/app.imy') == oldapk.read('assets/chaquopy/app.imy')
assert '6 tests completed, 5 failed' in read(phase/'baseline.log')
previews = list((phase/'previews').glob('*.png'))
assert len(previews) >= 6
native = root/'docs/details-batch-preview'
native.mkdir(exist_ok=True)
for path in previews:
    assert path.read_bytes().startswith(b'\x89PNG')
    shutil.copy2(path, native/path.name)
dest = root/'dist/CodePocket-details-batch-2026-10-26-debug.apk'
if dest.exists(): assert sha(dest) == sha(apk), 'Do not replace an existing different delivery'
else: shutil.copy2(apk, dest)
assert sha(dest) == sha(apk)
targeted, _ = junit(phase/'targeted-junit')
results = dict(apk=dest.relative_to(root).as_posix(), bytes=dest.stat().st_size, sha256=sha(dest),
    junit=totals, lint=lint, baseline_tests=6, baseline_failures=5, targeted=targeted,
    retained_test_cases=len(oldids), new_test_cases=len(ids-oldids), no_new_skips=True,
    native_previews=len(previews), old_apks_preserved=len(before), native_libraries_identical=10,
    python_app_archive_identical=True, source_python_tests=44, packaged_python_tests=39,
    shell_startup_tests=4, python_snippets=7, cpp17_snippets=7, signature='passed', signer_sha256=signer,
    alignment_16kb='passed', zip_crc='passed', final_package_task_executed=True, apk_build_invocations=1,
    device_verified=False, remote_ci_verified=False, complete_ui=False,
    deferred_new_lint_warning=dict(id='UnusedResources', resource='R.string.shell_failure_close',
        reason='Shared reader uses the same visible close label; original string retained. Non-blocking, recorded per batch policy; no suppression or extra APK build.'))
save(phase/'results.json', results)
save(phase/'test-case-manifest.json', manifest)
sources = ['app/src/main/java/com/codepocket/ide/'+name for name in
    ('MainActivity.kt', 'SettingsDialog.kt', 'ui/ReadOnlyDetails.kt', 'terminal/shell/ShellTerminalPane.java')]
save(phase/'source-hashes.json', {name:sha(root/name) for name in sources})
print(json.dumps(results, ensure_ascii=False, indent=2))
