param([Parameter(Mandatory=$true)][string]$Repository)
$ErrorActionPreference = 'Stop'
if ((Resolve-Path -LiteralPath $Repository).Path -ne 'C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android') { throw 'Unexpected workspace' }
Set-Location -LiteralPath $Repository
$evidence = 'verification/p1a-shell-2026-10-17'
New-Item -ItemType Directory -Force $evidence | Out-Null
$env:JAVA_HOME = (Get-ChildItem '.tools/jdk17' -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path $Repository '.tools/android-sdk'
$env:ANDROID_USER_HOME = Join-Path $Repository '.tools/android-user'
$env:GRADLE_USER_HOME = Join-Path $Repository '.tools/gradle-home'
& './.tools/gradle-8.9/bin/gradle.bat' :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --rerun-tasks --offline --stacktrace --no-daemon --max-workers=4 '-Pandroid.overridePathCheck=true' "-Pcodepocket.buildPython=$Repository/.tools/python313-full/tools/python.exe" "-Duser.home=$Repository/.tools/userhome" -I .tools/mirror.init.gradle 2>&1 | Tee-Object (Join-Path $evidence 'build.log')
if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
& './.tools/python313-full/tools/python.exe' -B scripts/verify_snippets.py --junit app/build/test-results/testDebugUnitTest --output (Join-Path $evidence 'snippet-inspection.json') --cpp-compiler 'C:/mingw64/bin/g++.exe' 2>&1 | Tee-Object (Join-Path $evidence 'snippet-tests.log')
if ($LASTEXITCODE -ne 0) { throw 'Generated snippet compiler smoke failed' }
& './.tools/python313-full/tools/python.exe' -B -m unittest discover -s tests -v 2>&1 | Tee-Object (Join-Path $evidence 'python-tests.log')
if ($LASTEXITCODE -ne 0) { throw 'Python source tests failed' }
& './.tools/python313-full/tools/python.exe' -B scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk --output (Join-Path $evidence 'apk-inspection.json') 2>&1 | Tee-Object (Join-Path $evidence 'packaged-python-tests.log')
if ($LASTEXITCODE -ne 0) { throw 'APK Python bytecode tests failed' }
& './.tools/android-sdk/build-tools/35.0.0/apksigner.bat' verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk 2>&1 | Tee-Object (Join-Path $evidence 'apk-signature.txt')
if ($LASTEXITCODE -ne 0) { throw 'APK signature failed' }
& './.tools/android-sdk/build-tools/35.0.0/zipalign.exe' -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk 2>&1 | Tee-Object (Join-Path $evidence 'apk-alignment.txt')
if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed' }
& './.tools/android-sdk/build-tools/35.0.0/aapt.exe' dump badging app/build/outputs/apk/debug/app-debug.apk 2>&1 | Tee-Object (Join-Path $evidence 'apk-badging.txt')
if ($LASTEXITCODE -ne 0) { throw 'APK badging failed' }
Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination 'dist/CodePocket-P1A-shell-debug.apk'
New-Item -ItemType Directory -Force (Join-Path $evidence 'junit') | Out-Null
Get-ChildItem 'app/build/test-results/testDebugUnitTest' -Filter 'TEST-*.xml' | Copy-Item -Destination (Join-Path $evidence 'junit')
Copy-Item -LiteralPath 'app/build/reports/lint-results-debug.xml' -Destination $evidence
& git diff --check
if ($LASTEXITCODE -ne 0) { throw 'git diff --check failed' }
Write-Output 'VERIFIED_APK=dist/CodePocket-P1A-shell-debug.apk'
Get-FileHash -LiteralPath 'dist/CodePocket-P1A-shell-debug.apk' -Algorithm SHA256
