from pathlib import Path
import hashlib
import importlib.util
import json
import shlex
import time
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("device_tool", Path(__file__).with_name("device-tool.py"))
d = importlib.util.module_from_spec(spec)
spec.loader.exec_module(d)
results = []

def record(case, **evidence):
    results.append({"case": case, "passed": True, **evidence})
    (d.OUT / "device-core-results.json").write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(results[-1], ensure_ascii=False), flush=True)

def snapshot(tag):
    view, xml = d.tree()
    (d.OUT / (tag + ".xml")).write_bytes(xml)
    (d.OUT / (tag + ".png")).write_bytes(d.adb("exec-out", "screencap", "-p").stdout)
    return view

def click(text):
    d.tap(d.find(text))

def open_file(name):
    click("☰ 文件")
    folder = d.find(d.TEST_DIR)
    if folder.get("text", "").startswith("▸"):
        d.tap(folder)
    click("🐍 " + name)
    time.sleep(.4)

def fields(view):
    return [n for n in view.iter("node") if n.get("class") == "android.widget.EditText" and n.get("package") == d.PACKAGE]

def expect_output(expected, tag):
    click("▶ 运行")
    time.sleep(.5)
    view = snapshot(tag)
    assert any(n.get("text") == expected for n in view.iter("node")), f"Missing actual output: {expected!r}"
    assert any("✓ 运行完成" in n.get("text", "") for n in view.iter("node"))

def write_test(name, content):
    target = "files/workspace/" + d.TEST_DIR + "/" + name
    d.shell("run-as", d.PACKAGE, "sh", "-c", "cat > " + shlex.quote(target), data=content.encode())

def read_test(name):
    return d.adb("exec-out", "run-as", d.PACKAGE, "cat", "files/workspace/" + d.TEST_DIR + "/" + name).stdout.decode()

def saved_navigation(path):
    deadline = time.monotonic() + 8
    while time.monotonic() < deadline:
        raw = d.adb("exec-out", "run-as", d.PACKAGE, "cat", "shared_prefs/codepocket_v2.xml").stdout
        values = {n.get("name"): n.text for n in ET.fromstring(raw) if n.tag == "string"}
        if values.get("activeFile:internal") == path and path in json.loads(values.get("opened:internal", "[]")):
            current = next(s for s in json.loads(values["sessions:internal"]) if s["path"] == path)
            (d.OUT / "device-navigation-before-stop.json").write_text(json.dumps({"active": path,
                "stable_id": current["id"], "opened_contains_active": True}, indent=2), encoding="utf-8")
            return current
        time.sleep(.2)
    raise AssertionError("Active file/tab/identity were not persisted before process stop")

try:
    open_file("a.py")
    d.replace_field("stdin", "7 8")
    click("收键盘")
    expect_output("15\n9\n中文输出\n", "device-python-a")
    record("python_real_stdin_stdlib_unicode", stdout="15\n9\n中文输出\n", source="a.py", engine="Chaquopy on Android")

    open_file("b.py")
    d.replace_field("stdin", "21")
    click("收键盘")
    expect_output("42\n", "device-python-b")
    open_file("a.py")
    assert fields(snapshot("device-stdin-a-restored"))[-1].get("text") == "7 8"
    open_file("b.py")
    assert fields(snapshot("device-stdin-b-restored"))[-1].get("text") == "21"
    record("per_file_stdin_switching", a="7 8", b="21")

    open_file("edit.py")
    d.replace_field("code", "42")
    click("收键盘")
    assert read_test("edit.py") == "42", repr(read_test("edit.py"))
    record("editor_input_autosaved_to_actual_file", file="edit.py", text="42")

    current = saved_navigation(d.TEST_DIR + "/edit.py")
    record("active_file_and_identity_persisted_before_process_stop", file=current["path"])
    key = "internal:" + current["id"]
    checkpoint = {"key": key, "path": current["path"], "text": "42", "baseHash": hashlib.sha256(b"print(1)\n").hexdigest()}
    name = "files/drafts/" + hashlib.sha256(key.encode()).hexdigest() + ".json"
    d.shell("run-as", d.PACKAGE, "sh", "-c", "cat > " + shlex.quote(name), data=json.dumps(checkpoint).encode())
    d.shell("am", "force-stop", d.PACKAGE)
    d.shell("am", "start", "-W", "-n", d.PACKAGE + "/.MainActivity")
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        ready, _ = d.tree()
        if fields(ready) and fields(ready)[0].get("text") == "42" and any(
                current["path"] in n.get("text", "") for n in ready.iter("node")):
            break
        time.sleep(.2)
    else:
        raise AssertionError("Cold start did not restore the saved test file and checkpoint")
    view = snapshot("device-checkpoint-restored")
    assert fields(view)[0].get("text") == "42"
    assert not any("●" in n.get("text", "") for n in view.iter("node"))
    d.replace_field("code", "43")
    click("收键盘")
    assert read_test("edit.py") == "43"
    record("stale_checkpoint_recovery_then_next_save", injected_checkpoint="same saved text, old base hash", saved="43")

    before = {name: d.shell("settings", "get", "system", name).stdout.decode().strip() for name in ("accelerometer_rotation", "user_rotation")}
    try:
        d.shell("settings", "put", "system", "accelerometer_rotation", "0")
        d.shell("settings", "put", "system", "user_rotation", "1")
        time.sleep(2)
        view = snapshot("device-landscape")
        assert fields(view)[0].get("text") == "43"
        d.replace_field("code", "44")
        click("收键盘")
        assert read_test("edit.py") == "44"
        record("landscape_state_and_next_save", saved="44")
    finally:
        for name, value in before.items():
            if value == "null":
                d.shell("settings", "delete", "system", name)
            else:
                d.shell("settings", "put", "system", name, value)
        time.sleep(2)

    open_file("slow.py")
    click("▶ 运行")
    time.sleep(.4)
    pid = d.shell("pidof", d.PACKAGE + ":python", check=False).stdout.decode().strip()
    assert pid, "Python child process not found"
    click("■ 停止")
    time.sleep(.3)
    after = d.shell("pidof", d.PACKAGE + ":python", check=False).stdout.decode().strip()
    assert not after, f"Stopped Python process remains: {after}"
    view = snapshot("device-native-sleep-stopped")
    assert any("已中止" in n.get("text", "") for n in view.iter("node"))
    assert d.shell("pidof", d.PACKAGE).stdout.decode().strip()
    open_file("b.py")
    expect_output("42\n", "device-run-after-stop")
    record("native_sleep_stop_and_rerun", stopped_pid=pid, ui_alive=True, next_stdout="42\n")

    open_file("error.py")
    click("▶ 运行")
    time.sleep(.5)
    view = snapshot("device-python-traceback")
    assert any("ValueError: device-test" in n.get("text", "") for n in view.iter("node"))
    assert any("运行错误" in n.get("text", "") for n in view.iter("node"))
    click("定位错误")
    view = snapshot("device-error-location")
    assert fields(view)[0].get("focused") == "true"
    record("python_traceback_and_error_navigation", error="ValueError: device-test", file="error.py", line=1)
except Exception as exc:
    (d.OUT / "device-core-failure.txt").write_text(repr(exc), encoding="utf-8")
    snapshot("device-core-failure")
    print("FAILED=" + repr(exc), flush=True)
    raise
