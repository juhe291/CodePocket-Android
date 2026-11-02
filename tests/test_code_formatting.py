"""Real pinned formatter behavior and failure boundaries; no Android claim."""
import ast
import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "app/src/main/python"))
import code_formatting
import autopep8
import pycodestyle


class CodeFormattingTests(unittest.TestCase):
    def call(self, code, start=0, end=0, indent=4, spaces=True):
        return json.loads(code_formatting.format_document(json.dumps(dict(
            code=code, start=start, end=end, indent=indent, spaces=spaces), ensure_ascii=False)))

    def formatted(self, code, **options):
        result = self.call(code, **options)
        self.assertTrue(result["available"], result)
        self.assertEqual("autopep8 2.3.2", result["engine"])
        self.assertEqual(hashlib.sha256(code.encode("utf-8")).hexdigest(), result["source"])
        self.assertLessEqual(result["start"], code_formatting._units(result["text"]))
        self.assertLessEqual(result["end"], code_formatting._units(result["text"]))
        self.assertEqual(code_formatting._tree(code), code_formatting._tree(result["text"]))
        return result

    def test_uses_the_actual_fixed_distributions(self):
        self.assertEqual("2.3.2", autopep8.__version__)
        self.assertEqual("2.14.0", pycodestyle.__version__)
        result = self.formatted("value=  1+2\n")
        self.assertEqual("value = 1 + 2\n", result["text"])

    def test_block_indentation_follows_two_four_and_eight_spaces(self):
        source = "def sample():\n    value=1\n    return value\n"
        for width in (2, 4, 8):
            with self.subTest(width=width):
                result = self.formatted(source, indent=width)
                self.assertIn("\n" + " " * width + "return value", result["text"])

    def test_tabs_keep_continuation_alignment_and_multiline_literal_content(self):
        source = 'def sample():\n    text="""first\n       keep literal indentation\n    last"""\n    value=sum((1,\n               2))\n    return text,value\n'
        result = self.formatted(source, spaces=False)
        self.assertIn('\n\ttext = """first\n       keep literal indentation\n    last"""', result["text"])
        self.assertIn("\n\treturn text, value", result["text"])

    def test_crlf_and_utf16_selection_inside_a_unicode_literal_are_preserved(self):
        source = "caption= '😀中文'\r\nvalue=  1\r\n"
        start = code_formatting._units(source[:source.index("中文")])
        end = start + 2
        result = self.formatted(source, start=start, end=end)
        self.assertNotIn("\n", result["text"].replace("\r\n", ""))
        raw = result["text"].encode("utf-16-le")
        self.assertEqual("中文", raw[result["start"] * 2:result["end"] * 2].decode("utf-16-le"))

    def test_preserves_raw_bytes_strings_and_comment_words(self):
        source = '# 注释 😀 保留\ntext= r"a  b\\t"\ndata= b"x\\x00y"\n'
        result = self.formatted(source)
        self.assertIn('r"a  b\\t"', result["text"])
        self.assertIn('b"x\\x00y"', result["text"])
        self.assertIn("# 注释 😀 保留", result["text"])

    def test_does_not_reorganize_imports_or_rewrite_equality(self):
        source = "import os,sys\nvalue= (item==None)\n"
        result = self.formatted(source)
        self.assertIn("import os, sys", result["text"])
        self.assertIn("== None", result["text"])
        self.assertNotIn("is None", result["text"])

    def test_modern_python_syntax_uses_the_python313_parser(self):
        sources = [
            "type Pair= tuple[int,int]\n",
            "def sample(x):\n    match x:\n        case [a,b]:\n            return a+b\n",
            "def sample(x):\n    return f'{x + 1=}😀'\n",
            "try:\n    run()\nexcept* ValueError:\n    pass\n",
        ]
        for source in sources:
            with self.subTest(source=source):
                self.formatted(source)

    def test_type_ignore_line_positions_do_not_cause_a_false_semantic_change(self):
        result = self.formatted("\ndef sample():\n    value= 1 # type: ignore[assignment]\n    return value\n")
        self.assertIn("# type: ignore[assignment]", result["text"])

    def test_format_off_keeps_the_explicitly_disabled_body(self):
        source = "# fmt: off\nvalue=   [1,2,3]\n# fmt: on\nother=   2\n"
        result = self.formatted(source)
        self.assertIn("value=   [1,2,3]", result["text"])
        self.assertIn("other = 2", result["text"])

    def test_selection_end_excludes_new_whitespace_before_the_next_token(self):
        for newline in ("\n", "\r\n"):
            source = "# 😀中文" + newline + "value=1+2" + newline
            start = source.index("value")
            begin = code_formatting._units(source[:start])
            end = code_formatting._units(source[:start+5])
            result = self.formatted(source, start=begin, end=end)
            selected = result["text"].encode("utf-16-le")[result["start"]*2:result["end"]*2].decode("utf-16-le")
            self.assertEqual("value", selected)

    def test_idempotence_for_both_spaces_and_tabs(self):
        for spaces in (True, False):
            for width in (2, 4, 8):
                with self.subTest(spaces=spaces, width=width):
                    first = self.formatted("def f():\n    return(1+ 2)\n", spaces=spaces, indent=width)
                    second = self.formatted(first["text"], spaces=spaces, indent=width)
                    self.assertEqual(first["text"], second["text"])

    def test_empty_source_has_no_phantom_newline(self):
        self.assertEqual("", self.formatted("")["text"])

    def test_syntax_errors_return_no_partial_formatted_text(self):
        result = self.call("def broken(\n")
        self.assertFalse(result["available"])
        self.assertEqual("syntax", result["error"])
        self.assertNotIn("text", result)

    def test_never_executes_top_level_code_reads_project_config_or_writes_source(self):
        with tempfile.TemporaryDirectory() as temporary:
            folder = Path(temporary)
            sentinel = folder / "executed"
            source = "from pathlib import Path\nPath(" + repr(str(sentinel)) + ").write_text('MUST NOT RUN')\nvalue=  1\n"
            target = folder / "main.py"
            target.write_text(source, encoding="utf-8")
            (folder / "pyproject.toml").write_text("[tool.autopep8]\naggressive=3\nmax_line_length=1\n", encoding="utf-8")
            before = Path.cwd()
            try:
                os.chdir(folder)
                result = self.formatted(source)
            finally:
                os.chdir(before)
            self.assertIn("value = 1", result["text"])
            self.assertFalse(sentinel.exists())
            self.assertEqual(source, target.read_text(encoding="utf-8"))

    def test_rejects_mid_surrogate_positions_and_out_of_range_selections(self):
        source = "name='😀'\n"
        middle = code_formatting._units(source[:source.index("😀")]) + 1
        for position in (middle, -1, 500, True):
            with self.subTest(position=position):
                result = self.call(source, start=position)
                self.assertFalse(result["available"])
                self.assertEqual("invalid_request", result["error"])

    def test_rejects_invalid_payload_types_controls_and_options(self):
        for payload in ("not json", "[]", "null", json.dumps(dict(code="\0", start=0, end=0)),
                        json.dumps(dict(code="x=1", start=0, end=0, indent=True)),
                        json.dumps(dict(code="x=1", start=0, end=0, spaces=1))):
            with self.subTest(payload=payload):
                result = json.loads(code_formatting.format_document(payload))
                self.assertFalse(result["available"])
                self.assertEqual("invalid_request", result["error"])

    def test_source_and_expanded_result_limits_are_separate(self):
        with mock.patch.object(code_formatting, "MAX_UNITS", 80):
            self.assertEqual("invalid_request", self.call("x" * 81)["error"])
            self.assertEqual("output_limit", self.call("x=1\n" * 15)["error"])

    def test_unknown_dependency_version_is_not_silently_accepted(self):
        with mock.patch.object(autopep8, "__version__", "future"):
            self.assertEqual("dependency", self.call("x=1\n")["error"])

    def test_formatter_exception_returns_no_partial_output(self):
        with mock.patch.object(autopep8, "fix_code", side_effect=RuntimeError("fixture")):
            result = self.call("x=1\n")
        self.assertEqual("backend", result["error"])
        self.assertNotIn("text", result)

    def test_semantic_or_literal_rewrites_are_rejected(self):
        for changed in ("x = 2\n", 'x = "rewritten"\n'):
            with self.subTest(changed=changed), mock.patch.object(autopep8, "fix_code", return_value=changed):
                result = self.call("x=1\n")
                self.assertEqual("unsafe_result", result["error"])
                self.assertNotIn("text", result)


if __name__ == "__main__":
    unittest.main()
