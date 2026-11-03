$ErrorActionPreference='Continue'
$repo=(Get-Location).Path
$evidence=Join-Path $repo 'verification/p3c-breakpoints-updates-2026-11-03'
$tmp=Join-Path $repo '.tools/semantics-tmp'
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
$env:TEMP=$tmp
$env:TMP=$tmp
$env:JAVA_HOME=(Get-ChildItem .tools/jdk17 -Directory|Select-Object -First 1).FullName
$env:ANDROID_HOME=Join-Path $repo '.tools/android-sdk'
$env:ANDROID_USER_HOME=Join-Path $repo '.tools/android-user'
$env:GRADLE_USER_HOME=Join-Path $repo '.tools/gradle-home'
$env:PYTHONIOENCODING='utf-8'
$env:SSL_CERT_FILE='C:\Program Files\Git\mingw64\etc\ssl\certs\ca-bundle.crt'

& (Join-Path $repo '.tools/python313-full/tools/python.exe') -m unittest discover -s tests -v *> (Join-Path $evidence 'source-python-tests.log')
$pythonExit=$LASTEXITCODE
Set-Content (Join-Path $evidence 'source-python-tests-exit.txt') $pythonExit -Encoding utf8

& ./.tools/gradle-8.9/bin/gradle.bat :app:testDebugUnitTest :editor-assist:test :terminal-emulator:testDebugUnitTest :app:lintDebug :editor-sora:lintDebug :workspace-core:lintDebug :app:assembleDebug --continue --offline --no-daemon --max-workers=4 '-Pkotlin.compiler.execution.strategy=in-process' '-Pandroid.overridePathCheck=true' "-Pcodepocket.buildPython=$repo/.tools/python313-full/tools/python.exe" "-Pcodepocket.semanticWheels=$repo/.tools/semantic-wheels" "-Duser.home=$repo/.tools/userhome" "-Djava.io.tmpdir=$tmp" -I .tools/mirror.init.gradle -I verification/cpp-local-integration-2026-10-27/host-tests.init.gradle *> (Join-Path $evidence 'full-build.log')
$gradleExit=$LASTEXITCODE
Set-Content (Join-Path $evidence 'full-build-exit.txt') $gradleExit -Encoding utf8
"pythonExit=$pythonExit gradleExit=$gradleExit"
exit $gradleExit
