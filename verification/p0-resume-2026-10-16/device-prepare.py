from pathlib import Path
import json
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(r"C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android")
ADB = ROOT / ".tools/android-sdk/platform-tools/adb.exe"
DEVICE = "UW5XDUMJ6PL7DU95"
OUT = ROOT / "verification/p0-device-retest-2026-10-16"
PACKAGE = "com.codepocket.ide"
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args, check=True, timeout=40):
    result = subprocess.run([str(ADB), "-s", DEVICE, *args], capture_output=True, timeout=timeout)
    if check and result.returncode:
        raise RuntimeError(f"ADB {args}: {result.stderr.decode('utf-8', 'replace')}")
    return result

backup = ROOT / ".tools/device-backups" / time.strftime("%Y%m%d-%H%M%S")
backup.mkdir(parents=True)
old = adb("shell", "pm", "path", PACKAGE).stdout.decode().strip()
if old:
    result = adb("exec-out", "run-as", PACKAGE, "tar", "-cf", "-", "files", "shared_prefs")
    if not result.stdout:
        raise RuntimeError("Existing app data backup is empty; will not install")
    (backup / "app-data.tar").write_bytes(result.stdout)
    print(f"BACKUP={backup / 'app-data.tar'} ({len(result.stdout)} bytes)", flush=True)

apk = ROOT / "dist/CodePocket-P0-resume-2026-10-16-debug.apk"
if not apk.exists():
    raise RuntimeError("Verified follow-up APK not ready")
installed = adb("install", "-r", str(apk), timeout=120)
(OUT / "device-install.txt").write_bytes(installed.stdout + installed.stderr)
print(installed.stdout.decode("utf-8", "replace"), flush=True)
started = adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.MainActivity")
(OUT / "device-launch.txt").write_bytes(started.stdout + started.stderr)
print(started.stdout.decode("utf-8", "replace"), flush=True)
time.sleep(2)
adb("shell", "uiautomator", "dump", "/data/local/tmp/codepocket-p0.xml")
xml = adb("shell", "cat", "/data/local/tmp/codepocket-p0.xml").stdout
(OUT / "device-launch-ui.xml").write_bytes(xml)
tree = ET.fromstring(xml)
app_nodes = [n for n in tree.iter("node") if n.get("package") == PACKAGE]
if not app_nodes:
    print("APP_NOT_VISIBLE: unlock device or allow installation/start prompt", flush=True)
else:
    (OUT / "device-launch.png").write_bytes(adb("exec-out", "screencap", "-p").stdout)
    for node in app_nodes:
        if node.get("text") or node.get("class", "").endswith("EditText"):
            print(json.dumps({k: node.get(k) for k in ("text", "class", "bounds", "focused")}, ensure_ascii=False), flush=True)
