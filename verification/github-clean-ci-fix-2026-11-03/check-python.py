"""Check an actually absent .tools parent, then retain every production Python test identity."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
sites=[ROOT/'.tools'/name/'site' for name in ('semantic-probe','formatting-probe','git-probe')]
sys.path[:0]=[str(p) for p in sites]+[str(ROOT/'app/src/main/python'),str(ROOT)]
env=dict(os.environ,PYTHONPATH=os.pathsep.join(map(str,sites+[ROOT/'app/src/main/python'])))
tempfile.tempdir=str(ROOT/'.tools/semantics-tmp')
clean=[]
for label,source in [('before',OUT/'probe_https-before.py'),('fixed',ROOT/'verification/git-remote-preflight-2026-11-02/probe_https.py')]:
    with tempfile.TemporaryDirectory(prefix='clean-ci-import-') as temporary:
        root=Path(temporary);target=root/'verification/git-remote-preflight-2026-11-02/probe_https.py'
        target.parent.mkdir(parents=True);target.write_bytes(source.read_bytes())
        assert not (root/'.tools').exists()
        result=subprocess.run([sys.executable,'-B','-c',"import runpy,sys;runpy.run_path(sys.argv[1],run_name='isolated_fixture')",str(target)],env=env,capture_output=True)
        (OUT/('clean-import-'+label+'.log')).write_bytes(result.stdout+result.stderr)
        clean.append(dict(label=label,exit=result.returncode,cache_created=(root/'.tools/git-remote-probe/tls').is_dir()))
        if label=='before':assert result.returncode!=0 and b'FileNotFoundError' in result.stderr
        else:assert result.returncode==0 and clean[-1]['cache_created'],result.stderr.decode(errors='replace')
(OUT/'clean-import-results.json').write_text(json.dumps(clean,indent=2)+'\n',encoding='utf-8')
suite=unittest.defaultTestLoader.discover(str(ROOT/'tests'))
def identities(test):
    if isinstance(test,unittest.TestSuite):
        return [name for child in test for name in identities(child)]
    return [test.id()]
names=identities(suite)
previous=json.loads((ROOT/'verification/git-remote-workbench-2026-11-02/python-results.json').read_text())
assert len(names)==len(previous['identities'])==157 and set(names)==set(previous['identities']),dict(count=len(names),missing=sorted(set(previous['identities'])-set(names)),added=sorted(set(names)-set(previous['identities'])))
with (OUT/'python.log').open('w',encoding='utf-8') as stream:
    result=unittest.TextTestRunner(stream=stream,verbosity=2).run(suite)
sources={str(p.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for base in ('tests','app/src/main/python') for p in (ROOT/base).rglob('*.py')}
sources['verification/git-remote-preflight-2026-11-02/probe_https.py']=hashlib.sha256((ROOT/'verification/git-remote-preflight-2026-11-02/probe_https.py').read_bytes()).hexdigest()
summary=dict(tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),skipped=len(result.skipped),identities=names,source_hashes=sources,android_execution=False)
(OUT/'python-results.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(tests=result.testsRun,failures=summary['failures'],errors=summary['errors'],skipped=summary['skipped'],clean_import=clean)))
sys.exit(0 if result.wasSuccessful() else 1)
