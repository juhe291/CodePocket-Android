"""Verify the real 0.2.2 debug APK: signature, archive, 16 KiB alignment, metadata and payload delta."""
import hashlib
import json
import os
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = Path(__file__).parent
APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
PREVIOUS = ROOT / "dist/CodePocket-bugfix-2026-11-03-debug.apk"
SDK = ROOT / ".tools/android-sdk/build-tools/35.0.0"


def run(args):
    result = subprocess.run(args, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode != 0:
        raise SystemExit(f"FAILED {args}: {result.stderr or result.stdout}")
    return result.stdout


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def entries(apk):
    result = {}
    with zipfile.ZipFile(apk) as zf:
        for info in zf.infolist():
            if info.is_dir():
                continue
            result[info.filename] = hashlib.sha256(zf.read(info)).hexdigest()
    return result


if not APK.is_file():
    raise SystemExit("missing app-debug.apk")
sig = run([str(SDK / "apksigner.bat"), "verify", "--verbose", str(APK)])
(EVIDENCE / "apk-signature.txt").write_text(sig, encoding="utf-8")
(EVIDENCE / "apk-zipalign.txt").write_text(run([str(SDK / "zipalign.exe"), "-c", "-P", "16", "4", str(APK)]), encoding="utf-8")
(EVIDENCE / "apk-badging.txt").write_text(run([str(SDK / "aapt.exe"), "dump", "badging", str(APK)]), encoding="utf-8")
with zipfile.ZipFile(APK) as zf:
    bad = zf.testzip()
    if bad is not None:
        raise SystemExit(f"archive check failed at {bad}")
    (EVIDENCE / "apk-archive.txt").write_text(
        f"{len(zf.infolist())} entries read successfully by zipfile.testzip()\n", encoding="utf-8")

current = entries(APK)
summary = {
    "apk": str(APK.relative_to(ROOT)).replace("\\", "/"),
    "bytes": APK.stat().st_size,
    "sha256": sha256(APK),
    "entries": len(current),
    "badging": (EVIDENCE / "apk-badging.txt").read_text(encoding="utf-8").splitlines()[0],
    "signature_verified": "Verifies" in sig,
    "zipalign_16k": True,
    "archive_ok": True,
}
if PREVIOUS.is_file():
    previous = entries(PREVIOUS)
    changed = sorted(k for k in current.keys() & previous.keys() if current[k] != previous[k])
    added = sorted(current.keys() - previous.keys())
    removed = sorted(previous.keys() - current.keys())
    summary["previous"] = {"apk": str(PREVIOUS.relative_to(ROOT)).replace("\\", "/"),
                           "sha256": sha256(PREVIOUS), "bytes": PREVIOUS.stat().st_size}
    summary["changed"] = changed
    summary["added"] = added
    summary["removed"] = removed
(EVIDENCE / "apk-inspection.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps({k: v for k, v in summary.items() if k not in ("changed", "added", "removed")}, indent=2))
print("changed:", summary.get("changed"))
print("added:", summary.get("added"))
print("removed:", summary.get("removed"))
