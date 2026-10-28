"""Real production runner tests for unsaved project modules; no fabricated Python execution."""
import hashlib
import json
import linecache
import os
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'app/src/main/python'))
from runner import run_code, run_terminal


class TestPythonProjectRun(unittest.TestCase):
    def manifest(self,folder,files):
        target=folder/'overlays.json'
        target.write_text(json.dumps({'version':1,'files':files}),encoding='utf-8')
        return str(target)

    def test_unsaved_module_runs_without_saving_and_keeps_its_original_file_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);helper=root/'helper.py';helper.write_text('answer=1\n',encoding='utf-8')
            overlays=self.manifest(root,{'helper.py':'answer=42\n'})
            result=json.loads(run_code('import helper\nprint(helper.answer)\nprint(helper.__file__)','',str(root/'main.py'),directory,overlays_path=overlays))
            self.assertEqual(result['stdout'],'42\n'+str(helper)+'\n');self.assertEqual(result['exit_code'],0)
            self.assertEqual(helper.read_text(),'answer=1\n');self.assertNotIn('helper',sys.modules)

    def test_package_relative_imports_and_resources_keep_the_original_project_root(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);package=root/'pkg';package.mkdir()
            (package/'__init__.py').write_text('from .helper import value\n',encoding='utf-8')
            (package/'helper.py').write_text('value="old"\n',encoding='utf-8');(package/'data.txt').write_text('resource',encoding='utf-8')
            source='from pathlib import Path\nvalue="draft/"+Path(__file__).with_name("data.txt").read_text()\n'
            overlays=self.manifest(root,{'pkg/helper.py':source})
            result=json.loads(run_code('import pkg\nprint(pkg.value)','',str(root/'main.py'),directory,overlays_path=overlays))
            self.assertEqual(result['stdout'],'draft/resource\n');self.assertEqual((package/'helper.py').read_text(),'value="old"\n')

    def test_draft_module_errors_report_the_executed_source_hash_and_real_file_line(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);helper=root/'helper.py';helper.write_text('value=1\n',encoding='utf-8')
            for source,line,kind in [('value=2\nraise ValueError("draft")\n',2,'runtime'),('value =\n',1,'compile')]:
                with self.subTest(kind=kind):
                    overlays=self.manifest(root,{'helper.py':source})
                    result=json.loads(run_code('import helper','',str(root/'main.py'),directory,overlays_path=overlays))
                    self.assertEqual(Path(result['error_file']),helper);self.assertEqual(result['error_line'],line)
                    self.assertEqual(result['error_kind'],kind);self.assertEqual(result['error_source_hash'],hashlib.sha256(source.encode()).hexdigest())
            self.assertEqual(helper.read_text(),'value=1\n')

    def test_malformed_or_traversing_overlays_are_rejected_before_user_code(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);marker=root/'executed.txt'
            for files in [{'../escape.py':'answer=42'}, {'helper.py':{'not':'source'}}, {'/absolute.py':'answer=42'}]:
                with self.subTest(files=files):
                    overlays=self.manifest(root,files)
                    with self.assertRaises(ValueError):
                        run_code('open("executed.txt","w").write("bad")','',str(root/'main.py'),directory,overlays_path=overlays)
                    self.assertFalse(marker.exists())

    def test_program_file_writes_and_new_module_imports_keep_the_existing_behavior(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            result=json.loads(run_code('from pathlib import Path\nPath("created.py").write_text("answer=42")\nimport created\nprint(created.answer)','',str(root/'main.py'),directory))
            self.assertEqual(result['stdout'],'42\n');self.assertEqual((root/'created.py').read_text(),'answer=42')

    def test_draft_traceback_and_inspection_use_draft_lines_then_restore_linecache(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);helper=root/'helper.py';helper.write_text('def value():\n    return 1\n',encoding='utf-8')
            original=(17,None,['pre-existing cache\n'],str(helper));linecache.cache[str(helper)]=original
            source='def value():\n    raise ValueError("draft only")\n'
            try:
                overlays=self.manifest(root,{'helper.py':source})
                result=json.loads(run_code('import inspect, helper\nprint(inspect.getsource(helper.value))\nhelper.value()','',str(root/'main.py'),directory,overlays_path=overlays))
                self.assertEqual(result['stdout'],source+'\n');self.assertIn('raise ValueError("draft only")',result['stderr'])
                self.assertEqual(result['error_source_hash'],hashlib.sha256(source.encode()).hexdigest())
                self.assertIs(linecache.cache[str(helper)],original)
            finally:
                linecache.cache.pop(str(helper),None)

    def test_only_actual_compiled_code_proves_a_helper_diagnostic_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);helper=root/'helper.py';text='def value():\n    raise ValueError("disk")\n';helper.write_text(text,encoding='utf-8')
            result=json.loads(run_code('import helper\nhelper.value()','',str(root/'main.py'),directory))
            self.assertEqual(result['error_source_hash'],hashlib.sha256(helper.read_bytes()).hexdigest());self.assertEqual(result['error_line'],2)
            for source in ['exec(compile("1/0",'+repr(str(helper))+',"exec"))',
                           'raise SyntaxError("custom",('+repr(str(helper))+',2,1,"fake"))']:
                with self.subTest(source=source):
                    result=json.loads(run_code(source,'',str(root/'main.py'),directory))
                    self.assertEqual(Path(result['error_file']),helper);self.assertIsNone(result['error_source_hash'])

    def test_overlay_limits_and_duplicate_keys_fail_before_program_side_effects(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);helper=root/'helper.py';helper.write_text('value=1',encoding='utf-8')
            marker=root/'executed.txt';manifest=root/'overlays.json'
            invalid=[{'version':True,'files':{}},{'version':1,'files':{'helper.py':'x'*200001}},
                     {'version':1,'files':{'helper.py':'😀'*100001}},
                     {'version':1,'files':{'helper.py':'bad\0source'}}]
            for value in invalid:
                manifest.write_text(json.dumps(value),encoding='utf-8')
                with self.assertRaises(ValueError):run_code('open("executed.txt","w").write("bad")','',str(root/'main.py'),directory,overlays_path=str(manifest))
                self.assertFalse(marker.exists())
            manifest.write_text('{"version":1,"files":{"helper.py":"a=1","helper.py":"a=2"}}',encoding='utf-8')
            with self.assertRaises(ValueError):run_code('open("executed.txt","w").write("bad")','',str(root/'main.py'),directory,overlays_path=str(manifest))
            self.assertFalse(marker.exists())

    def test_terminal_channel_reads_drafts_and_releases_only_its_duplicate_descriptors(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);helper=root/'helper.py';helper.write_text('answer=1\n',encoding='utf-8')
            overlays=self.manifest(root,{'helper.py':'answer=42\n'})
            with (root/'stdin.bin').open('w+b') as incoming,(root/'stdout.bin').open('w+b') as outgoing,(root/'stderr.bin').open('w+b') as error:
                result=json.loads(run_terminal('import helper\nprint(helper.answer)','',str(root/'main.py'),directory,
                    incoming.fileno(),outgoing.fileno(),error.fileno(),False,None,overlays))
                self.assertEqual(result['stdout'],'42\n');self.assertEqual(result['exit_code'],0)
                outgoing.seek(0);self.assertEqual(outgoing.read(),b'42'+os.linesep.encode())
                for stream in (incoming,outgoing,error):self.assertGreaterEqual(os.fstat(stream.fileno()).st_size,0)
            self.assertEqual(helper.read_text(),'answer=1\n')
