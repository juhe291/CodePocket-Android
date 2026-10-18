"""Local Python code executor for Chaquopy.

Single execution at a time (the Android UI serializes runs). Captures input,
stdout, errors, traceback line numbers and enforces a best-effort time/output
limit for Python bytecode. This is NOT a security sandbox.
"""
import io
import importlib
import importlib.abc
import importlib.machinery
import json
import os
import sys
import time
import traceback
import threading
from contextlib import redirect_stderr, redirect_stdout

MAX_OUTPUT_CHARS = 50000
TIME_LIMIT_SECONDS = 6.0
_RUN_LOCK = threading.Lock()


def _within(path, root):
    try:
        return os.path.commonpath((os.path.realpath(path), root)) == root
    except (TypeError, ValueError):
        return False


class FreshSourceLoader(importlib.machinery.SourceFileLoader):
    """Workspace edits must be visible even within one filesystem clock tick."""
    def get_code(self, fullname):
        return self.source_to_code(self.get_data(self.path), self.path)


class WorkspaceFinder(importlib.abc.MetaPathFinder):
    def __init__(self, root):
        self.root = root

    def find_spec(self, fullname, path=None, target=None):
        spec = importlib.machinery.PathFinder.find_spec(fullname, path)
        if (spec and isinstance(spec.loader, importlib.machinery.SourceFileLoader)
                and _within(spec.origin, self.root)):
            spec.loader = FreshSourceLoader(fullname, spec.origin)
            return spec
        return None


class CappedOutput(io.StringIO):
    def write(self, value):
        if self.tell() + len(value) > MAX_OUTPUT_CHARS:
            raise RuntimeError("输出超过 50000 字符，已中止运行")
        return super().write(value)


def run_code(source, stdin_text, filename="main.py", project_dir=""):
    # CPython streams, cwd and module caches are process-wide.
    with _RUN_LOCK:
        return _run_code(source, stdin_text, filename, project_dir)


def _run_code(source, stdin_text, filename, project_dir, terminal=None):
    stdout = terminal.stdout if terminal else CappedOutput()
    stderr = terminal.stderr if terminal else CappedOutput()
    old_stdin = sys.stdin
    old_path = list(sys.path)
    old_trace = sys.gettrace()
    old_argv = sys.argv
    old_cwd = os.getcwd()
    old_meta_path = list(sys.meta_path)
    root = os.path.realpath(project_dir) if project_dir else None
    original_modules = {
        name: module for name, module in list(sys.modules.items())
        if root and _within(getattr(module, "__file__", None), root)
    }
    for name in original_modules:
        sys.modules.pop(name, None)
    begin = time.monotonic()
    budget = terminal.budget if terminal else None
    if budget:
        budget.begin(begin)
    error_line = None
    error_file = None
    diagnostic = ""
    error_kind = "success"
    exit_code = 0

    def watchdog(frame, event, arg):
        if (budget.active_seconds() if budget else time.monotonic() - begin) > TIME_LIMIT_SECONDS:
            raise TimeoutError("运行超过 6 秒，已中止（仅限制 Python 字节码）")
        return watchdog

    try:
        sys.stdin = terminal.stdin if terminal and terminal.interactive else io.StringIO(stdin_text)
        sys.argv = [filename]
        # Allows relative imports from the app-private project workspace.
        if project_dir:
            parent = os.path.dirname(os.path.abspath(filename))
            sys.path.insert(0, parent)
            sys.path.insert(1, os.path.abspath(project_dir))
            os.chdir(root)
            sys.meta_path.insert(0, WorkspaceFinder(root))
            importlib.invalidate_caches()
        with redirect_stdout(stdout), redirect_stderr(stderr):
            try:
                sys.settrace(watchdog)
                compiled = compile(source, filename, "exec")
                exec(compiled, {"__name__": "__main__", "__file__": filename})
            except BaseException as exc:
                sys.settrace(None)
                if isinstance(exc, SystemExit):
                    exit_code = (0 if exc.code is None else exc.code) if isinstance(exc.code, (int, type(None))) else 1
                    if not isinstance(exc.code, (int, type(None))):
                        diagnostic = str(exc.code) + "\n"
                    error_kind = "success" if exit_code == 0 else "runtime"
                else:
                    exit_code = 1
                    error_kind = ("timeout" if isinstance(exc, TimeoutError)
                                  else "compile" if isinstance(exc, SyntaxError) else "runtime")
                    diagnostic = "".join(traceback.format_exception(type(exc), exc, exc.__traceback__))
                # Prefer the last traceback frame belonging to the active file.
                if isinstance(exc, SyntaxError):
                    error_line = exc.lineno
                    error_file = exc.filename
                else:
                    frames = traceback.extract_tb(exc.__traceback__)
                    user_frames = [f for f in frames if f.filename == filename or (root and _within(f.filename, root))]
                    if user_frames:
                        error_line = user_frames[-1].lineno
                        error_file = user_frames[-1].filename
    finally:
        sys.settrace(old_trace)
        sys.stdin = old_stdin
        sys.path[:] = old_path
        sys.argv = old_argv
        sys.meta_path[:] = old_meta_path
        os.chdir(old_cwd)
        for name, module in list(sys.modules.items()):
            if root and _within(getattr(module, "__file__", None), root):
                sys.modules.pop(name, None)
        sys.modules.update(original_modules)

    if terminal and diagnostic:
        # A full stderr capture must not hide the traceback on the actual terminal.
        try:
            terminal.error_stream.write(diagnostic[-MAX_OUTPUT_CHARS:])
            terminal.error_stream.flush()
        except (OSError, ValueError):
            pass
    if exit_code == 0:
        error_line = None
        error_file = None

    return json.dumps({
        "stdout": stdout.getvalue(),
        # Keep the diagnostic visible even when user stderr filled its limit.
        "stderr": (stderr.getvalue() + diagnostic)[-MAX_OUTPUT_CHARS:],
        "exit_code": exit_code,
        "error_line": error_line,
        "error_file": error_file,
        "error_kind": error_kind,
        "elapsed_ms": int((time.monotonic() - begin) * 1000),
    }, ensure_ascii=False)


class TerminalBudget:
    """Exclude blocking stdin on the execution thread; Android also enforces an independent hard deadline."""
    def __init__(self, events=None):
        self.events = events
        self.sequence = 0
        self.started = 0.0
        self.waiting_since = None
        self.waited = 0.0
        self.owner = threading.get_ident()

    def emit(self, state):
        self.sequence += 1
        if self.events is not None:
            self.events.onState(state, self.sequence)

    def begin(self, now):
        self.started = now
        self.emit("running")

    def active_seconds(self):
        now = time.monotonic()
        waiting = now - self.waiting_since if self.waiting_since is not None else 0.0
        return now - self.started - self.waited - waiting

    def read(self, operation):
        # Other Python threads must not pause the main execution budget while it is computing.
        main_read = threading.get_ident() == self.owner
        if main_read:
            self.waiting_since = time.monotonic()
            self.emit("waiting")
        try:
            return operation()
        finally:
            if main_read:
                self.waited += time.monotonic() - self.waiting_since
                self.waiting_since = None
                self.emit("running")


class TerminalInput(io.TextIOBase):
    def __init__(self, stream, budget):
        self.stream = stream
        self.budget = budget
        self._binary = TerminalBinaryInput(stream.buffer, budget)

    @property
    def encoding(self): return self.stream.encoding
    @property
    def errors(self): return self.stream.errors
    @property
    def buffer(self): return self._binary
    def fileno(self): return self.stream.fileno()
    def isatty(self): return self.stream.isatty()
    def readable(self): return True
    def seekable(self): return False
    def read(self, size=-1): return self.budget.read(lambda: self.stream.read(size))
    def readline(self, size=-1): return self.budget.read(lambda: self.stream.readline(size))


class TerminalBinaryInput(io.BufferedIOBase):
    def __init__(self, stream, budget): self.stream, self.budget = stream, budget
    def fileno(self): return self.stream.fileno()
    def isatty(self): return self.stream.isatty()
    def readable(self): return True
    def read(self, size=-1): return self.budget.read(lambda: self.stream.read(size))
    def read1(self, size=-1): return self.budget.read(lambda: self.stream.read1(size))
    def readline(self, size=-1): return self.budget.read(lambda: self.stream.readline(size))


class TerminalOutput(io.TextIOBase):
    def __init__(self, stream):
        self.stream = stream
        self.capture = io.StringIO()
        self.count = 0
        self._binary = TerminalBinaryOutput(self)

    def _reserve(self, count):
        if self.count + count > MAX_OUTPUT_CHARS:
            raise RuntimeError("输出超过 50000 字符，已中止运行")
        self.count += count

    def write(self, value):
        if not isinstance(value, str): raise TypeError("write() requires str")
        self._reserve(len(value))
        self.capture.write(value)
        self.stream.write(value)
        self.stream.flush()  # Prompts without a newline must arrive before input() blocks.
        return len(value)

    @property
    def encoding(self): return self.stream.encoding
    @property
    def errors(self): return self.stream.errors
    @property
    def buffer(self): return self._binary
    def fileno(self): return self.stream.fileno()
    def isatty(self): return self.stream.isatty()
    def writable(self): return True
    def flush(self):
        if not self.stream.closed: self.stream.flush()
    def getvalue(self): return self.capture.getvalue()


class TerminalBinaryOutput(io.RawIOBase):
    def __init__(self, output): self.output = output
    def writable(self): return True
    def fileno(self): return self.output.fileno()
    def isatty(self): return self.output.isatty()
    def flush(self): return self.output.flush()
    def write(self, value):
        value = bytes(value)
        self.output._reserve(len(value))
        self.output.capture.write(value.decode("utf-8", errors="replace"))
        self.output.stream.flush()
        self.output.stream.buffer.write(value)
        self.output.stream.buffer.flush()
        return len(value)


class TerminalStreams:
    def __init__(self, stdin_fd, stdout_fd, stderr_fd, interactive, events):
        from contextlib import ExitStack
        self.resources = ExitStack()
        try:
            # Real duplicate fds preserve isatty(), while fd != C stdin/stdout forces CPython
            # input() through our readline wrapper instead of bypassing waiting events via PyOS_Readline.
            incoming = self.resources.enter_context(os.fdopen(os.dup(stdin_fd), "r", encoding="utf-8", errors="replace"))
            outgoing = self.resources.enter_context(os.fdopen(os.dup(stdout_fd), "w", encoding="utf-8", errors="backslashreplace", buffering=1))
            self.error_stream = self.resources.enter_context(os.fdopen(os.dup(stderr_fd), "w", encoding="utf-8", errors="backslashreplace", buffering=1))
            self.budget = TerminalBudget(events)
            self.stdin = TerminalInput(incoming, self.budget)
            self.stdout, self.stderr = TerminalOutput(outgoing), TerminalOutput(self.error_stream)
            self.interactive = interactive
        except BaseException:
            self.resources.close()
            raise

    def close(self): self.resources.close()


def run_terminal(source, stdin_text, filename, project_dir, stdin_fd, stdout_fd, stderr_fd,
                 interactive=True, events=None):
    """Execute real Python against inherited terminal descriptors; never manufactures output or input."""
    with _RUN_LOCK:
        streams = TerminalStreams(stdin_fd, stdout_fd, stderr_fd, interactive, events)
        try:
            return _run_code(source, stdin_text, filename, project_dir, streams)
        finally:
            streams.close()
