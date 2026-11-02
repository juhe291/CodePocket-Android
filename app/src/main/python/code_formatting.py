"""Bounded in-memory Python formatting. Never imports or executes project code."""
import ast
import bisect
import hashlib
import io
import json
import re
import token
import tokenize

ENGINE = "autopep8 2.3.2"
MAX_UNITS = 200_000
MAX_JSON = 450_000


def _units(text):
    return len(text.encode("utf-16-le")) // 2


def _index(text, units):
    if type(units) is not int or not 0 <= units <= _units(text):
        raise ValueError("selection")
    return len(text.encode("utf-16-le")[:units * 2].decode("utf-16-le"))


def _tokens(code):
    return list(tokenize.generate_tokens(io.StringIO(code).readline))


def _tree(code):
    tree = ast.parse(code, type_comments=True)
    # TypeIgnore.lineno is a field, unlike ordinary node locations.
    for node in ast.walk(tree):
        if isinstance(node, ast.TypeIgnore):
            node.lineno = 0
    return ast.dump(tree, include_attributes=False)


def _literals(code):
    kinds = {tokenize.STRING}
    kinds.update(getattr(token, name) for name in ("FSTRING_START", "FSTRING_MIDDLE", "FSTRING_END") if hasattr(token, name))
    return [(item.type, item.string) for item in _tokens(code) if item.type in kinds]


def _comment_words(code):
    return [word for item in _tokens(code) if item.type == tokenize.COMMENT
            for word in item.string[1:].split()]


def _disabled(code):
    rows = set()
    opened = None
    for item in _tokens(code):
        if item.type != tokenize.COMMENT:
            continue
        match = re.fullmatch(r"#\s*(?:autopep8|fmt):\s*(off|on)\s*", item.string)
        if match and match[1] == "off" and opened is None:
            opened = item.start[0]
        elif match and match[1] == "on" and opened is not None:
            rows.update(range(opened, item.end[0] + 1))
            opened = None
    if opened is not None:
        rows.update(range(opened, len(code.splitlines()) + 1))
    return rows


def _tabs(code, width, autopep8):
    """Convert block indentation, retaining continuation alignment and literal bytes."""
    tokens = _tokens(code)
    protected = set(autopep8.multiline_string_lines(code)) | _disabled(code)
    contexts = {}
    depth = 0
    first = None
    base = 0
    trivia = {tokenize.NL, tokenize.COMMENT, tokenize.ENDMARKER, tokenize.ENCODING}
    for item in tokens:
        if item.type == tokenize.INDENT:
            depth += 1
        elif item.type == tokenize.DEDENT:
            depth -= 1
        elif item.type == tokenize.NEWLINE:
            if first is not None:
                for row in range(first, item.end[0] + 1):
                    contexts[row] = base
            first = None
        elif item.type not in trivia and first is None:
            first, base = item.start[0], depth
    result = []
    for number, line in enumerate(code.splitlines(keepends=True), 1):
        if number in protected:
            result.append(line)
            continue
        leading = len(line) - len(line.lstrip(" "))
        columns = min(leading, contexts.get(number, leading // width) * width)
        result.append("\t" * (columns // width) + " " * (columns % width) + line[columns:])
    return "".join(result)


def _anchors(code):
    lines = code.splitlines(keepends=True)
    starts = [0]
    for line in lines:
        starts.append(starts[-1] + len(line))
    trivia = {tokenize.NL, tokenize.NEWLINE, tokenize.INDENT, tokenize.DEDENT,
              tokenize.COMMENT, tokenize.ENDMARKER, tokenize.ENCODING}
    result = []
    for item in _tokens(code):
        if item.type in trivia or item.type == tokenize.OP and item.string in (";", "(", ")"):
            continue
        start = starts[item.start[0] - 1] + item.start[1]
        end = starts[item.end[0] - 1] + item.end[1]
        result.append(((item.type, item.string), start, end))
    return result


def _selections(before, after, positions):
    left, right = _anchors(before), _anchors(after)
    if [item[0] for item in left] != [item[0] for item in right]:
        # The AST/literal checks still apply. Keep a valid bounded position for a
        # rare syntactic spelling change which has no one-to-one token anchor.
        return [min(position, len(after)) for position in positions]
    starts = [item[1] for item in left]
    mapped = []
    for position in positions:
        index = bisect.bisect_right(starts, position) - 1
        # A range end belongs to the preceding token when two tokens originally
        # touched (value=...). Formatting may insert a gap between those tokens.
        if (positions[0] != positions[1] and position == max(positions) and index > 0
                and left[index][1] == position == left[index - 1][2]):
            index -= 1
        if index >= 0 and position <= left[index][2]:
            value = right[index][1] + min(position - left[index][1], right[index][2] - right[index][1])
        else:
            previous = left[index][2] if index >= 0 else 0
            destination = right[index][2] if index >= 0 else 0
            next_start = right[index + 1][1] if index + 1 < len(right) else len(after)
            value = destination + min(position - previous, next_start - destination)
        mapped.append(value)
    return mapped


def format_document(payload):
    """Return JSON bound to the source digest, with UTF-16 selection positions."""
    def failure(reason):
        return json.dumps(dict(available=False, engine=ENGINE, error=reason))
    try:
        if not isinstance(payload, str) or len(payload) > MAX_JSON:
            return failure("invalid_request")
        request = json.loads(payload)
        code = request["code"]
        width = request.get("indent", 4)
        spaces = request.get("spaces", True)
        if not isinstance(code, str) or "\0" in code or _units(code) > MAX_UNITS:
            return failure("invalid_request")
        if type(width) is not int or width not in (2, 4, 8) or type(spaces) is not bool:
            return failure("invalid_request")
        positions = [_index(code, request["start"]), _index(code, request["end"])]
    except (ValueError, KeyError, TypeError, UnicodeError):
        return failure("invalid_request")
    try:
        before_tree = _tree(code)
    except (SyntaxError, ValueError, RecursionError):
        return failure("syntax")
    try:
        import autopep8
        import pycodestyle
        if autopep8.__version__ != "2.3.2" or pycodestyle.__version__ != "2.14.0":
            return failure("dependency")
        normalized = code.replace("\r\n", "\n").replace("\r", "\n")
        formatted = autopep8.fix_code(normalized, options=dict(indent_size=width,
            aggressive=0, experimental=False, max_line_length=100,
            ignore=["E265", "E266", "E401"]), apply_config=False)
        if formatted and not _disabled(formatted):
            formatted = autopep8.reindent(formatted, width)
        if not spaces:
            formatted = _tabs(formatted, width, autopep8)
        if not code:
            formatted = ""
        if (_tree(formatted) != before_tree or _literals(formatted) != _literals(normalized)
                or _comment_words(formatted) != _comment_words(normalized)):
            return failure("unsafe_result")
        # A formatting command follows the file's predominant existing line ending.
        newline = autopep8.find_newline(code.splitlines(keepends=True))
        selected = _selections(normalized, formatted,
            [len(code[:position].replace("\r\n", "\n").replace("\r", "\n")) for position in positions])
        if newline != "\n":
            selected = [len(formatted[:position].replace("\n", newline)) for position in selected]
            formatted = formatted.replace("\n", newline)
        if _units(formatted) > MAX_UNITS:
            return failure("output_limit")
        response = dict(available=True, engine=ENGINE, text=formatted,
            start=_units(formatted[:selected[0]]), end=_units(formatted[:selected[1]]),
            source=hashlib.sha256(code.encode("utf-8")).hexdigest())
        raw = json.dumps(response, ensure_ascii=False)
        return raw if len(raw) <= MAX_JSON else failure("output_limit")
    except (ImportError, ModuleNotFoundError):
        return failure("dependency")
    except Exception:
        return failure("backend")
