from pathlib import Path
import json,hashlib,collections,re,shutil,xml.etree.ElementTree as E,zipfile
root=Path.cwd();phase=root/'verification/workbench-settings-chrome-batch-2026-10-26';previous=root/'verification/workbench-assist-batch-2026-10-26'
def read(p):
 b=p.read_bytes();return b.decode('utf-16' if b.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig',errors='replace')
def load(p):return json.loads(read(p))
def save(p,x):p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
before=load(phase/'before-apks.json')
for x in before:assert sha(root/'dist'/x['name'])==x['sha256'],x['name']
manifest=[];totals=dict(tests=0,failures=0,errors=0,skipped=0)
for p in (phase/'final-junit').glob('TEST-*.xml'):
 suite=E.parse(p).getroot()
 for k in totals:totals[k]+=int(suite.get(k,0))
 for c in suite.findall('testcase'):manifest.append(dict(id=suite.get('name')+'.'+c.get('name'),status='skipped' if c.find('skipped') is not None else 'failed' if c.find('failure') is not None or c.find('error') is not None else 'passed'))
manifest.sort(key=lambda c:c['id']);assert totals==dict(tests=745,failures=0,errors=0,skipped=1),totals
old=load(phase/'before-test-manifest.json');ids={c['id'] for c in manifest};oldids={c['id'] for c in old};assert len(ids)==745 and oldids<=ids and len(ids-oldids)==6
assert {c['id'] for c in manifest if c['status']=='skipped'}=={c['id'] for c in old if c['status']=='skipped'}
build=read(phase/'full-build.log');assert 'BUILD SUCCESSFUL' in build and re.search(r'> Task :app:packageDebug(?:\r?\n|$)',build)
lint={};oldresults=load(previous/'results.json')
for module in ('app','editor-sora'):
 nodes=E.parse(root/module/'build/reports/lint-results-debug.xml').getroot().findall('issue');severity=collections.Counter(n.get('severity') for n in nodes);counter=collections.Counter(n.get('id') for n in nodes)
 assert not severity.get('Error') and not severity.get('Fatal')
 expected=collections.Counter(oldresults['lint'][module]['ids'])
 if module=='app':expected.subtract({'ViewConstructor':1});expected=+expected
 assert counter==expected,(module,counter,expected)
 lint[module]=dict(severity=dict(severity),ids=dict(counter))
match=re.search(r'### Result\s*(\{[^\n]+\})',read(phase/'browser-matrix.log'));assert match
browser=json.loads(match[1]);assert browser['cases']==368 and browser['checks']==117 and browser['paletteChecks']==20 and browser['scenes']==46 and not browser['failures'] and not browser['errors']
apk=root/'app/build/outputs/apk/debug/app-debug.apk';meta=load(phase/'apk-inspection.json');assert meta['sha256']==sha(apk) and meta['test_errors']==meta['test_failures']==0 and meta['bundled_total_tests']==39
assert 'Ran 44 tests' in read(phase/'python-source.log') and re.search(r'\bOK\b',read(phase/'python-source.log'))
shell=load(phase/'shell-startup-inspection.json');assert len(shell['cases'])==4 and all(c['passed'] for c in shell['cases'])
snippets=load(phase/'snippets.json')['fixtures'];assert len(snippets)==14 and all(c['passed'] for c in snippets)
signer='d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408';assert signer in read(phase/'certificates.txt')
with zipfile.ZipFile(apk) as new,zipfile.ZipFile(root/'dist/CodePocket-assist-batch-2026-10-26-debug.apk') as oldapk:
 assert new.testzip() is None
 libs=[n for n in oldapk.namelist() if n.startswith('lib/') and n.endswith('.so')];assert len(libs)==10
 for name in libs:assert new.read(name)==oldapk.read(name),name
 assert new.read('assets/chaquopy/app.imy')==oldapk.read('assets/chaquopy/app.imy')
 assert json.loads(new.read('assets/textmate/light.json'))==load(root/'editor-sora/src/main/assets/textmate/light.json')
base=E.parse(phase/'baseline-junit/TEST-com.codepocket.ide.SettingsChromeIntegrationTest.xml').getroot();assert int(base.get('tests'))==6 and int(base.get('failures'))==6
previews=list((phase/'previews').glob('*.png'));assert len(previews)==7,len(previews)
shots=list((root/'output/playwright/settings-chrome-batch-2026-10-26').glob('*.png'));assert len(shots)==230,len(shots)
native=root/'docs/settings-chrome-preview';native.mkdir(exist_ok=True)
for p in previews:assert p.read_bytes().startswith(b'\x89PNG');shutil.copy2(p,native/p.name)
for p in shots:shutil.copy2(p,root/'docs/ui-preview/workbench-screenshots'/p.name)
dest=root/'dist/CodePocket-settings-chrome-batch-2026-10-26-debug.apk'
if dest.exists():assert sha(dest)==sha(apk),'Do not replace an existing different delivery'
else:shutil.copy2(apk,dest)
assert sha(dest)==sha(apk)
results=dict(apk=dest.relative_to(root).as_posix(),bytes=dest.stat().st_size,sha256=sha(dest),junit=totals,lint=lint,browser=browser,baseline_tests=6,baseline_failures=6,targeted=load(phase/'targeted-results.json'),retained_test_cases=len(oldids),new_test_cases=6,no_new_skips=True,native_previews=7,browser_screenshots=230,old_apks_preserved=len(before),native_libraries_identical=10,python_app_archive_identical=True,source_python_tests=44,packaged_python_tests=39,shell_startup_tests=4,python_snippets=7,cpp17_snippets=7,signature='passed',signer_sha256=signer,alignment_16kb='passed',zip_crc='passed',packaged_theme_matches_source=True,final_package_task_executed=True,apk_build_invocations=1,view_constructor_warning_resolved=True,device_verified=False,remote_ci_verified=False,complete_ui=False,
production_changed=['MainToolbar.kt: real workspace name, quiet folder/disclosure icons, full path and compact text','MainActivity.kt: remove duplicate rail Run callback, preserve top Run/F5/menu','SettingsDialog.kt: stable option identities, section grouping, once-per-group search context, shared choice rows and on-demand complete key help','CompletionCandidateRow.kt: normal Context constructor, original explicit palette constructor preserved'],
validation_boundary='Real Main/Sora buffer, workspace picker, settings cancel/apply/recreate and native View drawings under Robolectric. Browser is design-only. No live Android IME/BT keyboard, PTY, Chaquopy device, Harmony, API24, frame rate or Wandbox/remote Actions validation.',
retained_deferred_issues=['Passive tablet height transition may lose Sora focus; old/device cause unconfirmed','InteractionState secondary explanation is keyword based','Prior single packaged helper import failure remains unconfirmed, full reruns pass','Original P2/P3 and unimplemented feature scope remain deferred'],
test_refinement='Baseline six real UI failures. First 38 targeted tests passed. Workspace picker title assertion corrected to current production header (工作区); prior main structure brand assertion updated to actual short workspace name while retaining width/hierarchy checks. Added complete on-demand help content assertion before final full run; no test identities removed or added skips.')
results['settings_model_preservation']=load(phase/'settings-model-preservation.json')
results['deferred_visual_observation']='平板按键说明只读弹窗默认较宽，下一批与帮助/许可/详情统一阅读宽度；不单独重打包。'
save(phase/'results.json',results);save(phase/'test-case-manifest.json',manifest);save(phase/'browser-results.json',browser)
print(json.dumps({k:results[k] for k in ('apk','bytes','sha256','junit','lint','old_apks_preserved','browser')},ensure_ascii=False,indent=2))
