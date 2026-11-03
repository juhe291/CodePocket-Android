$ErrorActionPreference='Continue'
$taskEvidence='verification/git-directory-status-fix-2026-11-03'
$taskApk='app/build/outputs/apk/debug/app-debug.apk'
$taskBaseline='dist/CodePocket-updates-0.2.1-2026-11-03-debug.apk'
& "./$taskEvidence/verify-artifact.ps1"
if($LASTEXITCODE -ne 0){throw 'APK signature/metadata verification failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_apk.py $taskApk --output "$taskEvidence/apk-python.json" *> "$taskEvidence/apk-python.log"
if($LASTEXITCODE -ne 0){throw 'Actual packaged Python regression failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_semantics_apk.py $taskApk --output "$taskEvidence/semantic-apk-inspection.json" *> "$taskEvidence/semantic-apk.log"
if($LASTEXITCODE -ne 0){throw 'Actual packaged semantic regression failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_cpp_apk.py $taskApk --baseline $taskBaseline --allow-git-status-update --output "$taskEvidence/cpp-apk-inspection.json" *> "$taskEvidence/cpp-apk.log"
if($LASTEXITCODE -ne 0){throw 'Strict C++/one-module Git delta verification failed'}
& ./.tools/python313-full/tools/python.exe -B scripts/verify_git_status_apk.py $taskApk --baseline $taskBaseline --output "$taskEvidence/git-apk-inspection.json" *> "$taskEvidence/git-apk.log"
if($LASTEXITCODE -ne 0){throw 'Actual packaged Git regression failed'}
Write-Output 'SIGNATURE_ALIGNMENT_METADATA_PYTHON_SEMANTICS_CPP_AND_ONE_MODULE_GIT_DELTA_PASSED'
