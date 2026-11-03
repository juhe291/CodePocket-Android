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
import hashlib
import linecache
import os
import sys
import time
import traceback
import threading
import types
from contextlib import redirect_stderr, redirect_stdout

MAX_OUTPUT_CHARS = 50000
TIME_LIMIT_SECONDS = 6.0
_RUN_LOCK = threading.Lock()
_MAX_OVERLAY_BYTES = 8_000_000


def _load_overlays(path, root):
    if not path:
        return {}
    if not root:
        raise ValueError("恢复草稿运行缺少项目目录")
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("恢复草稿清单包含重复键")
            result[key] = value
        return result
    with open(path, "rb") as stream:
        data = stream.read(_MAX_OVERLAY_BYTES + 1)
    if len(data) > _MAX_OVERLAY_BYTES:
        raise ValueError("恢复草稿清单过大")
    manifest = json.loads(data.decode("utf-8"), object_pairs_hook=unique)
    if (not isinstance(manifest, dict) or set(manifest) != {"version", "files"}
            or type(manifest["version"]) is not int or manifest["version"] != 1
            or not isinstance(manifest["files"], dict) or len(manifest["files"]) > 128):
        raise ValueError("恢复草稿清单格式无效")
    result, units = {}, 0
    for name, source in manifest["files"].items():
        if (not isinstance(name, str) or not name.endswith((".py", ".pyi"))
                or any(part in ("", ".", "..") for part in name.split("/"))
                or "\\" in name or ":" in name or any(ord(c) < 32 or ord(c) == 127 for c in name)
                or not isinstance(source, str) or "\0" in source):
            raise ValueError("恢复草稿源码或相对路径无效")
        size = len(source.encode("utf-16-le")) // 2
        units += size
        target = os.path.abspath(os.path.join(root, *name.split("/")))
        if (size > 200_000 or units > 1_000_000 or not _within(target, root)
                or os.path.realpath(target) != target or not os.path.isfile(target)):
            raise ValueError("恢复草稿源码已移除、路径不规范或超过限制")
        result[target] = source
    return result


def _within(path, root):
    try:
        return os.path.commonpath((os.path.realpath(path), root)) == root
    except (TypeError, ValueError):
        return False


class FreshSourceLoader(importlib.machinery.SourceFileLoader):
    """Workspace edits must be visible even within one filesystem clock tick."""
    def __init__(self, fullname, path, source=None, compile_source=None):
        super().__init__(fullname, path)
        self.source = source
        self.compile_source = compile_source

    def get_code(self, fullname):
        source = self.source if self.source is not None else self.get_data(self.path)
        return (self.compile_source(source, self.path) if self.compile_source
                else self.source_to_code(source, self.path))

    def get_source(self, fullname):
        return self.source if self.source is not None else super().get_source(fullname)


class WorkspaceFinder(importlib.abc.MetaPathFinder):
    def __init__(self, root, overlays=None, compile_source=None):
        self.root = root
        self.overlays = overlays or {}
        self.compile_source = compile_source

    def find_spec(self, fullname, path=None, target=None):
        spec = importlib.machinery.PathFinder.find_spec(fullname, path)
        if (spec and isinstance(spec.loader, importlib.machinery.SourceFileLoader)
                and _within(spec.origin, self.root)):
            spec.loader = FreshSourceLoader(fullname, spec.origin,
                                           self.overlays.get(os.path.abspath(spec.origin)), self.compile_source)
            return spec
        return None


class CappedOutput(io.StringIO):
    def write(self, value):
        if self.tell() + len(value) > MAX_OUTPUT_CHARS:
            raise RuntimeError("输出超过 50000 字符，已中止运行")
        return super().write(value)


def run_code(source, stdin_text, filename="main.py", project_dir="", overlays_path="", breakpoints=None):
    # CPython streams, cwd and module caches are process-wide.
    with _RUN_LOCK:
        return _run_code(source, stdin_text, filename, project_dir, overlays_path=overlays_path, breakpoints=breakpoints)


def _breakpoint_lines(value):
    """Bounded 1-based line list; accepts the Android bundle's JSON text or a plain sequence."""
    if not value:
        return []
    if isinstance(value, str):
        try:
            value = json.loads(value)
        except ValueError:
            return []
    if not isinstance(value, (list, tuple)):
        return []
    result = set()
    for item in value:
        if type(item) is int and 0 < item <= 2_000_000:
            result.add(item)
        if len(result) >= 500:
            break
    return sorted(result)


def _run_code(source, stdin_text, filename, project_dir, terminal=None, overlays_path="", breakpoints=None):
    root = os.path.realpath(project_dir) if project_dir else None
    overlays = _load_overlays(overlays_path, root)
    stdout = terminal.stdout if terminal else CappedOutput()
    stderr = terminal.stderr if terminal else CappedOutput()
    # Breakpoints only make sense with a real interactive terminal that can answer the debugger prompt.
    active_breakpoints = set(_breakpoint_lines(breakpoints)) if terminal is not None and getattr(terminal, "interactive", False) else set()
    debugger = None
    if active_breakpoints:
        import pdb
        debugger = pdb.Pdb(stdin=terminal.stdin, stdout=terminal.stderr)
        debugger.reset()
    old_stdin = sys.stdin
    old_path = list(sys.path)
    old_trace = sys.gettrace()
    old_argv = sys.argv
    old_cwd = os.getcwd()
    old_meta_path = list(sys.meta_path)
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
    error_source_hash = None
    diagnostic = ""
    error_kind = "success"
    exit_code = 0
    breakpoint_quit = False
    compiled_sources, syntax_sources, cached_lines = {}, {}, {}
    missing = object()

    def compile_source(text, path):
        # Keep code objects alive and compare identity: a user-supplied co_filename is not proof.
        try:
            decoded = text.decode("utf-8-sig") if isinstance(text, bytes) else text
            digest = hashlib.sha256(decoded.encode("utf-8")).hexdigest()
        except UnicodeError:
            decoded, digest = None, None
        if decoded is not None:
            lines = decoded.splitlines(keepends=True)
            if lines and not lines[-1].endswith("\n"):
                lines[-1] += "\n"
            # Bdb canonicalises filenames (abspath+normcase; realpath for existing files), so the
            # debugger's set_break()/source display must find the same bounded source there too.
            canonical = os.path.normcase(os.path.abspath(path))
            if os.path.isfile(canonical):
                canonical = os.path.normcase(os.path.realpath(canonical))
            for key in {path, canonical}:
                if key not in cached_lines:
                    cached_lines[key] = linecache.cache.get(key, missing)
                linecache.cache[key] = (len(decoded), None, lines, path)
        try:
            code = compile(text, path, "exec")
        except SyntaxError as exc:
            syntax_sources[id(exc)] = (exc, digest)
            raise
        def register(item):
            compiled_sources[id(item)] = (item, digest)
            for value in item.co_consts:
                if isinstance(value, types.CodeType):
                    register(value)
        register(code)
        return code

    def watchdog(frame, event, arg):
        if (budget.active_seconds() if budget else time.monotonic() - begin) > TIME_LIMIT_SECONDS:
            raise TimeoutError("运行超过 6 秒，已中止（仅限制 Python 字节码）")
        return watchdog

    def debug_trace(frame, event, arg):
        # Enter the real Pdb on the active file's own call event, then let Pdb keep the global trace
        # so its native continue/next/step loop and breakpoint table work through the same PTY.
        if event == "call" and frame.f_code.co_filename == filename:
            debugger.botframe = frame
            debugger.set_continue()
            sys.settrace(debugger.trace_dispatch)
            return debugger.trace_dispatch
        return debug_trace

    try:
        sys.stdin = terminal.stdin if terminal and terminal.interactive else io.StringIO(stdin_text)
        sys.argv = [filename]
        # Allows relative imports from the app-private project workspace.
        if project_dir:
            parent = os.path.dirname(os.path.abspath(filename))
            sys.path.insert(0, parent)
            sys.path.insert(1, os.path.abspath(project_dir))
            os.chdir(root)
            sys.meta_path.insert(0, WorkspaceFinder(root, overlays, compile_source))
            importlib.invalidate_caches()
        with redirect_stdout(stdout), redirect_stderr(stderr):
            try:
                compiled = compile_source(source, filename)
                if debugger is not None:
                    # compile_source() published the bounded linecache source, so Pdb can resolve
                    # each requested line instead of silently ignoring "line does not exist".
                    for line in sorted(active_breakpoints)[:500]:
                        debugger.set_break(filename, line)
                sys.settrace(debug_trace if debugger is not None else watchdog)
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
                    import bdb
                    breakpoint_quit = isinstance(exc, bdb.BdbQuit)
                    error_kind = ("timeout" if isinstance(exc, TimeoutError)
                                  else "compile" if isinstance(exc, SyntaxError) else "runtime")
                    diagnostic = "已退出断点调试器。\n" if breakpoint_quit else \
                        "".join(traceback.format_exception(type(exc), exc, exc.__traceback__))
                # Prefer the last traceback frame belonging to the active file.
                if isinstance(exc, SyntaxError):
                    error_line = exc.lineno
                    error_file = exc.filename
                    registered = syntax_sources.get(id(exc))
                    if registered and registered[0] is exc:
                        error_source_hash = registered[1]
                else:
                    frame = exc.__traceback__
                    while frame is not None:
                        code = frame.tb_frame.f_code
                        if code.co_filename == filename or (root and _within(code.co_filename, root)):
                            error_line, error_file = frame.tb_lineno, code.co_filename
                            registered = compiled_sources.get(id(code))
                            error_source_hash = registered[1] if registered and registered[0] is code else None
                        frame = frame.tb_next
    finally:
        sys.settrace(old_trace)
        if debugger is not None:
            try:
                debugger.clear_all_breaks()
            except Exception:
                pass
        sys.stdin = old_stdin
        sys.path[:] = old_path
        sys.argv = old_argv
        sys.meta_path[:] = old_meta_path
        os.chdir(old_cwd)
        for name, module in list(sys.modules.items()):
            if root and _within(getattr(module, "__file__", None), root):
                sys.modules.pop(name, None)
        sys.modules.update(original_modules)
        for path, value in cached_lines.items():
            if value is missing:
                linecache.cache.pop(path, None)
            else:
                linecache.cache[path] = value

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
        error_source_hash = None

    return json.dumps({
        "stdout": stdout.getvalue(),
        # Keep the diagnostic visible even when user stderr filled its limit.
        "stderr": (stderr.getvalue() + diagnostic)[-MAX_OUTPUT_CHARS:],
        "exit_code": exit_code,
        "error_line": error_line,
        "error_file": error_file,
        "error_source_hash": error_source_hash,
        "error_kind": error_kind,
        "breakpoint_quit": breakpoint_quit,
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
                 interactive=True, events=None, overlays_path="", breakpoints=None):
    """Execute real Python against inherited terminal descriptors; never manufactures output or input."""
    with _RUN_LOCK:
        streams = TerminalStreams(stdin_fd, stdout_fd, stderr_fd, interactive, events)
        try:
            return _run_code(source, stdin_text, filename, project_dir, streams, overlays_path, breakpoints)
        finally:
            streams.close()
