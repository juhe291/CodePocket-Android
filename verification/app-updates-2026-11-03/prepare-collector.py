"""Adapt the previous strict delivery checks without loosening runtime or failure checks."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
old=ROOT/'verification/git-private-path-fix-2026-11-03'
text=(old/'collect-delivery.py').read_text()
text=text.replace("PREVIOUS=ROOT/'verification/git-remote-delivery-2026-11-03'","PREVIOUS=ROOT/'verification/git-private-path-fix-2026-11-03'")
text=text.replace("tests['tests']==1149","tests['tests']==1173").replace('passed=1147','passed=1171')
text=text.replace('len(before)==101','len(before)==102').replace('old_apks_preserved=101','old_apks_preserved=102')
text=text.replace("assert normalize(named_bindings(manifest.replace(factory[0],''),log(OUT/'resources.txt')))==normalize(named_bindings(log(PREVIOUS/'manifest-tree.txt'),log(OUT/'baseline-resources.txt')))","""previous_manifest=log(PREVIOUS/'manifest-tree.txt')
assert 'android:versionCode(0x0101021b)=(type 0x10)0x3' in manifest
assert 'android:versionName(0x0101021c)=\"0.2.1\" (Raw: \"0.2.1\")' in manifest
previous_manifest=previous_manifest.replace('android:versionCode(0x0101021b)=(type 0x10)0x2','android:versionCode(0x0101021b)=(type 0x10)0x3').replace('android:versionName(0x0101021c)=\"0.2.0\" (Raw: \"0.2.0\")','android:versionName(0x0101021c)=\"0.2.1\" (Raw: \"0.2.1\")')
assert normalize(named_bindings(manifest,log(OUT/'resources.txt')))==normalize(named_bindings(previous_manifest,log(OUT/'baseline-resources.txt')))
assert \"versionCode='3' versionName='0.2.1'\" in log(OUT/'badging.txt')
with zipfile.ZipFile(apk) as package:
    dex=b''.join(package.read(n) for n in package.namelist() if n.startswith('classes') and n.endswith('.dex'))
    for name in ('AppUpdateDialog','InstalledAppUpdateVersion','AppUpdateVersion','AppUpdateRepository','AppUpdateMetadata','GitHubAppUpdateSource','BuildConfig'):
        assert ('Lcom/codepocket/ide/'+name+';').encode() in dex,name
    assert b'juhe291/CodePocket-Android' in dex and b'UPDATE_REPOSITORY' in dex
    for name in ('AppUpdateEntryBaselineTest','AppUpdatePolicyTest','AppUpdateHttpTest','AppUpdateUiIntegrationTest','UpdateFixtureServer'):
        assert name.encode() not in dex,name
""")
text=text.replace("previews=ROOT/'verification/web-sidebar-integration-2026-11-03/previews'","previews=OUT/'previews'")
text=text.replace("('tablet-dark-left-browser.png','tablet-light-native-markdown.png','phone-large-bing-focus.png')","('phone-dark-update-available.png','tablet-light-large-no-release.png')")
text=text.replace("destination=ROOT/'dist/CodePocket-git-private-path-fix-2026-11-03-debug.apk'","assert images==json.loads((OUT/'viewed-previews.json').read_text())\n\ndestination=ROOT/'dist/CodePocket-updates-0.2.1-2026-11-03-debug.apk'")
text=text.replace("related_targeted=len(json.loads((ROOT/'verification/web-sidebar-preflight-2026-11-03/related-results.json').read_text())['identities'])","related_targeted=len(json.loads((OUT/'related-results.json').read_text())['identities'])")
text=text.replace('vector_tests_rerun=5','vector_tests_reused=5')
text=text.replace('git_device_bug_tests=11,git_related_tests=124,batch_actual_package_executions=2','update_tests=24,version_name=\"0.2.1\",version_code=3,update_repository=\"juhe291/CodePocket-Android\",batch_actual_package_executions=1')
(OUT/'collect-delivery.py').write_text(text,encoding='utf-8')
artifact=(OUT/'verify-artifact.ps1').read_text()
artifact=artifact.replace("Write-Output 'CURRENT_APK_SIGNATURE_ALIGNMENT_METADATA_PASSED'", """& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump --values resources $taskApk *> "$taskEvidence/resources.txt"
if($LASTEXITCODE -ne 0){throw 'Resource inspection failed'}
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump --values resources dist/CodePocket-git-private-path-fix-2026-11-03-debug.apk *> "$taskEvidence/baseline-resources.txt"
if($LASTEXITCODE -ne 0){throw 'Previous resource inspection failed'}
Write-Output 'CURRENT_APK_SIGNATURE_ALIGNMENT_METADATA_PASSED'""")
(OUT/'verify-artifact.ps1').write_text(artifact,encoding='utf-8')
print('Collector and artifact checks prepared; no build inputs changed')
