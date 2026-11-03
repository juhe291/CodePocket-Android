import hashlib
import json
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
phase = Path(__file__).resolve().parent
paths = '''app/src/main/java/com/codepocket/ide/MainActivity.kt
app/src/main/java/com/codepocket/ide/PythonAnalysisService.kt
app/src/main/java/com/codepocket/ide/AnalysisDiskCache.kt
app/src/main/java/com/codepocket/ide/ProjectReadExecutor.kt
app/src/main/java/com/codepocket/ide/PythonProjectContext.kt
app/src/main/res/values/strings.xml
app/src/test/java/com/codepocket/ide/PythonSemanticIntegrationTest.kt
app/src/test/java/com/codepocket/ide/PythonProjectContextTest.kt
app/src/test/java/com/codepocket/ide/ProjectReadExecutorTest.kt
app/src/test/java/com/codepocket/ide/AnalysisDiskCacheTest.kt
workspace-core/src/main/java/com/codepocket/ide/Workspace.kt
workspace-core/src/main/java/com/codepocket/ide/DocumentTree.kt
workspace-core/src/main/java/com/codepocket/ide/EditorSessions.kt
workspace-core/src/main/java/com/codepocket/ide/DraftStore.kt
workspace-core/src/main/java/com/codepocket/ide/PythonSources.kt
tests/test_semantics.py
scripts/generate_project_semantic_fixtures.py'''.splitlines()
hashes = {path: hashlib.sha256((root / path).read_bytes().replace(b'\r\n', b'\n')).hexdigest() for path in paths}
saved = phase / 'source-hashes.json'
if saved.exists():
    assert hashes == json.loads(saved.read_text()), 'Source changed after verification'
else:
    saved.write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf-8')
links = 0
for relative in ('docs/P2_PYTHON_PROJECT_CONTEXT_2026-10-26.md', 'docs/python-project-context-preview/README.md'):
    file = root / relative
    for link in re.findall(r'\]\(([^)]+)\)', file.read_text(encoding='utf-8')):
        if not link.startswith(('https:', 'http:', '#')):
            assert (file.parent / link.split('#')[0]).exists(), (relative, link)
            links += 1
result = json.loads((phase / 'results.json').read_text())
apk = root / result['apk']
assert hashlib.sha256(apk.read_bytes()).hexdigest() == result['sha256']
report = dict(source_files=len(hashes), source_hashes_match=True, relative_links_checked=links,
              delivery_apk_sha256_matches=True, full_goal_complete=False, device_execution=False)
(phase / 'delivery-check.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, indent=2))
