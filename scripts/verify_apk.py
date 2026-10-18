"""Inspect the actual APK and run the Python regression suite against its bundled bytecode.

Requires the same Python minor version used for the build (CodePocket: 3.13).
This is a host-side artifact check, not Android device execution.
"""
import argparse
import hashlib
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


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path, default=root / "verification" / "apk-inspection.json")
    args = parser.parse_args()
    temp_root = root / ".tools" / "test-tmp"
    temp_root.mkdir(parents=True, exist_ok=True)
    tempfile.tempdir = str(temp_root)

    with zipfile.ZipFile(args.apk) as archive:
        assert archive.testzip() is None, "APK CRC failure"
        names = archive.namelist()
        abis = sorted({n.split("/")[1] for n in names if n.startswith("lib/") and n.endswith(".so")})
        assert abis == ["arm64-v8a"], f"Unexpected native ABIs: {abis}"
        assert "lib/arm64-v8a/libpython3.13.so" in names
        assert any(n.startswith("classes") and n.endswith(".dex") for n in names)
        python_assets = sorted(n for n in names if n.startswith("assets/chaquopy/"))
        checked_archives = []
        for entry in python_assets:
            if entry.endswith(".imy"):
                with zipfile.ZipFile(io.BytesIO(archive.read(entry))) as nested:
                    assert nested.testzip() is None, f"Python asset CRC failure: {entry}"
                checked_archives.append(entry)
        with zipfile.ZipFile(io.BytesIO(archive.read("assets/chaquopy/app.imy"))) as app_archive:
            runner_entry = next(n for n in app_archive.namelist() if n == "runner.pyc")
            bytecode = app_archive.read(runner_entry)

    assert bytecode[:4] == importlib.util.MAGIC_NUMBER, "Host Python must match the APK bytecode version"
    bundled_runner = types.ModuleType("runner")
    bundled_runner.__file__ = f"{args.apk}!assets/chaquopy/app.imy!{runner_entry}"
    exec(marshal.loads(bytecode[16:]), bundled_runner.__dict__)
    bundled_runner.__packaged_bytecode__ = bytecode
    sys.modules["runner"] = bundled_runner
    sys.path.insert(0, str(root / "tests"))
    import test_runner
    assert test_runner.run_code is bundled_runner.run_code, "Tests must use APK bytecode"
    import test_terminal_runner
    assert test_terminal_runner.runner is bundled_runner
    suite = unittest.TestSuite([unittest.defaultTestLoader.loadTestsFromModule(test_runner),
        unittest.defaultTestLoader.loadTestsFromModule(test_terminal_runner)])
    result = unittest.TextTestRunner(verbosity=2).run(suite)

    report = {
        "apk": str(args.apk.resolve()),
        "size_bytes": args.apk.stat().st_size,
        "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        "zip_crc_passed": True,
        "abis": abis,
        "python_archives_checked": checked_archives,
        "bundled_runner": runner_entry,
        "bundled_runner_tests": result.testsRun,
        "test_failures": len(result.failures),
        "test_errors": len(result.errors),
        "device_execution": False,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    raise SystemExit(main())

