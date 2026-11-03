"""Read-only GitHub Actions status for the pushed commit; saves remote facts locally."""
import json
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

REPO = "juhe291/CodePocket-Android"
SHA = "fe3990750582c513b97c10cf556cf9ba15f2e560"
EVIDENCE = Path(__file__).parent
CRED = Path.home() / ".git-credentials"


def headers():
    for line in CRED.read_text(encoding="utf-8").splitlines():
        match = re.match(r"https://([^:]+):([^@]+)@github\.com$", line.strip())
        if match:
            import base64
            raw = base64.b64encode(f"{match.group(1)}:{match.group(2)}".encode()).decode()
            return {"Authorization": f"Basic {raw}", "Accept": "application/vnd.github+json",
                    "User-Agent": "CodePocket-local-verification"}
    raise SystemExit("no credential")


def api(path):
    request = urllib.request.Request(f"https://api.github.com{path}", headers=headers())
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read())


def latest_run():
    runs = api(f"/repos/{REPO}/actions/runs?head_sha={SHA}&per_page=10")["workflow_runs"]
    return runs[0] if runs else None


run = None
deadline = time.time() + 60
while run is None and time.time() < deadline:
    run = latest_run()
    if run is None:
        time.sleep(3)
if run is None:
    raise SystemExit("no workflow run found for pushed commit")
print("run", run["id"], run["status"], run["conclusion"], run["html_url"], flush=True)
while run["status"] != "completed" and time.time() < deadline + 1800:
    time.sleep(20)
    run = api(f"/repos/{REPO}/actions/runs/{run['id']}")
    print("poll", run["status"], run["conclusion"], flush=True)
jobs = api(f"/repos/{REPO}/actions/runs/{run['id']}/jobs")["jobs"]
out = {
    "run_id": run["id"], "status": run["status"], "conclusion": run["conclusion"],
    "head_sha": run["head_sha"], "url": run["html_url"],
    "jobs": [{"name": j["name"], "status": j["status"], "conclusion": j["conclusion"],
              "steps": [{"name": s["name"], "conclusion": s["conclusion"]} for s in j["steps"]]} for j in jobs],
    "artifacts": [{"id": a["id"], "name": a["name"], "size_in_bytes": a["size_in_bytes"],
                   "digest": a.get("digest")} for a in
                  api(f"/repos/{REPO}/actions/runs/{run['id']}/artifacts")["artifacts"]],
}
(EVIDENCE / "remote-ci-run.json").write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")
print(json.dumps(out, indent=2))
