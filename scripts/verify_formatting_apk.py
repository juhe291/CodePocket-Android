"""Inspect the formatting delta and run tests with actual APK bytecode. Host evidence only."""
import argparse
import hashlib
import importlib.machinery
import importlib.util
import io
import json
import marshal
from pathlib import Path
import sys
import tempfile
import types
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PINS = {
    'autopep8': ('2.3.2', 'ce8ad498672c845a0c3de2629c15b635ec2b05ef8177a6e7c91c74f3e9b51128'),
    'pycodestyle': ('2.14.0', 'dd6bf7cb4ee77f8e016f9c8e74a35ddd9f67e1d5fd4184d86c3b98e07099f42d'),
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def same_payload(before, after, name):
    if before == after:
        return
    assert name.endswith('.pyc') and len(before) >= 16 and len(after) >= 16, name
    assert before[:8] == after[:8] and before[12:] == after[12:], 'Old Python payload changed: ' + name


def compiled_source(bytecode, source):
    assert bytecode[:4] == importlib.util.MAGIC_NUMBER
    code = marshal.loads(bytecode[16:])
    assert code == compile(source, code.co_filename, 'exec'), 'Bundled bytecode differs from approved source'


def verify_delta(apk, baseline):
    """Explicit opt-in for this addition. Original C++ verifier defaults remain strict."""
    old_native = sorted(n for n in baseline.namelist() if n.startswith('lib/') and n.endswith('.so'))
    old_python = sorted(n for n in baseline.namelist() if n.startswith('assets/chaquopy/'))
    assert len(old_native) == 25 and len(old_python) == 20, 'Use the previous workbench candidate as the formatting baseline'
    assert set(old_native) == {n for n in apk.namelist() if n.startswith('lib/') and n.endswith('.so')}
    assert set(old_python) == {n for n in apk.namelist() if n.startswith('assets/chaquopy/')}
    exceptions = {'assets/chaquopy/app.imy', 'assets/chaquopy/requirements-common.imy', 'assets/chaquopy/build.json'}
    for name in old_native + old_python:
        if name not in exceptions:
            assert apk.read(name) == baseline.read(name), 'Original runtime changed: ' + name
    with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/app.imy'))) as current, \
            zipfile.ZipFile(io.BytesIO(baseline.read('assets/chaquopy/app.imy'))) as old:
        assert current.testzip() is None and len(current.namelist()) == len(set(current.namelist()))
        assert set(old.namelist()) == {'runner.pyc', 'diagnostics.pyc', 'semantics.pyc', '__pycache__/'}
        assert set(current.namelist()) == set(old.namelist()) | {'code_formatting.pyc'}
        for name in old.namelist():
            same_payload(old.read(name), current.read(name), name)
        compiled_source(current.read('code_formatting.pyc'), (ROOT/'app/src/main/python/code_formatting.py').read_bytes())
    added = []
    with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/requirements-common.imy'))) as current, \
            zipfile.ZipFile(io.BytesIO(baseline.read('assets/chaquopy/requirements-common.imy'))) as old:
        assert current.testzip() is None and len(current.namelist()) == len(set(current.namelist()))
        assert set(old.namelist()) <= set(current.namelist())
        for name in old.namelist():
            same_payload(old.read(name), current.read(name), name)
        added = sorted(set(current.namelist()) - set(old.namelist()))
        assert all(name in ('autopep8.py', 'autopep8.pyc', 'pycodestyle.py', 'pycodestyle.pyc') or
                   name.startswith(('autopep8-2.3.2.dist-info/', 'pycodestyle-2.14.0.dist-info/')) for name in added)
        assert len(added) == 19
        for module, (version, sha) in PINS.items():
            wheel = ROOT/'.tools/semantic-wheels'/f'{module}-{version}-py2.py3-none-any.whl'
            assert digest(wheel.read_bytes()) == sha
            with zipfile.ZipFile(wheel) as source:
                python_source = source.read(module+'.py')
                assert current.read(module+'.py') == python_source
                compiled_source(current.read(module+'.pyc'), python_source)
                license_data = source.read(f'{module}-{version}.dist-info/LICENSE')
                assert current.read(f'{module}-{version}.dist-info/LICENSE') == license_data
                assert apk.read(f'assets/format-licenses/{module.upper()}-LICENSE.txt') == license_data
                assert b'Permission is hereby granted' in license_data
    current = json.loads(apk.read('assets/chaquopy/build.json'))
    old = json.loads(baseline.read('assets/chaquopy/build.json'))
    assert set(current['extract_packages']) == set(old['extract_packages']) | set(PINS)
    current.pop('extract_packages'); old.pop('extract_packages')
    for name in ('app.imy', 'requirements-common.imy'):
        assert current['assets'].pop(name) == hashlib.sha1(apk.read('assets/chaquopy/'+name)).hexdigest()
        assert old['assets'].pop(name) == hashlib.sha1(baseline.read('assets/chaquopy/'+name)).hexdigest()
    assert current == old, 'Unapproved build metadata changed'
    return dict(original_native_unchanged=old_native,
                original_python_assets_unchanged=[n for n in old_python if n not in exceptions],
                approved_assets=sorted(exceptions), approved_dependency_entries=added,
                original_python_module_payloads_unchanged=True, pinned_wheel_sources_verified=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    with zipfile.ZipFile(args.apk) as apk, zipfile.ZipFile(args.baseline) as baseline:
        assert apk.testzip() is None
        delta = verify_delta(apk, baseline)
        with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/app.imy'))) as app:
            backend = app.read('code_formatting.pyc')
        with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/requirements-common.imy'))) as requirements:
            dependencies = {name: requirements.read(name+'.pyc') for name in PINS}
    with tempfile.TemporaryDirectory(prefix='format-apk-', dir=ROOT/'.tools') as directory:
        target = Path(directory)
        # autopep8 reloads pycodestyle during import. Its actual APK .pyc must
        # be discoverable by the import machinery as well as by our loader.
        sys.path.insert(0, str(target))
        loaded = {}
        for name in ('pycodestyle', 'autopep8'):
            file = target/(name+'.pyc'); file.write_bytes(dependencies[name])
            loader = importlib.machinery.SourcelessFileLoader(name, str(file))
            spec = importlib.util.spec_from_loader(name, loader)
            module = importlib.util.module_from_spec(spec); sys.modules[name] = module; loader.exec_module(module)
            assert isinstance(module.__loader__, importlib.machinery.SourcelessFileLoader)
            assert module.__version__ == PINS[name][0]
            loaded[name] = module
        module = types.ModuleType('code_formatting'); module.__file__ = str(args.apk)+'!code_formatting.pyc'
        exec(marshal.loads(backend[16:]), module.__dict__); sys.modules['code_formatting'] = module
        sys.path.insert(0, str(ROOT/'tests'))
        import test_code_formatting
        assert test_code_formatting.code_formatting is module
        assert test_code_formatting.autopep8 is loaded['autopep8'] and test_code_formatting.pycodestyle is loaded['pycodestyle']
        result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(test_code_formatting))
        report = dict(apk_sha256=digest(args.apk.read_bytes()), baseline_sha256=digest(args.baseline.read_bytes()),
                      tests=result.testsRun, failures=len(result.failures), errors=len(result.errors),
                      backend_bytecode_sha256=digest(backend), dependency_bytecode_sha256={k:digest(v) for k,v in dependencies.items()},
                      actual_apk_bytecode_loaded=True, device_execution=False, **delta)
        args.output.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
        print(json.dumps({k:v for k,v in report.items() if not isinstance(v,list)}, indent=2))
        return not result.wasSuccessful()


if __name__ == '__main__':
    sys.exit(main())
