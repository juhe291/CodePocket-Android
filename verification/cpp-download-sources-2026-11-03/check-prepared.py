"""An offline source override must regenerate the exact original packaged runtime inputs."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
CACHE=ROOT/'.tools/cpp-runtime'
def sha(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def snapshot():
    result={p.relative_to(CACHE).as_posix():sha(p) for base in ('jni','assets') for p in (CACHE/base).rglob('*') if p.is_file()}
    result['prepared.json']=sha(CACHE/'prepared.json')
    return result
before=snapshot();assert before
(OUT/'prepared-before.json').write_text(json.dumps(before,indent=2)+'\n',encoding='utf-8')
result=subprocess.run([sys.executable,'-B','scripts/prepare_cpp_runtime.py','--package-repository','https://packages.termux.dev/apt/termux-main'],cwd=ROOT,capture_output=True)
(OUT/'offline-prepare.log').write_bytes(result.stdout+result.stderr)
assert result.returncode==0,result.stderr.decode(errors='replace')
after=snapshot();assert after==before,{name for name in set(before)|set(after) if before.get(name)!=after.get(name)}
apk=ROOT/'dist/CodePocket-git-directory-status-fix-2026-11-03-debug.apk'
assert sha(apk)=='36c76a0b0a38909ee4d8c8b06267ab2af229d1a8b6607ec4506f125571d4e8d1'
summary=dict(prepared_files=len(before),byte_identical=True,network_enabled=False,new_apk_built=False,apk_sha256=sha(apk),files=after)
(OUT/'prepared-results.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in summary.items() if k!='files'}))
