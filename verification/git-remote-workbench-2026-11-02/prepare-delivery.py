import hashlib
import json
from pathlib import Path
import shutil

ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'verification/git-remote-delivery-2026-11-03'
OUT.mkdir(exist_ok=True)
assert not (OUT/'before-apks.json').exists(),'Do not replace the frozen build baseline'
def sha(file):
    with file.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
sources={}
for base in ('app/src','editor-sora/src/main','editor-assist/src','workspace-core/src','tests'):
    for file in sorted((ROOT/base).rglob('*')):
        if file.is_file() and '__pycache__' not in file.parts:sources[file.relative_to(ROOT).as_posix()]=sha(file)
for name in ('app/build.gradle.kts','app/requirements.txt','app/cpp-runtime-lock.json','scripts/verify_apk.py','scripts/verify_semantics_apk.py',
    'scripts/verify_cpp_apk.py','scripts/verify_git_remote_apk.py','scripts/verify_formatting_apk.py'):
    sources[name]=sha(ROOT/name)
(OUT/'build-inputs.json').write_text(json.dumps(sources,indent=2)+'\n',encoding='utf-8')
apks=[dict(name=file.name,bytes=file.stat().st_size,sha256=sha(file)) for file in sorted((ROOT/'dist').glob('*.apk'))]
assert len(apks)==100
(OUT/'before-apks.json').write_text(json.dumps(apks,indent=2)+'\n',encoding='utf-8')
for name in ('full-build.ps1','verify-artifact.ps1','verify-package.ps1'):
    text=(ROOT/'verification/git-workspace-2026-11-02'/name).read_text(encoding='utf-8-sig')
    text=text.replace('verification/git-workspace-2026-11-02','verification/git-remote-delivery-2026-11-03')
    if name=='verify-package.ps1':
        text=text.replace('dist/CodePocket-formatting-2026-10-30-debug.apk','dist/CodePocket-local-git-2026-11-02-debug.apk')
        text=text.replace('--allow-git-update','--allow-git-remote-update').replace('scripts/verify_git_apk.py','scripts/verify_git_remote_apk.py')
    (OUT/name).write_text(text,encoding='utf-8')
print(json.dumps(dict(build_inputs=len(sources),old_apks=len(apks),output=str(OUT))))
