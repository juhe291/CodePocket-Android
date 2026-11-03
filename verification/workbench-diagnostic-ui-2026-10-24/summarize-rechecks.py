from pathlib import Path
import json,xml.etree.ElementTree as ET
phase=Path('verification/workbench-diagnostic-ui-2026-10-24')
probe=json.loads((phase/'workspace-import-probe.json').read_text())
assert probe['unchanged_python_archive'] and probe['cases']==40
assert all(x['exit_code']==0 and x['stdout']=='42\n' for x in probe['all_cases'])
summary={
'first_attempt':json.loads((phase/'apk-inspection-first.json').read_text()),
'first_failing_test':'test_terminal_runner.TestTerminalPython.test_workspace_imports_still_work',
'first_exit_code':1,
'first_underlying_exception':'not captured by original assertion; unknown',
'independent_apk_bytecode_runs':40,'independent_exit_and_stdout_passed':True,
'recheck':json.loads((phase/'apk-inspection.json').read_text()),
'production_python_changed':False,'status':'not reproduced; retained for later investigation',
'assertion_changed':'Added result/stderr on failure; exit_code=0 and stdout=42 assertions retained'
}
(phase/'import-recheck-summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
results=json.loads((phase/'results.json').read_text())
def skipped(folder):
    return {t.get('name')+'.'+c.get('name') for p in folder.glob('TEST-*.xml') for t in [ET.parse(p).getroot()] for c in t.findall('testcase') if c.find('skipped') is not None}
current=skipped(phase/'final-junit');baseline=skipped(Path('verification/workbench-token-theme-2026-10-24/final-junit'))
assert current<=baseline,(current,baseline)
results.update(skipped_tests=sorted(current),no_new_skips=True,artifact_initial_test_failures=summary['first_attempt']['test_failures'],
    artifact_initial_test_errors=summary['first_attempt']['test_errors'],artifact_current_failures=summary['recheck']['test_failures'],
    artifact_current_errors=summary['recheck']['test_errors'],workspace_import_independent_runs=40,
    workspace_import_probe_passed=True,workspace_import_initial_failure_unresolved=True)
(phase/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:results[k] for k in ('junit','skipped_tests','no_new_skips','artifact_initial_test_failures','artifact_current_failures','workspace_import_initial_failure_unresolved')},ensure_ascii=False,indent=2))
