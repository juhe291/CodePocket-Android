from pathlib import Path
import argparse
import json
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(r"C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android")
ADB = ROOT / ".tools/android-sdk/platform-tools/adb.exe"
DEVICE = "UW5XDUMJ6PL7DU95"
OUT = ROOT / "verification/p0-device-retest-2026-10-16"
PACKAGE = "com.codepocket.ide"
TEST_DIR = "codex_p0_verify_20261016"
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args, check=True, data=None, timeout=40):
    result = subprocess.run([str(ADB), "-s", DEVICE, *map(str, args)], capture_output=True, input=data, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f"ADB {args}: {(result.stdout + result.stderr).decode('utf-8', 'replace')}")
    return result

def shell(*args, **kw):
    return adb("shell", shlex.join(list(map(str, args))), **kw)

def tree():
    shell("uiautomator", "dump", "/data/local/tmp/codepocket-p0.xml")
    xml = shell("cat", "/data/local/tmp/codepocket-p0.xml").stdout
    return ET.fromstring(xml), xml

def tap(node):
    coords = list(map(int, re.findall(r"\d+", node.get("bounds"))))
    shell("input", "tap", (coords[0] + coords[2]) // 2, (coords[1] + coords[3]) // 2)

def find(text):
    view, _ = tree()
    matches = [n for n in view.iter("node") if n.get("text") == text]
    if not matches:
        matches = [n for n in view.iter("node") if text in n.get("text", "")]
    if len(matches) != 1:
        raise RuntimeError(f"Expected one UI node for {text!r}, found {len(matches)}")
    return matches[0]

def inspect(tag):
    view, xml = tree()
    (OUT / (tag + ".xml")).write_bytes(xml)
    if any(n.get("package") == PACKAGE for n in view.iter("node")):
        (OUT / (tag + ".png")).write_bytes(adb("exec-out", "screencap", "-p").stdout)
    nodes = []
    for n in view.iter("node"):
        if n.get("text") or n.get("class", "").endswith("EditText"):
            nodes.append({k: n.get(k) for k in ("text", "class", "bounds", "focused", "package")})
    print(json.dumps(nodes, ensure_ascii=False, indent=2), flush=True)

def replace_field(which, value):
    if which == "code":
        tap(find("收键盘"))
        time.sleep(.3)
    view, _ = tree()
    if which == "code" and not any(TEST_DIR in n.get("text", "") for n in view.iter("node")):
        raise RuntimeError("Only edit synthetic test files")
    fields = [n for n in view.iter("node") if n.get("package") == PACKAGE and n.get("class") == "android.widget.EditText"]
    if not fields:
        raise RuntimeError("No app edit field")
    target = fields[0 if which == "code" else -1]
    coords = list(map(int, re.findall(r"\d+", target.get("bounds"))))
    shell("input", "tap", coords[0] + 65, coords[1] + 35)
    time.sleep(.5)
    view, _ = tree()
    focused = [n for n in view.iter("node") if n.get("class") == "android.widget.EditText" and n.get("focused") == "true"]
    if not focused:
        fields = [n for n in view.iter("node") if n.get("class") == "android.widget.EditText" and n.get("package") == PACKAGE]
        tap(fields[0 if which == "code" else -1])
        time.sleep(.3)
    shell("input", "keycombination", "-t", "200", "113", "29")
    time.sleep(.2)
    shell("input", "text", value.replace(" ", "%s"))
    time.sleep(1)

def seed():
    shell("run-as", PACKAGE, "mkdir", "files/workspace/" + TEST_DIR)
    programs = {
        "a.py": "import math\nprint(sum(map(int, input().split())))\nprint(math.isqrt(81))\nprint('中文输出')\n",
        "b.py": "print(int(input()) * 2)\n",
        "slow.py": "import time\ntime.sleep(60)\nprint('should not finish')\n",
        "edit.py": "print(1)\n",
        "error.py": "raise ValueError('device-test')\n",
    }
    for name, source in programs.items():
        command = "cat > " + shlex.quote("files/workspace/" + TEST_DIR + "/" + name)
        shell("run-as", PACKAGE, "sh", "-c", command, data=source.encode())
    print("SEEDED=" + TEST_DIR)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["inspect", "click", "replace-code", "replace-stdin", "seed", "key", "read-test", "pid"])
    parser.add_argument("value", nargs="?", default="device-state")
    args = parser.parse_args()
    if args.action == "inspect":
        inspect(args.value)
    elif args.action == "click":
        tap(find(args.value))
    elif args.action in ("replace-code", "replace-stdin"):
        replace_field("code" if args.action.endswith("code") else "stdin", args.value)
    elif args.action == "seed":
        seed()
    elif args.action == "key":
        shell("input", "keyevent", args.value)
    elif args.action == "read-test":
        if args.value not in ("a.py", "b.py", "edit.py", "slow.py", "error.py"):
            raise RuntimeError("Not a synthetic test file")
        print(shell("run-as", PACKAGE, "cat", "files/workspace/" + TEST_DIR + "/" + args.value).stdout.decode())
    elif args.action == "pid":
        result = shell("pidof", PACKAGE + ":python", check=False)
        print("PYTHON_PID=" + result.stdout.decode().strip())

if __name__ == "__main__":
    main()
