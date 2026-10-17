"""Compile/execute snippets emitted by the actual Kotlin tests; host verification only."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


MARKER = "__CODEPOCKET_SNIPPET_OK__"


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--junit", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--cpp-compiler", required=True)
    args = parser.parse_args()
    suite_file = args.junit / "TEST-com.codepocket.ide.SnippetTest.xml"
    suite = ET.parse(suite_file).getroot()
    assert suite.get("failures") == "0" and suite.get("errors") == "0", "Kotlin snippet tests failed"
    output = suite.findtext("system-out", "")
    matches = re.findall(r"^CODEPOCKET_SNIPPET_FIXTURES=(.+)$", output, re.MULTILINE)
    assert len(matches) == 1, "Missing or duplicate generated Kotlin fixtures"
    fixtures = json.loads(matches[0])
    assert len(fixtures) == 14 and sum(bool(item["cpp"]) for item in fixtures) == 7
    compiler = shutil.which(args.cpp_compiler)
    if compiler is None:
        raise SystemExit("C++ host compiler unavailable: " + args.cpp_compiler)
    env = os.environ.copy()
    env["PATH"] = str(Path(compiler).parent) + os.pathsep + env.get("PATH", "")
    version = subprocess.run([compiler, "--version"], capture_output=True, text=True,
                             encoding="utf-8", errors="replace", timeout=10, check=True).stdout.splitlines()[0]
    args.output.parent.mkdir(parents=True, exist_ok=True)
    generated = args.output.parent / "snippet-fixtures"
    generated.mkdir(exist_ok=True)
    spec = importlib.util.spec_from_file_location("snippet_runner", root / "app/src/main/python/runner.py")
    runner = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runner)
    rows = []
    for index, fixture in enumerate(fixtures):
        identifier = fixture["id"]
        assert re.fullmatch(r"(?:cpp|py)\.[a-z]+", identifier)
        begin = time.monotonic()
        code = fixture["code"]
        row = {"id": identifier, "variant": fixture["variant"], "cpp": fixture["cpp"],
               "template_sha256": hashlib.sha256(code.encode("utf-8")).hexdigest()}
        try:
            filename = f"{index:02d}-{identifier.replace('.', '-')}"
            if fixture["cpp"]:
                header = "#include <iostream>\n#include <vector>\nstd::vector<int> values{1, 2};\n"
                if identifier == "cpp.main":
                    driver = header + code
                elif identifier == "cpp.func":
                    driver = header + code + f'\nint main() {{ solve(); std::cout << "{MARKER}"; }}\n'
                else:
                    driver = header + "int main() {\n" + code + f'\nstd::cout << "{MARKER}";\n}}\n'
                source = generated / (filename + ".cpp")
                binary = generated / (filename + (".exe" if os.name == "nt" else "-bin"))
                source.write_text(driver, encoding="utf-8", newline="")
                compiled = subprocess.run([compiler, "-std=c++17", "-O0", "-Wall", "-Wextra",
                    "-finput-charset=UTF-8", "-fexec-charset=UTF-8", source.name, "-o", binary.name],
                    capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=30, env=env, cwd=generated.resolve())
                row.update(compile_exit_code=compiled.returncode, compiler_stderr=compiled.stderr)
                if compiled.returncode != 0:
                    raise RuntimeError("C++17 compilation failed")
                ran = subprocess.run([str(binary.resolve())], capture_output=True, text=True,
                    encoding="utf-8", errors="replace", timeout=5, env=env)
                row.update(exit_code=ran.returncode, stdout=ran.stdout, stderr=ran.stderr)
                assert ran.returncode == 0 and not ran.stderr
                assert (ran.stdout == "") if identifier == "cpp.main" else ran.stdout.endswith(MARKER)
            else:
                driver = code + f'\nprint("{MARKER}")\n'
                source = generated / (filename + ".py")
                source.write_text(driver, encoding="utf-8", newline="")
                # Use the project's real Python executor, not a made-up output or alternate evaluator.
                ran = json.loads(runner.run_code(driver, "", str(source.resolve()), str(generated.resolve())))
                row.update(ran)
                assert ran["error_kind"] == "success" and ran["exit_code"] == 0 and not ran["stderr"]
                assert ran["stdout"].endswith(MARKER + "\n")
            row.update(driver_source=source.as_posix(), driver_sha256=hashlib.sha256(driver.encode("utf-8")).hexdigest(), passed=True)
        except Exception as error:
            row.update(passed=False, error=f"{type(error).__name__}: {error}")
        row["elapsed_ms"] = round((time.monotonic() - begin) * 1000)
        rows.append(row)
        print(f'{identifier} [{fixture["variant"]}]: {"PASS" if row["passed"] else "FAIL"}')
    report = {"host_python": sys.version.split()[0], "cpp_compiler": compiler, "cpp_version": version,
              "source": suite_file.as_posix(), "fixtures": rows,
              "python_passed": sum(row["passed"] and not row["cpp"] for row in rows),
              "cpp_passed": sum(row["passed"] and row["cpp"] for row in rows),
              "failures": sum(not row["passed"] for row in rows), "device_execution": False}
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f'Python {report["python_passed"]}/7; C++17 {report["cpp_passed"]}/7; failures={report["failures"]}')
    return 0 if report["failures"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
