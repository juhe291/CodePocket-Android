"""Archive the completed related run and freeze inputs before the one final build."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]; OUT=Path(__file__).parent
PREVIOUS=ROOT/'verification/git-private-path-fix-2026-11-03'
def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def write(name,data):
    (OUT/name).write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')
original=json.loads((OUT/'integration-first-sources.json').read_text())
for name,digest in original.items():assert sha(ROOT/name)==digest,name
cases=[]; folder=OUT/'related-junit'; folder.mkdir()
for source in sorted((ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
    assert source.stat().st_mtime >= (OUT/'related.log').stat().st_birthtime,source
    for case in ET.parse(source).getroot().findall('testcase'):
        cases.append(dict(id=case.get('classname')+'.'+case.get('name'),status=next((s for s in ('failure','error','skipped') if case.find(s)is not None),'passed')))
    shutil.copyfile(source,folder/source.name)
assert cases and all(c['status']=='passed' for c in cases)
assert set(json.loads((OUT/'integration-first-results.json').read_text())['identities']) <= {c['id'] for c in cases}
write('related-results.json',dict(tests=len(cases),counts=dict(Counter(c['status'] for c in cases)),identities=[c['id'] for c in cases]))
keys=set(json.loads((PREVIOUS/'build-inputs.json').read_text()))|set(original)
write('build-inputs.json',{name:sha(ROOT/name) for name in sorted(keys)})
write('before-apks.json',[dict(name=p.name,bytes=p.stat().st_size,sha256=sha(p)) for p in sorted((ROOT/'dist').glob('*.apk'))])
write('viewed-previews.json',{p.name:sha(p) for p in sorted((OUT/'previews').glob('*.png'))})
for name in ('full-build.ps1','dependencies.init.gradle','verify-artifact.ps1','verify-package.ps1'):
    text=(PREVIOUS/name).read_text().replace('verification/git-private-path-fix-2026-11-03','verification/app-updates-2026-11-03')
    (OUT/name).write_text(text,encoding='utf-8')
text=(PREVIOUS/'archive-results.py').read_text()
text=text.replace("verification/git-remote-delivery-2026-11-03","verification/git-private-path-fix-2026-11-03")
text=text.replace('==1149','==1173').replace('==1109','==1149').replace('==40','==24')
text=text.replace("target=json.loads((ROOT/'verification/web-sidebar-preflight-2026-11-03/related-results.json').read_text())","target=json.loads((OUT/'related-results.json').read_text())")
(OUT/'archive-results.py').write_text(text,encoding='utf-8')
print(json.dumps(dict(related_tests=len(cases),frozen_inputs=len(keys),old_apks=len(list((ROOT/'dist').glob('*.apk'))))))
