$ErrorActionPreference='Continue'
$taskRepository=(Get-Location).Path
$taskEvidence='verification/workbench-search-entry-2026-10-24'
$taskPython=Join-Path $taskRepository '.tools/python313-full/tools/python.exe'
$env:JAVA_HOME=(Get-ChildItem .tools/jdk17 -Directory|Select-Object -First 1).FullName
& $taskPython -B -m unittest discover -s tests -v *> "$taskEvidence/python-source.log"
if($LASTEXITCODE -ne 0){throw 'Python source tests failed'}
& $taskPython -B scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk --output "$taskEvidence/apk-inspection.json" *> "$taskEvidence/packaged-python.log"
if($LASTEXITCODE -ne 0){throw 'Packaged Python tests failed'}
& $taskPython -B scripts/verify_shell_startup.py --junit verification/workbench-search-entry-2026-10-24/final-junit --shell 'C:/Program Files/Git/bin/bash.exe' --output "$taskEvidence/shell-startup-inspection.json" *> "$taskEvidence/shell-startup.log"
if($LASTEXITCODE -ne 0){throw 'Shell verification failed'}
& $taskPython -B scripts/verify_snippets.py --junit verification/workbench-search-entry-2026-10-24/final-junit --output "$taskEvidence/snippets.json" --cpp-compiler 'C:/mingw64/bin/g++.exe' *> "$taskEvidence/snippets.log"
if($LASTEXITCODE -ne 0){throw 'Real snippet execution failed'}
& './.tools/android-sdk/build-tools/35.0.0/apksigner.bat' verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk *> "$taskEvidence/certificates.txt"
if($LASTEXITCODE -ne 0){throw 'Signature failed'}
& './.tools/android-sdk/build-tools/35.0.0/zipalign.exe' -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk *> "$taskEvidence/alignment.txt"
if($LASTEXITCODE -ne 0){throw 'Alignment failed'}
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump badging app/build/outputs/apk/debug/app-debug.apk *> "$taskEvidence/badging.txt"
if($LASTEXITCODE -ne 0){throw 'Metadata failed'}
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump permissions app/build/outputs/apk/debug/app-debug.apk *> "$taskEvidence/permissions.txt"
if($LASTEXITCODE -ne 0){throw 'Permission inspection failed'}
Write-Output 'PYTHON_SHELL_SNIPPETS_SIGNATURE_ALIGNMENT_PASSED'
