"""Archive the real Windows baseline/target and downloaded Linux failures separately."""
import hashlib
import html
import json
from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).parent
NAMES = ('CppRuntimePreparationTest', 'MainSurfaceIntegrationTest', 'PythonSemanticIntegrationTest',
         'RealCaseBatchTest', 'SoraWorkbenchIntegrationTest', 'TerminalDockIntegrationTest')

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def cases(folder):
    rows = {}
    for name in NAMES:
        path = folder / ('TEST-com.codepocket.ide.' + name + '.xml')
        suite = ET.parse(path).getroot()
        for case in suite.findall('testcase'):
            identity = case.get('classname') + '.' + case.get('name')
            assert identity not in rows
            status = next((key for key in ('failure', 'error', 'skipped') if case.find(key) is not None), 'passed')
            rows[identity] = status
    return rows

target = OUT / 'targeted-junit'
target.mkdir(exist_ok=True)
for name in NAMES:
    path = ROOT / 'app/build/test-results/testDebugUnitTest' / ('TEST-com.codepocket.ide.' + name + '.xml')
    assert path.stat().st_mtime >= (OUT / 'targeted.log').stat().st_ctime
    shutil.copy2(path, target / path.name)
before, after = cases(OUT / 'baseline-junit'), cases(target)
assert len(before) == len(after) == 75 and before == after
known = 'com.codepocket.ide.CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs'
assert {key: value for key, value in after.items() if value != 'passed'} == {known: 'failure'}

archive = OUT / 'remote-reports.zip'
assert digest(archive) == '97adadac9a268e22d337fbf4fdd5545ff0e1ed5353077eccf437e0cfa6518c29'
remote = {}
with zipfile.ZipFile(archive) as bundle:
    assert bundle.testzip() is None
    for name in NAMES:
        text = bundle.read('app/build/reports/tests/testDebugUnitTest/classes/com.codepocket.ide.' + name + '.html').decode()
        for identity, trace in re.findall(r'<h3 class="failures">(.*?)</h3>.*?<pre>(.*?)</pre>', text, re.S):
            remote['com.codepocket.ide.' + name + '.' + html.unescape(identity)] = html.unescape(trace)
assert len(remote) == 10
(OUT / 'remote-failures.json').write_text(json.dumps(remote, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
sources = json.loads((OUT / 'before-sources.json').read_text())
sources['app/src/test/java/com/codepocket/ide/HostEditorReady.kt'] = None
summary = dict(windows_tests=75, windows_passed=74, windows_failures=1, skipped=0,
               unchanged_test_identities_and_results=True, known_windows_failure=known,
               android_execution=False, new_local_apk_built=False,
               previous_linux_run=41588320086, previous_linux_tests=1173, previous_linux_failures=10,
               previous_linux_artifact_sha256=digest(archive),
               source_hashes={name: digest(ROOT / name) for name in sources}, identities=after)
(OUT / 'results.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
print(json.dumps({key: value for key, value in summary.items() if not isinstance(value, dict)}))
