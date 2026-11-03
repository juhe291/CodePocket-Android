import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
broad=json.loads((OUT/'broad-results.json').read_text());targeted=json.loads((OUT/'saf-results.json').read_text())
assert broad['tests']==92 and targeted['tests']==62 and set(targeted['identities'])<=set(broad['identities'])
assert broad['old_local_workbench_identities_retained']==52 and broad['old_targeted_identities_retained']==40
for result in (broad,targeted):
    assert all(not stats[key] for stats in result['stats'].values() for key in ('failures','errors','skipped'))
first=json.loads((OUT/'first-results.json').read_text());assert first['tests']==57
assert sum(stats['failures'] for stats in first['stats'].values())==3
assert set(first['identities'])<=set(broad['identities'])
sources=json.loads((OUT/'broad-sources.json').read_text())
for path,sha in sources.items():assert hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==sha,path
fixture=ROOT/'verification/git-remote-workspace-2026-11-02/native-engine-fixtures.json'
for path,sha in json.loads(fixture.read_text())['source_sha256'].items():assert hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==sha,path
apk=ROOT/'dist/CodePocket-local-git-2026-11-02-debug.apk'
digest=hashlib.sha256()
with apk.open('rb') as stream:
    while chunk:=stream.read(1_000_000):digest.update(chunk)
sha=digest.hexdigest();assert sha=='26ac0bec36f7a0a89108727452d1f96a8576086e5f9ee19d889987a568961075'
result=dict(stage='P3-B original worktree transaction verified on host; App remote entry not connected',
    baseline_commit='5b13a7b',targeted_tests=62,broad_related_tests=92,new_journal_tests=22,
    original_local_workbench_tests_retained=52,previous_remote_gate_tests_retained=18,
    first_tests=57,first_failures=3,first_failure_reason='CRLF bytes were preserved correctly; handwritten LF expectations replaced with exact fixture bytes',
    real_saf_operations=['clone','fetch','fast_forward','configure'],
    python_source_tests_reused=151,python_reexecuted=False,
    worktree_journal=True,legacy_recovery_dispatch_preserved=True,remote_standard_locks=True,
    private_remote_service=False,encrypted_credentials=False,network_ui=False,
    full_app_regression_run=False,lint_run=False,new_apk_built=False,android_execution=False,
    current_apk=apk.relative_to(ROOT).as_posix(),current_apk_sha256=sha,goal_complete=False,
    remaining=['P3-B service, durable push intent/receipt, internal/SAF clone selection, credentials and UI',
        'P3-E web auxiliary panel','P3-C settings and themes','P3-D more language analysis'])
(OUT/'checkpoint.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result))
