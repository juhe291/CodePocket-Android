"""Download one GitHub Actions artifact with the existing local credential.

Read-only API use: no push, no upload, token never printed.
Artifact: 13223450909 from run 41618338816 (Linux App regression, 1173 tests / 2 failed).
Expected: size 253409, sha256 23dd021d2fa87330b59c1085b83471b542422c2e2173dc0f155d4832ce7d9519
"""
import base64
import hashlib
import json
import os
import re
import sys
import urllib.parse
import urllib.request

REPO = "juhe291/CodePocket-Android"
ARTIFACT_ID = 13223450909
EXPECTED_SIZE = 253409
EXPECTED_SHA = "23dd021d2fa87330b59c1085b83471b542422c2e2173dc0f155d4832ce7d9519"
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "latest-remote-reports.zip")
CRED = os.path.join(os.path.expanduser("~"), ".git-credentials")


def auth_header():
    with open(CRED, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            m = re.match(r"https://([^:]+):([^@]+)@github\.com$", line)
            if m:
                user, token = m.groups()
                raw = base64.b64encode(f"{user}:{token}".encode()).decode()
                return {"Authorization": f"Basic {raw}"}
    raise SystemExit("no github credential")


class _StripAuthOnRedirect(urllib.request.HTTPRedirectHandler):
    """GitHub redirects archives to blob storage; that signed URL must not receive the API basic auth."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new is not None and urllib.parse.urlparse(newurl).hostname != "api.github.com":
            new.remove_header("Authorization")
        return new


_OPENER = urllib.request.build_opener(_StripAuthOnRedirect)


def get(url, out=None):
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "CodePocket-local-verification"}
    headers.update(auth_header())
    req = urllib.request.Request(url, headers=headers)
    with _OPENER.open(req, timeout=120) as resp:
        data = resp.read()
    if out:
        with open(out, "wb") as fh:
            fh.write(data)
    return data


meta = json.loads(get(f"https://api.github.com/repos/{REPO}/actions/artifacts/{ARTIFACT_ID}"))
print("artifact:", meta.get("name"), "size:", meta.get("size_in_bytes"), "expired:", meta.get("expired"))
print("digest:", meta.get("digest"), "run:", meta.get("workflow_run", {}).get("id"))
data = get(f"https://api.github.com/repos/{REPO}/actions/artifacts/{ARTIFACT_ID}/zip", out=OUT)
sha = hashlib.sha256(data).hexdigest()
print("downloaded:", len(data), "bytes", "sha256:", sha)
if len(data) != EXPECTED_SIZE:
    print("SIZE MISMATCH, expected", EXPECTED_SIZE)
if sha != EXPECTED_SHA:
    print("SHA MISMATCH, expected", EXPECTED_SHA)
    sys.exit(2)
print("artifact verified against the recorded handoff digest")

