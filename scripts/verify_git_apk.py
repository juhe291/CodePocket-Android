"""Verify the pinned Git-only APK delta and execute its actual bytecode on the host."""
import argparse
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

ROOT = Path(__file__).resolve().parents[1]
PINS = {
    'dulwich': ('1.2.20', 'ab521391c3475e7c507f14aeacad0cdf26e97ba5050d8010a1ff34e417c9a842', 'COPYING'),
    'urllib3': ('2.8.0', '0cf3cae568d36aa9576b28dfb35f11328f1cb974ca7647d9475ebb86c75ac6e3', 'LICENSE.txt'),
}
BASELINE = '59e13ca581f0806ba25df937772228e3802c5ee1896aebb091cf80e174756668'


def verify_delta(apk, baseline):
    native = sorted(n for n in baseline.namelist() if n.startswith('lib/') and n.endswith('.so'))
    assets = sorted(n for n in baseline.namelist() if n.startswith('assets/chaquopy/'))
    assert len(native) == 25 and len(assets) == 20
    assert set(native) == {n for n in apk.namelist() if n.startswith('lib/') and n.endswith('.so')}
    assert set(assets) == {n for n in apk.namelist() if n.startswith('assets/chaquopy/')}
    changed = {'assets/chaquopy/app.imy', 'assets/chaquopy/requirements-common.imy', 'assets/chaquopy/build.json'}
    for name in native + assets:
        if name not in changed:
            assert apk.read(name) == baseline.read(name), 'Original runtime changed: ' + name
    with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/app.imy'))) as current, \
            zipfile.ZipFile(io.BytesIO(baseline.read('assets/chaquopy/app.imy'))) as old:
        assert set(old.namelist()) == {'runner.pyc', 'diagnostics.pyc', 'semantics.pyc', 'code_formatting.pyc', '__pycache__/'}
        assert current.testzip() is None and len(current.namelist()) == len(set(current.namelist()))
        assert set(current.namelist()) == set(old.namelist()) | {'git_workspace.pyc'}
        for name in old.namelist():
            same_payload(old.read(name), current.read(name), name)
        compiled_source(current.read('git_workspace.pyc'), (ROOT/'app/src/main/python/git_workspace.py').read_bytes())
    approved = set()
    with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/requirements-common.imy'))) as current, \
            zipfile.ZipFile(io.BytesIO(baseline.read('assets/chaquopy/requirements-common.imy'))) as old:
        assert current.testzip() is None and len(current.namelist()) == len(set(current.namelist()))
        assert set(old.namelist()) <= set(current.namelist())
        for name in old.namelist():
            same_payload(old.read(name), current.read(name), name)
        added = set(current.namelist()) - set(old.namelist())
        for module, (version, sha, license_name) in PINS.items():
            wheel = ROOT/'.tools/semantic-wheels'/f'{module}-{version}-py3-none-any.whl'
            assert digest(wheel.read_bytes()) == sha
            prefix = f'{module}-{version}.dist-info/'
            with zipfile.ZipFile(wheel) as source:
                for name in source.namelist():
                    if module=='dulwich' and name in (f'dulwich-{version}.data/scripts/dul-receive-pack', f'dulwich-{version}.data/scripts/dul-upload-pack'):
                        continue  # Chaquopy does not install host command-line entry scripts.
                    assert name.startswith((module+'/', prefix)), name
                    if name.endswith('/') or name in (prefix+'RECORD', prefix+'WHEEL'):
                        continue  # Chaquopy omits pip installation bookkeeping.
                    approved.add(name)
                    assert current.read(name) == source.read(name), 'Pinned wheel file differs: ' + name
                    if name.endswith('.py'):
                        bytecode = name[:-3]+'.pyc'; approved.add(bytecode)
                        compiled_source(current.read(bytecode), source.read(name))
                for name, data in ((prefix+'INSTALLER', b'pip\n'), (prefix+'REQUESTED', b'')):
                    approved.add(name); assert current.read(name) == data
                license_data = source.read(prefix+'licenses/'+license_name)
                assert apk.read(f'assets/git-licenses/{module.upper()}-LICENSE.txt') == license_data
        directories = {part+'/' for name in approved for part in [name.rsplit('/', i)[0] for i in range(1, name.count('/')+1)]}
        assert added == approved | directories, ('Unexpected or missing Git wheel entries', sorted(added-(approved|directories)), sorted((approved|directories)-added))
    current = json.loads(apk.read('assets/chaquopy/build.json')); old = json.loads(baseline.read('assets/chaquopy/build.json'))
    assert set(current.pop('extract_packages')) == set(old.pop('extract_packages')) | set(PINS)
    import hashlib
    for name in ('app.imy', 'requirements-common.imy'):
        assert current['assets'].pop(name) == hashlib.sha1(apk.read('assets/chaquopy/'+name)).hexdigest()
        assert old['assets'].pop(name) == hashlib.sha1(baseline.read('assets/chaquopy/'+name)).hexdigest()
    assert current == old, 'Unapproved Chaquopy build metadata changed'
    return dict(original_native_unchanged=native, original_python_assets_unchanged=[n for n in assets if n not in changed],
                approved_assets=sorted(changed), approved_dependency_entries=sorted(added), pinned_wheel_sources_verified=True,
                original_python_module_payloads_unchanged=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path); parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True); args = parser.parse_args()
    assert digest(args.baseline.read_bytes()) == BASELINE, 'Use the verified formatting candidate as the Git baseline'
    with zipfile.ZipFile(args.apk) as apk, zipfile.ZipFile(args.baseline) as baseline:
        assert apk.testzip() is None and len(apk.namelist()) == len(set(apk.namelist()))
        delta = verify_delta(apk, baseline)
        with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/app.imy'))) as app:
            backend = app.read('git_workspace.pyc')
            formatting = app.read('code_formatting.pyc')
        with tempfile.TemporaryDirectory(prefix='git-apk-', dir=ROOT/'.tools') as folder:
            target = Path(folder)
            with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/requirements-common.imy'))) as requirements:
                for name in requirements.namelist():
                    if name.endswith(('/', '.py')) or not name.startswith(('dulwich/', 'urllib3/', 'dulwich-1.2.20.dist-info/', 'urllib3-2.8.0.dist-info/', 'autopep8.pyc', 'pycodestyle.pyc')):
                        continue
                    path = target/name; assert path.resolve().is_relative_to(target.resolve())
                    path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(requirements.read(name))
            sys.path.insert(0, str(target))
            for name in list(sys.modules):
                if name.split('.')[0] in PINS:
                    del sys.modules[name]
            loaded = {}
            for name, (version, _, _) in PINS.items():
                package = importlib.import_module(name)
                assert isinstance(package.__loader__, importlib.machinery.SourcelessFileLoader)
                assert Path(package.__file__).resolve().is_relative_to(target.resolve())
                actual = getattr(package, '__version__')
                assert ('.'.join(map(str, actual)) if isinstance(actual, tuple) else actual) == version
                loaded[name] = digest((target/name/'__init__.pyc').read_bytes())
            module = types.ModuleType('git_workspace'); module.__file__ = str(args.apk)+'!git_workspace.pyc'
            exec(marshal.loads(backend[16:]), module.__dict__); sys.modules['git_workspace'] = module
            sys.path.insert(0, str(ROOT/'tests')); import test_git_workspace
            assert test_git_workspace.git_workspace is module
            result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(test_git_workspace))
            formatter = types.ModuleType('code_formatting'); formatter.__file__ = str(args.apk)+'!code_formatting.pyc'
            exec(marshal.loads(formatting[16:]), formatter.__dict__); sys.modules['code_formatting'] = formatter
            import test_code_formatting
            assert test_code_formatting.code_formatting is formatter
            for name in ('autopep8','pycodestyle'):
                dependency = importlib.import_module(name)
                assert isinstance(dependency.__loader__, importlib.machinery.SourcelessFileLoader)
                assert Path(dependency.__file__).resolve().is_relative_to(target.resolve())
            formatted = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(test_code_formatting))
            report = dict(apk_sha256=digest(args.apk.read_bytes()), baseline_sha256=BASELINE, tests=result.testsRun,
                          failures=len(result.failures), errors=len(result.errors), backend_bytecode_sha256=digest(backend),
                          formatting_tests=formatted.testsRun, formatting_failures=len(formatted.failures), formatting_errors=len(formatted.errors),
                          package_bytecode_sha256=loaded, actual_apk_bytecode_loaded=True, device_execution=False, **delta)
            args.output.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
            print(json.dumps({k:v for k,v in report.items() if not isinstance(v,list)}, indent=2))
            return not (result.wasSuccessful() and formatted.wasSuccessful())


if __name__ == '__main__':
    sys.exit(main())
