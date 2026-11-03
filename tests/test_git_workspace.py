"""Production Git backend checks against real pinned Dulwich and standard host Git."""
import importlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'app/src/main/python'))
try:
    import git_workspace
except ModuleNotFoundError:
    git_workspace = None


class GitWorkspaceTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(git_workspace, 'Production Git backend has not been implemented')
        cache = ROOT / '.tools/git-probe'
        cache.mkdir(parents=True, exist_ok=True)
        self.root = Path(tempfile.mkdtemp(prefix='backend-', dir=cache)).resolve()
        self.assertTrue(self.root.is_relative_to(cache.resolve()))
        self.config = cache / 'empty-config'; self.config.touch(exist_ok=True)
        self.hooks = cache / 'empty-hooks'; self.hooks.mkdir(exist_ok=True)
        self.env = dict(os.environ, GIT_CONFIG_GLOBAL=str(self.config), GIT_CONFIG_SYSTEM=str(self.config),
                        GIT_CONFIG_NOSYSTEM='1')

    def call(self, action, **fields):
        with patch('subprocess.Popen', side_effect=AssertionError('Backend executed an external command')):
            return json.loads(git_workspace.run(str(self.root), json.dumps(dict(action=action, **fields))))

    def ok(self, action, **fields):
        result = self.call(action, **fields)
        self.assertTrue(result.get('ok'), result)
        return result

    def write(self, path, data):
        file = self.root / path
        self.assertTrue(file.resolve().is_relative_to(self.root))
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(data)

    def git(self, *args):
        result = subprocess.run(['git', '-c', 'core.autocrlf=false', '-c', 'core.quotepath=false',
            '-c', 'commit.gpgsign=false', '-c', f'core.hooksPath={self.hooks}', *args], cwd=self.root,
            env=self.env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
        self.assertEqual(0, result.returncode, result.stderr.decode('utf-8', 'replace'))
        return result.stdout

    def token(self):
        return self.ok('status')['snapshot']

    def init(self):
        return self.ok('init', expected=self.token())

    def stage(self, *paths):
        return self.ok('stage', paths=list(paths), expected=self.token())

    def commit(self, message='first commit'):
        return self.ok('commit', expected=self.token(), name='CodePocket 测试', email='probe@example.invalid', message=message)

    def first(self):
        self.init(); self.write('main.py', b'print(1)\n'); self.stage('main.py'); return self.commit()

    def test_absent_repository_is_distinct_from_a_clean_repository_and_initializes_explicitly(self):
        status = self.ok('status')
        self.assertFalse(status['repository']); self.assertFalse((self.root / '.git').exists())
        result = self.init(); self.assertTrue(result['repository']); self.assertEqual('main', result['branch'])
        self.assertEqual('dulwich 1.2.20', result['engine']); self.assertIsNone(result['head'])
        self.assertEqual([], result['untracked'])

    def test_real_stage_commit_history_and_standard_git_interoperate(self):
        commit = self.first()
        self.assertEqual(commit['head'].encode(), self.git('rev-parse', 'HEAD').strip())
        self.assertEqual(b'print(1)\n', self.git('show', 'HEAD:main.py'))
        history = self.ok('history')['commits']
        self.assertEqual(commit['head'], history[0]['id']); self.assertEqual('first commit', history[0]['message'])
        self.git('fsck', '--full', '--strict')

    def test_staging_and_unstaging_do_not_save_or_rewrite_working_files(self):
        self.first(); self.write('main.py', b'print(2)\r\n')
        result = self.stage('main.py'); self.assertEqual(['main.py'], result['staged']['modify'])
        result = self.ok('unstage', paths=['main.py'], expected=self.token())
        self.assertEqual([], result['staged']['modify']); self.assertEqual(['main.py'], result['unstaged'])
        self.assertEqual(b'print(2)\r\n', (self.root / 'main.py').read_bytes())
        self.assertEqual(b'', self.git('diff', '--cached'))

    def test_staged_and_working_diff_remain_separate(self):
        self.first(); self.write('main.py', b'print(2)\n'); self.stage('main.py'); self.write('main.py', b'print(3)\n')
        staged = self.ok('diff', path='main.py', staged=True)['text']
        working = self.ok('diff', path='main.py', staged=False)['text']
        self.assertIn('+print(2)', staged); self.assertNotIn('+print(3)', staged)
        self.assertIn('-print(2)', working); self.assertIn('+print(3)', working)

    def test_stale_snapshot_prevents_index_or_head_mutation(self):
        self.first(); self.write('main.py', b'print(2)\n'); token = self.token()
        index = (self.root / '.git/index').read_bytes(); head = self.git('rev-parse', 'HEAD')
        self.write('main.py', b'print(3)\n')
        result = self.call('stage', paths=['main.py'], expected=token)
        self.assertEqual('stale', result['error']); self.assertEqual(index, (self.root / '.git/index').read_bytes())
        self.assertEqual(head, self.git('rev-parse', 'HEAD')); self.assertEqual(b'print(3)\n', (self.root / 'main.py').read_bytes())

    def test_missing_precondition_and_unknown_operation_cannot_write(self):
        self.init(); self.write('main.py', b'print(1)\n')
        self.assertEqual('precondition', self.call('stage', paths=['main.py'])['error'])
        self.assertEqual('operation', self.call('push', expected=self.token())['error'])
        self.assertFalse((self.root / '.git/index').exists())

    def test_parent_absolute_and_git_metadata_paths_are_rejected(self):
        self.first(); before = self.token()
        for path in ['../outside.py', '/absolute.py', 'dir/../../outside.py', '.git/config', 'dir/.git/index', 'C:\\file.py']:
            self.assertEqual('path', self.call('stage', paths=[path], expected=before)['error'], path)
        self.assertEqual(before, self.token())

    def test_gitdir_files_and_alternate_objects_cannot_escape_the_snapshot(self):
        self.write('.git', b'gitdir: ../outside\n')
        self.assertEqual('repository_layout', self.call('status')['error'])
        (self.root / '.git').unlink(); self.init()
        self.write('.git/objects/info/alternates', b'../../../outside\n')
        self.assertEqual('repository_layout', self.call('status')['error'])

    def test_repository_hooks_are_disabled_and_external_configuration_is_rejected(self):
        self.first(); self.write('.git/hooks/post-commit', b'#!/bin/sh\nexit 99\n')
        self.write('main.py', b'print(2)\n'); self.stage('main.py'); self.commit('hooks disabled')
        self.assertEqual(b'2\n', self.git('rev-list', '--count', 'HEAD'))
        config = self.root / '.git/config'; original = config.read_bytes()
        config.write_bytes(original + b'\n[include]\n path = ../outside\n')
        self.assertEqual('configuration', self.call('status')['error'])
        self.assertEqual(original + b'\n[include]\n path = ../outside\n', config.read_bytes())

    def test_custom_filter_is_not_silently_staged_as_plain_bytes(self):
        self.first(); self.write('.gitattributes', b'*.py filter=lfs\n')
        self.assertEqual('configuration', self.call('status')['error'])
        self.assertEqual(b'print(1)\n', self.git('show', 'HEAD:main.py'))

    def test_ignored_files_are_hidden_and_cannot_be_accidentally_staged(self):
        self.init(); self.write('.gitignore', b'*.tmp\n'); self.write('a.tmp', b'private')
        result = self.ok('status'); self.assertEqual(['.gitignore'], result['untracked'])
        self.assertEqual('not_changed', self.call('stage', paths=['a.tmp'], expected=result['snapshot'])['error'])

    def test_binary_unicode_and_crlf_are_preserved_and_untracked_diff_is_real(self):
        self.init(); data = '# 中文 😀\r\nprint(42)\r\n'.encode()
        self.write('目录/源.py', data); self.write('data.bin', bytes(range(256)))
        diff = self.ok('diff', path='目录/源.py', staged=False)['text']; self.assertIn('+print(42)', diff)
        self.stage('目录/源.py', 'data.bin'); self.commit('UTF8')
        self.assertEqual(data, self.git('show', 'HEAD:目录/源.py'))
        self.assertEqual(bytes(range(256)), self.git('show', 'HEAD:data.bin'))

    def test_existing_packed_history_is_supported_by_the_production_backend(self):
        self.first(); self.write('main.py', b'print(2)\n'); self.stage('main.py'); self.commit('second')
        self.git('repack', '-ad'); self.git('pack-refs', '--all')
        self.assertEqual(2, len(self.ok('history')['commits']))
        self.write('main.py', b'print(3)\n'); self.stage('main.py'); self.commit('third')
        self.assertEqual(b'3\n', self.git('rev-list', '--count', 'HEAD')); self.git('fsck', '--full', '--strict')

    def test_index_lock_returns_a_specific_error_and_keeps_original_state(self):
        self.first(); self.write('main.py', b'print(2)\n'); index = (self.root / '.git/index').read_bytes()
        self.write('.git/index.lock', b'foreign lock')
        result = self.call('stage', paths=['main.py'], expected=self.token())
        self.assertEqual('locked', result['error']); self.assertEqual(index, (self.root / '.git/index').read_bytes())
        self.assertEqual(b'foreign lock', (self.root / '.git/index.lock').read_bytes())

    def test_invalid_identity_or_empty_staging_does_not_create_a_commit(self):
        self.first(); head = self.git('rev-parse', 'HEAD')
        result = self.call('commit', expected=self.token(), name='test', email='probe@example.invalid', message='empty')
        self.assertEqual('no_changes', result['error'])
        self.write('main.py', b'print(2)\n'); self.stage('main.py')
        result = self.call('commit', expected=self.token(), name='bad\nname', email='bad', message='message')
        self.assertEqual('identity', result['error']); self.assertEqual(head, self.git('rev-parse', 'HEAD'))

    def test_read_only_status_diff_history_and_details_leave_all_bytes_unchanged(self):
        self.first(); self.write('main.py', b'print(2)\n'); self.stage('main.py')
        def manifest():
            return {p.relative_to(self.root).as_posix():p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        before = manifest(); head = self.ok('status')['head']
        self.ok('diff', path='main.py', staged=True); self.ok('diff', path='main.py', staged=False)
        self.ok('history'); self.assertEqual('first commit', self.ok('show_commit', id=head)['message'])
        self.assertEqual(before, manifest())

    def test_filemode_false_preserves_an_existing_executable_index_entry_when_staging(self):
        self.init(); self.git('config', 'core.filemode', 'false')
        self.assertEqual(b'false\n', self.git('config', '--get', 'core.filemode'))
        self.write('run.sh', b'#!/bin/sh\necho one\n'); self.stage('run.sh')
        self.git('update-index', '--chmod=+x', 'run.sh'); self.commit('executable')
        self.write('run.sh', b'#!/bin/sh\necho two\n'); self.stage('run.sh'); self.commit('new body')
        self.assertTrue(self.git('ls-tree', 'HEAD', 'run.sh').startswith(b'100755 '))

    def test_provider_without_mode_support_keeps_index_modes_without_rewriting_git_config(self):
        self.init(); self.write('run.sh', b'#!/bin/sh\necho one\n'); self.stage('run.sh')
        self.git('update-index', '--chmod=+x', 'run.sh'); self.commit('executable')
        config = self.root / '.git/config'
        original = config.read_bytes().replace(b'filemode = false', b'filemode = true')
        config.write_bytes(original)
        self.assertEqual([], self.ok('status', modeAvailable=False)['unstaged'])
        self.write('run.sh', b'#!/bin/sh\necho two\n')
        self.ok('stage', paths=['run.sh'], expected=self.ok('status', modeAvailable=False)['snapshot'], modeAvailable=False)
        self.ok('commit', expected=self.ok('status', modeAvailable=False)['snapshot'], modeAvailable=False,
                name='CodePocket', email='probe@example.invalid', message='provider byte change')
        self.assertTrue(self.git('ls-tree', 'HEAD', 'run.sh').startswith(b'100755 '))
        self.assertEqual(original, config.read_bytes())

    def test_history_preview_reports_truncation_and_commit_details_keep_full_unicode_message(self):
        self.init(); self.write('main.py', b'print(1)\n'); self.stage('main.py')
        message = '中文 😀 commit\n' + 'detail line\n' * 80
        head = self.commit(message)['head']; history = self.ok('history')['commits']
        self.assertTrue(history[0]['truncated']); self.assertEqual(message[:400], history[0]['message'])
        self.assertEqual(message, self.ok('show_commit', id=head)['message'])


if __name__ == '__main__':
    unittest.main(verbosity=2)
