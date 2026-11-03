$ErrorActionPreference='Continue'
$taskRepository=(Get-Location).Path
$taskTemporary=Join-Path $taskRepository '.tools/semantics-tmp'
New-Item -ItemType Directory -Path $taskTemporary -Force | Out-Null
$env:TEMP=$taskTemporary
$env:TMP=$taskTemporary
$taskEvidence='verification/python-project-run-2026-10-28'
$taskPython=Join-Path $taskRepository '.tools/python313-full/tools/python.exe'
$env:PYTHONPATH=Join-Path $taskRepository '.tools/semantic-probe/site'
$env:JAVA_HOME=(Get-ChildItem .tools/jdk17 -Directory|Select-Object -First 1).FullName
Write-Output 'Source suite already passed: python-source-offline-site.log'

& $taskPython -B scripts/verify_shell_startup.py --junit verification/python-project-run-2026-10-28/final-junit --shell 'C:/Program Files/Git/bin/bash.exe' --output "$taskEvidence/shell-startup-inspection.json" *> "$taskEvidence/shell-startup-retry.log"
if($LASTEXITCODE -ne 0){throw 'Shell verification failed'}
& $taskPython -B scripts/verify_snippets.py --junit verification/python-project-run-2026-10-28/final-junit --output "$taskEvidence/snippets.json" --cpp-compiler 'C:/mingw64/bin/g++.exe' *> "$taskEvidence/snippets.log"
if($LASTEXITCODE -ne 0){throw 'Real snippet execution failed'}
& $taskPython -B scripts/verify_semantics_apk.py app/build/outputs/apk/debug/app-debug.apk --output "$taskEvidence/semantic-apk-inspection.json" *> "$taskEvidence/semantic-apk.log"
if($LASTEXITCODE -ne 0){throw "Packaged semantic tests failed"}
& './.tools/android-sdk/build-tools/35.0.0/apksigner.bat' verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk *> "$taskEvidence/certificates.txt"
if($LASTEXITCODE -ne 0){throw 'Signature failed'}
& './.tools/android-sdk/build-tools/35.0.0/zipalign.exe' -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk *> "$taskEvidence/alignment.txt"
if($LASTEXITCODE -ne 0){throw 'Alignment failed'}
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump badging app/build/outputs/apk/debug/app-debug.apk *> "$taskEvidence/badging.txt"
if($LASTEXITCODE -ne 0){throw 'Metadata failed'}
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump permissions app/build/outputs/apk/debug/app-debug.apk *> "$taskEvidence/permissions.txt"
if($LASTEXITCODE -ne 0){throw 'Permission inspection failed'}
Write-Output 'PYTHON_SHELL_SNIPPETS_SIGNATURE_ALIGNMENT_PASSED'
& $taskPython -B scripts/verify_cpp_apk.py app/build/outputs/apk/debug/app-debug.apk --baseline dist/CodePocket-python-project-context-2026-10-26-debug.apk --allow-runner-update --output "$taskEvidence/cpp-apk-inspection.json" *> "$taskEvidence/cpp-apk.log"
if($LASTEXITCODE -ne 0){throw 'Actual packaged C++ verification failed'}
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump xmltree app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml *> "$taskEvidence/manifest-tree.txt"
if($LASTEXITCODE -ne 0){throw 'Manifest inspection failed'}
Write-Output 'ACTUAL_CPP_APK_INSPECTION_PASSED_NO_ANDROID_EXECUTION'
