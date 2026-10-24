from pathlib import Path
import json, hashlib, xml.etree.ElementTree as ET, zipfile, shutil, collections, re
root=Path.cwd()
(root/'docs/tab-navigation-preview').mkdir(exist_ok=True)
phase=root/'verification/workbench-tab-navigation-2026-10-24'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
before=json.loads((phase/'before-apks.json').read_text(encoding='utf-8-sig'))
for item in before:
    assert sha(root/'dist'/item['name'])==item['sha256'], item['name']
junit=dict(tests=0,failures=0,skipped=0,errors=0)
failures=[]
for p in (phase/'final-junit').glob('TEST-*.xml'):
    t=ET.parse(p).getroot()
    for k in junit:junit[k]+=int(t.get(k,0))
    failures += [t.get('name')+'.'+c.get('name') for c in t.findall('testcase') if c.find('failure') is not None or c.find('error') is not None]
assert junit['tests']>670, junit
assert junit["failures"]==junit["errors"]==0, failures
assert "BUILD SUCCESSFUL" in (phase/"full-build.log").read_bytes().decode("utf-8",errors="ignore").replace("\x00","")
lint={}
for module in ('app','editor-sora'):
    nodes=ET.parse(root/module/'build/reports/lint-results-debug.xml').getroot().findall('issue')
    severity=collections.Counter(n.get('severity') for n in nodes)
    assert not severity.get('Error') and not severity.get('Fatal'), severity
    lint[module]=dict(severity=dict(severity),ids=dict(collections.Counter(n.get('id') for n in nodes)))
apk=root/'app/build/outputs/apk/debug/app-debug.apk'
dest=root/'dist/CodePocket-tab-navigation-2026-10-24-debug.apk'
if dest.exists(): assert sha(dest)==sha(apk), 'Refuse to overwrite an existing APK with different bytes'
with zipfile.ZipFile(apk) as z, zipfile.ZipFile(root/'dist/CodePocket-tab-stability-2026-10-24-debug.apk') as old:
    assert z.testzip() is None
    natives=[n for n in old.namelist() if n.startswith('lib/') and n.endswith('.so')]
    for n in natives: assert z.read(n)==old.read(n),n
    assert z.read('assets/chaquopy/app.imy')==old.read('assets/chaquopy/app.imy')
meta=json.loads((phase/'apk-inspection.json').read_text())
assert meta['sha256']==sha(apk) and meta['test_failures']==meta['test_errors']==0
shell=json.loads((phase/'shell-startup-inspection.json').read_text())
assert all(c['passed'] for c in shell['cases'])
snippets=json.loads((phase/'snippets.json').read_text())['fixtures']
assert all(c['passed'] for c in snippets)
source_log=(phase/'python-source.log').read_bytes().decode('utf-8',errors='ignore').replace('\x00','')
assert 'Ran 44 tests' in source_log and re.search(r'\bOK\b',source_log)
assert junit['tests']==704 and junit['skipped']==1, junit
if not dest.exists(): shutil.copy2(apk,dest)
assert sha(dest)==sha(apk)
for p in (phase/'previews').glob('*.png'):shutil.copy2(p,root/'docs/tab-navigation-preview'/p.name)
results=dict(apk=str(dest.relative_to(root)),bytes=dest.stat().st_size,sha256=sha(dest),junit=junit,failures=failures,lint=lint,
old_apks_preserved=len(before),native_libraries_identical=len(natives),python_app_archive_identical=True,
source_python_tests=44,packaged_python_tests=meta['bundled_total_tests'],shell_startup_tests=len(shell['cases']),
python_snippets=sum(not c['cpp'] for c in snippets),cpp17_snippets=sum(c['cpp'] for c in snippets),
assemble='Debug tests, Lint, packageDebug and assembleDebug succeeded',
signature='passed',alignment_16kb='passed',device_verified=False,remote_ci_verified=False,complete_ui=False)
def case_ids(folder):
    return {t.get('name')+'.'+c.get('name') for p in folder.glob('TEST-*.xml') for t in [ET.parse(p).getroot()] for c in t.findall('testcase')}
baseline={case['id'] for case in json.loads((root/'verification/workbench-tab-stability-2026-10-24/test-case-manifest.json').read_text())}
current=case_ids(phase/'final-junit')
assert baseline<=current, baseline-current
with zipfile.ZipFile(apk) as z:
    packaged_theme=json.loads(z.read('assets/textmate/light.json'))
source_theme=json.loads((root/'editor-sora/src/main/assets/textmate/light.json').read_text())
assert packaged_theme==source_theme
contrast=json.loads((root/'verification/workbench-token-theme-2026-10-24/palette-contrast.json').read_text())
assert all(c['contrast_on_white']>=4.5 for c in contrast)
results.update(baseline_test_cases_retained=len(baseline),new_test_cases=len(current-baseline),packaged_theme_matches_source=True,
    minimum_light_token_contrast=min(c['contrast_on_white'] for c in contrast))
(phase/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
# Keep the prior case identity set and the old skip exactly, not just the total.
manifest=[]
for p in (phase/'final-junit').glob('TEST-*.xml'):
    t=ET.parse(p).getroot()
    for c in t.findall('testcase'):
        manifest.append(dict(id=t.get('name')+'.'+c.get('name'),status='skipped' if c.find('skipped') is not None else 'failed' if c.find('failure') is not None or c.find('error') is not None else 'passed'))
manifest.sort(key=lambda c:c['id'])
old_manifest=json.loads((root/'verification/workbench-tab-stability-2026-10-24/test-case-manifest.json').read_text())
assert {c['id'] for c in manifest if c['status']=='skipped'}=={c['id'] for c in old_manifest if c['status']=='skipped'}
(phase/'test-case-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
browser_log=(phase/'browser-matrix.log').read_bytes().decode('utf-8',errors='replace').replace('\x00','')
match=re.search(r'### Result\s*(\{[^\n]+\})',browser_log)
assert match,browser_log[:500]
browser=json.loads(match.group(1));assert browser['cases']==256 and browser['checks']==31 and not browser['failures'] and not browser['errors'],browser
(phase/'browser-results.json').write_text(json.dumps(browser,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
certificate=(phase/'certificates.txt').read_bytes().decode('utf-8',errors='replace').replace('\x00','')
signer='d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408'
assert signer in certificate
def suite_summary(path):
    t=ET.parse(path).getroot()
    return dict(tests=int(t.get('tests')),failures=int(t.get('failures')),cases=[dict(name=c.get('name'),failure=c.find('failure').get('message') if c.find('failure') is not None else None) for c in t.findall('testcase')])
results.update(browser=browser,signer_sha256=signer,no_new_skips=True,
    final_build='Full main app tests and Lint successful; packageDebug actually executed in full-build.log',
    baseline=suite_summary(phase/'baseline-junit/TEST-com.codepocket.ide.EditorTabNavigationIntegrationTest.xml'),
    first_full=json.loads((phase/'first-full-summary.json').read_text()),
    targeted='Five actual Main/Sora navigation cases, fixed dirty tabs, and existing workbench animation regressions passed.',
    navigation='Reveal only on explicit file selection, selection change or viewport width change; repeated same-selection UI binding preserves manual scrolling.',
    production_changed=['MainActivity.kt (explicit current-file navigation)','ui/EditorTabs.kt (bounded post-layout reveal and viewport handling)','ui/EditorTab.kt (native reveal rectangle and narrow-viewport cap)'],
    prior_single_import_failure='Unconfirmed and not reproduced; no production Python changes in this batch')
(phase/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
shots=root/'output/playwright/tab-navigation-2026-10-24'
assert len(list(shots.glob('*.png')))==43
for p in shots.glob('*.png'):shutil.copy2(p,root/'docs/ui-preview/workbench-screenshots'/p.name)
print(json.dumps(results,ensure_ascii=False,indent=2))
