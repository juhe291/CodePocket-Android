$ErrorActionPreference='Continue'
$taskRepository=(Get-Location).Path
$taskEvidence='verification/git-private-path-fix-2026-11-03'
$taskApk='app/build/outputs/apk/debug/app-debug.apk'
$taskBaseline='dist/CodePocket-github-sync-2026-11-03-debug.apk'
$env:TEMP=Join-Path $taskRepository '.tools/semantics-tmp'
$env:TMP=$env:TEMP
& "./$taskEvidence/verify-artifact.ps1"
if($LASTEXITCODE -ne 0){throw 'APK signature/metadata inspection failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_cpp_apk.py $taskApk --baseline $taskBaseline --unchanged-git-remote-runtime --output "$taskEvidence/cpp-apk-inspection.json" *> "$taskEvidence/cpp-apk.log"
if($LASTEXITCODE -ne 0){throw 'Packaged C++ inspection failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_web_auxiliary_apk.py $taskApk --baseline $taskBaseline --output "$taskEvidence/web-apk-inspection.json" *> "$taskEvidence/web-apk.log"
if($LASTEXITCODE -ne 0){throw 'Web APK inspection failed'}
Write-Output 'WEB_APK_SIGNATURE_METADATA_CPP_CLASSES_LICENSES_AND_RUNTIME_IDENTITY_PASSED'
