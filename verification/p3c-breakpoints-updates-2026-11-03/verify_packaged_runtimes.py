"""Execute the packaged (actual APK) Git and formatting Python modules against their host tests.

The pinned-baseline scripts compare against one historical release candidate and do not accept a
new batch; this executes the same real bytecode taken from this APK instead, with the same
requirements-common.imy wheel payload extracted from the artifact.
"""
import importlib
import io
import json
import marshal
import sys
import tempfile
import types
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = Path(__file__).parent
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
PINS = {"dulwich", "urllib3", "autopep8", "pycodestyle"}


def run_module(apk, name, module_file, test_module):
    with zipfile.ZipFile(io.BytesIO(apk.read("assets/chaquopy/app.imy"))) as app:
        code = app.read(module_file)
    module = types.ModuleType(name)
    module.__file__ = f"{APK}!{module_file}"
    exec(marshal.loads(code[16:]), module.__dict__)
    sys.modules[name] = module
    test = importlib.import_module(test_module)
    assert getattr(test, name) is module, f"{test_module} did not use the packaged {name} module"
    result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(test))
    return result


with zipfile.ZipFile(APK) as apk:
    with tempfile.TemporaryDirectory(prefix="packaged-runtimes-", dir=ROOT / ".tools") as folder:
        target = Path(folder)
        with zipfile.ZipFile(io.BytesIO(apk.read("assets/chaquopy/requirements-common.imy"))) as requirements:
            for item in requirements.infolist():
                if item.is_dir():
                    continue
                path = target / item.filename
                assert path.resolve().is_relative_to(target.resolve())
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(requirements.read(item))
        for name in list(sys.modules):
            if name.split(".")[0] in PINS:
                del sys.modules[name]
        sys.path.insert(0, str(target))
        loaded = {}
        for name in sorted(PINS):
            package = importlib.import_module(name)
            assert Path(package.__file__).resolve().is_relative_to(target.resolve()), package.__file__
            loaded[name] = getattr(package, "__version__", "?")
        sys.path.insert(0, str(ROOT / "tests"))
        git = run_module(apk, "git_workspace", "git_workspace.pyc", "test_git_workspace")
        formatting = run_module(apk, "code_formatting", "code_formatting.pyc", "test_code_formatting")
summary = {
    "apk": str(APK.relative_to(ROOT)).replace("\\", "/"),
    "packaged_dependencies": {k: (list(map(str, v)) if isinstance(v, tuple) else v) for k, v in loaded.items()},
    "packaged_git_tests": git.testsRun,
    "packaged_git_failures": len(git.failures) + len(git.errors),
    "packaged_formatting_tests": formatting.testsRun,
    "packaged_formatting_failures": len(formatting.failures) + len(formatting.errors),
    "device_execution": False,
}
(EVIDENCE / "packaged-runtimes.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2))
if summary["packaged_git_failures"] or summary["packaged_formatting_failures"]:
    raise SystemExit(1)
