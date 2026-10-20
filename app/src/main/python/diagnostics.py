"""Compile-only editor diagnostics: never execute or import user code."""
import json

MAX_CHARS = 45000


def check_syntax(code):
    if len(code.encode("utf-16-le", errors="surrogatepass")) // 2 > MAX_CHARS:
        return json.dumps({"issues": [], "status": "文件过长，已暂停 Python 语法检查"}, ensure_ascii=False)
    try:
        compile(code, "<editor>", "exec", dont_inherit=True)
    except SyntaxError as error:
        lines = code.splitlines(keepends=True) or [""]
        def position(line, column):
            index = max(0, min((line or 1) - 1, len(lines) - 1))
            prefix = "".join(lines[:index])
            value = lines[index].rstrip("\r\n")
            column = max(0, min((column or 1) - 1, len(value)))
            return len((prefix + value[:column]).encode("utf-16-le", errors="surrogatepass")) // 2
        start = position(error.lineno, error.offset)
        end = max(start, position(error.end_lineno or error.lineno, error.end_offset or error.offset))
        return json.dumps({"issues": [{"start": start, "end": end, "message": error.msg,
            "source": "Python 3.13 语法检查"}], "status": "Python 语法检查"}, ensure_ascii=False)
    except (ValueError, RecursionError, MemoryError) as error:
        return json.dumps({"issues": [], "status": "Python 语法检查受限: " + str(error)[:160]}, ensure_ascii=False)
    return json.dumps({"issues": [], "status": "Python 语法检查"}, ensure_ascii=False)
