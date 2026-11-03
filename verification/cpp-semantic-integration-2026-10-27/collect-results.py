"""Collect the completed batch. Old cases/skips/APKs and runtime hashes remain mandatory."""
import collections
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
PHASE = Path(__file__).resolve().parent


def sha(file):
    with file.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def junit(directory):
    cases = []
    for file in sorted(directory.glob('TEST-*.xml')):
        for case in ET.parse(file).getroot().findall('testcase'):
            status = next((tag for tag in ('failure', 'error', 'skipped') if case.find(tag) is not None), 'passed')
            cases.append(dict(id=case.get('classname') + '.' + case.get('name'), status=status))
    assert cases and len(cases) == len({case['id'] for case in cases})
    count = collections.Counter(case['status'] for case in cases)
    return cases, dict(tests=len(cases), failures=count['failure'], errors=count['error'], skipped=count['skipped'])


def main():
    cases, tests = junit(PHASE / 'final-source-fixed-junit')
    _, targeted = junit(PHASE / 'targeted-source-fixed-junit')
    _, terminal = junit(PHASE / 'final-terminal-junit')
    old = json.loads((ROOT / 'verification/python-project-context-2026-10-26/test-case-manifest.json').read_text())
    assert len(old) == 794 and tests == dict(tests=821, failures=0, errors=0, skipped=1), tests
    assert targeted == dict(tests=91, failures=0, errors=0, skipped=0), targeted
    assert terminal['failures'] == terminal['errors'] == 0
    assert {case['id'] for case in old} <= {case['id'] for case in cases}
    assert {case['id'] for case in old if case['status'] == 'skipped'} == {case['id'] for case in cases if case['status'] == 'skipped'}
    (PHASE / 'test-case-manifest.json').write_text(json.dumps(cases, indent=2) + '\n', encoding='utf-8')
    log = (PHASE / 'full-build.log').read_text(encoding='utf-8-sig')
    assert 'BUILD SUCCESSFUL' in log and '> Task :app:packageDebug\n' in log
    lint = {}
    for module in ('app', 'editor-sora'):
        issues = ET.parse(ROOT / module / 'build/reports/lint-results-debug.xml').getroot().findall('issue')
        severity = collections.Counter(item.get('severity') for item in issues)
        assert severity['Error'] == severity['Fatal'] == 0
        lint[module] = dict(severity=dict(severity), ids=dict(collections.Counter(item.get('id') for item in issues)))
    before = json.loads((PHASE / 'before-apks.json').read_text())
    assert len(before) == 89
    for item in before:
        file = ROOT / 'dist' / item['name']
        assert file.stat().st_size == item['bytes'] and sha(file) == item['sha256'], item['name']
    apk = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'
    cpp = json.loads((PHASE / 'cpp-apk-inspection.json').read_text())
    assert sha(apk) == cpp['apk_sha256']
    assert len(cpp['native_libraries']) == 9 and not cpp['real_android_execution']
    assert len(cpp['original_native_unchanged']) == 10 and len(cpp['original_python_assets_unchanged']) == 20
    with zipfile.ZipFile(apk) as archive:
        assert len([name for name in archive.namelist() if name.startswith('lib/') and name.endswith('.so')]) == 20
        dex = b''.join(archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
        for name in ('CppAnalysisService','CppSemanticClient','CppRuntime','CppLspEngine','CppProjectContext','NativeAnalysisBridge'):
            assert ('Lcom/codepocket/ide/' + name + ';').encode() in dex
    manifest = (PHASE / 'manifest-tree.txt').read_text(encoding='utf-8-sig')
    assert 'CppAnalysisService' in manifest and ':cpp_analysis' in manifest
    assert 'extractNativeLibs' in manifest
    cert = (PHASE / 'certificates.txt').read_text(encoding='utf-8-sig').lower()
    assert 'd58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408' in cert
    semantics = json.loads((PHASE / 'semantic-apk-inspection.json').read_text())
    assert semantics['tests'] == 22 and semantics['failures'] == semantics['errors'] == 0
    assert semantics['apk_sha256'] == sha(apk)
    assert '66 tests' in (PHASE / 'python-source.log').read_text(encoding='utf-8-sig')
    result = dict(apk='dist/CodePocket-cpp-semantics-2026-10-27-debug.apk', bytes=apk.stat().st_size,
        sha256=sha(apk), apk_size_delta_bytes=apk.stat().st_size-cpp['baseline_bytes'],
        junit=tests, targeted=targeted, terminal=terminal, old_cases_preserved=794, new_cases=27,
        no_new_skips=True, lint=lint, python_source_tests=66, actual_apk_semantic_tests=22,
        cpp_real_host_cases=15, cpp_native_files=9, cpp_native_bytes=cpp['native_bytes'],
        cpp_header_entries=cpp['headers']['entries'], cpp_header_bytes=cpp['headers']['uncompressed_bytes'],
        corresponding_source_bundled=True, original_native_unchanged=10, chaquopy_assets_unchanged=20,
        old_apks_preserved=89, previews_viewed=4, android_execution=False, full_goal_complete=False)
    destination = ROOT / result['apk']
    assert not destination.exists() or sha(destination) == result['sha256']
    destination.write_bytes(apk.read_bytes())
    (PHASE / 'results.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
