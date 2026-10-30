$ErrorActionPreference='Continue'
$taskRepository=(Get-Location).Path
$taskTemporary=Join-Path $taskRepository '.tools/semantics-tmp'
New-Item -ItemType Directory -Path $taskTemporary -Force | Out-Null
$env:TEMP=$taskTemporary
$env:TMP=$taskTemporary
$env:JAVA_HOME=(Get-ChildItem .tools/jdk17 -Directory|Select-Object -First 1).FullName
$env:ANDROID_HOME=Join-Path $taskRepository '.tools/android-sdk'
$env:ANDROID_USER_HOME=Join-Path $taskRepository '.tools/android-user'
$env:GRADLE_USER_HOME=Join-Path $taskRepository '.tools/gradle-home'
& ./.tools/gradle-8.9/bin/gradle.bat :app:testDebugUnitTest --tests *WorkbenchStartupBehaviorTest* --tests *WorkbenchViewsTest* --tests *TabletAnimationLayoutTest* --tests *TerminalWorkbenchViewTest* --tests *InteractionTest* --tests *ShellUiTest* --offline --no-daemon --max-workers=4 '-Pkotlin.compiler.execution.strategy=in-process' '-Pandroid.overridePathCheck=true' '-Pcodepocket.testRepository=https://repo.maven.apache.org/maven2' "-Pcodepocket.buildPython=$taskRepository/.tools/python313-full/tools/python.exe" "-Pcodepocket.semanticWheels=$taskRepository/.tools/semantic-wheels" "-Duser.home=$taskRepository/.tools/userhome" "-Djava.io.tmpdir=$taskTemporary" -I .tools/mirror.init.gradle -I verification/cpp-local-integration-2026-10-27/host-tests.init.gradle *> verification/workbench-behavior-2026-10-28/closing-regression-targeted.log
$taskExit=$LASTEXITCODE
Get-Content verification/workbench-behavior-2026-10-28/closing-regression-targeted.log -Tail 65
exit $taskExit





