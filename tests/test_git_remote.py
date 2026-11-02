"""Real remote operations on private snapshots; publication to Android/SAF is tested separately."""
import hashlib
import json
from pathlib import Path
import ssl
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path[:0]=[str(ROOT/'app/src/main/python'),str(ROOT/'verification/git-remote-preflight-2026-11-02')]
import probe_https as fixture
import git_https
from dulwich.objects import Blob, Tree, Commit
try:
    import git_remote
except ModuleNotFoundError:
    git_remote=None


class GitRemoteTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):fixture.certificate()
    def setUp(self):
        self.assertIsNotNone(git_remote,'Production remote Git operations are missing')
        self.root=Path(tempfile.mkdtemp(prefix='remote-',dir=fixture.CACHE)).resolve()
        self.assertTrue(self.root.is_relative_to(fixture.CACHE.resolve()))
        self.server=fixture.Fixture(self.root);self.transports=[]
        self.url=self.server.base+'/owner/project.git'
    def tearDown(self):
        for transport in getattr(self,'transports',[]):transport.close()
        if hasattr(self,'server'):self.server.close()
    def folder(self,name='snapshot'):
        path=self.root/name;path.mkdir();return path
    def state(self,root,mode=True):return fixture.local_operation(root,'status',modeAvailable=mode)
    def files(self,root):return {p.relative_to(root).as_posix():p.read_bytes() for p in root.rglob('*') if p.is_file()}
    def transport(self,url,username,token,budget):
        transport=git_https.GitHttps(url,username,token,_ssl_context=ssl.create_default_context(cafile=str(fixture.CERT)),_budget=budget)
        self.transports.append(transport);return transport
    def call(self,root,action,cancelled=lambda:False,factory=None,**fields):
        mode=fields.get('modeAvailable',True)
        fields.setdefault('expected',self.state(root,mode)['snapshot'])
        fields.setdefault('url',self.url);fields.setdefault('branch','main')
        fields.setdefault('username','fixture');fields.setdefault('token',fixture.TOKEN)
        with patch('subprocess.Popen',side_effect=AssertionError('Remote backend started an external process')):
            return json.loads(git_remote.run(str(root),json.dumps(dict(action=action,**fields)),cancelled,
                transport_factory=factory or self.transport))
    def ok(self,root,action,**fields):
        result=self.call(root,action,**fields);self.assertTrue(result.get('ok'),result);return result
    def clone(self,root=None,**fields):
        root=root or self.folder();return root,self.ok(root,'clone',**fields)
    def advance(self,files):
        for name,data in files.items():
            path=self.server.source/name;path.parent.mkdir(parents=True,exist_ok=True)
            if data is None:path.unlink()
            else:path.write_bytes(data)
        state=self.state(self.server.source)
        state=fixture.local_operation(self.server.source,'stage',expected=state['snapshot'],paths=list(files))
        head=fixture.local_operation(self.server.source,'commit',expected=state['snapshot'],name='TLS Fixture',
            email='fixture@example.invalid',message='Advance remote fixture')['head']
        self.server.copy_objects();self.server.remote.refs[b'refs/heads/main']=head.encode();return head
    def ff(self,root,remote_head,**fields):
        fields.setdefault('expected_head',self.state(root)['head']);fields.setdefault('expected_remote',remote_head)
        return self.call(root,'fast_forward',**fields)
    def malicious_commit(self,name,mode,data):
        remote=self.server.remote;old=remote[remote.refs[b'refs/heads/main']]
        tree=Tree.from_string(remote[old.tree].as_raw_string());blob=Blob.from_string(data);remote.object_store.add_object(blob)
        if name==b'.GIT':
            child=Tree();child.add(b'config',0o100644,blob.id);remote.object_store.add_object(child);tree.add(name,0o40000,child.id)
        else:tree.add(name,mode,blob.id)
        remote.object_store.add_object(tree);commit=Commit();commit.tree=tree.id;commit.parents=[old.id]
        commit.author=commit.committer=b'TLS Fixture <fixture@example.invalid>';commit.author_time=commit.commit_time=1700000000
        commit.author_timezone=commit.commit_timezone=0;commit.message=b'Unsafe fixture checkout\n'
        remote.object_store.add_object(commit);remote.refs[b'refs/heads/main']=commit.id
        return commit.id.decode()
    def test_configure_preserves_other_config_and_never_connects_or_writes_credentials(self):
        root=self.folder();fixture.commit(root,'print(1)\n');path=root/'.git/config'
        path.write_bytes(path.read_bytes()+b'\n[custom]\n\tvalue = keep-me\n')
        result=self.ok(root,'configure');self.assertEqual(self.url,result['remote']['url'])
        self.assertEqual([],self.server.requests);self.assertIn(b'keep-me',path.read_bytes());self.assertNotIn(fixture.TOKEN.encode(),path.read_bytes())
    def test_clone_transfers_real_tree_index_refs_and_standard_git_reads_everything(self):
        root,result=self.clone();self.assertEqual('print(1)\n',(root/'main.py').read_text())
        self.assertEqual(self.server.head,result['head']);self.assertEqual([],result['unstaged'])
        config=(root/'.git/config').read_text();self.assertIn(self.url,config);self.assertNotIn(fixture.TOKEN,config)
        for data in self.files(root).values():self.assertNotIn(fixture.TOKEN.encode(),data)
        check=subprocess.run(['git','-C',str(root),'fsck','--full'],capture_output=True)
        self.assertEqual(0,check.returncode,check.stderr)
    def test_clone_nonempty_or_stale_snapshot_is_rejected_before_network(self):
        root=self.folder();(root/'keep.txt').write_text('keep');before=self.files(root)
        self.assertEqual('target_not_empty',self.call(root,'clone')['error']);self.assertEqual(before,self.files(root))
        empty=self.folder('stale');snapshot=self.state(empty)['snapshot'];(empty/'changed.txt').write_text('new')
        self.assertEqual('stale',self.call(empty,'clone',expected=snapshot)['error']);self.assertEqual([],self.server.requests)
    def test_empty_remote_clones_as_an_unborn_branch(self):
        del self.server.remote.refs[b'refs/heads/main'];root,result=self.clone()
        self.assertIsNone(result['head']);self.assertEqual('main',result['branch']);self.assertEqual(['.git'],[p.name for p in root.iterdir()])
    def test_fetch_only_updates_objects_and_tracking_ref_not_source_head_or_index(self):
        root,_=self.clone();before=self.files(root);new=self.advance({'main.py':b'print(2)\n'})
        result=self.ok(root,'fetch');self.assertEqual(self.server.head,result['head']);self.assertEqual(new,result['remote']['head'])
        for path in ('main.py','.git/HEAD','.git/index'):self.assertEqual(before[path],self.files(root)[path])
        self.assertEqual(new,(root/'.git/refs/remotes/origin/main').read_text().strip())
    def test_fast_forward_updates_add_modify_delete_and_preserves_unrelated_binary(self):
        self.advance({'docs/old.txt':b'remove\n','keep.bin':bytes(range(64))});root,_=self.clone();old=self.state(root)['head']
        new=self.advance({'docs/old.txt':None,'main.py':b'print(2)\n','docs/新文件.md':'# 真实更新 😀\n'.encode()})
        self.ok(root,'fetch');result=self.ff(root,new,expected_head=old);self.assertTrue(result.get('ok'),result)
        self.assertEqual(new,self.state(root)['head']);self.assertFalse((root/'docs/old.txt').exists())
        self.assertEqual('# 真实更新 😀\n',(root/'docs/新文件.md').read_text(encoding='utf-8'));self.assertEqual(bytes(range(64)),(root/'keep.bin').read_bytes())
        self.assertEqual([],self.state(root)['unstaged']);self.assertTrue(all(not v for v in self.state(root)['staged'].values()))
    def test_fast_forward_refuses_dirty_worktree_without_changing_metadata(self):
        root,_=self.clone();new=self.advance({'main.py':b'print(2)\n'});self.ok(root,'fetch')
        (root/'main.py').write_text('unsaved-on-disk\n');before=self.files(root)
        self.assertEqual('dirty',self.ff(root,new)['error']);self.assertEqual(before,self.files(root))
    def test_fast_forward_refuses_staged_changes_without_consuming_them(self):
        root,_=self.clone();new=self.advance({'main.py':b'print(2)\n'});self.ok(root,'fetch');(root/'main.py').write_text('staged\n')
        state=self.state(root);fixture.local_operation(root,'stage',expected=state['snapshot'],paths=['main.py']);before=self.files(root)
        self.assertEqual('dirty',self.ff(root,new)['error']);self.assertEqual(before,self.files(root))
    def test_fast_forward_refuses_divergence_without_reset_or_merge(self):
        root,_=self.clone();new=self.advance({'main.py':b'print("remote")\n'});self.ok(root,'fetch');fixture.commit(root,'print("local")\n')
        before=self.files(root);self.assertEqual('non_fast_forward',self.ff(root,new)['error']);self.assertEqual(before,self.files(root))
    def test_untracked_incoming_collision_is_preserved(self):
        root,_=self.clone();(root/'incoming.txt').write_text('keep-local');new=self.advance({'incoming.txt':b'remote'})
        self.ok(root,'fetch');before=self.files(root);self.assertEqual('untracked_collision',self.ff(root,new)['error'])
        self.assertEqual(before,self.files(root));self.assertEqual('keep-local',(root/'incoming.txt').read_text())
    def test_unsafe_symlink_and_git_metadata_alias_never_reach_checkout(self):
        sentinel=self.root/'sentinel';sentinel.write_text('keep-outside')
        for i,(name,mode,data) in enumerate(((b'escape',0o120000,b'../../sentinel'),(b'.GIT',0o40000,b'evil = yes'))):
            self.malicious_commit(name,mode,data);root=self.folder('unsafe-'+str(i));result=self.call(root,'clone')
            self.assertFalse(result.get('ok'),result);self.assertIn(result['error'],('unsupported_tree','path'))
            self.assertEqual('keep-outside',sentinel.read_text());self.assertFalse((root/'escape').exists())
            self.assertNotIn(b'evil = yes',(root/'.git/config').read_bytes())
    def test_push_is_a_single_non_force_update_and_leaves_local_bytes_unchanged(self):
        root,_=self.clone();old=self.server.head;new=fixture.commit(root,'print(2)\n');before=self.files(root)
        result=self.ok(root,'push',expected_head=new,expected_remote=old)
        self.assertEqual('pushed',result['network_outcome']);self.assertEqual(new,self.server.remote.refs[b'refs/heads/main'].decode())
        self.assertEqual(before,self.files(root))
    def test_push_refuses_stale_remote_before_uploading_any_pack(self):
        root,_=self.clone();new=fixture.commit(root,'print(2)\n');before=self.files(root);start=len(self.server.requests)
        result=self.call(root,'push',expected_head=new,expected_remote='1'*40)
        self.assertEqual('remote_changed',result['error']);self.assertEqual(before,self.files(root))
        self.assertTrue(all(r['method']=='GET' for r in self.server.requests[start:]));self.assertEqual(self.server.head,self.server.remote.refs[b'refs/heads/main'].decode())
    def test_push_missing_remote_history_and_divergence_never_force(self):
        root,_=self.clone();local=fixture.commit(root,'print("ours")\n');remote=self.advance({'main.py':b'print("theirs")\n'})
        before=self.files(root);result=self.call(root,'push',expected_head=local,expected_remote=remote)
        self.assertEqual('needs_fetch',result['error']);self.assertEqual(before,self.files(root));self.ok(root,'fetch')
        before=self.files(root);result=self.call(root,'push',expected_head=local,expected_remote=remote)
        self.assertEqual('non_fast_forward',result['error']);self.assertEqual(before,self.files(root))
        self.assertEqual(remote,self.server.remote.refs[b'refs/heads/main'].decode())
    def test_authentication_failure_is_sanitized_and_cancel_precedes_clone(self):
        root=self.folder();result=self.call(root,'clone',token='fixture-wrong-secret')
        self.assertEqual('authentication',result['error']);self.assertNotIn('fixture-wrong-secret',json.dumps(result))
        empty=self.folder('cancelled');count=len(self.server.requests);result=self.call(empty,'clone',cancelled=lambda:True)
        self.assertEqual('cancelled',result['error']);self.assertEqual([],list(empty.iterdir()));self.assertEqual(count,len(self.server.requests))
    def test_accepted_push_followed_by_transport_failure_reports_unknown_remote_outcome(self):
        root,_=self.clone();old=self.server.head;new=fixture.commit(root,'print(2)\n');before=self.files(root)
        def factory(*args):
            transport=self.transport(*args);send=transport.client.send_pack
            def fail_after_accept(*a,**kw):send(*a,**kw);raise git_https.RemoteFailure('timeout')
            transport.client.send_pack=fail_after_accept;return transport
        result=self.call(root,'push',factory=factory,expected_head=new,expected_remote=old)
        self.assertEqual('push_unknown',result['error']);self.assertEqual(new,result['push_head'])
        self.assertEqual(new,self.server.remote.refs[b'refs/heads/main'].decode());self.assertEqual(before,self.files(root))
    def test_provider_mode_checkout_preserves_executable_git_index_mode(self):
        self.malicious_commit(b'script.sh',0o100755,b'echo ok\n');root,result=self.clone(modeAvailable=False)
        self.assertTrue(result['ok']);repo=fixture.git_workspace.LocalRepo(str(root),False)
        try:self.assertEqual(0o100755,repo.open_index()[b'script.sh'].mode)
        finally:repo.close()
    def test_explicit_configure_can_replace_an_existing_ssh_origin(self):
        root=self.folder();fixture.commit(root,'print(1)\n');config=fixture.ConfigFile.from_path(str(root/'.git/config'))
        config.set((b'remote',b'origin'),b'url',b'git@github.com:owner/old.git');config.write_to_path(str(root/'.git/config'))
        result=self.ok(root,'configure');self.assertEqual(self.url,result['remote']['url'])
        self.assertNotIn(b'git@github.com',(root/'.git/config').read_bytes());self.assertEqual([],self.server.requests)
    def test_fast_forward_handles_file_directory_transitions_without_removing_unrelated_files(self):
        self.advance({'old/file.txt':b'old-dir','switch':b'old-file'});root,_=self.clone()
        (root/'keep-local.txt').write_text('untracked-kept')
        # Build the real incoming tree through the fixture's production local backend.
        (self.server.source/'old/file.txt').unlink();(self.server.source/'old').rmdir();(self.server.source/'old').write_bytes(b'new-file')
        (self.server.source/'switch').unlink();(self.server.source/'switch').mkdir();(self.server.source/'switch/new.txt').write_bytes(b'new-dir')
        state=self.state(self.server.source);paths=['old/file.txt','old','switch','switch/new.txt']
        state=fixture.local_operation(self.server.source,'stage',expected=state['snapshot'],paths=paths)
        new=fixture.local_operation(self.server.source,'commit',expected=state['snapshot'],name='TLS Fixture',email='fixture@example.invalid',message='Transitions')['head']
        self.server.copy_objects();self.server.remote.refs[b'refs/heads/main']=new.encode();self.ok(root,'fetch')
        result=self.ff(root,new);self.assertTrue(result.get('ok'),result)
        self.assertEqual(b'new-file',(root/'old').read_bytes());self.assertEqual(b'new-dir',(root/'switch/new.txt').read_bytes())
        self.assertEqual('untracked-kept',(root/'keep-local.txt').read_text())
    def test_incoming_attributes_cannot_enable_external_filters(self):
        self.malicious_commit(b'.gitattributes',0o100644,b'*.txt filter=external\n');root=self.folder()
        result=self.call(root,'clone');self.assertEqual('configuration',result['error']);self.assertFalse((root/'.gitattributes').exists())
    def test_hidden_index_flags_cannot_hide_changes_from_fast_forward(self):
        root,_=self.clone();new=self.advance({'main.py':b'print(2)\n'});self.ok(root,'fetch')
        with fixture.git_workspace.LocalRepo(str(root)) as repo:
            index=repo.open_index();entry=index[b'main.py'];entry.flags|=0x8000;index[b'main.py']=entry;index.write()
        before=self.files(root);result=self.ff(root,new)
        self.assertEqual('unsupported_index',result['error']);self.assertEqual(before,self.files(root))

    def planned(self,root,action,plan=None,**fields):
        fields=dict(action=action,expected=self.state(root,False)['snapshot'],modeAvailable=False,
            url=self.url,branch='main',username='fixture',token=fixture.TOKEN,**fields)
        return json.loads(git_remote.run(str(root),json.dumps(fields),plan_output=str(plan or root.parent/'result.plan'),
            transport_factory=self.transport))
    def test_publication_plan_is_outside_snapshot_and_lists_every_binary_file_and_directory(self):
        root=self.folder();before=self.state(root,False)['snapshot'];result=self.planned(root,'clone')
        self.assertTrue(result.get('ok'),result);plan=root.parent/'result.plan';data=json.loads(plan.read_text())
        self.assertEqual('result.plan',result['plan']);self.assertEqual(1,data['version']);self.assertEqual(str(root),data['root'])
        self.assertEqual(before,data['before']);self.assertEqual(result['snapshot'],data['after'])
        self.assertEqual('clone',data['action']);self.assertEqual(self.url,data['url']);self.assertEqual('main',data['remote_branch'])
        self.assertIsNone(data['before_head']);self.assertEqual(self.server.head,data['after_head'])
        self.assertEqual(self.files(root).keys(),data['files'].keys())
        for name,value in self.files(root).items():
            self.assertEqual(dict(sha256=hashlib.sha256(value).hexdigest(),size=len(value)),data['files'][name])
        self.assertEqual({''}|{p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_dir()},set(data['directories']))
        self.assertNotIn(fixture.TOKEN,plan.read_text());self.assertEqual(result['snapshot'],self.state(root,False)['snapshot'])
    def test_publication_plan_cannot_overwrite_an_existing_result_or_an_outside_file(self):
        root=self.folder();fixture.commit(root,'print(1)\n');before=self.files(root)
        existing=root.parent/'result.plan';existing.write_text('keep-existing')
        result=self.planned(root,'configure');self.assertEqual('plan_path',result['error'])
        self.assertEqual('keep-existing',existing.read_text());self.assertEqual(before,self.files(root))
        outside=self.root/'outside.plan';outside.write_text('keep-outside')
        result=self.planned(root,'configure',plan=outside);self.assertEqual('plan_path',result['error'])
        self.assertEqual('keep-outside',outside.read_text());self.assertEqual(before,self.files(root))
    def test_failed_remote_operation_never_creates_a_publication_plan(self):
        root=self.folder();(root/'keep.txt').write_text('keep');result=self.planned(root,'clone')
        self.assertEqual('target_not_empty',result['error']);self.assertFalse((root.parent/'result.plan').exists())
        self.assertEqual('keep',(root/'keep.txt').read_text())
    def test_plan_destination_is_never_taken_from_request_json(self):
        root=self.folder();fixture.commit(root,'print(1)\n');before=self.files(root)
        result=self.call(root,'configure',plan_output=str(self.root/'untrusted.plan'))
        self.assertEqual('input',result['error']);self.assertEqual(before,self.files(root));self.assertFalse((self.root/'untrusted.plan').exists())
    def test_android_atomic_cancellation_is_checked_before_any_clone_or_connection(self):
        class Cancellation:
            def __init__(self):self.calls=0
            def get(self):self.calls+=1;return True
        flag=Cancellation();root=self.folder();request=dict(action='clone',url=self.url,branch='main',expected=self.state(root)['snapshot'])
        result=json.loads(git_remote.run_android(str(root),json.dumps(request),flag,str(root.parent/'result.plan')))
        self.assertEqual('cancelled',result['error']);self.assertGreater(flag.calls,0)
        self.assertEqual([],list(root.iterdir()));self.assertEqual([],self.server.requests)
    def test_android_entry_generates_the_owned_plan_for_a_real_non_network_configuration(self):
        class Cancellation:
            def get(self):return False
        root=self.folder();fixture.commit(root,'print(1)\n');request=dict(action='configure',url=self.url,branch='main',expected=self.state(root)['snapshot'])
        result=json.loads(git_remote.run_android(str(root),json.dumps(request),Cancellation(),str(root.parent/'result.plan')))
        self.assertTrue(result.get('ok'),result);self.assertEqual('result.plan',result['plan'])
        self.assertEqual('configure',json.loads((root.parent/'result.plan').read_text())['action']);self.assertEqual([],self.server.requests)
    def test_snapshot_reads_non_repository_and_ssh_origin_without_network_or_mutation(self):
        empty=self.folder('empty-snapshot')
        repo=self.folder('ssh-snapshot');fixture.commit(repo,'print(1)\n')
        config=repo/'.git/config';config.write_bytes(config.read_bytes()+b'\n[remote "origin"]\n\turl = git@example.invalid:owner/project.git\n')
        for root in (empty,repo):
            before=self.files(root)
            def forbidden(*args):raise AssertionError('A local snapshot opened a transport')
            result=json.loads(git_remote.run(str(root),json.dumps(dict(action='snapshot',modeAvailable=False)),transport_factory=forbidden))
            self.assertTrue(result.get('ok'),result);self.assertEqual(root==repo,result['repository'])
            self.assertEqual(self.state(root,False)['snapshot'],result['snapshot'])
            self.assertEqual(dict(url=None),result['remote']);self.assertEqual(before,self.files(root))
        self.assertEqual([],self.server.requests)
    def test_clone_filemode_uses_private_posix_capability_and_cannot_be_spoofed_by_request(self):
        root=self.folder();fixture.commit(root,'print(1)\n')
        for available,platform,wanted in ((True,'posix',True),(False,'posix',False),(True,'nt',False),(False,'nt',False)):
            git_remote.prepare_clone_filemode(root,available,platform)
            self.assertEqual(wanted,git_remote.raw_config(root).get_boolean((b'core',),b'filemode'))
        before=self.files(root)
        result=self.call(root,'snapshot',platform='posix')
        self.assertEqual('input',result['error']);self.assertEqual(before,self.files(root));self.assertEqual([],self.server.requests)
    def test_confirmed_push_does_not_depend_on_a_second_local_status_read(self):
        root,_=self.clone();old=self.server.head;new=fixture.commit(root,'print(2)\n');read=git_remote.state;calls=[]
        def once(*args):
            calls.append(args)
            if len(calls)>1:raise OSError('local status failed after push')
            return read(*args)
        with patch('git_remote.state',side_effect=once):
            result=self.call(root,'push',expected_head=new,expected_remote=old)
        self.assertTrue(result.get('ok'),result);self.assertEqual('pushed',result['network_outcome'])
        self.assertEqual(new,self.server.remote.refs[b'refs/heads/main'].decode());self.assertEqual(1,len(calls))
    def test_confirmed_server_report_survives_a_transport_cleanup_error(self):
        root,_=self.clone();old=self.server.head;new=fixture.commit(root,'print(2)\n')
        def factory(*args):
            transport=self.transport(*args);close=transport.close;armed=[True]
            def fail_close_once():
                close()
                if armed:
                    armed.pop();raise git_https.RemoteFailure('network')
            transport.close=fail_close_once;return transport
        result=self.call(root,'push',factory=factory,expected_head=new,expected_remote=old)
        self.assertTrue(result.get('ok'),result);self.assertEqual('pushed',result['network_outcome'])
        self.assertEqual(new,self.server.remote.refs[b'refs/heads/main'].decode())


if __name__=='__main__':unittest.main(verbosity=2)
