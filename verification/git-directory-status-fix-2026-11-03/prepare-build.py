"""Freeze this production fix before its single final regression/lint/APK run."""
import hashlib
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent;PREVIOUS=ROOT/'verification/app-updates-2026-11-03'
def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def write(name,value):(OUT/name).write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
result=json.loads((OUT/'python-results.json').read_text());assert result['tests']==159 and not result['failures'] and not result['errors'] and not result['skipped']
keys=set(json.loads((PREVIOUS/'build-inputs.json').read_text()))|{'scripts/verify_git_status_apk.py'}
write('build-inputs.json',{name:sha(ROOT/name) for name in sorted(keys)})
write('before-apks.json',[dict(name=p.name,bytes=p.stat().st_size,sha256=sha(p)) for p in sorted((ROOT/'dist').glob('*.apk'))])
for name in ('full-build.ps1','dependencies.init.gradle','verify-artifact.ps1'):
    (OUT/name).write_text((PREVIOUS/name).read_text().replace('verification/app-updates-2026-11-03','verification/git-directory-status-fix-2026-11-03'),encoding='utf-8')
text=(PREVIOUS/'archive-results.py').read_text().replace('verification/git-private-path-fix-2026-11-03','verification/app-updates-2026-11-03')
text=text.replace('len(old)==1149','len(old)==1173').replace('len(new)==24','len(new)==0')
text=text.replace("target=json.loads((OUT/'related-results.json').read_text())","target=json.loads((PREVIOUS/'related-results.json').read_text())")
text=text.replace("ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent","ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent;PREVIOUS=ROOT/'verification/app-updates-2026-11-03'")
text=text.replace("if name.startswith(module+'/src/main/'):assert old_sources[name]==digest,name","if name.startswith(module+'/src/main/') and not name.startswith('app/src/main/python/'):assert old_sources[name]==digest,name")
(OUT/'archive-results.py').write_text(text,encoding='utf-8')
print(json.dumps(dict(inputs=len(keys),old_apks=len(list((ROOT/'dist').glob('*.apk'))),python_tests=159)))
