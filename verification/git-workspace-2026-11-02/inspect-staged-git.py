"""Validate the new verifier against staged build inputs, not an APK result."""
import json
from pathlib import Path
import sys
import zipfile
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
from verify_git_apk import verify_delta

with zipfile.ZipFile(ROOT/'dist/CodePocket-formatting-2026-10-30-debug.apk') as baseline:
    class Staged:
        def namelist(self):
            return baseline.namelist()+['assets/git-licenses/DULWICH-LICENSE.txt','assets/git-licenses/URLLIB3-LICENSE.txt']
        def read(self,name):
            if name.startswith('assets/chaquopy/'):
                path=ROOT/'app/build/intermediates/assets/debug/mergeDebugAssets'/name.removeprefix('assets/')
                if path.is_file():return path.read_bytes()
            if name.startswith('assets/git-licenses/'):
                return (ROOT/'app/src/main'/name).read_bytes()
            return baseline.read(name)
    result=verify_delta(Staged(),baseline)
    print(json.dumps(dict(staged_inputs_only=True,apk_verified=False,dependency_entries=len(result['approved_dependency_entries']))))
