"""Real CPython parser, also exercised against the APK diagnostics.pyc by verify_apk.py."""
import json
from pathlib import Path
import sys
import unittest
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "app/src/main/python"))
import diagnostics

class TestSyntaxDiagnostics(unittest.TestCase):
    def report(self, code):
        return json.loads(diagnostics.check_syntax(code))
    def test_valid_program_is_not_executed(self):
        with patch("builtins.input", side_effect=AssertionError("must not run")), patch("builtins.print", side_effect=AssertionError("must not run")):
            self.assertEqual([], self.report('print(input())\nraise RuntimeError("not executed")')["issues"])
    def test_infinite_loop_is_only_compiled(self):
        self.assertEqual([], self.report("while True: pass")["issues"])
    def test_imports_and_file_writes_are_not_executed(self):
        self.assertEqual([], self.report('import nonexistent_module\nopen("must-not-exist", "w").write("bad")')["issues"])
    def test_missing_colon_and_indentation_use_real_parser_messages(self):
        for code in ("if True\n    pass", "def f():\npass", "x = (1]", "def f(: pass"):
            value = self.report(code)
            issue = value["issues"][0]
            self.assertIn("Python", issue["source"])
            self.assertTrue(issue["message"])
            self.assertLessEqual(0, issue["start"])
            self.assertLessEqual(issue["start"], issue["end"])
            self.assertLessEqual(issue["end"], len(code.encode("utf-16-le")) // 2)
    def test_emoji_before_error_uses_utf16_not_codepoint_offsets(self):
        code = 's = "😀"; x = )'
        issue = self.report(code)["issues"][0]
        self.assertEqual(len(code[:code.index(")")].encode("utf-16-le")) // 2, issue["start"])
    def test_chinese_and_crlf_line_offsets(self):
        code = '名字 = "😀"\r\nif True\r\n    pass'
        issue = self.report(code)["issues"][0]
        self.assertGreaterEqual(issue["start"], len('名字 = "😀"\r\n'.encode("utf-16-le")) // 2)
    def test_eof_error_and_empty_code_are_safe(self):
        self.assertEqual([], self.report("")["issues"])
        self.assertTrue(self.report("x = (")["issues"])
    def test_valid_nested_brackets_and_formatted_strings(self):
        self.assertEqual([], self.report('x = [{"a": (1, 2)}]\nprint(f"{x[0]}")')["issues"])
    def test_large_documents_have_explicit_fallback(self):
        result = self.report("#" + "x" * diagnostics.MAX_CHARS)
        self.assertEqual([], result["issues"])
        self.assertIn("过长", result["status"])
    def test_unknown_names_are_not_fabricated_as_syntax_errors(self):
        self.assertEqual([], self.report("value = undefined_name + 1")["issues"])
