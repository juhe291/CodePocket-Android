"""Actual pinned Dulwich over isolated local TLS; this is not App integration."""
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import socketserver
import ssl
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from urllib.parse import urlsplit
from wsgiref.simple_server import WSGIRequestHandler, WSGIServer, make_server
import zipfile

ROOT=Path(__file__).resolve().parents[2]
SITE=ROOT/'.tools/git-probe/site'
sys.path[:0]=[str(SITE),str(ROOT/'app/src/main/python')]
from dulwich.client import Urllib3HttpGitClient, HTTPUnauthorized
from dulwich.config import ConfigFile
from dulwich.errors import GitProtocolError
from dulwich.graph import can_fast_forward
from dulwich.repo import Repo
from dulwich.server import DictBackend
from dulwich.web import make_wsgi_chain
import dulwich
import urllib3
import git_workspace

CACHE=ROOT/'.tools/git-remote-probe'
assert CACHE.resolve().is_relative_to(ROOT.resolve())
CACHE.mkdir(parents=True,exist_ok=True)
TLS=CACHE/'tls';TLS.mkdir(exist_ok=True)
CERT=TLS/'localhost-cert.pem';KEY=TLS/'localhost-key.pem'
TOKEN='fixture-only-pat-never-a-real-account'
AUTH='Basic '+base64.b64encode(('fixture:'+TOKEN).encode()).decode('ascii')


def certificate():
    if CERT.is_file() and KEY.is_file():return
    openssl=shutil.which('openssl')
    if not openssl and os.name=='nt':
        git_path=Path(subprocess.check_output(['git','--exec-path'],text=True).strip())
        openssl=str(git_path.parents[1]/'bin/openssl.exe')
    assert openssl and Path(openssl).is_file(), 'Only an isolated fixture TLS key is generated; openssl is missing'
    result=subprocess.run([openssl,'req','-x509','-newkey','rsa:2048','-nodes','-days','30',
        '-keyout',str(KEY),'-out',str(CERT),'-subj','/CN=localhost',
        '-addext','subjectAltName=DNS:localhost,IP:127.0.0.1'],capture_output=True)
    assert result.returncode==0,result.stderr.decode(errors='replace')


def local_operation(root,action,**fields):
    result=json.loads(git_workspace.run(str(root),json.dumps(dict(action=action,**fields))))
    assert result.get('ok'),result
    return result


def commit(root,text):
    (root/'main.py').write_text(text,encoding='utf-8')
    state=local_operation(root,'status')
    if not state['repository']:state=local_operation(root,'init',expected=state['snapshot'])
    state=local_operation(root,'stage',paths=['main.py'],expected=state['snapshot'])
    return local_operation(root,'commit',expected=state['snapshot'],name='TLS Fixture',
        email='fixture@example.invalid',message='Local fixture commit')['head']


class QuietHandler(WSGIRequestHandler):
    def log_message(self,*args):pass


class ThreadedServer(socketserver.ThreadingMixIn,WSGIServer):
    daemon_threads=True


class Fixture:
    def __init__(self,root):
        self.root=root;self.source=root/'source';self.source.mkdir()
        self.head=commit(self.source,'print(1)\n')
        self.remote_path=root/'upstream.git';self.remote_path.mkdir()
        self.remote=Repo.init_bare(str(self.remote_path))
        self.copy_objects()
        self.remote.refs[b'refs/heads/main']=self.head.encode()
        self.remote.refs.set_symbolic_ref(b'HEAD',b'refs/heads/main')
        application=make_wsgi_chain(DictBackend({'/owner/project.git':self.remote,'/public/project.git':self.remote}))
        self.requests=[];self.redirect_seen=[]
        def app(environ,start):
            path=environ.get('PATH_INFO','');auth=environ.get('HTTP_AUTHORIZATION','')
            self.requests.append(dict(method=environ['REQUEST_METHOD'],path=path,authenticated=auth==AUTH))
            if path.startswith('/redirect.git/'):
                start('307 Temporary Redirect',[('Location',self.base+'/owner/project.git/info/refs?service=git-upload-pack'),('Content-Length','0')]);return [b'']
            if path.startswith('/owner/') and auth!=AUTH:
                start('401 Unauthorized',[('WWW-Authenticate','Basic realm="fixture"'),('Content-Length','0')]);return [b'']
            return application(environ,start)
        self.server=make_server('127.0.0.1',0,app,server_class=ThreadedServer,handler_class=QuietHandler)
        context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.minimum_version=ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(str(CERT),str(KEY));self.server.socket=context.wrap_socket(self.server.socket,server_side=True)
        self.base='https://localhost:'+str(self.server.server_port)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
    def copy_objects(self):
        with git_workspace.LocalRepo(str(self.source)) as source:
            for oid in source.object_store:
                if oid not in self.remote.object_store:self.remote.object_store.add_object(source[oid])
    def close(self):
        self.server.shutdown();self.server.server_close();self.thread.join(3);self.remote.close()


class BoundPool(urllib3.PoolManager):
    """Probe-only transport policy, independent of production code to be added."""
    def __init__(self,origin,ca=CERT,limit=16_000_000,timeout=10):
        self.origin=urlsplit(origin).netloc;self.sent=0;self.received=0;self.limit=limit
        self.deadline=time.monotonic()+timeout;self.responses=[]
        super().__init__(cert_reqs=ssl.CERT_REQUIRED,ca_certs=str(ca),retries=False,
            timeout=urllib3.Timeout(connect=3,read=3,total=timeout),maxsize=1,block=True)
    def request(self,method,url,**kwargs):
        parsed=urlsplit(url)
        assert parsed.scheme=='https' and parsed.netloc==self.origin, 'Origin change rejected before sending credentials'
        assert time.monotonic()<self.deadline
        body=kwargs.get('body')
        if body is not None and not isinstance(body,bytes):
            chunks=[];length=0
            for chunk in body:
                length+=len(chunk);assert length<=self.limit,'Outgoing pack limit';chunks.append(chunk)
            body=b''.join(chunks);kwargs['body']=body
        if body is not None:
            self.sent+=len(body);assert self.sent<=self.limit
            kwargs['headers']['Content-Length']=str(len(body))
        kwargs.update(redirect=False,retries=False,pool_timeout=max(.01,self.deadline-time.monotonic()))
        response=super().request(method,url,**kwargs);self.responses.append(response)
        raw_close=response.close
        def close():
            try:raw_close()
            finally:response.release_conn()
        response.close=close
        raw=response.read
        def read(amount=None,*args,**options):
            assert time.monotonic()<self.deadline
            data=raw(amount,*args,**options);self.received+=len(data)
            assert self.received<=self.limit,'Incoming pack limit'
            return data
        response.read=read
        return response
    def close(self):
        for response in self.responses:response.close()
        self.clear()


class HttpsProbe(unittest.TestCase):
    @classmethod
    def setUpClass(cls):certificate()
    def setUp(self):
        self.root=Path(tempfile.mkdtemp(prefix='case-',dir=CACHE)).resolve()
        assert self.root.is_relative_to(CACHE.resolve())
        self.fixture=Fixture(self.root);self.pools=[];self.repos=[]
    def tearDown(self):
        for pool in self.pools:getattr(pool,'close',pool.clear)()
        for repo in self.repos:repo.close()
        self.fixture.close()
    def client(self,path='/owner/project.git',authenticated=True,**options):
        pool=BoundPool(self.fixture.base,**options);self.pools.append(pool)
        config=ConfigFile()
        client=Urllib3HttpGitClient(self.fixture.base+path,pool_manager=pool,config=config,
            username='fixture' if authenticated else None,password=TOKEN if authenticated else None,dumb=False)
        return client,self.fixture.base+path,pool
    def target(self,name='target'):
        folder=self.root/name;folder.mkdir();Repo.init(str(folder)).close()
        repo=git_workspace.LocalRepo(str(folder));self.repos.append(repo);return repo
    def fetch(self,client,path,target):
        with patch('subprocess.Popen',side_effect=AssertionError('Git transport must not start an external process')):
            return client.fetch(path,target,protocol_version=1)
    def test_01_fetches_real_pack_and_symbolic_head_over_verified_tls(self):
        client,path,pool=self.client();target=self.target();result=self.fetch(client,path,target)
        self.assertEqual(self.fixture.head.encode(),result.refs[b'refs/heads/main'])
        self.assertEqual(b'refs/heads/main',result.symrefs[b'HEAD'])
        self.assertEqual(self.fixture.head.encode(),target[result.refs[b'refs/heads/main']].id)
        self.assertGreater(pool.received,0);self.assertTrue(all(r['authenticated'] for r in self.fixture.requests))
    def test_02_anonymous_public_fetch_and_private_auth_failure(self):
        client,path,_=self.client('/public/project.git',False);result=self.fetch(client,path,self.target())
        self.assertIn(b'refs/heads/main',result.refs)
        client,path,_=self.client(authenticated=False)
        with self.assertRaises(HTTPUnauthorized):self.fetch(client,path,self.target('private-denied'))
    def test_03_untrusted_tls_certificate_is_rejected(self):
        from urllib3.util.ssl_ import create_urllib3_context
        pool=urllib3.PoolManager(ssl_context=create_urllib3_context(),retries=False,timeout=urllib3.Timeout(connect=3,read=3))
        self.pools.append(pool)
        client=Urllib3HttpGitClient(self.fixture.base+'/public/project.git',pool_manager=pool,config=ConfigFile())
        with self.assertRaises(GitProtocolError):self.fetch(client,self.fixture.base+'/public/project.git',self.target())
    def test_04_redirect_is_not_followed_and_no_second_request_is_sent(self):
        client,path,_=self.client('/redirect.git')
        with self.assertRaises(GitProtocolError):self.fetch(client,path,self.target())
        self.assertEqual(['/redirect.git/info/refs'],[r['path'] for r in self.fixture.requests])
    def test_05_other_origin_is_rejected_before_any_http_request(self):
        client,_,pool=self.client();count=len(self.fixture.requests)
        with self.assertRaises(AssertionError):pool.request('GET','https://elsewhere.invalid/repo.git',headers={'Authorization':'never-send'})
        self.assertEqual(count,len(self.fixture.requests))
    def test_06_pushes_one_fast_forward_ref_and_standard_git_reads_result(self):
        client,path,_=self.client();target=self.target();result=self.fetch(client,path,target)
        old=result.refs[b'refs/heads/main'];target.refs[b'refs/heads/main']=old;target.refs.set_symbolic_ref(b'HEAD',b'refs/heads/main')
        new=commit(Path(target.path),'print(2)\n').encode()
        def update(refs):
            self.assertEqual(old,refs[b'refs/heads/main']);self.assertTrue(can_fast_forward(target,old,new))
            return {b'refs/heads/main':new}
        with patch('subprocess.Popen',side_effect=AssertionError('Git push must be pure Python')):
            pushed=client.send_pack(path,update,target.generate_pack_data)
        self.assertEqual(new,self.fixture.remote.refs[b'refs/heads/main']);self.assertFalse(any(pushed.ref_status.values()))
        check=subprocess.run(['git','--git-dir',str(self.fixture.remote_path),'fsck','--full'],capture_output=True)
        self.assertEqual(0,check.returncode,check.stderr)
    def test_07_non_fast_forward_is_refused_without_changing_remote(self):
        client,path,_=self.client();target=self.target();result=self.fetch(client,path,target);old=result.refs[b'refs/heads/main']
        target.refs[b'refs/heads/main']=old;target.refs.set_symbolic_ref(b'HEAD',b'refs/heads/main')
        ours=commit(Path(target.path),'print("ours")\n').encode()
        theirs=commit(self.fixture.source,'print("theirs")\n').encode();self.fixture.copy_objects()
        self.fixture.remote.refs[b'refs/heads/main']=theirs
        def update(refs):
            if refs[b'refs/heads/main']!=old:raise ValueError('remote_changed')
            self.assertTrue(can_fast_forward(target,refs[b'refs/heads/main'],ours));return {b'refs/heads/main':ours}
        with self.assertRaisesRegex(ValueError,'remote_changed'):client.send_pack(path,update,target.generate_pack_data)
        self.assertEqual(theirs,self.fixture.remote.refs[b'refs/heads/main'])
    def test_08_transfer_budget_and_no_credential_written_to_repository(self):
        client,path,_=self.client(limit=32)
        with self.assertRaises(AssertionError):self.fetch(client,path,self.target())
        for path in self.root.rglob('*'):
            if path.is_file():self.assertNotIn(TOKEN.encode(),path.read_bytes(),str(path))


def inspect_runtime_ca():
    apk=ROOT/'dist/CodePocket-local-git-2026-11-02-debug.apk'
    with zipfile.ZipFile(apk) as archive:data=archive.read('assets/chaquopy/cacert.pem')
    path=CACHE/'pinned-runtime-cacert.pem';path.write_bytes(data)
    return dict(apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),bytes=len(data),
        sha256=hashlib.sha256(data).hexdigest(),certificates=data.count(b'BEGIN CERTIFICATE'),path=str(path))


if __name__=='__main__':
    phase=Path(__file__).resolve().parent
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(HttpsProbe))
    report=dict(tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),
        dulwich='.'.join(map(str,dulwich.__version__)),urllib3=urllib3.__version__,runtime_ca=inspect_runtime_ca(),
        local_tls_fixture_only=True,production_integration=False,android_execution=False,real_remote_push=False)
    (phase/'results.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
    sys.exit(not result.wasSuccessful())
