# CodePocket v0.2 · Android C++ / Python IDE

面向学生、手机和平板的原生 Kotlin 轻量 IDE。保留 v0.2 的 Android View 界面，提供多文件编辑、本地 Python 和云端 C++17。

**2026-10-15 本地完整构建成功。** [Debug APK](dist/CodePocket-v0.2-debug.apk)（20.67 MiB，Android 7.0+ / arm64-v8a）已通过签名、归档及 APK 内 Python 字节码回归检查。Kotlin 16 项测试中 15 项通过、1 项跳过；Lint 0 错误、12 条警告。实机交互和远端 GitHub Actions 尚未验收。

**真实构建结果见 [验证报告](docs/VERIFICATION.md)，交互验收见 [实机清单](docs/DEVICE_TESTS.md)。单元测试和 APK 打包通过不能代替实机验收。**

## 架构与依赖

| 模块 | 职责 |
| --- | --- |
| `MainActivity.kt` | 自适应布局、文件树、多标签、主题、快捷键、SAF、自动保存、错误导航 |
| `CodeEditor.kt` | 保留原生 EditText 光标、选区、复制粘贴和鼠标行为，通知光标变化 |
| `EditorFeatures.kt` | 分语言词法高亮、候选补全、忽略注释/字符串的括号匹配 |
| `EditorActions.kt` | 可在 JVM 测试的缩进、换行和括号/引号补齐规则 |
| `Workspace.kt` | 内部项目、路径边界、嵌套文件操作、限量读取、临时文件同步后替换 |
| `ExecutionEngines.kt` | Chaquopy、Wandbox HTTP/JSON、退出码和错误分类 |
| `RunSession.kt` | 进程内单一执行队列，状态跨 Activity 重建保留，不持有旧界面 |
| `runner.py` | stdin/stdout/stderr、traceback、尽力超时、输出限量、模块刷新和环境恢复 |

固定版本：JDK 17、Gradle 8.9、AGP 8.7.3、Kotlin 1.9.25、Chaquopy 17.1.0、Python 3.13、compileSdk 35、targetSdk 34。支持 Android API 24+；APK 只包含 **arm64-v8a**，普通 x86_64 模拟器不能安装。

[Chaquopy 兼容表](https://chaquo.com/chaquopy/doc/current/versions.html)支持当前版本组合。[构建机 Python 的主次版本须与嵌入版本匹配](https://chaquo.com/chaquopy/doc/current/android.html#buildpython)。

## 已实现功能

- 深色/浅色主题记忆，多标签和当前光标切换保留，已打开标签在重建时恢复。
- C++/Python 关键字、类型、函数、字符串、数字和注释分别着色；修正 Python `//` 整除，支持三引号字符串。
- 行号同步滚动、自动缩进、成对括号/引号、括号匹配着色；Tab/点击接受词法补全。
- 根据**当前窗口宽度**布局：低于 600 dp 上下排列；600 dp 起编辑/结果分屏；宽度至少 720 dp 且高度至少 480 dp 有可切换文件侧栏。窗口重建时重新计算，未只按方向判断。
- 嵌套文件/目录的新建、重命名、移动、确认删除，支持 `.cpp/.h/.hpp/.py` 等文本文件。
- 编辑停止约 700 ms 自动保存，切文件、离开、切主题和运行前保存。保存失败阻止切换/关闭/运行；先成功读取新文件，再切换当前缓冲区，避免误覆盖。
- Python 本地离线执行、常用标准库、预填 stdin、工作区模块和 traceback；修改模块后重跑不沿用旧缓存。
- C++17 通过公共 [Wandbox](https://github.com/melpon/wandbox) 运行，无需 API Key；首次运行提示并确认发送源码和 stdin。
- 编译错误、运行错误、网络错误、超时、服务响应错误分类。点击错误区/“定位错误”返回对应文件和首个识别出的行；支持 Python 工作区模块。
- 加载状态和重复执行保护；旋转/切主题不会启动第二个运行器。运行期间阻止文件结构操作，仍可编辑。

## 键盘、鼠标和触屏

| 按键 | 功能 |
| --- | --- |
| Tab / Shift+Tab | 接受候选或缩进 / 减少缩进，支持多行选区 |
| Ctrl+Space | 刷新补全候选 |
| Ctrl+S / Ctrl+N / Ctrl+O | 保存 / 新建 / 从系统文件选择器导入 |
| Ctrl+B | 切换侧栏；窄屏切换文件树对话框 |
| F5 / Ctrl+Enter | 运行 |

触屏工具栏提供新建、保存、导入导出、Tab、反缩进、补全、括号和运行。文件树长按/右键打开管理菜单。原生鼠标定位、拖选、滚轮、编辑上下文菜单和输入法组合输入需要逐设备验证；部分系统可能拦截快捷键。

## 文件与 Android 权限

项目在 App 私有 `files/workspace`，**不申请全面存储权限**。SAF 导入复制到内部目录，之后保存不覆盖外部原文件；导出通过系统选择器写到用户指定位置，不长期持有来源权限。

禁止目录越界、绝对路径、符号链接、根目录删除/移动、目录移入自身、覆盖同名项和非法名称。递归删除先检查子树。保存先写同目录临时文件并同步，替换失败保留旧文件。读取最多 1 MB 且 20 万字符；导入限量读取，避免先载入整个大文件。

确认删除后无法撤销；卸载会清空项目，请提前导出。Python 不是沙箱，不可信代码可以直接访问 App 文件，不受 UI 文件模块边界限制。

## 本地构建与测试

安装 JDK 17、含 `venv` 的完整 Python 3.13、Android SDK `platforms;android-35` / `build-tools;35.0.0`，设置 `JAVA_HOME` 和 `ANDROID_HOME`。Python 最小嵌入包不含 `venv`，不能直接用于 Chaquopy 构建；本次本地使用 [Python 官方文档介绍的 NuGet 包](https://docs.python.org/3.13/using/windows.html#the-nuget-org-packages)，无需系统安装。项目包含 Gradle Wrapper 及官方分发 SHA256：

```powershell
python -m unittest discover -s tests -v
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
```

Linux/macOS 使用 `bash ./gradlew`。如 Python 不在 PATH，可加 `"-Pcodepocket.buildPython=C:/path/to/python.exe"`。

Windows 中文路径可能触发 AGP 路径检查；本次构建命令使用 `-Pandroid.overridePathCheck=true`。关闭检查不代表所有工具都支持 Unicode，遇到实际路径问题仍须处理。首次构建需联网下载依赖，设备端 Python 运行不需网络。

`.tools/` 是本次便携工具/缓存目录，不提交。`scripts/check_wandbox.py` 为可选网络检查，只发送脚本里的三个测试程序，可加 `--proxy http://127.0.0.1:端口`。

## GitHub Actions 与 APK

上传**本目录内容**作为仓库根目录，保留 `.github/` 和 `gradle/wrapper/`，不要只上传 ZIP 或再套一层目录。推送 `main/master`、PR 或手动运行 **Build Android APK**。

工作流准备 JDK 17、Python 3.13、SDK 35，执行 Python/Kotlin 测试、Lint、Debug 构建及 APK ZIP/签名/包信息检查。成功后下载 Artifacts 中的 `CodePocket-v0.2-debug-apk`；报告为 `CodePocket-verification-reports`。本地产物是 `app/build/outputs/apk/debug/app-debug.apk`。

当前是未初始化 Git 的源码目录，未连接远端；本地验证不等于已运行 GitHub Actions。

## 已知限制

- 补全为词法提示，没有 LSP/IntelliSense、跨文件语义和自动导入。
- C++ 只运行当前单文件，未上传本地头文件或链接多个 `.cpp`；支持服务自带的 STL/头文件。
- stdin 运行前预填，输出执行结束后显示，不支持运行中逐行交互。
- Python 的 6 秒 watchdog 仅尽力限制字节码，不能强制终止 sleep、阻塞 I/O、原生扩展或恶意代码。无法结束的调用会持续占用队列。
- 用户派生线程、修改标准库对象等全局状态未完全隔离；普通运行会恢复标准流、cwd、argv、sys.path、trace 和工作区模块缓存。
- 超过 45000 字符停用高亮/括号匹配以限制开销；原生 EditText 不适合超大文件。
- 错误导航识别首个诊断，运行后编辑文件可能使原行号偏移。
- Wandbox 可能限流、断网或更换编译器；动态发现 GCC，连接 10 秒/读取 30 秒超时，HTTP 错误不自动重复提交程序。
- 输入法、蓝牙键鼠、自由窗口、不同 Android 版本及 App 内 Chaquopy 执行仍须按实机清单验收。

产物复查：使用 Python 3.13 执行 python scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk，可验证 APK 归档、ABI 和实际打包的 Python 模块；此检查仍在主机运行。
