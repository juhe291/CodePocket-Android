"""Own disposable TLS Git server, controlled only by the host integration tests."""
import base64
import json
from pathlib import Path
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT/'verification/git-remote-preflight-2026-11-02'),str(ROOT/'app/src/main/python'),str(ROOT/'.tools/git-probe/site')]
import probe_https as fixture
import git_workspace

fixture.certificate();root=Path(tempfile.mkdtemp(prefix='workbench-',dir=fixture.CACHE)).resolve()
assert root.is_relative_to(fixture.CACHE.resolve())
server=fixture.Fixture(root)
def advance(changes):
    for name,encoded in changes.items():
        git_workspace.path_name(name);path=server.source/name
        assert path.resolve().is_relative_to(server.source.resolve())
        if encoded is None:path.unlink()
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(base64.b64decode(encoded,validate=True))
    state=fixture.local_operation(server.source,'status',modeAvailable=False)
    state=fixture.local_operation(server.source,'stage',paths=list(changes),expected=state['snapshot'],modeAvailable=False)
    result=fixture.local_operation(server.source,'commit',expected=state['snapshot'],name='Workbench fixture',email='fixture@example.invalid',
        message='Workbench remote update',modeAvailable=False)
    server.copy_objects();server.remote.refs[b'refs/heads/main']=result['head'].encode();return result['head']
try:
    advance({'helper.py':base64.b64encode(b'value = 1\n').decode(),'remove.bin':base64.b64encode(bytes([0,255,2])).decode()})
    print(json.dumps(dict(url=server.base+'/owner/project.git',head=server.remote.refs[b'refs/heads/main'].decode())),flush=True)
    for line in sys.stdin:
        command=json.loads(line);action=command['action']
        if action=='stop':break
        if action=='advance':result=dict(head=advance(command['changes']))
        elif action=='head':result=dict(head=server.remote.refs[b'refs/heads/main'].decode())
        elif action=='requests':result=dict(count=len(server.requests))
        else:raise ValueError('unknown fixture command')
        print(json.dumps(result),flush=True)
finally:server.close()
