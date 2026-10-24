from pathlib import Path
import json, hashlib, xml.etree.ElementTree as ET, zipfile, shutil, collections, re
root=Path.cwd()
phase=root/'verification/workbench-terminal-dock-2026-10-24'
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
dest=root/'dist/CodePocket-terminal-dock-2026-10-24-debug.apk'
assert not dest.exists(), dest
with zipfile.ZipFile(apk) as z, zipfile.ZipFile(root/'dist/CodePocket-command-ui-final-2026-10-24-debug.apk') as old:
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
shutil.copy2(apk,dest)
assert sha(dest)==sha(apk)
for p in (phase/'previews').glob('*.png'):shutil.copy2(p,root/'docs/terminal-dock-preview'/p.name)
results=dict(apk=str(dest.relative_to(root)),bytes=dest.stat().st_size,sha256=sha(dest),junit=junit,failures=failures,lint=lint,
old_apks_preserved=len(before),native_libraries_identical=len(natives),python_app_archive_identical=True,
source_python_tests=44,packaged_python_tests=meta['bundled_total_tests'],shell_startup_tests=len(shell['cases']),
python_snippets=sum(not c['cpp'] for c in snippets),cpp17_snippets=sum(c['cpp'] for c in snippets),
assemble='Debug tests, Lint, packageDebug and assembleDebug succeeded',
signature='passed',alignment_16kb='passed',device_verified=False,remote_ci_verified=False,complete_ui=False)
def case_ids(folder):
    return {t.get('name')+'.'+c.get('name') for p in folder.glob('TEST-*.xml') for t in [ET.parse(p).getroot()] for c in t.findall('testcase')}
baseline={case['id'] for case in json.loads((root/'verification/workbench-command-ui-2026-10-24/test-case-manifest.json').read_text())}
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
print(json.dumps(results,ensure_ascii=False,indent=2))
