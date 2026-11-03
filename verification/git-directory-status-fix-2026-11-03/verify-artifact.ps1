$ErrorActionPreference='Stop'
$taskRepository=(Get-Location).Path
$env:JAVA_HOME=(Get-ChildItem .tools/jdk17 -Directory|Select-Object -First 1).FullName
$taskEvidence='verification/git-directory-status-fix-2026-11-03'
$taskApk='app/build/outputs/apk/debug/app-debug.apk'
& './.tools/android-sdk/build-tools/35.0.0/apksigner.bat' verify --verbose --print-certs $taskApk *> "$taskEvidence/certificates.txt"
if($LASTEXITCODE -ne 0){throw 'APK signature verification failed'}
& './.tools/android-sdk/build-tools/35.0.0/zipalign.exe' -c -P 16 4 $taskApk *> "$taskEvidence/alignment.txt"
if($LASTEXITCODE -ne 0){throw 'APK alignment verification failed'}
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump badging $taskApk *> "$taskEvidence/badging.txt"
if($LASTEXITCODE -ne 0){throw 'APK metadata verification failed'}
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump permissions $taskApk *> "$taskEvidence/permissions.txt"
if($LASTEXITCODE -ne 0){throw 'APK permissions verification failed'}
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump xmltree $taskApk AndroidManifest.xml *> "$taskEvidence/manifest-tree.txt"
if($LASTEXITCODE -ne 0){throw 'APK manifest verification failed'}
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump --values resources $taskApk *> "$taskEvidence/resources.txt"
if($LASTEXITCODE -ne 0){throw 'Resource inspection failed'}
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump --values resources dist/CodePocket-git-private-path-fix-2026-11-03-debug.apk *> "$taskEvidence/baseline-resources.txt"
if($LASTEXITCODE -ne 0){throw 'Previous resource inspection failed'}
Write-Output 'CURRENT_APK_SIGNATURE_ALIGNMENT_METADATA_PASSED'
