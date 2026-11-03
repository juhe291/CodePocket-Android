"""Check this batch's real regression/artifact evidence before copying one candidate APK."""
from collections import Counter
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PHASE = Path(__file__).resolve().parent
PREVIOUS = ROOT / 'verification/test-case-batch-2026-10-28'
KNOWN_FAILURE = 'com.codepocket.ide.CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs'


def sha(file):
    with file.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def log(file):
    raw = file.read_bytes()
    return raw.decode('utf-16' if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig').replace('\r\n', '\n')


def junit(folder):
    cases = []
    for file in sorted(folder.glob('TEST-*.xml')):
        for case in ET.parse(file).getroot().findall('testcase'):
            cases.append(dict(id=case.get('classname') + '.' + case.get('name'), status=next(
                (tag for tag in ('failure', 'error', 'skipped') if case.find(tag) is not None), 'passed')))
    assert cases and len(cases) == len({case['id'] for case in cases})
    counts = Counter(case['status'] for case in cases)
    return cases, dict(tests=len(cases), passed=counts['passed'], failures=counts['failure'], errors=counts['error'], skipped=counts['skipped'])


cases, counts = junit(PHASE / 'final-junit')
assert counts['tests'] == 968 and counts['errors'] == 0, counts
_, first_full = junit(PHASE / 'full-first-junit')
assert first_full == dict(tests=967, passed=958, failures=8, errors=0, skipped=1), first_full
_, second_full = junit(PHASE / 'full-second-junit')
assert second_full == dict(tests=967, passed=965, failures=1, errors=0, skipped=1), second_full
old = json.loads((PREVIOUS / 'test-case-manifest.json').read_text())
assert len(old) == 946
current = {case['id']: case['status'] for case in cases}
assert all(case['id'] in current for case in old), 'A previous test identity was lost'
assert {case['id'] for case in cases if case['status'] == 'skipped'} == {case['id'] for case in old if case['status'] == 'skipped'}
assert {case['id'] for case in cases if case['status'] == 'failure'} <= {KNOWN_FAILURE}
assert all(current[case['id']] == case['status'] or case['id'] == KNOWN_FAILURE and current[case['id']] == 'passed' for case in old)
new = [case for case in cases if case['id'] not in {row['id'] for row in old}]
assert len(new) == 22 and all(case['status'] == 'passed' for case in new)
baseline = json.loads((PHASE / 'baseline-identities.json').read_text())['cases']
assert len(baseline) == 8 and all(current[case['id']] == 'passed' for case in baseline)
_, targeted = junit(PHASE / 'workspace-complete-targeted-junit')
assert targeted == dict(tests=144, passed=144, failures=0, errors=0, skipped=0)
_, closing_targeted = junit(PHASE / 'closing-regression-targeted-junit')
assert closing_targeted == dict(tests=108, passed=108, failures=0, errors=0, skipped=0)
edge_cases, edge_targeted = junit(PHASE / 'drawer-edge-final-targeted-junit')
assert edge_targeted == dict(tests=35, passed=35, failures=0, errors=0, skipped=0)
assert any(case['id'].endswith('.outgoingTabletExplorerSnapshotStaysToTheRightOfTheActivityRail') and case['status'] == 'passed' for case in edge_cases)
for name, digest in json.loads((PHASE / 'final-source-hashes.json').read_text()).items():
    assert sha(ROOT / name) == digest, name

build = log(PHASE / 'full-build.log')
build_exit = int(log(PHASE / 'full-build-exit.txt').strip())
assert '> Task :app:assembleDebug' in build and '> Task :app:packageDebug' in build
assert ('BUILD FAILED' in build and build_exit == 1) if counts['failures'] else ('BUILD SUCCESSFUL' in build and build_exit == 0)
lint = {}
for module in ('app', 'editor-sora', 'workspace-core'):
    source = ROOT / module / 'build/reports/lint-results-debug.xml'
    severities = Counter(issue.get('severity') for issue in ET.parse(source).getroot().findall('issue'))
    assert severities['Error'] == severities['Fatal'] == 0, (module, severities)
    lint[module] = dict(severities)
    shutil.copy2(source, PHASE / ('lint-' + module + '.xml'))
terminal_source = ROOT / 'third-party/termux/terminal-emulator/build/test-results/testDebugUnitTest'
_, terminal = junit(terminal_source)
assert terminal == dict(tests=146, passed=146, failures=0, errors=0, skipped=0)
terminal_dest = PHASE / 'final-terminal-junit'
terminal_dest.mkdir(exist_ok=True)
for source in terminal_source.glob('TEST-*.xml'):
    shutil.copy2(source, terminal_dest / source.name)

apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
previous_apk = ROOT / 'dist/CodePocket-test-cases-and-run-input-2026-10-28-debug.apk'
assert sha(apk) != json.loads((PHASE / 'second-build-apk.json').read_text())['sha256'], 'The outgoing-edge fix must be rebuilt'
assert sha(previous_apk) == 'd5fb5e63d5a6c7781c524e3a70baf610d05a04ccd347d207c5dcf9a203f714b6'
with zipfile.ZipFile(apk) as archive, zipfile.ZipFile(previous_apk) as previous:
    assert archive.testzip() is None
    natives = [name for name in previous.namelist() if name.startswith('lib/') and name.endswith('.so')]
    assets = [name for name in previous.namelist() if name.startswith(('assets/cpp-', 'assets/chaquopy/')) and not name.endswith('/')]
    assert len(natives) == 25 and len(assets) == 48
    assert set(natives) == {name for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')}
    assert set(assets) == {name for name in archive.namelist() if name.startswith(('assets/cpp-', 'assets/chaquopy/')) and not name.endswith('/')}
    for name in natives + assets:
        assert archive.read(name) == previous.read(name), name
    dex = b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
    for name in (b'WorkbenchWelcome', b'project-search-query', b'CaseRunSession', b'TestCasesDialog', b'SyntaxThemePreview'):
        assert name in dex, name
python = json.loads((PHASE / 'apk-python.json').read_text())
cpp = json.loads((PHASE / 'cpp-apk-inspection.json').read_text())
semantics = json.loads((PHASE / 'semantic-apk-inspection.json').read_text())
assert python['sha256'] == cpp['apk_sha256'] == semantics['apk_sha256'] == sha(apk)
assert python['bundled_total_tests'] == 48 and python['test_failures'] == python['test_errors'] == 0
assert semantics['tests'] == 22 and semantics['failures'] == semantics['errors'] == 0
assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in log(PHASE / 'certificates.txt').lower()
assert log(PHASE / 'permissions.txt') == log(PREVIOUS / 'permissions.txt')
assert log(PHASE / 'manifest-tree.txt') == log(PREVIOUS / 'manifest-tree.txt')
before = json.loads((PHASE / 'before-apks.json').read_text())
assert len(before) == 97
for item in before:
    file = ROOT / 'dist' / item['name']
    assert file.stat().st_size == item['bytes'] and sha(file) == item['sha256'], file
reused = json.loads((PHASE / 'unchanged-runtime-source-hashes.json').read_text())
for name, digest in reused['files'].items():
    assert sha(ROOT / name) == digest, name

result = dict(apk='dist/CodePocket-workbench-behavior-2026-10-30-debug.apk', bytes=apk.stat().st_size,
    sha256=sha(apk), size_delta_bytes=apk.stat().st_size-previous_apk.stat().st_size, full_build_exit=build_exit,
    junit=counts, previous_test_identities_preserved=946, new_tests=22, original_baseline_tests_passed=8, original_baseline_failures_fixed=7,
    targeted=targeted, closing_targeted=closing_targeted, edge_targeted=edge_targeted, first_full=first_full, second_full=second_full, terminal=terminal, lint=lint, new_skips=False, concentrated_build_attempts=3,
    native_libraries_byte_identical=25, runtime_assets_byte_identical=48, old_apks_preserved=97,
    packaged_python_tests_reexecuted=48, packaged_semantics_tests_reexecuted=22,
    reused_previous_runtime_checks=dict(python_source=75, shell_startup=4, snippets=14),
    previews_viewed=6, android_device_execution=False, full_goal_complete=False,
    retained_failure=KNOWN_FAILURE if counts['failures'] else None,
    search_explorer_welcome_batch_implemented=True, remaining_plan=['formatting', 'original P3'])
destination = ROOT / result['apk']
assert not destination.exists() or sha(destination) == result['sha256']
if not destination.exists():
    shutil.copy2(apk, destination)
(PHASE / 'test-case-manifest.json').write_text(json.dumps(cases, indent=2) + '\n')
(PHASE / 'results.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
