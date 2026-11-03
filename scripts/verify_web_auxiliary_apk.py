"""Verify the web-panel candidate against the fixed HTTPS Git APK; no Android execution."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]
BASELINE='c63e8df1d0f6b89df2f972fe2a58117c43a8fd7b0da982eb30c1e68571492c1c'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path)
    parser.add_argument('--baseline',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    assert digest(args.baseline.read_bytes())==BASELINE
    with zipfile.ZipFile(args.apk) as apk,zipfile.ZipFile(args.baseline) as old:
        names=apk.namelist()
        assert apk.testzip() is None and len(names)==len(set(names))
        natives={n for n in old.namelist() if n.startswith('lib/') and n.endswith('.so')}
        assert len(natives)==25
        assert natives=={n for n in names if n.startswith('lib/') and n.endswith('.so')}
        # Include runtime manifests, licenses and support files, not only executable images.
        prefixes=('assets/chaquopy/','assets/cpp-runtime/','assets/cpp-source/','assets/cpp-licenses/',
                  'assets/git-licenses/','assets/formatting-licenses/')
        runtime={n for n in old.namelist() if n.startswith(prefixes)}
        assert runtime=={n for n in names if n.startswith(prefixes)}
        assert len([n for n in runtime if n.startswith('assets/chaquopy/')])==20
        for name in natives|runtime:
            assert apk.read(name)==old.read(name),'Runtime changed: '+name
        licenses={}
        for path in (ROOT/'app/src/main/assets/web-licenses').iterdir():
            if path.is_file():
                asset='assets/web-licenses/'+path.name
                assert apk.read(asset)==path.read_bytes(),asset
                licenses[asset]=digest(apk.read(asset))
        assert len(licenses)==4
        assert {n for n in names if n.startswith('assets/web-licenses/')}==set(licenses)
        dex=b''.join(apk.read(n) for n in names if n.startswith('classes') and n.endswith('.dex'))
        classes=['com/codepocket/ide/WebAuxiliaryPanel','com/codepocket/ide/BrowserAddress',
                 'com/codepocket/ide/WebSearchEngine','androidx/webkit/WebViewCompat',
                 'androidx/webkit/WebSettingsCompat','io/noties/markwon/Markwon','org/commonmark/parser/Parser']
        for name in classes:
            assert ('L'+name+';').encode() in dex,'Production class missing: '+name
        for name in ('WebAuxiliaryIntegrationTest','WebPanelBehaviorTest','BrowserPolicyTest','HostRemoteGit','GitPrivatePathRegressionTest','GitAliasedApplication'):
            assert name.encode() not in dex,'Host test packaged: '+name
        source=json.loads((ROOT/'verification/git-remote-delivery-2026-11-03/build-inputs.json').read_text(encoding='utf-8-sig'))
        unchanged_sources={}
        for name,sha in source.items():
            if name.startswith(('app/src/main/python/','tests/')) or name in ('app/requirements.txt','scripts/shell_startup_fixture.py','scripts/snippet_fixture.py'):
                path=ROOT/name
                assert path.is_file() and digest(path.read_bytes())==sha,'Runtime regression source changed: '+name
                unchanged_sources[name]=sha
        assert unchanged_sources
        report=dict(apk_sha256=digest(args.apk.read_bytes()),baseline_sha256=BASELINE,zip_crc=True,
                    original_natives_byte_identical=sorted(natives),original_runtime_assets_byte_identical=sorted(runtime),
                    web_license_assets=licenses,production_classes=classes,unchanged_runtime_source_hashes=unchanged_sources,
                    runtime_regressions_reused_from='verification/git-remote-delivery-2026-11-03/results.json',
                    reused_packaged_python_tests=48,reused_packaged_semantics_tests=22,reused_packaged_git_formatting_tests=82,
                    android_chromium_execution=False,device_execution=False)
    args.output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print('WEB_APK_CLASSES_LICENSES_CRC_AND_UNCHANGED_RUNTIME_VERIFIED; runtime tests reused, not rerun')


if __name__=='__main__':
    main()
