"""Retain the previous 159 source cases and add nine downloader contract cases."""
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
names=identities(suite);old=json.loads((ROOT/'verification/git-directory-status-fix-2026-11-03/python-results.json').read_text())['identities']
new=set(names)-set(old)
assert len(names)==len(set(names))==168 and len(old)==159 and set(old)<set(names) and len(new)==9
assert all(name.startswith('test_cpp_runtime_download.CppRuntimeDownloadTests.') for name in new)
with (OUT/'python.log').open('w',encoding='utf-8') as stream:result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
summary=dict(tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skipped=len(result.skipped),previous_identities_retained=len(old),identities=names,
    source_hashes={p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for base in ('tests','app/src/main/python') for p in (ROOT/base).rglob('*.py')},android_execution=False)
for name in ('scripts/prepare_cpp_runtime.py','.github/workflows/build-apk.yml'):
    summary['source_hashes'][name]=hashlib.sha256((ROOT/name).read_bytes()).hexdigest()
(OUT/'python-results.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in summary.items() if isinstance(v,(int,bool))}))
sys.exit(0 if result.wasSuccessful() and not result.skipped else 1)
