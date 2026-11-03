"""Bounded offline Git operations on an app-owned snapshot, never on a provider directly.

The Android caller owns capture/publication and validates the workspace generation.
This module only changes its private snapshot; network operations are not exposed.
"""
import difflib
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import time

import dulwich
from dulwich import porcelain
from dulwich.config import ConfigFile, StackedConfig
from dulwich.file import FileLocked
from dulwich.repo import Repo

MAX_FILE = 16_000_000
MAX_TOTAL = 128_000_000
MAX_ENTRIES = 10_000
MAX_JSON = 450_000
MAX_DIFF = 200_000
ENGINE = 'dulwich 1.2.20'
WRITES = {'init', 'stage', 'unstage', 'commit'}
OPERATIONS = WRITES | {'status', 'diff', 'history', 'show_commit'}


class Rejected(Exception):
    pass


def require(condition, reason):
    if not condition:
        raise Rejected(reason)


def path_name(value):
    require(isinstance(value, str) and 0 < len(value) <= 512, 'path')
    require(not any(c in value for c in ('\\', '\0', ':', '\r', '\n')) and not value.startswith('/'), 'path')
    parts = value.split('/')
    require(all(p not in ('', '.', '..') and p.casefold() != '.git' for p in parts), 'path')
    value.encode('utf-8', 'strict')
    return value


def fingerprint(root, deadline, mode_available=True):
    """Content identity survives copying to a different private snapshot directory."""
    digest = hashlib.sha256(); count = total = 0

    def visit(folder, depth):
        nonlocal count, total
        require(depth <= 32 and time.monotonic() <= deadline, 'limit')
        for entry in sorted(os.scandir(folder), key=lambda item: item.name):
            require(time.monotonic() <= deadline, 'limit')
            count += 1; require(count <= MAX_ENTRIES, 'limit')
            item = Path(entry.path); relative = item.relative_to(root).as_posix().encode('utf-8', 'strict')
            mode = entry.stat(follow_symlinks=False).st_mode
            require(not entry.is_symlink() and item.resolve() == item, 'path')
            require(stat.S_ISDIR(mode) or stat.S_ISREG(mode), 'path')
            digest.update(len(relative).to_bytes(4, 'big')); digest.update(relative)
            if stat.S_ISDIR(mode):
                digest.update(b'D'); visit(item, depth + 1)
            else:
                size = entry.stat(follow_symlinks=False).st_size
                total += size; require(size <= MAX_FILE and total <= MAX_TOTAL, 'limit')
                digest.update(b'F'); digest.update(size.to_bytes(8, 'big'))
                if mode_available and not relative.startswith(b'.git/'):
                    digest.update(b'X' if mode & 0o111 else b'-')
                with item.open('rb') as stream:
                    while data := stream.read(65536):
                        require(time.monotonic() <= deadline, 'limit'); digest.update(data)
    visit(root, 0)
    return digest.hexdigest()


def initial_config():
    config = ConfigFile()
    for key, value in ((b'repositoryformatversion', b'0'), (b'bare', b'false'),
                       (b'filemode', b'false'), (b'autocrlf', b'false')):
        config.set((b'core',), key, value)
    config.set((b'gc',), b'auto', b'0'); config.set((b'gc',), b'autoPackLimit', b'0')
    config.set((b'maintenance',), b'auto', b'false')
    config.set((b'commit',), b'gpgsign', b'false')
    return config


def configuration(root, mode_available=True):
    control = root / '.git'
    require(control.is_dir() and not control.is_symlink(), 'repository_layout')
    for name in ('commondir', 'objects/info/alternates', 'objects/info/http-alternates'):
        require(not (control / name).exists(), 'repository_layout')
    for name in ('MERGE_HEAD', 'rebase-merge', 'rebase-apply', 'sequencer', 'CHERRY_PICK_HEAD', 'REVERT_HEAD'):
        require(not (control / name).exists(), 'operation_in_progress')
    source = control / 'config'
    require(not source.exists() or source.stat().st_size <= 65536, 'configuration')
    try:
        raw = ConfigFile.from_path(str(source), expand_includes=False) if source.exists() else ConfigFile()
        for section in raw.sections():
            require(section[0].lower() not in (b'include', b'includeif', b'filter'), 'configuration')
        require(not raw.get_boolean((b'core',), b'bare', False), 'repository_layout')
        for key in (b'worktree', b'attributesfile'):
            try:
                raw.get((b'core',), key)
            except KeyError:
                pass
            else:
                raise Rejected('configuration')
        try:
            version = int(raw.get((b'core',), b'repositoryformatversion'))
        except KeyError:
            version = 0
        require(version in (0, 1), 'configuration')
        for key, value in raw.items((b'extensions',)):
            require(key.lower() == b'objectformat' and value.lower() == b'sha1', 'configuration')
        approved = initial_config()
        for key in (b'filemode', b'ignorecase', b'autocrlf', b'eol', b'safecrlf'):
            if key == b'filemode' and not mode_available:
                continue
            try:
                value = raw.get((b'core',), key)
            except KeyError:
                continue
            approved.set((b'core',), key, value)
        # Attribute-driven external filters must not silently turn LFS pointers
        # or other normalized blobs into ordinary working-file bytes.
        for folder, directories, files in os.walk(root):
            directories[:] = [name for name in directories if name != '.git']
            if '.gitattributes' in files:
                data = (Path(folder) / '.gitattributes').read_bytes()
                require(len(data) <= 65536, 'configuration')
                require(not re.search(rb'(?:^|\s)[!-]?(?:filter|working-tree-encoding)(?:=|\s|$)', data), 'configuration')
        return approved
    except (ValueError, TypeError, UnicodeError) as error:
        raise Rejected('configuration') from error


class LocalRepo(Repo):
    def __init__(self, root, mode_available=True):
        self._approved = configuration(Path(root), mode_available)
        super().__init__(root)
        self.hooks.clear()

    def get_config(self):
        return self._approved

    def get_config_stack(self):
        return StackedConfig([self._approved])


def decoded(value):
    return value.decode('utf-8', 'strict') if isinstance(value, bytes) else str(value)


def decoded_path(value):
    return decoded(value).replace(os.sep, '/')


def status(repo):
    current = porcelain.status(repo, untracked_files='all', optional_locks=False)
    try:
        head = repo.head().decode('ascii')
    except KeyError:
        head = None
    chain, _ = repo.refs.follow(b'HEAD')
    reference = chain[-1]
    branch = decoded(reference[len(b'refs/heads/'):]) if reference.startswith(b'refs/heads/') else 'HEAD'
    return dict(repository=True, head=head, branch=branch, detached=branch == 'HEAD',
        staged={kind:sorted(decoded_path(p) for p in current.staged.get(kind, [])) for kind in ('add', 'modify', 'delete')},
        unstaged=sorted(decoded_path(p) for p in current.unstaged), untracked=sorted(decoded_path(p) for p in current.untracked))


class DiffOutput(io.BytesIO):
    def write(self, data):
        require(self.tell() + len(data) <= MAX_DIFF, 'limit')
        return super().write(data)


def diff(repo, request, current):
    path = path_name(request.get('path')); staged = request.get('staged')
    require(isinstance(staged, bool), 'input')
    if not staged and path in current['untracked']:
        data = (Path(repo.path) / path).read_bytes()
        require(len(data) <= MAX_DIFF, 'limit')
        try:
            text = data.decode('utf-8', 'strict')
            require('\0' not in text, 'binary')
        except (UnicodeError, Rejected):
            return dict(text='Binary file: ' + path, binary=True)
        text = ''.join(difflib.unified_diff([], text.splitlines(keepends=True), fromfile='/dev/null', tofile='b/' + path))
        require(len(text.encode('utf-8')) <= MAX_DIFF, 'limit')
        return dict(text=text, binary=False)
    output = DiffOutput(); tree_path = path.encode('utf-8')
    if staged:
        # porcelain.diff(staged=True) commits an intermediate tree into the
        # object store. Compare the requested blobs directly so reads stay read-only.
        from dulwich.objects import Blob
        from dulwich.patch import write_blob_diff
        old_mode = old_blob = new_mode = new_blob = None
        if current['head']:
            try:
                tree = repo[repo.head()].tree
                old_mode, old_sha = repo[tree].lookup_path(repo.object_store.__getitem__, tree_path)
                old_blob = repo[old_sha]
            except KeyError:
                pass
        try:
            entry = repo.open_index(config=repo.get_config())[tree_path]
        except KeyError:
            entry = None
        if entry is not None:
            require(hasattr(entry, 'sha') and hasattr(entry, 'mode'), 'operation_in_progress')
            new_mode = entry.mode; new_blob = repo[entry.sha]
        require((old_blob is None or isinstance(old_blob, Blob)) and
                (new_blob is None or isinstance(new_blob, Blob)), 'configuration')
        if old_mode != new_mode or old_blob != new_blob:
            write_blob_diff(output, (tree_path if old_blob is not None else None, old_mode, old_blob),
                            (tree_path if new_blob is not None else None, new_mode, new_blob))
    else:
        from dulwich.diff import diff_working_tree_to_index
        diff_working_tree_to_index(repo, output, paths=[tree_path], config=repo.get_config())
    return dict(text=output.getvalue().decode('utf-8', 'replace'), binary=False)


def identity(request):
    name = request.get('name'); email = request.get('email'); message = request.get('message')
    require(isinstance(name, str) and 0 < len(name.strip()) <= 128 and not re.search(r'[<>\r\n\0]', name), 'identity')
    require(isinstance(email, str) and len(email) <= 254 and re.fullmatch(r'[^\s<>@\0]+@[^\s<>@\0]+', email), 'identity')
    require(isinstance(message, str) and 0 < len(message.strip()) <= 4000 and '\0' not in message, 'message')
    return (name.strip() + ' <' + email + '>').encode('utf-8', 'strict'), message.encode('utf-8', 'strict')


def operate(root, request, deadline):
    require(isinstance(request, dict) and request.get('action') in OPERATIONS, 'operation')
    mode_available = request.get('modeAvailable', True)
    require(isinstance(mode_available, bool), 'input')
    action = request['action']; before = fingerprint(root, deadline, mode_available)
    if action in WRITES:
        expected = request.get('expected')
        require(isinstance(expected, str) and re.fullmatch(r'[a-f0-9]{64}', expected), 'precondition')
        require(expected == before, 'stale')
    control = root / '.git'
    if not control.exists():
        if action == 'status':
            return dict(repository=False, head=None, branch=None, staged={}, unstaged=[], untracked=[], snapshot=before)
        require(action == 'init', 'not_repository')
        Repo.init(root, default_branch=b'main', config=StackedConfig([initial_config()])).close()
    else:
        require(control.is_dir(), 'repository_layout')
        require(action != 'init', 'already_repository')
    with LocalRepo(str(root), mode_available) as repo:
        current = status(repo)
        extra = {}
        if action in ('stage', 'unstage'):
            paths = request.get('paths')
            require(isinstance(paths, list) and 0 < len(paths) <= 100 and len(set(paths)) == len(paths), 'input')
            paths = [path_name(path) for path in paths]
            permitted = set(sum(current['staged'].values(), []))
            if action == 'stage':
                permitted.update(current['unstaged']); permitted.update(current['untracked'])
            require(all(path in permitted for path in paths), 'not_changed')
            if action == 'stage':
                old_modes = {}
                if not repo.get_config().get_boolean((b'core',), b'filemode', False):
                    previous = repo.open_index(config=repo.get_config())
                    for path in paths:
                        key = path.encode('utf-8')
                        if key in previous:
                            entry = previous[key]
                            require(hasattr(entry, 'mode'), 'operation_in_progress')
                            if stat.S_ISREG(entry.mode):
                                old_modes[key] = entry.mode
                repo.get_worktree().stage(paths, config=repo.get_config())
                # Dulwich stage uses stat mode even when core.filemode=false.
                # Preserve tracked executable bits in the private result index.
                if old_modes:
                    index = repo.open_index(config=repo.get_config()); changed = False
                    for path, mode in old_modes.items():
                        if path in index and stat.S_ISREG(index[path].mode) and index[path].mode != mode:
                            index[path].mode = mode; changed = True
                    if changed:
                        index.write()
            else:
                repo.get_worktree().unstage(paths, config=repo.get_config())
        elif action == 'commit':
            author, message = identity(request)
            require(any(current['staged'].values()), 'no_changes')
            repo.get_worktree().commit(message=message, author=author, committer=author,
                no_verify=True, sign=False, signoff=False, config=repo.get_config())
        elif action == 'diff':
            extra = diff(repo, request, current)
        elif action == 'history':
            limit = request.get('limit', 20)
            require(type(limit) is int and 1 <= limit <= 50, 'input')
            commits = []
            if current['head']:
                for entry in repo.get_walker(max_entries=limit):
                    commit = entry.commit; message = commit.message.decode('utf-8', 'replace')
                    commits.append(dict(id=commit.id.decode('ascii'), message=message[:400],
                        truncated=len(message) > 400, author=commit.author.decode('utf-8', 'replace'), time=commit.commit_time))
            extra = dict(commits=commits)
        elif action == 'show_commit':
            oid = request.get('id')
            require(isinstance(oid, str) and re.fullmatch(r'[a-f0-9]{40}', oid), 'input')
            from dulwich.objects import Commit
            commit = repo[oid.encode('ascii')]; require(isinstance(commit, Commit), 'input')
            require(len(commit.message) <= MAX_DIFF, 'limit')
            extra = dict(id=oid, message=commit.message.decode('utf-8', 'replace'),
                         author=commit.author.decode('utf-8', 'replace'), time=commit.commit_time)
        after = fingerprint(root, deadline, mode_available)
        if action not in WRITES:
            require(after == before, 'stale')
        return dict(**status(repo), **extra, snapshot=after)


def run(root, raw):
    try:
        require(dulwich.__version__ == (1, 2, 20), 'engine')
        require(isinstance(root, str) and isinstance(raw, str) and len(raw) <= 100_000, 'input')
        root = Path(root)
        require(root.is_absolute() and root.is_dir() and root.resolve() == root, 'path')
        result = dict(ok=True, engine=ENGINE, **operate(root, json.loads(raw), time.monotonic() + 15))
        encoded = json.dumps(result, ensure_ascii=False)
        require(len(encoded) <= MAX_JSON, 'limit')
        return encoded
    except Rejected as error:
        reason = str(error)
    except FileLocked:
        reason = 'locked'
    except (ValueError, TypeError, UnicodeError):
        reason = 'input'
    except (OSError, KeyError):
        reason = 'repository'
    except Exception:
        reason = 'backend'
    return json.dumps(dict(ok=False, error=reason))
