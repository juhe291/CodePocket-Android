"""Collect current batch evidence; fail on lost tests, new skips, old APK changes or mismatched assets."""
import collections
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PHASE = Path(__file__).resolve().parent


def sha(data):
    return hashlib.sha256(data).hexdigest()


def junit(folder):
    cases = []
    for file in sorted(folder.glob('TEST-*.xml')):
        for case in ET.parse(file).getroot().findall('testcase'):
            status = next((tag for tag in ('failure', 'error', 'skipped') if case.find(tag) is not None), 'passed')
            cases.append(dict(id=case.get('classname') + '.' + case.get('name'), status=status))
    assert cases and len({c['id'] for c in cases}) == len(cases)
    count = collections.Counter(c['status'] for c in cases)
    return cases, dict(tests=len(cases), failures=count['failure'], errors=count['error'], skipped=count['skipped'])


def main():
    cases, count = junit(PHASE / 'final-junit')
    targeted_cases, targeted = junit(PHASE / 'targeted-junit')
    old = json.loads((PHASE / 'baseline-test-case-manifest.json').read_text(encoding='utf-8-sig'))
    assert len(old) == 772 and count == dict(tests=794, failures=0, errors=0, skipped=1), count
    assert targeted == dict(tests=132, failures=0, errors=0, skipped=0), targeted
    assert {c['id'] for c in old} <= {c['id'] for c in cases}
    assert {c['id'] for c in old if c['status'] == 'skipped'} == {c['id'] for c in cases if c['status'] == 'skipped'}
    (PHASE / 'test-case-manifest.json').write_text(json.dumps(cases, indent=2) + '\n', encoding='utf-8')
    assert 'BUILD SUCCESSFUL' in (PHASE / 'full-build.log').read_text(encoding='utf-8-sig')
    lint = {}
    for module in ('app', 'editor-sora'):
        issues = ET.parse(ROOT / module / 'build/reports/lint-results-debug.xml').getroot().findall('issue')
        severity = collections.Counter(x.get('severity') for x in issues)
        assert not severity['Error'] and not severity['Fatal']
        lint[module] = dict(severity=dict(severity), ids=dict(collections.Counter(x.get('id') for x in issues)))
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    previous = ROOT / 'dist/CodePocket-python-semantics-2026-10-26-debug.apk'
    before = json.loads((PHASE / 'before-apks.json').read_text(encoding='utf-8-sig'))
    assert len(before) == 88
    for item in before:
        old_apk = ROOT / 'dist' / item['name']
        assert old_apk.stat().st_size == item['bytes'] and sha(old_apk.read_bytes()) == item['sha256'], item['name']
    with zipfile.ZipFile(apk) as current, zipfile.ZipFile(previous) as baseline:
        assert current.testzip() is None
        assert current.read('AndroidManifest.xml') == baseline.read('AndroidManifest.xml')
        native = [n for n in current.namelist() if n.startswith('lib/') and n.endswith('.so')]
        assert len(native) == 10
        assert set(native) == {n for n in baseline.namelist() if n.startswith('lib/') and n.endswith('.so')}
        for name in native:
            assert current.read(name) == baseline.read(name), name
        assets = [n for n in baseline.namelist() if n.startswith('assets/chaquopy/')]
        assert set(assets) == {n for n in current.namelist() if n.startswith('assets/chaquopy/')}
        for name in assets:
            assert current.read(name) == baseline.read(name), name
        dex = b''.join(current.read(n) for n in current.namelist() if n.endswith('.dex'))
        for name in ('PythonProjectContext', 'PythonSources', 'ProjectReadExecutor', 'AnalysisDiskCache'):
            assert ('Lcom/codepocket/ide/' + name + ';').encode() in dex, name
    semantic = json.loads((PHASE / 'semantic-apk-inspection.json').read_text())
    assert semantic['tests'] == 22 and semantic['failures'] == semantic['errors'] == 0
    cert = (PHASE / 'certificates.txt').read_text(encoding='utf-8-sig').lower()
    assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in cert
    assert '66 tests' in (PHASE / 'python-source.log').read_text(encoding='utf-8-sig')
    result = dict(apk='dist/CodePocket-python-project-context-2026-10-26-debug.apk', bytes=apk.stat().st_size,
                  sha256=sha(apk.read_bytes()), apk_size_delta_bytes=apk.stat().st_size-previous.stat().st_size,
                  junit=count, targeted=targeted, old_test_cases_preserved=len(old), new_test_cases=len(cases)-len(old),
                  no_new_skips=True, lint=lint, python_source_tests=66, actual_apk_semantic_tests=22,
                  old_apks_preserved=len(before), native_libraries_unchanged=len(native),
                  chaquopy_assets_byte_identical=len(assets), new_context_classes_packaged=True,
                  device_execution=False, native_previews_viewed=3, full_goal_complete=False)
    (PHASE / 'results.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    destination = ROOT / result['apk']
    assert not destination.exists() or sha(destination.read_bytes()) == result['sha256']
    destination.write_bytes(apk.read_bytes())
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
