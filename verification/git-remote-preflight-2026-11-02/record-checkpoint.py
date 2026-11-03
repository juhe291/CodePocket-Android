"""Bind the HTTPS checkpoint to actual logs, exact Python sources and the older APK."""
import hashlib
import json
from pathlib import Path
import re

ROOT=Path(__file__).resolve().parents[2];PHASE=Path(__file__).resolve().parent
def read(path):
    raw=path.read_bytes()
    return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig')
def identities(path):return re.findall(r'^test_\w+ \(([^)]+)\) \.\.\.',read(path),re.M)
baseline=read(PHASE/'production-transport-baseline.log');target=read(PHASE/'production-transport-first.log')
full=read(PHASE/'python-source-all.log')
assert 'Ran 12 tests' in baseline and 'FAILED (failures=12)' in baseline
assert 'Ran 12 tests' in target and target.rstrip().endswith('OK')
assert 'Ran 126 tests' in full and full.rstrip().endswith('OK')
old=identities(ROOT/'verification/git-workspace-2026-11-02/python-source-all.log');new=identities(PHASE/'python-source-all.log')
assert len(old)==len(set(old))==114 and len(new)==len(set(new))==126,(len(old),len(new))
assert set(old)<=set(new) and len(set(new)-set(old))==12
assert set(identities(PHASE/'production-transport-baseline.log'))==set(identities(PHASE/'production-transport-first.log'))
probe=json.loads((PHASE/'results.json').read_text());assert probe['tests']==8 and probe['failures']==probe['errors']==0
public=json.loads((PHASE/'public-ca-result.json').read_text());assert public['http_status']==200 and not public['credentials_sent']
apk=ROOT/'dist/CodePocket-local-git-2026-11-02-debug.apk'
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
assert sha(apk)==public['runtime_ca']['apk_sha256']=='26ac0bec36f7a0a89108727452d1f96a8576086e5f9ee19d889987a568961075'
inputs={}
for folder in ('app/src/main/python','tests'):
    for path in sorted((ROOT/folder).rglob('*.py')):
        if '__pycache__' not in path.parts:inputs[path.relative_to(ROOT).as_posix()]=sha(path)
for name in ('probe_https.py','probe_public_ca.py'):
    path=PHASE/name;inputs[path.relative_to(ROOT).as_posix()]=sha(path)
(PHASE/'source-hashes.json').write_text(json.dumps(inputs,indent=2)+'\n',encoding='utf-8')
result=dict(stage='P3-B production HTTPS transport foundation; not App integration',baseline_commit='5b13a7b',
    missing_baseline=dict(tests=12,failures=12),production_transport_tests=12,python_source_tests=126,
    old_python_test_identities_retained=114,fixture_tests=8,actual_public_git_tls_read=True,
    current_apk=str(apk.relative_to(ROOT)),current_apk_sha256=sha(apk),new_apk_built=False,
    android_execution=False,production_remote_service=False,encrypted_credential_store=False,
    clone_and_worktree_transactions=False,source_control_network_ui=False,goal_complete=False,
    remaining=['P3-B remote operations, internal/SAF publication, credentials and UI','P3-E web auxiliary panel restored from original requirements','P3-C settings and themes','P3-D more language analysis'])
(PHASE/'checkpoint.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
(PHASE/'python-test-identities.json').write_text(json.dumps(new,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,indent=2))
