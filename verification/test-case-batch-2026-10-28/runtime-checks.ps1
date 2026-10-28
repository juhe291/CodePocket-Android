$ErrorActionPreference='Stop'
$taskRepository=(Get-Location).Path
$taskEvidence='verification/test-case-batch-2026-10-28'
$taskPython=Join-Path $taskRepository '.tools/python313-full/tools/python.exe'
$env:TEMP=Join-Path $taskRepository '.tools/semantics-tmp'
$env:TMP=$env:TEMP
$env:PYTHONPATH=Join-Path $taskRepository '.tools/semantic-probe/site'
& $taskPython -B -m unittest discover -s tests -v *> "$taskEvidence/python-source.log"
if($LASTEXITCODE -ne 0){throw 'Source Python tests failed'}
& $taskPython -B scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk --output "$taskEvidence/apk-python.json" *> "$taskEvidence/apk-python.log"
if($LASTEXITCODE -ne 0){throw 'Packaged Python tests failed'}
& $taskPython -B scripts/verify_cpp_apk.py app/build/outputs/apk/debug/app-debug.apk --baseline dist/CodePocket-python-project-context-2026-10-26-debug.apk --allow-runner-update --output "$taskEvidence/cpp-apk-inspection.json" *> "$taskEvidence/cpp-apk-inspection.log"
if($LASTEXITCODE -ne 0){throw 'C++ APK inspection failed'}
& $taskPython -B scripts/verify_semantics_apk.py app/build/outputs/apk/debug/app-debug.apk --output "$taskEvidence/semantic-apk-inspection.json" *> "$taskEvidence/semantic-apk.log"
if($LASTEXITCODE -ne 0){throw 'Packaged semantic tests failed'}
& $taskPython -B scripts/verify_shell_startup.py --junit "$taskEvidence/final-junit" --shell 'C:/Program Files/Git/bin/bash.exe' --output "$taskEvidence/shell-startup-inspection.json" *> "$taskEvidence/shell-startup.log"
if($LASTEXITCODE -ne 0){throw 'Shell verification failed'}
& $taskPython -B scripts/verify_snippets.py --junit "$taskEvidence/final-junit" --output "$taskEvidence/snippets.json" --cpp-compiler 'C:/mingw64/bin/g++.exe' *> "$taskEvidence/snippets.log"
if($LASTEXITCODE -ne 0){throw 'Real snippet execution failed'}
Write-Output 'CURRENT_PYTHON_CPP_SEMANTICS_SHELL_SNIPPETS_HOST_CHECKS_PASSED'

