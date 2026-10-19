"""Execute the Java-generated shell rc on a host POSIX shell. Not an Android PTY test."""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--junit", type=Path, required=True)
    parser.add_argument("--shell", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    suite = ET.parse(args.junit / "TEST-com.codepocket.ide.terminal.shell.ShellLaunchPlanTest.xml").getroot()
    assert int(suite.get("failures", 0)) == int(suite.get("errors", 0)) == 0
    fixtures = re.findall(r"^CODEPOCKET_SHELL_RC=(.+)$", suite.findtext("system-out", ""), re.MULTILINE)
    assert len(fixtures) == 1, "Expected exact Java-generated startup fixture"
    startup = base64.b64decode(fixtures[0], validate=True)
    prompt = b"\x01\r\x01\x1b]133;A\x07\x01CodePocket $ \x01\x1b]133;B\x07\x01"
    started_fixtures = re.findall(r"^CODEPOCKET_SHELL_STARTED=(.+)$", suite.findtext("system-out", ""), re.MULTILINE)
    assert len(started_fixtures) == 1, "Expected exact Java-generated command-start marker"
    started = base64.b64decode(started_fixtures[0], validate=True)
    shell = str(Path(args.shell).resolve()) if Path(args.shell).exists() else args.shell
    env = os.environ.copy()
    for key in ("ENV", "BASH_ENV", "PS1", "PS2"):
        env.pop(key, None)
    command = '. "$1"; printf "%s\\n" "$PS1"; printf "hello\\n"'
    rows = []
    with tempfile.TemporaryDirectory(prefix="codepocket-shell-") as folder:
        root = Path(folder)
        rc = root / "generated rc.sh"
        rc.write_bytes(startup)
        system = root / "system rc.sh"
        system.write_bytes(b"PS1=':/data/data/com.codepocket.ide/files/workspace $ '\n")
        old = subprocess.run([shell, "-c", command, "test", system.as_posix()], env=dict(env, PS1=prompt.decode()),
                             capture_output=True, timeout=10, check=True)
        assert b"]133;B" not in old.stdout and b":/data/data/" in old.stdout
        rows.append({"case": "old_environment_prompt_is_overwritten", "passed": True})
        exact = subprocess.run([shell, "-c", command, "test", rc.as_posix()], env=env,
                               capture_output=True, timeout=10, check=True)
        assert exact.stdout == prompt + b"\nhello\n", repr(exact.stdout)
        assert not exact.stderr, repr(exact.stderr)
        rows.append({"case": "exact_generated_startup_restores_prompt_and_runs_hello", "passed": True})
        # Relocate only the Android system rc pathname so a real host shell executes the include in order.
        relocated = root / "relocated rc.sh"
        assert startup.count(b"/system/etc/mkshrc") == 2
        relocated.write_bytes(startup.replace(b"/system/etc/mkshrc", b'"$CODEPOCKET_TEST_RC"'))
        ordered = subprocess.run([shell, "-c", command, "test", relocated.as_posix()],
                                 env=dict(env, CODEPOCKET_TEST_RC=system.as_posix()),
                                 capture_output=True, timeout=10, check=True)
        assert ordered.stdout == prompt + b"\nhello\n", repr(ordered.stdout)
        assert not ordered.stderr, repr(ordered.stderr)
        rows.append({"case": "system_rc_runs_before_application_prompt", "passed": True})
        run_start = root / "command-start.sh"
        run_start.write_bytes(started + b"printf 'hello from actual shell\\n'\n")
        launched = subprocess.run([shell, "-c", '. "$1"', "test", run_start.as_posix()], env=env,
                                  capture_output=True, timeout=10, check=True)
        assert launched.stdout == b"\x1b]133;C\x07hello from actual shell\n", repr(launched.stdout)
        assert not launched.stderr, repr(launched.stderr)
        rows.append({"case": "command_started_is_emitted_before_executed_output", "passed": True})
    result = {"host_shell": shell, "startup_sha256": hashlib.sha256(startup).hexdigest(),
              "cases": rows, "android_mksh_pty_executed": False, "chaquopy_executed": False}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Host shell startup regressions: 4/4 passed (not Android PTY or Chaquopy execution)")


if __name__ == "__main__":
    main()
