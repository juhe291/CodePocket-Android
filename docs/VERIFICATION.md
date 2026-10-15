# CodePocket 接手检查与最终验证

验证日期：2026-10-15（Asia/Shanghai）。本地完整 Debug 构建成功；设备交互和 GitHub Actions 远端运行尚未验收。

## 交付 APK

- [CodePocket-v0.2-debug.apk](../dist/CodePocket-v0.2-debug.apk)，21,676,972 字节，约 20.67 MiB。
- 包名 com.codepocket.ide，版本 0.2.0（versionCode 2），Debug。
- 最低 API 24（Android 7.0），targetSdk 34，compileSdk / Build Tools 35；仅 arm64-v8a。
- SHA256：3f7c3d72cfa1c11b581149f5138bc28f01678ff3a04810d867848ea0c1b6899e。
- APK v2 签名验证、ZIP CRC 和含 16 KB 原生库偏移的 ZIP 对齐检查通过。对齐检查不等于已在 16 KB 页面设备中运行。
- 存在 DEX、libpython3.13.so、Chaquopy 原生库、标准库和 runner.pyc。6 个 Python 资源归档 CRC 均通过。

## 实际执行的检查

| 检查 | 真实结果 |
| --- | --- |
| 完整 Gradle | :app:testDebugUnitTest :app:lintDebug :app:assembleDebug，BUILD SUCCESSFUL |
| 最终一次重建 | 43 秒，62 个任务：22 执行、40 使用已有结果 |
| Kotlin JVM 单元测试 | 16 项，15 通过、1 因 Windows 跳过符号链接用例，0 失败/错误 |
| 源码 Python 3.13.9 回归 | 13/13 通过，2026-10-15 重新执行 |
| APK 内实际 runner.pyc | 从 APK 提取并在主机 CPython 3.13 中执行同一组 13 项测试，13/13 通过 |
| Android Lint | 0 错误、12 条警告，未禁用 Lint、未添加 baseline 掩盖问题 |
| Wandbox 真实接口 | 2026-10-14 三项通过：STL/stdin 输出 8、编译错误 status=1 带 prog.cc 行号、运行退出码 7 |
| 安卓设备 | 本次 adb devices -l 未检测到设备或模拟器；未执行 Android 内部运行/交互验收 |
| GitHub Actions | 工作流已维护，本目录没有 Git 仓库/远端，因此未在 GitHub 上实际运行 |

APK 字节码测试证明打包内容正确且在匹配主机 Python 上通过测试，不能代替 Chaquopy 在 Android 上的启动、原生库加载及执行验证。

## 修改文件与原因

| 文件 | 修改 |
| --- | --- |
| MainActivity.kt | 修复 background/foreground 与 Android View 属性同名导致的编译错误；API 26 导航栏标志增加版本判断以兼容 API 24；先读后切缓冲区；保存失败阻止丢弃；自动保存、标签恢复、宽度布局、文件树切换、云端提醒、限量导入、导出快照、错误回到原文件 |
| Workspace.kt | 去除 API 24 不支持的 Path；路径与符号链接边界；递归删除先检查；写入同步后原子替换、失败保留原文；严格 UTF-8、BOM 与大小限制 |
| EditorFeatures.kt | 分语言高亮，修复 Python 整除/三引号，补全与忽略注释和字符串的括号匹配 |
| EditorActions.kt / CodeEditor.kt | 自动缩进、选区缩进、括号/引号补齐和跳过、光标变化通知，避免普通字符输入触发整篇词法扫描 |
| ExecutionEngines.kt / RunSession.kt | 编译/运行/网络/超时/服务错误分类，stderr 不再等于失败，连接清理、响应限量、跨 Activity 单一运行队列 |
| runner.py | 模块和 pyc 刷新、标准流/cwd/argv/trace 恢复、SystemExit 与语法错误分类、异常文件行号、满 stderr 仍保留 traceback |
| AndroidManifest.xml / styles.xml | 清除冗余方向属性，保留自适应窗口；原生浅色主题覆盖对话框 |
| app/build.gradle.kts | 固定 Build Tools 35，可配置匹配的 buildPython，增加 JVM 测试依赖 |
| gradlew、gradle/wrapper | 加入 Gradle 8.9 Wrapper 和官方分发校验值 |
| .github/workflows/build-apk.yml | SDK、Python/Kotlin 测试、Lint、APK 签名/归档/对齐、打包字节码测试，以及可下载 Debug APK 和报告 |
| tests、app/src/test | Python、文件安全、编辑规则和 Wandbox 响应回归 |
| scripts、README、docs、.gitignore | 本地可复现构建、实际 APK 检查、架构/限制、实机清单和生成目录忽略 |

完整文件清单以 verification/changed-files.json 为准。保留原始源码 verification/original-v0.2.zip。

## 环境与已解决的构建障碍

原环境只有 JDK 26，没有 SDK/Gradle。便携 JDK 17、Gradle 8.9、SDK 35、完整 Python 3.13.9 均保留在忽略提交的 .tools 中，未改系统 Java 或切换代理节点。

最小 Python 嵌入包缺 venv，已换为完整 NuGet Python；大型依赖下载中断后按官方校验值补齐。Gradle 8.9 分发 SHA256 为 d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab，Kotlin 1.9.25 编译器与 Maven Central SHA1 6e698f23ca1f74d6672827f61e92c2fe015d1b88 一致。

项目从 OneDrive 桌面迁移到本地桌面后，在当前真实路径重新构建全部任务，未用旧类文件代替迁移后的验证。当前目录：

    C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android

本机复现：

    .\scripts\build-local.ps1 -ProxyHost 127.0.0.1 -UseLocalMirror

临时镜像仅在 .tools/mirror.init.gradle 中；正式仓库仍使用 Google / Maven Central。代理地址按实际环境设置，不要求切节点。

## 尚存的 12 条 Lint 警告

- 9 条 SetTextI18n：中文界面文本尚未全部资源化。
- 1 条 OldTargetApi：targetSdk 仍为 34，保持原版行为；未在本轮直接升级目标版本并引入边到边等新行为。
- 1 条 ChromeOsAbiSupport：当前仅打包 arm64，未支持 x86_64 ChromeOS。
- 1 条 DataExtractionRules：保留 allowBackup=false，Android 12+ 的数据提取规则尚未单独定义，系统迁移行为还需设备确认。

## 尚需设备验证与已知能力边界

按 [DEVICE_TESTS.md](DEVICE_TESTS.md) 验证触屏、组合输入、蓝牙键鼠、自由窗口/旋转、SAF、主题与应用内 Chaquopy、网络失败和运行状态。当前 C++ 只运行单文件，不上传本地头文件或做多文件链接；补全是词法级。Python 不是安全沙箱，6 秒字节码 watchdog 不能强制结束所有阻塞/原生调用。stdin 预填，未支持运行时交互。

## 证据文件

- verification/build-local.log：最终成功日志。
- app/build/test-results/testDebugUnitTest：JUnit XML；app/build/reports/tests：HTML。
- app/build/reports/lint-results-debug.xml、.txt、.html：Lint 报告。
- verification/python-tests-2026-10-15.log、packaged-python-tests.log：源码与 APK 字节码测试。
- verification/apk-inspection.json、apk-signature.txt、apk-badging.txt、apk-alignment.txt：产物检查。
- verification/build-paused-2026-10-14.log 及 build-attempt-*：历史暂停/失败日志，不能误当成最终结果。
