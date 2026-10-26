from pathlib import Path
import collections, hashlib, json, re, shutil, xml.etree.ElementTree as ET, zipfile
root=Path.cwd();phase=root/'verification/workbench-pickers-batch-2026-10-26';previous=root/'verification/workbench-context-batch-2026-10-26'
def read(p):
 data=p.read_bytes();return data.decode('utf-16' if data.startswith((b"\xff\xfe",b"\xfe\xff")) else 'utf-8-sig',errors='replace')
def load(p):return json.loads(read(p))
def save(p,data):p.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
before=load(phase/'before-apks.json')
for item in before:assert sha(root/'dist'/item['name'])==item['sha256'],item['name']
manifest=[];totals=dict(tests=0,failures=0,errors=0,skipped=0)
for p in (phase/'final-junit').glob('TEST-*.xml'):
 suite=ET.parse(p).getroot()
 for key in totals:totals[key]+=int(suite.get(key,0))
 for case in suite.findall('testcase'):
  manifest.append(dict(id=suite.get('name')+'.'+case.get('name'),status='skipped' if case.find('skipped') is not None else 'failed' if case.find('failure') is not None or case.find('error') is not None else 'passed'))
manifest.sort(key=lambda c:c['id']);assert totals==dict(tests=733,failures=0,errors=0,skipped=1),totals
old_manifest=load(previous/'test-case-manifest.json');ids={c['id'] for c in manifest};old_ids={c['id'] for c in old_manifest}
assert old_ids<=ids and len(ids-old_ids)==6
assert {c['id'] for c in manifest if c['status']=='skipped'}=={c['id'] for c in old_manifest if c['status']=='skipped'}
build=read(phase/'full-build.log');assert 'BUILD SUCCESSFUL' in build
assert re.search(r'> Task :app:packageDebug(?:\r?\n|$)',build),'This batch must actually package the APK'
lint={};old_results=load(previous/'results.json')
for module in ('app','editor-sora'):
 nodes=ET.parse(root/module/'build/reports/lint-results-debug.xml').getroot().findall('issue');severity=collections.Counter(n.get('severity') for n in nodes)
 assert not severity.get('Error') and not severity.get('Fatal'),severity
 assert severity.get('Warning',0)<=old_results['lint'][module]['severity'].get('Warning',0)
 lint[module]=dict(severity=dict(severity),ids=dict(collections.Counter(n.get('id') for n in nodes)))
match=re.search(r'### Result\s*(\{[^\n]+\})',read(phase/'browser-matrix.log'));assert match
browser=json.loads(match.group(1));assert browser['cases']==320 and browser['checks']==72 and browser['paletteChecks']==20 and not browser['failures'] and not browser['errors'],browser
apk=root/'app/build/outputs/apk/debug/app-debug.apk';meta=load(phase/'apk-inspection.json')
assert meta['sha256']==sha(apk) and meta['test_failures']==meta['test_errors']==0 and meta['bundled_total_tests']==39
assert 'Ran 44 tests' in read(phase/'python-source.log') and re.search(r'\bOK\b',read(phase/'python-source.log'))
shell=load(phase/'shell-startup-inspection.json');assert len(shell['cases'])==4 and all(c['passed'] for c in shell['cases'])
snippets=load(phase/'snippets.json')['fixtures'];assert len(snippets)==14 and all(c['passed'] for c in snippets)
signer='d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408';assert signer in read(phase/'certificates.txt')
with zipfile.ZipFile(apk) as new,zipfile.ZipFile(root/'dist/CodePocket-context-batch-2026-10-26-debug.apk') as old:
 assert new.testzip() is None
 natives=[n for n in old.namelist() if n.startswith('lib/') and n.endswith('.so')];assert len(natives)==10
 for name in natives:assert new.read(name)==old.read(name),name
 assert new.read('assets/chaquopy/app.imy')==old.read('assets/chaquopy/app.imy')
 assert json.loads(new.read('assets/textmate/light.json'))==load(root/'editor-sora/src/main/assets/textmate/light.json')
baseline=ET.parse(phase/'baseline-junit/TEST-com.codepocket.ide.WorkspacePickerIntegrationTest.xml').getroot();assert int(baseline.get('tests'))==6 and int(baseline.get('failures'))==6
previews=list((phase/'previews').glob('*.png'));assert len(previews)==6
for p in previews:assert p.read_bytes().startswith(b'\x89PNG\r\n\x1a\n') and p.stat().st_size>100
shots=root/'output/playwright/pickers-batch-2026-10-26';assert len(list(shots.glob('*.png')))==174
native=root/'docs/pickers-batch-preview';native.mkdir(exist_ok=True)
for p in previews:shutil.copy2(p,native/p.name)
for p in shots.glob('*.png'):shutil.copy2(p,root/'docs/ui-preview/workbench-screenshots'/p.name)
dest=root/'dist/CodePocket-pickers-batch-2026-10-26-debug.apk'
if dest.exists():assert sha(dest)==sha(apk),'Do not overwrite a different delivered APK'
else:shutil.copy2(apk,dest)
assert sha(dest)==sha(apk)
results=dict(apk=dest.relative_to(root).as_posix(),bytes=dest.stat().st_size,sha256=sha(dest),junit=totals,lint=lint,browser=browser,
 baseline_tests=6,baseline_failures=6,targeted=load(phase/'targeted-results.json'),baseline_test_cases_retained=len(old_ids),new_test_cases=6,no_new_skips=True,
 native_previews=len(previews),browser_screenshots=174,old_apks_preserved=len(before),native_libraries_identical=10,python_app_archive_identical=True,
 source_python_tests=44,packaged_python_tests=39,shell_startup_tests=4,python_snippets=7,cpp17_snippets=7,
 signature='passed',signer_sha256=signer,alignment_16kb='passed',zip_crc='passed',packaged_theme_matches_source=True,
 final_build='Full main app tests, app/Sora Lint and assembleDebug succeeded.',final_package_task_executed=True,apk_build_invocations=1,
 production_changed=['MainActivity.kt: workspace/draft picker metadata and cancel, adaptive move presentation','ui/WorkbenchChoiceAdapter.kt: two-level rows, type icons, selected marker and full-path descriptions','ui/WorkbenchStyle.kt: retain typed choice adapters rather than flatten metadata','ui/WorkbenchForms.kt: natural-height bounded directory viewport'],
 device_verified=False,remote_ci_verified=False,complete_ui=False,
 host_boundary='Real Main/Sora Views, workspace and permission-confirmation cancel, exact chosen draft export snapshot and Android intent, long-list cancel, dirty-buffer file move with stable file ID, source/descendant exclusions and empty directory natural height under Robolectric. No actual Android provider UI, IME, physical keyboard or live Android PTY process claim.',
 scope='One batch of three related picker UI changes; original workspace/permission, draft-export and move transaction callbacks retained. Runtime preserved.',
 prior_single_import_failure='Previously unconfirmed and not reproduced; production Python unchanged.',prior_host_focus_observation='Passive tablet height transition remains recorded in compact-captions phase, not fixed or device-verified here.',
 browser_repair='Long-list assertion initially used an unconstrained phone window; constrained to 640px and aligned row height with native 64dp. Independent body scrolling was still absent in the preview, so fixed body/footer flex containment; final matrix passed.',test_refinement='Initial baseline and targeted runs asserted typed-adapter identity. After all 53 targeted cases passed, replaced that implementation assertion with visible cancel text/height; retained path/selection/export/move assertions and added exact draft full-path description for the full suite.')
save(phase/'results.json',results);save(phase/'test-case-manifest.json',manifest);save(phase/'browser-results.json',browser)
print(json.dumps({k:results[k] for k in ('apk','bytes','sha256','junit','lint','old_apks_preserved','browser')},ensure_ascii=False,indent=2))
