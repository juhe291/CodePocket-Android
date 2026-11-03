"""Audit this preflight against actual files; never equate it to production/Android completion."""
import hashlib
import json
from pathlib import Path
import subprocess

PHASE = Path(__file__).resolve().parent
ROOT = PHASE.parents[1]
metadata = json.loads((PHASE / 'package-candidates.json').read_text())
assert metadata['gpg_signature_verified'] and metadata['index_matches_inrelease_sha256']
assert metadata['signature_fingerprint'] == 'CC72CF8BA7DBFA0182877D045A897D96E57CF20C'
bundle = json.loads((PHASE / 'native-bundle-inspection.json').read_text())
assert bundle['native_count'] == 9 and bundle['raw_bytes'] == 219813944
assert bundle['all_dependencies_resolved'] and not bundle['android_execution'] and not bundle['production_integrated']
for item in bundle['files']:
    source = ROOT / item['source']
    staged = ROOT / '.tools/cpp-semantic-probe/staged-native/arm64-v8a' / item['packaged_name']
    assert hashlib.sha256(source.read_bytes()).hexdigest() == item['source_sha256']
    assert hashlib.sha256(staged.read_bytes()).hexdigest() == item['sha256']
fixture = json.loads((PHASE / 'host-semantic-fixtures.json').read_text(encoding='utf-8'))
assert fixture['tests'] == len(fixture['cases']) == 15
assert fixture['real_backend'] and fixture['actual_android_headers'] and not fixture['android_execution']
assert fixture['context_generation_restart']
for case, expected, forbidden in [('updated_header', 'save', {'run', 'old_disk'}), ('closed_header', 'old_disk', {'run', 'save'})]:
    result = fixture['cases'][case]['response']['result']
    names = {item['filterText'] for item in result['items']}
    assert expected in names and not names.intersection(forbidden)
licenses = json.loads((PHASE / 'license-inspection.json').read_text())
assert not licenses['release_ready'] and licenses['corresponding_source_delivery_pending']
assert licenses['libiconv_source']['sha256'] == '88dd96a8c0464eca144fc791ae60cd31cd8ee78321e67397e25fc095c4a19aa6'
for name, sha in licenses['notice_files'].items():
    assert hashlib.sha256((PHASE / 'licenses' / name).read_bytes().replace(b'\r\n', b'\n')).hexdigest() == sha
apk = ROOT / 'dist/CodePocket-python-project-context-2026-10-26-debug.apk'
assert hashlib.sha256(apk.read_bytes()).hexdigest() == '836042a759ba9d80ac85a0e5647da1e2e45f6bc550fa5b0c76da2f62d5fb3c5c'
changed = subprocess.check_output(['git', 'diff', '--name-only', 'c5672c7', '--', 'app', 'workspace-core', 'editor-assist', 'editor-sora', 'tests', 'scripts'], cwd=ROOT)
assert not changed.strip(), changed
record = dict(preflight_verified=True, real_lsp_cases=15, original_apk_unchanged=True,
              production_sources_unchanged=True, candidate_native_files=9,
              native_raw_bytes=bundle['raw_bytes'], probe_zip_bytes=bundle['probe_zip_bytes'],
              source_signature_verified=True, notices_collected=len(licenses['notice_files']),
              app_regression_not_rerun=True, new_apk_built=False,
              android_execution=False, production_integrated=False, full_goal_complete=False)
(PHASE / 'results.json').write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(record, indent=2))
