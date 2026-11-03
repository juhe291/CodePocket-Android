$ErrorActionPreference='Stop'
$taskRepository=(Get-Location).Path
$taskEvidence='verification/workbench-behavior-2026-10-28'
$taskApk='app/build/outputs/apk/debug/app-debug.apk'
$env:TEMP=Join-Path $taskRepository '.tools/semantics-tmp'
$env:TMP=$env:TEMP
& "./$taskEvidence/verify-artifact.ps1"
if($LASTEXITCODE -ne 0){throw 'APK signature/metadata inspection failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_apk.py $taskApk --output "$taskEvidence/apk-python.json" *> "$taskEvidence/apk-python.log"
if($LASTEXITCODE -ne 0){throw 'Packaged Python regression failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_semantics_apk.py $taskApk --output "$taskEvidence/semantic-apk-inspection.json" *> "$taskEvidence/semantic-apk.log"
if($LASTEXITCODE -ne 0){throw 'Packaged semantic regression failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_cpp_apk.py $taskApk --baseline dist/CodePocket-python-project-context-2026-10-26-debug.apk --allow-runner-update --output "$taskEvidence/cpp-apk-inspection.json" *> "$taskEvidence/cpp-apk.log"
if($LASTEXITCODE -ne 0){throw 'Packaged C++ inspection failed'}
Write-Output 'FINAL_APK_SIGNATURE_METADATA_PYTHON_SEMANTICS_CPP_PASSED'
