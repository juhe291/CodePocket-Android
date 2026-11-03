import hashlib
import json
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
native=json.loads((OUT/'broad-results.json').read_text());assert native['tests']==120
assert native['old_related_test_identities_retained']==92
assert all(not counts[k] for counts in native['stats'].values() for k in ('failures','errors','skipped'))
for label,expected in (('first',36),('cross',37)):
    record=json.loads((OUT/(label+'-results.json')).read_text());assert record['tests']==expected
    assert set(record['identities'])<=set(native['identities'])
sources=json.loads((OUT/'broad-sources.json').read_text())
for path,sha in sources.items():assert hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==sha,path
def log(path):
    raw=path.read_bytes();return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig',errors='replace')
assert 'Ran 155 tests' in log(OUT/'python-report-retained.log') and log(OUT/'python-report-retained.log').strip().endswith('OK')
assert 'FAILED (failures=2)' in log(OUT/'report-before-baseline.log')
sys.path[:0]=[str(ROOT),str(ROOT/'.tools/git-probe/site'),str(ROOT/'.tools/formatting-probe/site'),str(ROOT/'.tools/semantic-probe/site')]
suite=unittest.defaultTestLoader.discover(str(ROOT/'tests'));assert not unittest.defaultTestLoader.errors
def walk(test):
    if isinstance(test,unittest.TestSuite):
        for child in test:yield from walk(child)
    else:yield test.id()
ids=sorted(walk(suite));assert len(ids)==155 and len(set(ids))==155
old=json.loads((ROOT/'verification/git-remote-workspace-2026-11-02/python-test-identities.json').read_text())
assert len(old)==151 and set(old)<=set(ids)
(OUT/'python-test-identities.json').write_text(json.dumps(ids,indent=2)+'\n',encoding='utf-8')
apk=ROOT/'dist/CodePocket-local-git-2026-11-02-debug.apk';digest=hashlib.sha256()
with apk.open('rb') as stream:
    while chunk:=stream.read(1_000_000):digest.update(chunk)
sha=digest.hexdigest();assert sha=='26ac0bec36f7a0a89108727452d1f96a8576086e5f9ee19d889987a568961075'
result=dict(stage='P3-B private service, credential vault and durable push receipts; App UI not connected',
    baseline_commit='5b13a7b',related_tests=120,new_service_security_tests=22,previous_related_identities_retained=92,old_ipc_tests_retained=6,
    first_targeted_tests=36,cross_targeted_tests=37,cross_first_failures=1,cross_process_filesystem_lock=True,
    python_source_tests=155,old_python_identities_retained=151,report_regression_baseline_failures=2,
    manifest_private_service=True,host_rpc=True,host_aes_gcm=True,device_keystore_execution=False,android_chaquopy_execution=False,
    network_ui=False,full_app_regression_run=False,lint_run=False,new_apk_built=False,current_apk=apk.relative_to(ROOT).as_posix(),current_apk_sha256=sha,
    goal_complete=False,remaining=['P3-B remote workbench, clone destination and unsaved protection, credential/UI integration and batch delivery',
        'P3-E web auxiliary panel','P3-C settings and themes','P3-D more language analysis'])
(OUT/'checkpoint.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
