param(
    [string]$JavaHome,
    [string]$SdkRoot,
    [string]$BuildPython,
    [string]$ProxyHost,
    [int]$ProxyPort = 7890,
    [switch]$UseLocalMirror
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    if (!$JavaHome -and (Test-Path '.tools/jdk17')) {
        $JavaHome = (Get-ChildItem '.tools/jdk17' -Directory | Select-Object -First 1).FullName
    }
    if (!$SdkRoot -and (Test-Path '.tools/android-sdk/platforms/android-35/android.jar')) {
        $SdkRoot = Join-Path $projectRoot '.tools/android-sdk'
    }
    if (!$BuildPython -and (Test-Path '.tools/python313-full/tools/python.exe')) {
        $BuildPython = Join-Path $projectRoot '.tools/python313-full/tools/python.exe'
    }
    if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
    if ($SdkRoot) { $env:ANDROID_HOME = $SdkRoot }
    if (!$env:JAVA_HOME) { throw '请配置 JDK 17 的 JAVA_HOME，或传入 -JavaHome。' }
    if (!$env:ANDROID_HOME) { throw '请配置 ANDROID_HOME，或传入 -SdkRoot。' }
    if ($BuildPython) {
        & $BuildPython -c 'import sys, venv; assert sys.version_info[:2] == (3, 13), "Requires Python 3.13"'
        if ($LASTEXITCODE -ne 0) { throw '构建 Python 必须是包含 venv 的完整 3.13，不能直接使用最小嵌入包。' }
    }
    $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools/android-user'
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools/gradle-home'
    New-Item -ItemType Directory -Force 'verification' | Out-Null
    $options = @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', '--stacktrace', '--no-daemon', '--info')
    $options += '-Pandroid.overridePathCheck=true'
    $options += "-Duser.home=$projectRoot/.tools/userhome"
    $options += '--max-workers=4'
    if ($UseLocalMirror) {
        if (!(Test-Path '.tools/mirror.init.gradle')) { throw '本地临时镜像配置不存在。' }
        $options += @('-I', '.tools/mirror.init.gradle', '-Dhttp.nonProxyHosts=*.aliyun.com')
    }
    if ($BuildPython) { $options += "-Pcodepocket.buildPython=$BuildPython" }
    if ($ProxyHost) {
        $options += @("-Dhttps.proxyHost=$ProxyHost", "-Dhttps.proxyPort=$ProxyPort", "-Dhttp.proxyHost=$ProxyHost", "-Dhttp.proxyPort=$ProxyPort")
    }
    $gradle = if (Test-Path '.tools/gradle-8.9/bin/gradle.bat') { '.tools/gradle-8.9/bin/gradle.bat' } else { '.\gradlew.bat' }
    & $gradle @options 2>&1 | Tee-Object 'verification/build-local.log'
    exit $LASTEXITCODE
} finally { Pop-Location }
