$ErrorActionPreference='Continue' # Windows PowerShell 5 wraps native unittest stderr; exit codes below are authoritative.
$taskRepository=(Get-Location).Path
$taskEvidence='verification/git-remote-delivery-2026-11-03'
$taskApk='app/build/outputs/apk/debug/app-debug.apk'
$taskBaseline='dist/CodePocket-local-git-2026-11-02-debug.apk'
$env:TEMP=Join-Path $taskRepository '.tools/semantics-tmp'
$env:TMP=$env:TEMP
& "./$taskEvidence/verify-artifact.ps1"
if($LASTEXITCODE -ne 0){throw 'APK signature/metadata inspection failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_apk.py $taskApk --output "$taskEvidence/apk-python.json" *> "$taskEvidence/apk-python.log"
if($LASTEXITCODE -ne 0){throw 'Packaged Python regression failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_semantics_apk.py $taskApk --output "$taskEvidence/semantic-apk-inspection.json" *> "$taskEvidence/semantic-apk.log"
if($LASTEXITCODE -ne 0){throw 'Packaged semantic regression failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_cpp_apk.py $taskApk --baseline $taskBaseline --allow-git-remote-update --output "$taskEvidence/cpp-apk-inspection.json" *> "$taskEvidence/cpp-apk.log"
if($LASTEXITCODE -ne 0){throw 'Packaged C++ inspection failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_git_remote_apk.py $taskApk --baseline $taskBaseline --output "$taskEvidence/git-apk-inspection.json" *> "$taskEvidence/git-apk.log"
if($LASTEXITCODE -ne 0){throw 'Packaged Git and formatting regression failed'}
Write-Output 'FINAL_APK_SIGNATURE_METADATA_PYTHON_SEMANTICS_CPP_GIT_FORMATTING_PASSED'
