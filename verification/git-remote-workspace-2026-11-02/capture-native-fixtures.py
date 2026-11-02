"""Export actual pinned-engine TLS results for the independent Kotlin publication gate.

Only disposable repositories below .tools/git-remote-probe are touched. No fixture
token or TLS key is exported. Native tests relocate the root field explicitly.
"""
import base64
import hashlib
import json
from pathlib import Path
import shutil
import ssl
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT/'app/src/main/python'),str(ROOT/'verification/git-remote-preflight-2026-11-02'),str(ROOT/'.tools/git-probe/site')]
import probe_https as fixture
import git_https
import git_remote


def tree(root):
    return dict(files={p.relative_to(root).as_posix():base64.b64encode(p.read_bytes()).decode('ascii')
        for p in sorted(root.rglob('*')) if p.is_file()},
        directories=['']+[p.relative_to(root).as_posix() for p in sorted(root.rglob('*')) if p.is_dir()])


def main():
    fixture.certificate();root=Path(tempfile.mkdtemp(prefix='native-plan-',dir=fixture.CACHE)).resolve()
    assert root.is_relative_to(fixture.CACHE.resolve())
    server=fixture.Fixture(root);cases=[]
    def state(path):return fixture.local_operation(path,'status',modeAvailable=False)
    def advance(changes):
        for name,value in changes.items():
            path=server.source/name;path.parent.mkdir(parents=True,exist_ok=True)
            if value is None:path.unlink()
            else:path.write_bytes(value)
        current=state(server.source)
        current=fixture.local_operation(server.source,'stage',expected=current['snapshot'],paths=list(changes),modeAvailable=False)
        head=fixture.local_operation(server.source,'commit',expected=current['snapshot'],name='Native fixture',email='fixture@example.invalid',
            message='Native publication fixture',modeAvailable=False)['head']
        server.copy_objects();server.remote.refs[b'refs/heads/main']=head.encode();return head
    def transport(url,username,token,budget):
        return git_https.GitHttps(url,username,token,_ssl_context=ssl.create_default_context(cafile=str(fixture.CERT)),_budget=budget)
    def operation(name,action,source=None,**extra):
        job=root/name;job.mkdir();work=job/'work'
        if source is None:work.mkdir()
        else:shutil.copytree(source,work)
        before=tree(work);request=dict(action=action,expected=state(work)['snapshot'],url=server.base+'/owner/project.git',branch='main',modeAvailable=False)
        request.update(extra)
        wire=request|dict(username='fixture',token=fixture.TOKEN)
        result=json.loads(git_remote.run(str(work),json.dumps(wire),plan_output=str(job/'result.plan'),transport_factory=transport))
        assert result.get('ok'),result
        plan=json.loads((job/'result.plan').read_text())
        case=dict(name=name,request=request,response=result,plan=plan,before=before,after=tree(work));cases.append(case)
        return work,result
    try:
        advance({'remove.bin':bytes([0,255,2]),'old/file.txt':b'directory-before','switch':b'file-before'})
        clone,_=operation('clone','clone')
        new=advance({'main.py':b'print(2)\n','remove.bin':None,'added/data.bin':bytes([255,0,4])})
        # Include both directions of file/directory transitions in a real commit.
        (server.source/'old/file.txt').unlink();(server.source/'old').rmdir();(server.source/'old').write_bytes(b'file-after')
        (server.source/'switch').unlink();(server.source/'switch').mkdir();(server.source/'switch/new.txt').write_bytes(b'directory-after')
        current=state(server.source)
        current=fixture.local_operation(server.source,'stage',expected=current['snapshot'],paths=['old/file.txt','old','switch','switch/new.txt'],modeAvailable=False)
        new=fixture.local_operation(server.source,'commit',expected=current['snapshot'],name='Native fixture',email='fixture@example.invalid',
            message='File-directory transitions',modeAvailable=False)['head']
        server.copy_objects();server.remote.refs[b'refs/heads/main']=new.encode()
        fetched,_=operation('fetch','fetch',clone)
        updated,_=operation('fast_forward','fast_forward',fetched,expected_head=state(fetched)['head'],expected_remote=new)
        operation('configure','configure',updated,url='https://github.com/fixture/other.git')
    finally:server.close()
    result=dict(schema=1,cases=cases,source_sha256={str(p.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest()
        for p in (ROOT/'app/src/main/python/git_remote.py',ROOT/'app/src/main/python/git_https.py',ROOT/'app/src/main/python/git_workspace.py',Path(__file__))})
    text=json.dumps(result,ensure_ascii=True,indent=2);assert fixture.TOKEN not in text
    output=Path(__file__).with_name('native-engine-fixtures.json')
    with output.open('x',encoding='utf-8') as stream:stream.write(text+'\n')
    print(json.dumps(dict(cases=[c['name'] for c in cases],bytes=output.stat().st_size,sha256=hashlib.sha256(output.read_bytes()).hexdigest())))


if __name__=='__main__':main()
