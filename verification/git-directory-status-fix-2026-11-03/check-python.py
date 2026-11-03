"""Full Python regressions including a forced real POSIX ENOTDIR path on Windows."""
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
sys.path[:0]=[str(ROOT/'.tools'/name/'site') for name in ('semantic-probe','formatting-probe','git-probe')]+[str(ROOT/'app/src/main/python'),str(ROOT)]
tempfile.tempdir=str(ROOT/'.tools/semantics-tmp')
suite=unittest.defaultTestLoader.discover(str(ROOT/'tests'))
def identities(test):
    if isinstance(test,unittest.TestSuite):return [name for child in test for name in identities(child)]
    return [test.id()]
names=identities(suite);previous=json.loads((ROOT/'verification/github-clean-ci-fix-2026-11-03/python-results.json').read_text())
added={'test_git_workspace.GitWorkspaceTests.test_directory_replaced_by_file_keeps_real_status_and_can_stage_on_posix',
       'test_git_workspace.GitWorkspaceTests.test_unexplained_not_a_directory_error_does_not_become_clean_status'}
assert len(names)==159 and set(names)==set(previous['identities'])|added
with (OUT/'python.log').open('w',encoding='utf-8') as stream:
    result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
sources={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for base in ('tests','app/src/main/python') for p in (ROOT/base).rglob('*.py')}
summary=dict(tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skipped=len(result.skipped),identities=names,source_hashes=sources,android_execution=False)
(OUT/'python-results.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(tests=result.testsRun,failures=summary['failures'],errors=summary['errors'],skipped=summary['skipped'])))
sys.exit(0 if result.wasSuccessful() else 1)
