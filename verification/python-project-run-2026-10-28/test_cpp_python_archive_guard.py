"""Validation byte-contract tests; these fixtures do not pretend to execute Python or Android."""
import io
import hashlib
import json
from pathlib import Path
import sys
import unittest
import zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts'))
from verify_cpp_apk import verify_runner_update, verify_build_update

def pyc(payload,mtime=1,size=4):
    return bytes.fromhex('f30d0d0a00000000')+mtime.to_bytes(4,'little')+size.to_bytes(4,'little')+payload
def archive(files):
    stream=io.BytesIO()
    with zipfile.ZipFile(stream,'w') as output:
        for name,data in files.items():output.writestr(name,data)
    return stream.getvalue()

class ArchiveGuardTest(unittest.TestCase):
    def setUp(self):self.before={name:pyc(name.encode()) for name in ('runner.pyc','diagnostics.pyc','semantics.pyc')}
    def test_runner_change_and_mtime_only_rebuild_of_other_modules_are_separate_evidence(self):
        after={name:pyc(name.encode(),mtime=2) for name in self.before};after['runner.pyc']=pyc(b'new code',mtime=2)
        result=verify_runner_update(archive(after),archive(self.before))
        self.assertEqual(result['modules'],['runner.pyc']);self.assertEqual(len(result['timestamp_only_updates']),2)
        self.assertEqual({v['module'] for v in result['timestamp_only_updates']},{'diagnostics.pyc','semantics.pyc'})
    def test_any_other_module_code_flags_or_source_size_change_is_rejected(self):
        for name in ('diagnostics.pyc','semantics.pyc'):
            for value in (pyc(b'changed'),pyc(name.encode(),size=5),b'badmagic'+pyc(name.encode())[8:]):
                with self.subTest(name=name,value=value):
                    after=dict(self.before,**{'runner.pyc':pyc(b'new code'),name:value})
                    with self.assertRaises(AssertionError):verify_runner_update(archive(after),archive(self.before))
    def test_added_removed_modules_or_no_runner_code_change_do_not_satisfy_the_exception(self):
        for after in (dict(self.before,extra=pyc(b'extra')),
                      {name:data for name,data in self.before.items() if name!='semantics.pyc'},dict(self.before)):
            with self.assertRaises(AssertionError):verify_runner_update(archive(after),archive(self.before))
    def test_build_metadata_hash_must_match_the_actual_archive_and_no_other_setting_can_change(self):
        old_app=b'old archive';new_app=b'new archive'
        before=dict(assets={'app.imy':hashlib.sha1(old_app).hexdigest(),'other.imy':'keep'},python_version='3.13',extract_packages=['jedi','parso'])
        after=json.loads(json.dumps(before));after['assets']['app.imy']=hashlib.sha1(new_app).hexdigest()
        result=verify_build_update(json.dumps(after),json.dumps(before),new_app,old_app)
        self.assertEqual(result['matches_actual_archive_sha1'],hashlib.sha1(new_app).hexdigest())
        after['python_version']='3.12'
        with self.assertRaises(AssertionError):verify_build_update(json.dumps(after),json.dumps(before),new_app,old_app)
        with self.assertRaises(AssertionError):verify_build_update(json.dumps(before),json.dumps(before),new_app,old_app)

if __name__=='__main__':unittest.main(verbosity=2)
