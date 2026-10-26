"""Real Jedi engine, not canned completion fixtures. Also runs against bundled bytecode."""
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "app/src/main/python"))
import semantics


class SemanticsTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="codepocket-semantic-")
        self.root = Path(self.temp.name)
        self.cache = self.root.parent / (self.root.name + "-cache")
    def tearDown(self):
        self.temp.cleanup()
        import shutil
        if self.cache.is_dir():
            shutil.rmtree(self.cache)
    def query(self, code, mode="complete", caret=None, **extra):
        payload = dict(code=code, caret=semantics._units(code) if caret is None else caret,
                       root=str(self.root), path="main.py", mode=mode, project=True)
        payload.update(extra)
        return json.loads(semantics.analyze(json.dumps(payload), str(self.cache)))
    def labels(self, result):
        self.assertTrue(result["available"], result)
        return [c["label"] for c in result["items"]]
    def test_embedded_environment_does_not_spawn_python(self):
        with patch("subprocess.Popen", side_effect=AssertionError("No executable on Android")):
            self.assertIn("append", self.labels(self.query("items: list[str] = []\nitems.ap")))
    def test_instance_members_and_actual_function_kind(self):
        result = self.query("class Learner:\n    def solve(self, task):\n        return task\nLearner().so")
        item = next(x for x in result["items"] if x["label"] == "solve")
        self.assertEqual("function", item["kind"])
        self.assertIn("def solve", item["detail"])
    def test_standard_library_members(self):
        self.assertIn("loads", self.labels(self.query("import json\njson.lo")))
    def test_unsaved_snapshot_and_exact_middle_token_replacement(self):
        disk = self.root / "main.py"; disk.write_text("value = 1\n")
        code = "value = 'text'\nvalue.uppxx"
        caret = len(code) - 2
        result = self.query(code, caret=caret)
        self.assertIn("upper", self.labels(result))
        self.assertEqual((code.index("uppxx"), len(code)), (result["start"], result["end"]))
        self.assertEqual("value = 1\n", disk.read_text())
    def test_cross_file_is_static_and_top_level_not_executed(self):
        marker = self.root / "MUST_NOT_EXECUTE"
        (self.root / "sibling.py").write_text("from pathlib import Path\nPath(" + repr(str(marker)) + ").write_text('bad')\nclass Token:\n    def run(self): pass\n")
        with patch("subprocess.Popen", side_effect=AssertionError("must not execute")):
            self.assertIn("run", self.labels(self.query("from sibling import Token\nToken().ru")))
        self.assertFalse(marker.exists())
    def test_scope_hides_other_function_local(self):
        names = self.labels(self.query("def hidden():\n    secret_local = 1\nsec"))
        self.assertNotIn("secret_local", names)
    def test_utf16_emoji_chinese_and_crlf(self):
        code = "name = '😀中文'\r\nitems = []; items.ap"
        result = self.query(code)
        self.assertIn("append", self.labels(result))
        self.assertEqual(semantics._units(code[:-2]), result["start"])
        self.assertEqual(semantics._units(code), result["end"])
    def test_unicode_identifier(self):
        code = "变量名称 = 1\n变量名"
        self.assertIn("变量名称", self.labels(self.query(code)))
    def test_rejects_split_surrogate_and_out_of_bounds(self):
        for caret in (1, -1, 100, True):
            self.assertFalse(self.query("😀", caret=caret)["available"])
    def test_active_parameter_uses_real_signature(self):
        code = "def combine(left: int, right: str = 'x') -> str:\n    return right\ncombine(1,"
        result = self.query(code, "signature"); self.assertTrue(result["available"])
        item = result["signatures"][0]
        self.assertEqual("combine", item["name"]); self.assertEqual(1, item["active"])
        self.assertIn("left: int", item["parameters"])
        self.assertEqual(code.rindex("("), item["opened"])
    def test_keyword_parameter_and_custom_shadowing(self):
        code = "def print(custom: str): pass\nprint(custom="
        item = self.query(code, "signature")["signatures"][0]
        self.assertEqual(["custom: str"], item["parameters"]); self.assertEqual(0, item["active"])
    def test_incomplete_syntax_returns_no_fabricated_names(self):
        result = self.query("def broken(:\n    pass\nunknown_object.")
        self.assertTrue(result["available"]); self.assertEqual([], result["items"])
    def test_limits_and_path_escape(self):
        for values in (dict(code="x" * 45001), dict(path="../outside.py"), dict(path=str(self.root / "main.py")), dict(mode="run")):
            code = values.pop("code", "items=[]; items.ap")
            self.assertFalse(self.query(code, **values)["available"])
    def test_project_configuration_is_not_loaded(self):
        folder = self.root / ".jedi"; folder.mkdir()
        (folder / "project.json").write_text("this is deliberately invalid JSON")
        (self.root / "setup.py").write_text("raise RuntimeError('MUST NOT EXECUTE')")
        self.assertIn("upper", self.labels(self.query("text = ''; text.up")))
    def test_current_file_mode_does_not_import_saf_mirror(self):
        (self.root / "sibling.py").write_text("class Token:\n    def run(self): pass\n")
        self.assertNotIn("run", self.labels(self.query("from sibling import Token\nToken().ru", project=False)))

    def test_saved_module_change_is_not_served_from_old_parse_cache(self):
        module=self.root / "sibling.py"
        module.write_text("class Token:\n    def run(self): pass\n")
        self.assertIn("run",self.labels(self.query("from sibling import Token\nToken().ru")))
        module.write_text("class Token:\n    def save(self): pass\n")
        self.assertIn("save",self.labels(self.query("from sibling import Token\nToken().sa")))
        self.assertNotIn("run",self.labels(self.query("from sibling import Token\nToken().ru")))

    def test_signature_bracket_is_utf16_with_emoji_and_crlf(self):
        code="label='😀中文'\r\ndef combine(first, second): pass\r\ncombine(1,"
        item=self.query(code,"signature")["signatures"][0]
        self.assertEqual(semantics._units(code[:code.rindex("(")]),item["opened"])
        self.assertEqual(1,item["active"])

    def test_concurrent_callers_use_serial_backend(self):
        from concurrent.futures import ThreadPoolExecutor
        with ThreadPoolExecutor(max_workers=3) as pool:
            results=list(pool.map(self.query,["items=[];items.ap","import json\njson.lo","value='';value.up"]))
        for result,expected in zip(results,["append","loads","upper"]):
            self.assertIn(expected,self.labels(result))

    def test_completed_tokens_do_not_offer_noop_edits(self):
        for code in ("for", "items=[]; items.append"):
            result=self.query(code);self.assertTrue(result["available"])
            prefix=code[result.get("start",len(code)):]
            self.assertNotIn(prefix,self.labels(result))


if __name__ == "__main__":
    unittest.main()
