"""Check the verified handoff without rebuilding or changing the APK."""
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PHASE = Path(__file__).resolve().parent
result = json.loads((PHASE / 'results.json').read_text())
apk = ROOT / result['apk']
with apk.open('rb') as stream: assert hashlib.file_digest(stream, 'sha256').hexdigest() == result['sha256']
paths = set(subprocess.check_output(['git', 'diff', 'c5277cc', '--name-only', '--', '.github/workflows/build-apk.yml', 'app', 'workspace-core', 'editor-assist', 'scripts'], cwd=ROOT, text=True).splitlines())
paths.update(str(file.relative_to(ROOT)).replace('\\', '/') for pattern in (
    'app/src/main/java/com/codepocket/ide/Cpp*.kt', 'app/src/main/java/com/codepocket/ide/NativeCppProcess.kt',
    'app/src/main/assets/cpp-licenses/*', 'app/src/main/jni/*', 'app/cpp-runtime-lock.json',
    'app/src/test/java/com/codepocket/ide/Cpp*Test.kt', 'workspace-core/src/main/java/com/codepocket/ide/CppSources.kt',
    'scripts/cpp_runtime_elf.py', 'scripts/prepare_cpp_runtime.py', 'scripts/verify_cpp_apk.py') for file in ROOT.glob(pattern))
saved = PHASE / 'source-hashes.json'
hashes = {name: hashlib.sha256((ROOT / name).read_bytes().replace(b'\r\n', b'\n')).hexdigest() for name in sorted(paths)}
if saved.exists(): assert json.loads(saved.read_text()) == hashes, 'Verified source changed'
else: saved.write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf-8')
links = 0
for name in ('docs/P2_CPP_SEMANTICS_2026-10-27.md', 'docs/cpp-semantic-preview/README.md'):
    file = ROOT / name
    for target in re.findall(r'\]\(([^)]+)\)', file.read_text()):
        if not target.startswith(('http:', 'https:', '#')):
            assert (file.parent / target.split('#')[0]).exists(), target
            links += 1
workflow = subprocess.check_output(['git', 'diff', 'c5277cc', '--numstat', '--', '.github/workflows/build-apk.yml'], cwd=ROOT, text=True).strip()
assert workflow == '3\t0\t.github/workflows/build-apk.yml'
assert 'full_goal_complete' in result and not result['full_goal_complete']
report = dict(source_files=len(hashes), source_hashes_match=True, relative_links_checked=links,
    apk_sha256_matches=True, existing_ci_change='one preparation step, explicitly authorized; no remote run',
    android_execution=False, full_goal_complete=False)
(PHASE / 'delivery-check.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
