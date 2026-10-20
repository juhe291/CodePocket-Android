"""Validate Android vector path grammar, including commands ignored by aapt/legacy graphics.

No third-party dependencies; also usable by CI before an APK is built.
"""
import argparse
import json
import math
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID = "{http://schemas.android.com/apk/res/android}"
NUMBER = r"[-+]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][-+]?[0-9]+)?"
TOKEN = re.compile(r"[A-Za-z]|" + NUMBER)
ARITY = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}


def validate_path(data):
    tokens = []
    previous_end = 0
    for match in TOKEN.finditer(data):
        if data[previous_end:match.start()].strip(" ,\t\r\n"):
            raise ValueError("Invalid characters in path")
        tokens.append(match.group())
        previous_end = match.end()
    if data[previous_end:].strip(" ,\t\r\n"):
        raise ValueError("Invalid characters at end of path")
    if not tokens or tokens[0] not in ("M", "m"):
        raise ValueError("Path must begin with a move command")
    at = 0
    while at < len(tokens):
        command = tokens[at]
        if command.upper() not in ARITY:
            raise ValueError(f"Invalid command {command}")
        at += 1
        values = []
        while at < len(tokens) and not tokens[at].isalpha():
            number = float(tokens[at])
            if not math.isfinite(number):
                raise ValueError("Non-finite coordinate")
            values.append(number)
            at += 1
        count = ARITY[command.upper()]
        if (count == 0 and values) or (count > 0 and (not values or len(values) % count)):
            raise ValueError(f"{command} requires multiples of {count} coordinates; got {len(values)}")
        if command.upper() == "A":
            for start in range(0, len(values), 7):
                if values[start] < 0 or values[start + 1] < 0 or any(v not in (0, 1) for v in values[start + 3:start + 5]):
                    raise ValueError("Invalid arc radii or flags")


def verify(directory):
    paths = 0
    files = []
    errors = []
    for file in sorted(Path(directory).rglob("*.xml")):
        root = ET.parse(file).getroot()
        if root.tag != "vector":
            continue
        files.append(file.name)
        for element in root.iter():
            if element.tag not in ("path", "clip-path"):
                continue
            data = element.get(ANDROID + "pathData", "")
            paths += 1
            try:
                validate_path(data)
            except ValueError as exc:
                errors.append(f"{file.name}: {exc}: {data}")
    if not files:
        errors.append("No vector resources found")
    return {"vectors": len(files), "paths": paths, "files": files, "errors": errors}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", nargs="?", default=str(Path(__file__).resolve().parents[1] / "app/src/main/res"))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = verify(args.directory)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))
    raise SystemExit(bool(report["errors"]))
