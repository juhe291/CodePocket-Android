"""Record the tested foundation without claiming App publication or a new APK."""
import hashlib
import json
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
sys.path[:0]=[str(ROOT),str(ROOT/'.tools/git-probe/site'),str(ROOT/'.tools/formatting-probe/site'),str(ROOT/'.tools/semantic-probe/site')]
def log(path):
    raw=path.read_bytes();return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig',errors='replace')
assert 'Ran 151 tests' in log(OUT/'python-source-all.log') and log(OUT/'python-source-all.log').strip().endswith('OK')
assert 'Ran 25 tests' in log(OUT/'backend-plan-host.log') and log(OUT/'backend-plan-host.log').strip().endswith('OK')
suite=unittest.defaultTestLoader.discover(str(ROOT/'tests'))
assert not unittest.defaultTestLoader.errors,unittest.defaultTestLoader.errors
def walk(test):
    if isinstance(test,unittest.TestSuite):
        for child in test:yield from walk(child)
    else:yield test.id()
ids=sorted(walk(suite));assert len(ids)==151 and len(set(ids))==151
old=json.loads((ROOT/'verification/git-remote-preflight-2026-11-02/python-test-identities.json').read_text())
assert len(old)==126 and set(old)<=set(ids)
new=set(ids)-set(old);assert len(new)==25 and all(name.startswith('test_git_remote.GitRemoteTests.') for name in new)
(OUT/'python-test-identities.json').write_text(json.dumps(ids,indent=2)+'\n',encoding='utf-8')
native=json.loads((OUT/'native-engine-results.json').read_text());assert native['tests']==40
first=json.loads((OUT/'native-first-results.json').read_text());assert first['tests']==39
assert set(first['identities'])<=set(native['identities'])
sources=json.loads((OUT/'native-engine-sources.json').read_text())
for path,sha in sources.items():assert hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==sha,path
fixtures=json.loads((OUT/'native-engine-fixtures.json').read_text())
for path,sha in fixtures['source_sha256'].items():assert hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==sha,path
apk=ROOT/'dist/CodePocket-local-git-2026-11-02-debug.apk'
sha=hashlib.sha256(apk.read_bytes()).hexdigest();assert sha=='26ac0bec36f7a0a89108727452d1f96a8576086e5f9ee19d889987a568961075'
result=dict(stage='P3-B remote engine and publication validation foundation; original publication not connected',
    baseline_commit='5b13a7b',remote_missing_baseline=dict(tests=17,failures=17),
    remote_boundary_first=dict(tests=21,failures=1),remote_tests=25,python_source_tests=151,old_python_identities_retained=126,
    native_targeted_tests=40,new_native_tests=18,old_native_identities_retained=22,
    real_tls_to_native_fixtures=[case['name'] for case in fixtures['cases']],
    current_apk=apk.relative_to(ROOT).as_posix(),current_apk_sha256=sha,new_apk_built=False,android_execution=False,
    production_remote_service=False,encrypted_credential_store=False,original_worktree_transaction=False,network_ui=False,
    goal_complete=False,remaining=['P3-B worktree journal, private service, credentials and UI','P3-E web auxiliary panel','P3-C settings and themes','P3-D more language analysis'])
(OUT/'checkpoint.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,ensure_ascii=True))
