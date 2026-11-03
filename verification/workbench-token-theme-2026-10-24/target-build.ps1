$ErrorActionPreference='Continue'
$taskRepository=(Get-Location).Path
$env:JAVA_HOME=(Get-ChildItem .tools/jdk17 -Directory|Select-Object -First 1).FullName
$env:ANDROID_HOME=Join-Path $taskRepository '.tools/android-sdk'
$env:ANDROID_USER_HOME=Join-Path $taskRepository '.tools/android-user'
$env:GRADLE_USER_HOME=Join-Path $taskRepository '.tools/gradle-home'
& ./.tools/gradle-8.9/bin/gradle.bat :app:testDebugUnitTest --tests com.codepocket.ide.SoraWorkbenchIntegrationTest --tests com.codepocket.ide.SettingsSurfaceVisualTest --tests com.codepocket.ide.AuxiliarySurfaceIntegrationTest --offline --no-daemon --max-workers=4 '-Pandroid.overridePathCheck=true' '-Pcodepocket.testRepository=https://repo.maven.apache.org/maven2' "-Pcodepocket.buildPython=$taskRepository/.tools/python313-full/tools/python.exe" "-Duser.home=$taskRepository/.tools/userhome" -I .tools/mirror.init.gradle *> verification/workbench-token-theme-2026-10-24/target-final.log
$taskExit=$LASTEXITCODE
Get-Content verification/workbench-token-theme-2026-10-24/target-final.log -Tail 65
exit $taskExit
