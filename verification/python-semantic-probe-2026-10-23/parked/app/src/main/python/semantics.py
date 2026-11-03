"""Static Jedi analysis for a private, serial Android service. Never runs project code.

The caller owns the process deadline. UTF-16 offsets match Android/Sora; Jedi uses
Unicode code points. Explicit Project + embedded environment avoid executable discovery,
project configuration, buildout hooks and local native extensions.
"""
import json
from pathlib import Path
import sys
import threading

MAX_UNITS = 45000
ENGINE = "Jedi 0.20.1"
_lock = threading.Lock()


def _units(text):
    return len(text.encode("utf-16-le")) // 2


def _prefix(code, offset):
    if not isinstance(offset, int) or isinstance(offset, bool) or offset < 0:
        raise ValueError("Invalid UTF-16 offset")
    raw = code.encode("utf-16-le")
    if offset * 2 > len(raw):
        raise ValueError("Offset outside document")
    return raw[:offset * 2].decode("utf-16-le")  # Reject a split surrogate pair.


def analyze(payload, cache_directory):
    """One JSON request/response. Code is an unsaved snapshot, not a path to execute."""
    with _lock:
        try:
            request = json.loads(payload)
            code = request["code"]
            if not isinstance(code, str) or _units(code) > MAX_UNITS:
                raise ValueError("Document exceeds semantic analysis limit")
            prefix = _prefix(code, request["caret"])
            root = Path(request["root"]).resolve(strict=True)
            relative = Path(request["path"])
            if (not root.is_dir() or relative.is_absolute() or
                    any(p in (".", "..") for p in relative.parts) or
                    "\\" in request["path"] or relative.suffix.lower() != ".py"):
                raise ValueError("Invalid Python document path")
            path = (root / relative).resolve()
            if not path.is_relative_to(root):
                raise ValueError("Document outside project")
            mode = request["mode"]
            if mode not in ("complete", "signature"):
                raise ValueError("Unknown analysis operation")
            import jedi
            import parso
            from jedi.api.environment import InterpreterEnvironment
            if jedi.__version__ != "0.20.1" or parso.__version__ != "0.8.7":
                raise ValueError("Unsupported semantic dependency version")
            # Never use a workspace directory as a trusted extension/import environment.
            trusted = [p for p in sys.path if p and not Path(p).resolve().is_relative_to(root)]
            class EmbeddedEnvironment(InterpreterEnvironment):
                def get_sys_path(self):
                    return trusted
            # Jedi 0.20.1 filters require the per-script Parso cache. Serial lock owns it.
            jedi.settings.fast_parser = True
            jedi.settings.cache_directory = str(Path(cache_directory).resolve())
            jedi.settings.auto_import_modules = []
            paths = ([str(path.parent), str(root)] if request.get("project", False) else []) + trusted
            project = jedi.Project(root, sys_path=paths, smart_sys_path=False, load_unsafe_extensions=False)
            script = jedi.Script(code=code, path=path, project=project, environment=EmbeddedEnvironment())
            line = prefix.count("\n") + 1
            column = len(prefix.rsplit("\n", 1)[-1])
            answer = dict(available=True, engine=ENGINE, items=[], signatures=[])
            if mode == "complete":
                candidates = script.complete(line, column)
                # Jedi can retain a preceding function's locals on a dedented, unfinished line.
                # Its context API resolves that boundary: reject only foreign local definitions.
                token_start = len(prefix)
                while token_start and ("_" + prefix[token_start - 1]).isidentifier():
                    token_start -= 1
                if not prefix[:token_start].rstrip().endswith("."):
                    context = script.get_context(line, column)
                    scopes = set()
                    while context is not None:
                        scopes.add((context.full_name, context.line, context.column))
                        context = context.parent()
                    def visible(candidate):
                        parent = candidate.parent()
                        return (candidate.type == "param" or parent is None or parent.type != "function" or
                                (parent.full_name, parent.line, parent.column) in scopes)
                    candidates = [c for c in candidates if visible(c)]
                # File-name/string completions have different edits; decline instead of corrupting text.
                candidates = [c for c in candidates if c.name.isidentifier() and len(c.name) <= 256][:30]
                if candidates:
                    length = candidates[0].get_completion_prefix_length()
                    start = len(prefix) - length
                    end = len(prefix)
                    while end < len(code) and ("_" + code[end]).isidentifier():
                        end += 1
                    answer.update(start=_units(code[:start]), end=_units(code[:end]))
                    for candidate in candidates:
                        if candidate.get_completion_prefix_length() != length:
                            continue
                        answer["items"].append(dict(label=candidate.name, kind=candidate.type,
                            detail=(candidate.description + "\n" + candidate.docstring(raw=True, fast=True))[:600]))
            else:
                for signature in script.get_signatures(line, column)[:8]:
                    bracket_line, bracket_column = signature.bracket_start
                    before = "".join(code.splitlines(keepends=True)[:bracket_line - 1])
                    opened = _units(before + code.splitlines()[bracket_line - 1][:bracket_column])
                    answer["signatures"].append(dict(name=signature.name[:256],
                        parameters=[p.description.removeprefix("param ")[:256] for p in signature.params[:40]],
                        active=signature.index, opened=opened,
                        detail=signature.docstring(raw=True, fast=True)[:600]))
            return json.dumps(answer, ensure_ascii=True)
        except Exception as error:
            return json.dumps(dict(available=False, engine=ENGINE, items=[], signatures=[],
                                   error=type(error).__name__), ensure_ascii=True)
        finally:
            # Bound cached user parse trees and prevent stale imported source after disk changes.
            if "parso" in sys.modules:
                sys.modules["parso"].cache.parser_cache.clear()
