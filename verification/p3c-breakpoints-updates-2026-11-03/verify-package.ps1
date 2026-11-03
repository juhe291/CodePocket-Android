$ErrorActionPreference='Continue'
$repo=(Get-Location).Path
$evidence='verification/p3c-breakpoints-updates-2026-11-03'
$apk='app/build/outputs/apk/debug/app-debug.apk'
$baseline='dist/CodePocket-bugfix-2026-11-03-debug.apk'
$python='./.tools/python313-full/tools/python.exe'
$env:TEMP=Join-Path $repo '.tools/semantics-tmp'
$env:TMP=$env:TEMP
$env:PYTHONIOENCODING='utf-8'
$env:PYTHONPATH=(Join-Path $repo '.tools/semantic-probe/site')+';'+(Join-Path $repo '.tools/formatting-probe/site')+';'+(Join-Path $repo '.tools/git-probe/site')

& './.tools/android-sdk/build-tools/35.0.0/apksigner.bat' verify --verbose --print-certs $apk *> "$evidence/certificates.txt"
if($LASTEXITCODE -ne 0){throw 'APK signature verification failed'}
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump permissions $apk *> "$evidence/permissions.txt"
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump xmltree $apk AndroidManifest.xml *> "$evidence/manifest-tree.txt"
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump --values resources $apk *> "$evidence/resources.txt"

& $python -B scripts/verify_apk.py $apk --output "$evidence/packaged-python.json" *> "$evidence/packaged-python.log"
$pythonCheck=$LASTEXITCODE
& $python -B scripts/verify_semantics_apk.py $apk --output "$evidence/semantic-apk-inspection.json" *> "$evidence/semantic-apk.log"
$semanticCheck=$LASTEXITCODE
& $python -B scripts/verify_git_apk.py $apk --baseline $baseline --output "$evidence/git-apk-inspection.json" *> "$evidence/git-apk.log"
$gitCheck=$LASTEXITCODE
& $python -B scripts/verify_git_status_apk.py $apk --baseline $baseline --output "$evidence/git-status-apk-inspection.json" *> "$evidence/git-status-apk.log"
$gitStatusCheck=$LASTEXITCODE
& $python -B scripts/verify_formatting_apk.py $apk --baseline $baseline --output "$evidence/formatting-apk-inspection.json" *> "$evidence/formatting-apk.log"
$formatCheck=$LASTEXITCODE
& $python -B scripts/verify_cpp_apk.py $apk --baseline $baseline --output "$evidence/cpp-apk-inspection.json" *> "$evidence/cpp-apk.log"
$cppCheck=$LASTEXITCODE
& $python -B scripts/verify_web_auxiliary_apk.py $apk --baseline $baseline --output "$evidence/web-apk-inspection.json" *> "$evidence/web-apk.log"
$webCheck=$LASTEXITCODE
& $python -B scripts/verify_vector_resources.py app/src/main/res --output "$evidence/vector-resources.json" *> "$evidence/vector-resources.log"
$vectorCheck=$LASTEXITCODE
& $python -B scripts/verify_shell_startup.py --junit app/build/test-results/testDebugUnitTest --shell 'C:/Program Files/Git/bin/bash.exe' --output "$evidence/shell-startup-inspection.json" *> "$evidence/shell-startup.log"
$shellCheck=$LASTEXITCODE
& $python -B scripts/verify_snippets.py --junit app/build/test-results/testDebugUnitTest --output "$evidence/snippets.json" --cpp-compiler 'C:/mingw64/bin/g++.exe' *> "$evidence/snippets.log"
$snippetCheck=$LASTEXITCODE
"python=$pythonCheck semantic=$semanticCheck git=$gitCheck gitStatus=$gitStatusCheck formatting=$formatCheck cpp=$cppCheck web=$webCheck vector=$vectorCheck shell=$shellCheck snippets=$snippetCheck"
$all=@($pythonCheck,$semanticCheck,$gitCheck,$gitStatusCheck,$formatCheck,$cppCheck,$webCheck,$vectorCheck,$shellCheck,$snippetCheck)|Where-Object{$_ -ne 0}
if($all.Count -gt 0){exit 1}
Write-Output 'APK_CHECKS_PASSED'
