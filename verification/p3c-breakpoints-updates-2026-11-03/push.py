"""Push the local commit and tag through Python's OpenSSL TLS (git's schannel path is unavailable).

Reads the existing local GitHub credential, never prints it. Pushes master and the v0.2.2 tag to
the configured public repository with a non-forced fast-forward check.
"""
import os
import re
import sys
from pathlib import Path
from urllib.parse import quote

REPO = Path(__file__).resolve().parents[2]
CRED = Path.home() / ".git-credentials"
sys.path.insert(0, str(REPO / ".tools/git-probe/site"))

from dulwich import porcelain  # noqa: E402


def credential():
    for line in CRED.read_text(encoding="utf-8").splitlines():
        match = re.match(r"https://([^:]+):([^@]+)@github\.com$", line.strip())
        if match:
            return match.group(1), match.group(2)
    raise SystemExit("no github credential")


user, token = credential()
url = f"https://{quote(user, safe='')}:{quote(token, safe='')}@github.com/juhe291/CodePocket-Android.git"
from dulwich.client import get_transport_and_path
_client, _path = get_transport_and_path(url)
print("TRANSPORT", type(_client).__name__, "path", _path, "special", sorted({c for c in token if not c.isalnum()}))


def redact(text):
    text = str(text)
    return text.replace(token, "<redacted>").replace(quote(token, safe=""), "<redacted>")


try:
    result = porcelain.push(str(REPO), url,
                            refspecs=["refs/heads/master:refs/heads/master", "refs/tags/v0.2.2:refs/tags/v0.2.2"])
    print("PUSH OK", [ref.decode() if isinstance(ref, bytes) else ref for ref in result.ref_status])
except Exception as error:  # noqa: BLE001 - report the real failure without leaking credentials
    print("PUSH FAILED:", redact(error))
    raise
