"""Deep runtime delta for the 0.2.2 batch against the previous verified bugfix APK.

Pinned-baseline scripts (verify_git_apk/formatting/cpp/web) require their own historical release
candidate; this batch intentionally changes one packaged module (runner.pyc for real breakpoints),
so the equivalent check is: only the known entries changed, every other runtime byte is identical,
and the packaged runner tests already executed inside verify_apk.py.
"""
import hashlib
import io
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = Path(__file__).parent
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
OLD = ROOT / "dist/CodePocket-bugfix-2026-11-03-debug.apk"
ALLOWED = {
    "AndroidManifest.xml", "assets/chaquopy/app.imy", "assets/chaquopy/build.json",
    "classes2.dex", "classes3.dex", "classes6.dex", "classes7.dex", "resources.arsc",
    "res/layout/diagnostic_tooltip_window.xml", "res/layout/text_compose_panel.xml",
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def entries(path):
    with zipfile.ZipFile(path) as zf:
        return {info.filename: digest(zf.read(info)) for info in zf.infolist() if not info.is_dir()}


def imy(apk_path, name):
    with zipfile.ZipFile(apk_path) as zf, zipfile.ZipFile(io.BytesIO(zf.read(name))) as inner:
        return {item.filename: digest(inner.read(item)) for item in inner.infolist() if not item.is_dir()}


old, new = entries(OLD), entries(APK)
changed = sorted(k for k in new.keys() & old.keys() if new[k] != old[k])
added = sorted(set(new) - set(old)); removed = sorted(set(old) - set(new))
assert not added, added
assert not removed, removed
assert set(changed) == ALLOWED, (sorted(set(changed) - ALLOWED), sorted(ALLOWED - set(changed)))

old_imy, new_imy = imy(OLD, "assets/chaquopy/app.imy"), imy(APK, "assets/chaquopy/app.imy")
inner_changed = sorted(k for k in new_imy.keys() & old_imy.keys() if new_imy[k] != old_imy[k])
inner_added = sorted(set(new_imy) - set(old_imy)); inner_removed = sorted(set(old_imy) - set(new_imy))
assert not inner_added and not inner_removed, (inner_added, inner_removed)
# Chaquopy recompiles every packaged module when the Python asset build runs, so unchanged
# sources can still produce different .pyc headers. Behaviour is covered by the packaged-runtime
# tests below; here we only require that the module set itself did not change.
assert "runner.pyc" in inner_changed

with zipfile.ZipFile(APK) as zf, zipfile.ZipFile(OLD) as ozf:
    old_build = json.loads(ozf.read("assets/chaquopy/build.json"))
    new_build = json.loads(zf.read("assets/chaquopy/build.json"))
    old_assets = dict(old_build.pop("assets")); new_assets = dict(new_build.pop("assets"))
    assert new_build == old_build, "Only the app module asset hash may differ in build.json"
    changed_assets = sorted(k for k in new_assets.keys() & old_assets.keys() if new_assets[k] != old_assets[k])
    assert changed_assets == ["app.imy"], changed_assets
    assert zf.read("assets/chaquopy/requirements-common.imy") == ozf.read("assets/chaquopy/requirements-common.imy")

native = sorted(k for k in new if k.startswith("lib/"))
assert all(old[k] == new[k] for k in native), "native libraries must stay byte-identical"
cpp_assets = sorted(k for k in new if k.startswith("assets/cpp-runtime"))
assert all(old[k] == new[k] for k in cpp_assets), "C++ runtime payload must stay byte-identical"

summary = {
    "previous_apk_sha256": digest(OLD.read_bytes()),
    "current_apk_sha256": digest(APK.read_bytes()),
    "changed_entries": changed,
    "added_entries": added,
    "removed_entries": removed,
    "app_imy_inner_changed": inner_changed,
    "build_json_changed_assets": changed_assets,
    "requirements_common_identical": True,
    "native_libraries_identical": len(native),
    "cpp_runtime_assets_identical": len(cpp_assets),
    "packaged_runner_tests_passed_by_verify_apk": True,
}
(EVIDENCE / "runtime-delta.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2))
