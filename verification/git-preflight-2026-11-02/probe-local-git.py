"""Real pure-Python Git feasibility checks. No Android or production integration claim."""
from contextlib import contextmanager
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
PHASE = Path(__file__).resolve().parent
CACHE = ROOT / '.tools/git-probe'
sys.path.insert(0, str(CACHE / 'site'))
import dulwich
from dulwich import porcelain
from dulwich.config import ConfigFile, StackedConfig
from dulwich.file import FileLocked
from dulwich.repo import Repo


def safe_config():
    config = ConfigFile()
    for key, value in ((b'repositoryformatversion', b'0'), (b'bare', b'false'),
                       (b'filemode', b'false'), (b'autocrlf', b'false')):
        config.set((b'core',), key, value)
    return config


class ProbeRepo(Repo):
    """Probe only: no inherited config, filters, global identity or shell hooks.

    This fixed SHA-1 fixture policy is not a production repository validator.
    Full config/extension/path/SAF validation remains required for integration.
    """
    def get_config(self):
        return safe_config()

    def get_config_stack(self):
        return StackedConfig([safe_config()])

    def __init__(self, path):
        super().__init__(path)
        self.hooks.clear()


@contextmanager
def no_processes():
    with patch('subprocess.Popen', side_effect=AssertionError('Engine attempted an external process')):
        yield


class LocalGitProbe(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix='local-', dir=CACHE)).resolve()
        assert self.root.is_relative_to(CACHE.resolve())
        self.empty_config = CACHE / 'empty-config'
        self.empty_config.touch(exist_ok=True)
        self.hooks = CACHE / 'empty-hooks'
        self.hooks.mkdir(exist_ok=True)
        self.env = dict(os.environ, GIT_CONFIG_GLOBAL=str(self.empty_config),
                        GIT_CONFIG_SYSTEM=str(self.empty_config), GIT_CONFIG_NOSYSTEM='1')
        with no_processes():
            Repo.init(self.root, default_branch=b'main', config=StackedConfig([safe_config()])).close()

    def git(self, *arguments):
        args = ['git', '-c', 'user.name=CodePocket Probe', '-c', 'user.email=probe@example.invalid',
                '-c', 'core.autocrlf=false', '-c', 'core.filemode=false', '-c', 'core.quotepath=false',
                '-c', 'commit.gpgsign=false', '-c', f'core.hooksPath={self.hooks}', *arguments]
        result = subprocess.run(args, cwd=self.root, env=self.env, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=30)
        self.assertEqual(0, result.returncode, result.stderr.decode('utf-8', 'replace'))
        return result.stdout

    def write(self, name, data):
        path = self.root / name
        self.assertTrue(path.resolve().is_relative_to(self.root))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def commit(self, paths, message=b'probe commit'):
        with no_processes(), ProbeRepo(self.root) as repo:
            repo.get_worktree().stage(paths, config=safe_config())
            return repo.get_worktree().commit(message=message, author=b'Probe <probe@example.invalid>',
                committer=b'Probe <probe@example.invalid>', no_verify=True, sign=False, signoff=False,
                config=safe_config())

    def status(self):
        with no_processes(), ProbeRepo(self.root) as repo:
            return porcelain.status(repo, untracked_files='all', optional_locks=False)

    def test_fixed_pure_wheel_import_and_no_native_extension(self):
        self.assertEqual((1, 2, 20), dulwich.__version__)
        self.assertTrue(Path(dulwich.__file__).resolve().is_relative_to((CACHE / 'site').resolve()))
        extensions = [str(m.__file__) for n, m in sys.modules.items() if n.startswith('dulwich')
                      and getattr(m, '__file__', '').endswith(('.pyd', '.so', '.dll'))]
        self.assertEqual([], extensions)

    def test_initial_commit_is_readable_by_standard_git(self):
        self.write('main.py', b'print(42)\n')
        oid = self.commit(['main.py'])
        self.assertEqual(oid, self.git('rev-parse', 'HEAD').strip())
        self.assertEqual(b'print(42)\n', self.git('show', 'HEAD:main.py'))
        self.assertEqual(b'', self.git('status', '--porcelain'))
        self.git('fsck', '--full', '--strict')

    def test_staged_unstaged_untracked_deleted_and_ignore(self):
        self.write('tracked.py', b'first\n'); self.write('delete.py', b'delete\n')
        self.write('.gitignore', b'*.tmp\n')
        self.commit(['tracked.py', 'delete.py', '.gitignore'])
        self.write('tracked.py', b'second\n'); self.write('new.py', b'new\n'); self.write('ignore.tmp', b'ignored')
        (self.root / 'delete.py').unlink()
        self.write('staged.py', b'staged\n')
        with no_processes(), ProbeRepo(self.root) as repo:
            repo.get_worktree().stage(['staged.py'], config=safe_config())
        status = self.status()
        self.assertEqual([b'staged.py'], status.staged['add'])
        self.assertEqual({b'tracked.py', b'delete.py'}, set(status.unstaged))
        self.assertEqual([b'new.py'], status.untracked)
        self.assertIn(b'?? new.py', self.git('status', '--porcelain'))

    def test_unstaged_and_staged_diffs_come_from_actual_index_and_tree(self):
        self.write('main.cpp', b'int x=1;\n'); self.commit(['main.cpp'])
        self.write('main.cpp', b'int x=2;\n')
        with no_processes(), ProbeRepo(self.root) as repo:
            before = io.BytesIO(); porcelain.diff(repo, outstream=before)
            self.assertIn(b'-int x=1;', before.getvalue()); self.assertIn(b'+int x=2;', before.getvalue())
            repo.get_worktree().stage(['main.cpp'], config=safe_config())
            self.write('main.cpp', b'int x=3;\n')
            staged = io.BytesIO(); porcelain.diff(repo, staged=True, outstream=staged)
            working = io.BytesIO(); porcelain.diff(repo, outstream=working)
        self.assertIn(b'+int x=2;', staged.getvalue()); self.assertNotIn(b'+int x=3;', staged.getvalue())
        self.assertIn(b'-int x=2;', working.getvalue()); self.assertIn(b'+int x=3;', working.getvalue())
        self.assertIn(b'+int x=2;', self.git('diff', '--cached'))

    def test_unstage_and_deletion_commit_preserve_working_text(self):
        self.write('main.py', b'one\n'); self.write('gone.py', b'gone\n')
        self.commit(['main.py', 'gone.py'])
        self.write('main.py', b'two\n'); (self.root / 'gone.py').unlink()
        with no_processes(), ProbeRepo(self.root) as repo:
            repo.get_worktree().stage(['main.py'], config=safe_config())
            repo.get_worktree().unstage(['main.py'], config=safe_config())
        self.assertEqual(b'two\n', (self.root / 'main.py').read_bytes())
        self.assertEqual(b'', self.git('diff', '--cached'))
        self.commit(['gone.py'])
        self.assertEqual(b'main.py\n', self.git('ls-tree', '--name-only', 'HEAD'))
        self.assertEqual(b'two\n', (self.root / 'main.py').read_bytes())
        self.git('fsck', '--full', '--strict')

    def test_unicode_path_crlf_and_binary_blob_bytes_are_exact(self):
        text = '# 中文 😀\r\nprint(42)\r\n'.encode('utf-8')
        self.write('目录/源.py', text); self.write('data.bin', bytes(range(256)))
        self.commit(['目录/源.py', 'data.bin'])
        self.assertEqual(text, self.git('show', 'HEAD:目录/源.py'))
        self.assertEqual(bytes(range(256)), self.git('show', 'HEAD:data.bin'))
        self.assertEqual([], self.status().unstaged)

    def test_standard_git_packed_history_is_read_and_extended(self):
        self.write('main.cpp', b'int x=1;\n'); first = self.commit(['main.cpp'])
        self.write('main.cpp', b'int x=2;\n'); second = self.commit(['main.cpp'])
        self.git('repack', '-ad'); self.git('pack-refs', '--all')
        self.assertTrue(list((self.root / '.git/objects/pack').glob('*.pack')))
        with no_processes(), ProbeRepo(self.root) as repo:
            self.assertEqual([second, first], [entry.commit.id for entry in repo.get_walker()])
        self.write('main.cpp', b'int x=3;\n'); third = self.commit(['main.cpp'])
        self.assertEqual(third, self.git('rev-parse', 'HEAD').strip())
        self.assertEqual(b'3\n', self.git('rev-list', '--count', 'HEAD'))
        self.git('fsck', '--full', '--strict')

    def test_no_verify_alone_still_calls_post_commit_hook(self):
        calls = []
        class Hook:
            def execute(self, *args):
                calls.append('post-commit')
        self.write('main.py', b'print(1)\n')
        with no_processes(), ProbeRepo(self.root) as repo:
            repo.hooks['post-commit'] = Hook()
            repo.get_worktree().stage(['main.py'], config=safe_config())
            repo.get_worktree().commit(message=b'demonstrate API boundary', author=b'Probe <probe@example.invalid>',
                committer=b'Probe <probe@example.invalid>', no_verify=True, sign=False, config=safe_config())
        self.assertEqual(['post-commit'], calls)

    def test_approved_config_does_not_execute_repository_filter_or_hook(self):
        self.write('main.py', b'print(1)\n')
        self.write('.gitattributes', b'*.py filter=probe\n')
        config = self.root / '.git/config'
        original = config.read_bytes()
        config.write_bytes(original + b'\n[filter "probe"]\n clean = must-never-run\n required = true\n[commit]\n gpgsign = true\n')
        self.write('.git/hooks/post-commit', b'#!/bin/sh\nexit 99\n')
        self.commit(['main.py', '.gitattributes'])
        self.assertEqual(b'print(1)\n', self.git('show', 'HEAD:main.py'))
        self.assertEqual([], self.status().unstaged)
        self.assertEqual(original + b'\n[filter "probe"]\n clean = must-never-run\n required = true\n[commit]\n gpgsign = true\n', config.read_bytes())

    def test_index_lock_failure_preserves_index_head_and_working_file(self):
        self.write('main.py', b'one\n'); self.commit(['main.py'])
        index = self.root / '.git/index'; previous_index = index.read_bytes()
        previous_head = self.git('rev-parse', 'HEAD'); self.write('main.py', b'two\n')
        self.write('.git/index.lock', b'existing lock')
        with no_processes(), ProbeRepo(self.root) as repo:
            with self.assertRaises(FileLocked):
                repo.get_worktree().stage(['main.py'], config=safe_config())
        self.assertEqual(previous_index, index.read_bytes())
        self.assertEqual(previous_head, self.git('rev-parse', 'HEAD'))
        self.assertEqual(b'two\n', (self.root / 'main.py').read_bytes())
        self.assertEqual(b'existing lock', (self.root / '.git/index.lock').read_bytes())


if __name__ == '__main__':
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(LocalGitProbe)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    report = dict(tests=result.testsRun, failures=len(result.failures), errors=len(result.errors),
        dulwich='1.2.20', python=sys.version, source_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        candidate_inputs=json.loads((PHASE / 'candidate-inputs.json').read_text()),
        pure_python_engine=True, standard_git_used_only_for_host_comparison=True,
        android_execution=False, production_integrated=False, new_apk_built=False)
    (PHASE / 'local-git-results.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    sys.exit(not result.wasSuccessful())
