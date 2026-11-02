import hashlib
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]
files={'.gitattributes','README.md','docs/RESUME.md','docs/P3_IMPLEMENTATION_PLAN_2026-11-02.md','docs/P3_GITHUB_SYNC_BATCH_2026-11-02.md',
    'docs/P3_WEB_AUXILIARY_BATCH_2026-11-02.md','app/src/main/AndroidManifest.xml','app/src/main/java/com/codepocket/ide/PythonSemanticClient.kt',
    'app/src/main/java/com/codepocket/ide/GitCredentialVault.kt','app/src/main/java/com/codepocket/ide/GitPushLedger.kt',
    'app/src/main/java/com/codepocket/ide/GitRemoteProtocol.kt','app/src/main/java/com/codepocket/ide/GitRemoteService.kt',
    'app/src/main/python/git_https.py','app/src/main/python/git_remote.py','app/src/test/java/com/codepocket/ide/GitRemotePlanTest.kt',
    'app/src/test/java/com/codepocket/ide/GitRemoteJournalTest.kt','app/src/test/java/com/codepocket/ide/GitRemoteSecurityTest.kt',
    'app/src/test/java/com/codepocket/ide/GitRemoteServiceTest.kt','app/src/test/java/com/codepocket/ide/SafGitBinaryIntegrationTest.kt',
    'app/src/test/java/com/codepocket/ide/SafGitRemoteJournalIntegrationTest.kt','workspace-core/src/main/java/com/codepocket/ide/DocumentTree.kt',
    'workspace-core/src/main/java/com/codepocket/ide/GitRemoteAddress.kt','workspace-core/src/main/java/com/codepocket/ide/GitRemotePlan.kt',
    'workspace-core/src/main/java/com/codepocket/ide/GitWorktreeTransaction.kt','workspace-core/src/main/java/com/codepocket/ide/GitWorkspaceStore.kt',
    'tests/test_git_https.py','tests/test_git_remote.py'}
prefixes=['verification/'+name+'/' for name in ('git-remote-preflight-2026-11-02','git-remote-workspace-2026-11-02',
    'git-remote-journal-2026-11-02','git-remote-service-2026-11-02','web-sidebar-research-2026-11-02')]
paths=[p.decode('utf-8') for p in subprocess.check_output(['git','diff','--cached','--name-only','-z'],cwd=ROOT).split(b'\0') if p]
assert len(paths)>100
for path in paths:
    assert path in files or any(path.startswith(prefix) for prefix in prefixes),path
    assert Path(path).suffix not in ('.apk','.jks','.keystore','.key','.pem','.p12'),path
    if path.startswith('verification/') and Path(path).suffix in ('.xml','.log'):
        assert subprocess.check_output(['git','show',':'+path],cwd=ROOT)==(ROOT/path).read_bytes(),path
sources=json.loads((ROOT/'verification/git-remote-service-2026-11-02/broad-sources.json').read_text())
assert all(hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==sha for path,sha in sources.items())
print(json.dumps(dict(staged_files=len(paths),scope_checked=True,raw_evidence_bytes_preserved=True,no_apk_or_key=True,runtime_sources_unchanged=True)))
