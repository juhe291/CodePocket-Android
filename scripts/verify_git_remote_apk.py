"""Check the bounded remote-Git APK delta and run its actual Python bytecode on the host."""
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
BASELINE='26ac0bec36f7a0a89108727452d1f96a8576086e5f9ee19d889987a568961075'
REMOTE={'git_https','git_remote'}

def verify_delta(apk,baseline):
    native=sorted(n for n in baseline.namelist() if n.startswith('lib/') and n.endswith('.so'))
    assets=sorted(n for n in baseline.namelist() if n.startswith('assets/chaquopy/'))
    assert len(native)==25 and len(assets)==20
    assert set(native)=={n for n in apk.namelist() if n.startswith('lib/') and n.endswith('.so')}
    assert set(assets)=={n for n in apk.namelist() if n.startswith('assets/chaquopy/')}
    changes={'assets/chaquopy/app.imy','assets/chaquopy/build.json'}
    for name in native+assets:
        if name not in changes:assert apk.read(name)==baseline.read(name),name
    with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/app.imy'))) as current,zipfile.ZipFile(io.BytesIO(baseline.read('assets/chaquopy/app.imy'))) as old:
        assert current.testzip() is None and len(current.namelist())==len(set(current.namelist()))
        assert set(old.namelist())=={'runner.pyc','diagnostics.pyc','semantics.pyc','code_formatting.pyc','git_workspace.pyc','__pycache__/'}
        assert set(current.namelist())==set(old.namelist())|{name+'.pyc' for name in REMOTE}
        for name in old.namelist():same_payload(old.read(name),current.read(name),name)
        for name in REMOTE:compiled_source(current.read(name+'.pyc'),(ROOT/f'app/src/main/python/{name}.py').read_bytes())
    current=json.loads(apk.read('assets/chaquopy/build.json'));old=json.loads(baseline.read('assets/chaquopy/build.json'))
    assert current['assets'].pop('app.imy')==hashlib.sha1(apk.read('assets/chaquopy/app.imy')).hexdigest()
    assert old['assets'].pop('app.imy')==hashlib.sha1(baseline.read('assets/chaquopy/app.imy')).hexdigest()
    assert current==old,'Unapproved Chaquopy build metadata change'
    for name,(version,sha,license_name) in PINS.items():
        wheel=ROOT/f'.tools/semantic-wheels/{name}-{version}-py3-none-any.whl';assert digest(wheel.read_bytes())==sha
        with zipfile.ZipFile(wheel) as archive:
            assert apk.read(f'assets/git-licenses/{name.upper()}-LICENSE.txt')==archive.read(f'{name}-{version}.dist-info/licenses/{license_name}')
    return dict(original_native_unchanged=native,original_python_assets_unchanged=[n for n in assets if n not in changes],
        approved_assets=sorted(changes),new_modules=sorted(REMOTE),dependencies_byte_identical=True,pinned_wheel_hashes_verified=True,
        original_python_module_payloads_unchanged=True)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('apk',type=Path)
    parser.add_argument('--baseline',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    assert digest(args.baseline.read_bytes())==BASELINE
    with zipfile.ZipFile(args.apk) as apk,zipfile.ZipFile(args.baseline) as baseline:
        assert apk.testzip() is None and len(apk.namelist())==len(set(apk.namelist()));delta=verify_delta(apk,baseline)
        with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/app.imy'))) as app:
            bytecodes={name:app.read(name+'.pyc') for name in ['git_workspace','git_https','git_remote','code_formatting']}
        with tempfile.TemporaryDirectory(prefix='git-remote-apk-',dir=ROOT/'.tools') as temporary:
            target=Path(temporary)
            with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/requirements-common.imy'))) as requirements:
                for name in requirements.namelist():
                    if name.endswith(('/','.py')) or not name.startswith(('dulwich/','urllib3/','autopep8.pyc','pycodestyle.pyc')):continue
                    path=target/name;assert path.resolve().is_relative_to(target.resolve());path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(requirements.read(name))
            sys.path.insert(0,str(target))
            for name in list(sys.modules):
                if name.split('.')[0] in PINS or name in ('autopep8','pycodestyle'):del sys.modules[name]
            for name,(version,_,_) in PINS.items():
                package=importlib.import_module(name);assert isinstance(package.__loader__,importlib.machinery.SourcelessFileLoader)
                assert Path(package.__file__).resolve().is_relative_to(target.resolve())
                actual=package.__version__;assert ('.'.join(map(str,actual)) if isinstance(actual,tuple) else actual)==version
            for name,bytecode in bytecodes.items():
                module=types.ModuleType(name);module.__file__=str(args.apk)+'!'+name+'.pyc';sys.modules[name]=module
                exec(marshal.loads(bytecode[16:]),module.__dict__)
            sys.path.insert(0,str(ROOT/'tests'));suites={}
            for name in bytecodes:
                test=importlib.import_module('test_'+name);assert getattr(test,name) is sys.modules[name]
                suites[name]=unittest.defaultTestLoader.loadTestsFromModule(test)
            counts={name:suite.countTestCases() for name,suite in suites.items()}
            assert counts==dict(git_workspace=19,git_https=12,git_remote=31,code_formatting=20),counts
            result=unittest.TextTestRunner(verbosity=2).run(unittest.TestSuite(suites.values()))
            for name in ('autopep8','pycodestyle'):
                dependency=importlib.import_module(name);assert isinstance(dependency.__loader__,importlib.machinery.SourcelessFileLoader)
                assert Path(dependency.__file__).resolve().is_relative_to(target.resolve())
            report=dict(apk_sha256=digest(args.apk.read_bytes()),baseline_sha256=BASELINE,tests=result.testsRun,counts=counts,
                failures=len(result.failures),errors=len(result.errors),actual_apk_bytecode_loaded=True,device_execution=False,
                modules_sha256={name:digest(value) for name,value in bytecodes.items()},**delta)
            args.output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
            return not result.wasSuccessful()

if __name__=='__main__':sys.exit(main())
