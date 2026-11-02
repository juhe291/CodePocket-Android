"""Exercise the strict artifact delta on generated build assets. This does not build or inspect a new APK."""
import io
import json
from pathlib import Path
import sys
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
from verify_formatting_apk import verify_delta

class BuildAssets:
    def __init__(self, baseline, overrides=None):
        self.baseline=baseline
        self.overrides={
            'assets/chaquopy/app.imy':(ROOT/'app/build/python/assets/source/debug/chaquopy/app.imy').read_bytes(),
            'assets/chaquopy/requirements-common.imy':(ROOT/'app/build/python/assets/requirements/debug/chaquopy/requirements-common.imy').read_bytes(),
            'assets/chaquopy/build.json':(ROOT/'app/build/intermediates/assets/debug/mergeDebugAssets/chaquopy/build.json').read_bytes(),
        }
        self.overrides.update(overrides or {})
    def namelist(self):return self.baseline.namelist()
    def read(self,name):
        if name in self.overrides:return self.overrides[name]
        if name.startswith('assets/format-licenses/'):
            return (ROOT/'app/src/main'/name).read_bytes()
        return self.baseline.read(name)

def changed_archive(data, change):
    output=io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as original,zipfile.ZipFile(output,'w') as changed:
        for name in original.namelist():changed.writestr(name,change(name,original.read(name)))
    return output.getvalue()

class DeltaChecks(unittest.TestCase):
    def setUp(self):self.baseline=zipfile.ZipFile(ROOT/'dist/CodePocket-workbench-behavior-2026-10-30-debug.apk')
    def tearDown(self):self.baseline.close()
    def test_approved_build_assets_pass_without_creating_an_apk(self):
        report=verify_delta(BuildAssets(self.baseline),self.baseline)
        self.assertEqual(25,len(report['original_native_unchanged']))
        self.assertEqual(19,len(report['approved_dependency_entries']))
    def test_original_native_changes_are_rejected(self):
        name=next(n for n in self.baseline.namelist() if n.startswith('lib/') and n.endswith('.so'))
        with self.assertRaisesRegex(AssertionError,'Original runtime changed'):verify_delta(BuildAssets(self.baseline,{name:b'changed'}),self.baseline)
    def test_original_runner_changes_are_rejected(self):
        current=BuildAssets(self.baseline)
        data=changed_archive(current.read('assets/chaquopy/app.imy'),lambda name,data:data[:-1]+bytes([data[-1]^1]) if name=='runner.pyc' else data)
        with self.assertRaisesRegex(AssertionError,'Old Python payload changed'):verify_delta(BuildAssets(self.baseline,{'assets/chaquopy/app.imy':data}),self.baseline)
    def test_unapproved_build_fields_are_rejected(self):
        current=BuildAssets(self.baseline);config=json.loads(current.read('assets/chaquopy/build.json'));config['unapproved']='changed'
        with self.assertRaisesRegex(AssertionError,'Unapproved build metadata'):verify_delta(BuildAssets(self.baseline,{'assets/chaquopy/build.json':json.dumps(config).encode()}),self.baseline)

if __name__=='__main__':unittest.main(verbosity=2)
