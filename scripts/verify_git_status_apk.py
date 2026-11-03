"""Strict one-module Git status fix against the verified 0.2.1 update APK."""
import argparse
import hashlib
import importlib
import importlib.machinery
import io
import json
import marshal
from pathlib import Path
import sys
import tempfile
import types
import unittest
import zipfile
from verify_formatting_apk import compiled_source, digest, same_payload
from verify_git_apk import PINS

ROOT=Path(__file__).resolve().parents[1]
BASELINE='4715b84bb9454c5136f823ea07cf2e09d34e3cfc4c21b24a35e688502e44bd91'

def verify_delta(apk,baseline):
    assert digest(baseline.read('AndroidManifest.xml'))==digest(apk.read('AndroidManifest.xml'))
    assert len(apk.namelist())==len(set(apk.namelist())) and apk.testzip() is None
    assert set(apk.namelist())==set(baseline.namelist())
    changed={'assets/chaquopy/app.imy','assets/chaquopy/build.json'}
    signatures={n for n in baseline.namelist() if n.startswith('META-INF/') and (n.endswith(('.SF','.RSA','.DSA','.EC')) or n=='META-INF/MANIFEST.MF')}
    for name in baseline.namelist():
        if name not in changed|signatures:assert apk.read(name)==baseline.read(name),'Unapproved package change: '+name
    native=sorted(n for n in baseline.namelist() if n.startswith('lib/') and n.endswith('.so'));assert len(native)==25
    python=sorted(n for n in baseline.namelist() if n.startswith('assets/chaquopy/'));assert len(python)==20
    with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/app.imy'))) as current,zipfile.ZipFile(io.BytesIO(baseline.read('assets/chaquopy/app.imy'))) as old:
        assert current.testzip() is None and len(current.namelist())==len(set(current.namelist()))
        assert set(current.namelist())==set(old.namelist())=={'runner.pyc','diagnostics.pyc','semantics.pyc','code_formatting.pyc','git_workspace.pyc','git_https.pyc','git_remote.pyc','__pycache__/'}
        timestamps=[]
        for name in old.namelist():
            if name!='git_workspace.pyc':
                before=old.read(name);after=current.read(name)
                same_payload(before,after,name)
                if before!=after:
                    timestamps.append(dict(module=name,before_mtime=int.from_bytes(before[8:12],'little'),
                        after_mtime=int.from_bytes(after[8:12],'little'),unchanged_payload_sha256=digest(after[16:])))
        compiled_source(current.read('git_workspace.pyc'),(ROOT/'app/src/main/python/git_workspace.py').read_bytes())
        assert current.read('git_workspace.pyc')!=old.read('git_workspace.pyc')
    current=json.loads(apk.read('assets/chaquopy/build.json'));old=json.loads(baseline.read('assets/chaquopy/build.json'))
    assert current['assets'].pop('app.imy')==hashlib.sha1(apk.read('assets/chaquopy/app.imy')).hexdigest()
    assert old['assets'].pop('app.imy')==hashlib.sha1(baseline.read('assets/chaquopy/app.imy')).hexdigest()
    assert current==old,'Unapproved Chaquopy metadata change'
    return dict(original_native_unchanged=native,original_python_assets_unchanged=[n for n in python if n not in changed],
        approved_assets=sorted(changed),changed_module='git_workspace.pyc',other_package_entries_byte_identical=True,
        dex_resources_manifest_licenses_byte_identical=True,other_python_payloads_identical=True,python_timestamp_headers=timestamps)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('apk',type=Path)
    parser.add_argument('--baseline',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    assert digest(args.baseline.read_bytes())==BASELINE
    with zipfile.ZipFile(args.apk) as apk,zipfile.ZipFile(args.baseline) as old:
        delta=verify_delta(apk,old)
        with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/app.imy'))) as app:
            bytecodes={name:app.read(name+'.pyc') for name in ('git_workspace','git_https','git_remote','code_formatting')}
        with tempfile.TemporaryDirectory(prefix='git-status-apk-',dir=ROOT/'.tools') as temporary:
            target=Path(temporary)
            with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/requirements-common.imy'))) as dependencies:
                for name in dependencies.namelist():
                    if name.endswith(('/','.py')) or not name.startswith(('dulwich/','urllib3/','autopep8.pyc','pycodestyle.pyc')):continue
                    path=target/name;assert path.resolve().is_relative_to(target.resolve());path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(dependencies.read(name))
            sys.path.insert(0,str(target))
            for name in list(sys.modules):
                if name.split('.')[0] in PINS or name in ('autopep8','pycodestyle'):del sys.modules[name]
            for name,(version,_,_) in PINS.items():
                package=importlib.import_module(name);assert isinstance(package.__loader__,importlib.machinery.SourcelessFileLoader)
                assert Path(package.__file__).resolve().is_relative_to(target.resolve())
                value=package.__version__;assert ('.'.join(map(str,value)) if isinstance(value,tuple) else value)==version
            for name,bytecode in bytecodes.items():
                module=types.ModuleType(name);module.__file__=str(args.apk)+'!'+name+'.pyc';sys.modules[name]=module
                exec(marshal.loads(bytecode[16:]),module.__dict__)
            sys.path.insert(0,str(ROOT/'tests'));suites={}
            for name in bytecodes:
                test=importlib.import_module('test_'+name);assert getattr(test,name) is sys.modules[name]
                suites[name]=unittest.defaultTestLoader.loadTestsFromModule(test)
            counts={name:suite.countTestCases() for name,suite in suites.items()}
            assert counts==dict(git_workspace=21,git_https=12,git_remote=31,code_formatting=20)
            result=unittest.TextTestRunner(verbosity=2).run(unittest.TestSuite(suites.values()))
            for name in ('autopep8','pycodestyle'):
                module=importlib.import_module(name);assert isinstance(module.__loader__,importlib.machinery.SourcelessFileLoader)
                assert Path(module.__file__).resolve().is_relative_to(target.resolve())
            report=dict(apk_sha256=digest(args.apk.read_bytes()),baseline_sha256=BASELINE,tests=result.testsRun,counts=counts,
                failures=len(result.failures),errors=len(result.errors),actual_apk_bytecode_loaded=True,device_execution=False,
                modules_sha256={name:digest(value) for name,value in bytecodes.items()},**delta)
            args.output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
            return not result.wasSuccessful()

if __name__=='__main__':sys.exit(main())
