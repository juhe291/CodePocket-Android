"""Tests execute runner.py with ordinary CPython (no Android required)."""
import json
import os
import sys
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..',
                                'app', 'src', 'main', 'python'))
from runner import run_code
import runner


class TestLocalPython(unittest.TestCase):
    def test_stdin_stdout(self):
        result = json.loads(run_code('a,b=map(int,input().split())\nprint(a+b)', '3 5'))
        self.assertEqual(result['stdout'], '8\n')
        self.assertEqual(result['exit_code'], 0)

    def test_syntax_error_position(self):
        result = json.loads(run_code('print(1)\nx =', ''))
        self.assertEqual(result['exit_code'], 1)
        self.assertEqual(result['error_line'], 2)
        self.assertIn('SyntaxError', result['stderr'])
        self.assertEqual(result['error_kind'], 'compile')

    def test_runtime_error_position(self):
        result = json.loads(run_code('a=3\nprint(a/0)', ''))
        self.assertEqual(result['error_line'], 2)
        self.assertIn('ZeroDivisionError', result['stderr'])

    def test_standard_library(self):
        result = json.loads(run_code('import math\nprint(math.factorial(5))', ''))
        self.assertEqual(result['stdout'], '120\n')

    def test_local_workspace_import(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / 'helper.py').write_text('def multiply(a, b): return a*b\n', encoding='utf-8')
            result = json.loads(run_code('import helper\nprint(helper.multiply(6,7))', '',
                                         str(folder / 'main.py'), str(folder)))
            self.assertEqual(result['stdout'], '42\n')
            self.assertEqual(result['exit_code'], 0)

    def test_no_stdin_leak_between_runs(self):
        self.assertEqual(json.loads(run_code('print(input())', 'first'))['stdout'], 'first\n')
        self.assertEqual(json.loads(run_code('print(input())', 'second'))['stdout'], 'second\n')

    def test_edited_module_is_reloaded_without_stale_bytecode(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            helper = folder / 'helper.py'
            helper.write_text('value = 11\n', encoding='utf-8')
            args = ('import helper\nprint(helper.value)', '', str(folder / 'main.py'), directory)
            self.assertEqual(json.loads(run_code(*args))['stdout'], '11\n')
            timestamp = helper.stat().st_mtime
            helper.write_text('value = 22\n', encoding='utf-8')
            os.utime(helper, (timestamp, timestamp))
            self.assertEqual(json.loads(run_code(*args))['stdout'], '22\n')
            self.assertNotIn('helper', sys.modules)

    def test_imported_module_error_has_its_own_file_and_line(self):
        with tempfile.TemporaryDirectory() as directory:
            helper = Path(directory) / 'helper.py'
            helper.write_text('x = 1\nraise ValueError("bad")\n', encoding='utf-8')
            result = json.loads(run_code('import helper', '', str(Path(directory) / 'main.py'), directory))
            self.assertEqual(Path(result['error_file']), helper)
            self.assertEqual(result['error_line'], 2)

    def test_streams_path_cwd_argv_and_trace_are_restored_after_error(self):
        before = (sys.stdin, sys.stdout, sys.stderr, list(sys.path), os.getcwd(), sys.argv, sys.gettrace())
        with tempfile.TemporaryDirectory() as directory:
            result = json.loads(run_code('import os,sys\nprint(sys.argv[0])\nopen("data.txt","w").write("ok")\n1/0', '',
                                         str(Path(directory) / 'main.py'), directory))
            self.assertEqual((Path(directory) / 'data.txt').read_text(), 'ok')
        self.assertEqual(result['exit_code'], 1)
        self.assertEqual(before, (sys.stdin, sys.stdout, sys.stderr, list(sys.path), os.getcwd(), sys.argv, sys.gettrace()))

    def test_system_exit_semantics(self):
        for source, expected in [('import sys; sys.exit()', 0), ('raise SystemExit(0)', 0),
                                 ('raise SystemExit(7)', 7), ('raise SystemExit("bye")', 1)]:
            with self.subTest(source=source):
                result = json.loads(run_code(source, ''))
                self.assertEqual(result['exit_code'], expected)
                self.assertNotIn('Traceback', result['stderr'])

    def test_timeout_and_next_execution(self):
        with patch.object(runner, 'TIME_LIMIT_SECONDS', 0.03):
            result = json.loads(run_code('while True:\n    pass', ''))
        self.assertEqual(result['error_kind'], 'timeout')
        self.assertIn('TimeoutError', result['stderr'])
        self.assertEqual(json.loads(run_code('print(42)', ''))['stdout'], '42\n')

    def test_output_limit_and_diagnostic_survive_full_stderr(self):
        result = json.loads(run_code('print("x" * 50001)', ''))
        self.assertEqual(result['exit_code'], 1)
        self.assertLessEqual(len(result['stdout']), runner.MAX_OUTPUT_CHARS)
        self.assertIn('输出超过', result['stderr'])
        result = json.loads(run_code('import sys\nsys.stderr.write("x" * 50000)\n1/0', ''))
        self.assertLessEqual(len(result['stderr']), runner.MAX_OUTPUT_CHARS)
        self.assertIn('ZeroDivisionError', result['stderr'])

    def test_eof_and_unicode(self):
        result = json.loads(run_code('print(input())\ninput()', '你好'))
        self.assertEqual(result['stdout'], '你好\n')
        self.assertIn('EOFError', result['stderr'])


if __name__ == '__main__':
    unittest.main()
