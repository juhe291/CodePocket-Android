"""Production HTTPS transport against real TLS Git, plus pure URL/credential boundaries."""
from pathlib import Path
import ssl
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'app/src/main/python'),str(ROOT/'verification/git-remote-preflight-2026-11-02')]
import probe_https as fixture
try:
    import git_https
except ModuleNotFoundError:
    git_https=None


class GitHttpsPolicyTests(unittest.TestCase):
    def setUp(self):self.assertIsNotNone(git_https,'Production HTTPS Git transport is missing')
    def test_canonical_https_targets_keep_unicode_ports_and_ipv6(self):
        for original,canonical in (
            ('https://GitHub.COM:443/owner/repo.git/','https://github.com/owner/repo.git'),
            ('https://git.example:8443/team/项目.git','https://git.example:8443/team/%E9%A1%B9%E7%9B%AE.git'),
            ('https://[::1]:443/group/repo.git','https://[::1]/group/repo.git')):
            self.assertEqual(canonical,git_https.canonical_url(original))
    def test_unsafe_or_ambiguous_targets_are_rejected_without_echoing_them(self):
        for url in ('http://github.com/a/b','git@github.com:a/b.git','https://user:secret@github.com/a/b',
            'https://@github.com/a/b','https://github.com/a/../b','https://github.com/a/%2e%2e/b',
            'https://github.com/a/%2fb','https://github.com/a/b?token=secret','https://github.com/a/b#secret',
            'https://github.com/a/%oops','https://github.com:0/a/b','https://github.com/a\\b','https://github.com/a/\nb'):
            with self.subTest(url=url),self.assertRaises(git_https.RemoteFailure) as caught:git_https.canonical_url(url)
            self.assertEqual('url_invalid',caught.exception.code);self.assertNotIn('secret',str(caught.exception))
    def test_credentials_are_validated_and_insecure_context_cannot_be_injected(self):
        for name,token in ((None,'secret'),('bad:name','secret'),('name','secret\n'),('name\0','secret')):
            with self.assertRaises(git_https.RemoteFailure) as caught:git_https.GitHttps('https://github.com/a/b',name,token)
            self.assertEqual('credentials',caught.exception.code)
        context=ssl._create_unverified_context()
        with self.assertRaises(git_https.RemoteFailure) as caught:git_https.GitHttps('https://github.com/a/b',_ssl_context=context)
        self.assertEqual('tls_policy',caught.exception.code)


class GitHttpsTransportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):fixture.certificate()
    def setUp(self):
        self.assertIsNotNone(git_https,'Production HTTPS Git transport is missing')
        self.root=Path(tempfile.mkdtemp(prefix='transport-',dir=fixture.CACHE)).resolve()
        self.assertTrue(self.root.is_relative_to(fixture.CACHE.resolve()))
        self.server=fixture.Fixture(self.root);self.transports=[];self.repos=[]
    def tearDown(self):
        for transport in getattr(self,'transports',[]):transport.close()
        for repo in getattr(self,'repos',[]):repo.close()
        if hasattr(self,'server'):self.server.close()
    def transport(self,path='/owner/project.git',authenticated=True,**options):
        options.setdefault('_ssl_context',ssl.create_default_context(cafile=str(fixture.CERT)))
        transport=git_https.GitHttps(self.server.base+path,'fixture' if authenticated else None,
            fixture.TOKEN if authenticated else None,**options)
        self.transports.append(transport);return transport
    def target(self,name='target'):
        path=self.root/name;path.mkdir();fixture.Repo.init(str(path)).close()
        repo=fixture.git_workspace.LocalRepo(str(path));self.repos.append(repo);return repo
    def fetch(self,transport,target):
        with patch('subprocess.Popen',side_effect=AssertionError('Production transport started an external process')):
            return transport.client.fetch(transport.url,target,protocol_version=1)
    def test_authenticated_fetch_uses_real_verified_tls_pack_and_no_credential_url(self):
        transport=self.transport();result=self.fetch(transport,self.target())
        self.assertEqual(self.server.head.encode(),result.refs[b'refs/heads/main'])
        self.assertTrue(all(r['authenticated'] for r in self.server.requests));self.assertNotIn(fixture.TOKEN,transport.url)
        self.assertNotIn(fixture.TOKEN,transport.client.get_url(transport.url));self.assertNotIn(fixture.TOKEN,repr(transport))
        for path in self.root.rglob('*'):
            if path.is_file():self.assertNotIn(fixture.TOKEN.encode(),path.read_bytes(),str(path))
    def test_anonymous_public_fetch_and_private_authentication_error(self):
        result=self.fetch(self.transport('/public/project.git',False),self.target())
        self.assertIn(b'refs/heads/main',result.refs)
        with self.assertRaises(git_https.RemoteFailure) as caught:self.fetch(self.transport(authenticated=False),self.target('denied'))
        self.assertEqual('authentication',caught.exception.code)
    def test_default_tls_rejects_untrusted_fixture_certificate(self):
        transport=git_https.GitHttps(self.server.base+'/public/project.git');self.transports.append(transport)
        with self.assertRaises(git_https.RemoteFailure) as caught:self.fetch(transport,self.target())
        self.assertEqual('tls',caught.exception.code)
    def test_redirect_is_not_followed_and_does_not_rebase_credentials(self):
        transport=self.transport('/redirect.git')
        with self.assertRaises(git_https.RemoteFailure) as caught:self.fetch(transport,self.target())
        self.assertEqual('redirect',caught.exception.code)
        self.assertEqual(['/redirect.git/info/refs'],[r['path'] for r in self.server.requests])
    def test_origin_and_repository_path_change_are_blocked_before_any_request(self):
        transport=self.transport()
        for url in ('https://elsewhere.invalid/a/b.git/info/refs?service=git-upload-pack',self.server.base+'/other/repo.git/info/refs?service=git-upload-pack'):
            with self.assertRaises(git_https.RemoteFailure) as caught:transport.pool.request('GET',url,headers={})
            self.assertEqual('url_scope',caught.exception.code)
        self.assertEqual([],self.server.requests)
    def test_transfer_limit_closes_response_and_preserves_tokens(self):
        transport=self.transport(_budget=git_https.NetworkBudget(max_bytes=32))
        with self.assertRaises(git_https.RemoteFailure) as caught:self.fetch(transport,self.target())
        self.assertEqual('transfer_limit',caught.exception.code);self.assertNotIn(fixture.TOKEN,str(caught.exception))
    def test_cancel_and_expired_deadline_fail_before_connecting(self):
        clock=[0.0];budget=git_https.NetworkBudget(seconds=1,clock=lambda:clock[0]);transport=self.transport(_budget=budget)
        clock[0]=2
        with self.assertRaises(git_https.RemoteFailure) as caught:self.fetch(transport,self.target())
        self.assertEqual('timeout',caught.exception.code)
        transport=self.transport(_budget=git_https.NetworkBudget(cancelled=lambda:True))
        with self.assertRaises(git_https.RemoteFailure) as caught:self.fetch(transport,self.target('cancelled'))
        self.assertEqual('cancelled',caught.exception.code);self.assertEqual([],self.server.requests)
    def test_repeated_fetch_releases_the_single_bounded_connection(self):
        transport=self.transport(_budget=git_https.NetworkBudget(seconds=5));target=self.target()
        for _ in range(3):self.assertIn(b'refs/heads/main',self.fetch(transport,target).refs)
        self.assertGreaterEqual(len(self.server.requests),4)
    def test_real_single_ref_pack_upload_updates_only_the_fixture(self):
        transport=self.transport();target=self.target();result=self.fetch(transport,target);old=result.refs[b'refs/heads/main']
        target.refs[b'refs/heads/main']=old;target.refs.set_symbolic_ref(b'HEAD',b'refs/heads/main')
        new=fixture.commit(Path(target.path),'print(2)\n').encode()
        def update(refs):
            self.assertEqual(old,refs[b'refs/heads/main']);self.assertTrue(fixture.can_fast_forward(target,old,new))
            return {b'refs/heads/main':new}
        with patch('subprocess.Popen',side_effect=AssertionError('Production transport used an external command')):
            result=transport.client.send_pack(transport.url,update,target.generate_pack_data)
        self.assertFalse(any(result.ref_status.values()));self.assertTrue(transport.pool.write_started)
        self.assertEqual(new,self.server.remote.refs[b'refs/heads/main'])


if __name__=='__main__':unittest.main(verbosity=2)
