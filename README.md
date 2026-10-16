# CodePocket · Android C++ / Python IDE

面向学习编程和算法练习的原生 Kotlin 轻量 IDE。保留现有 Android View 编辑器，Python 使用 Chaquopy 本地运行，C++17 使用 Wandbox 云端运行，不需要 API Key。

**2026-10-15 P0 本地构建与主机回归通过，设备验收尚未完成。** [本轮 Debug APK](dist/CodePocket-P0-debug.apk) 为 20.73 MiB，Android 7.0+ / arm64-v8a。Kotlin 35 项中 34 通过、1 跳过；Python 源码和 APK 字节码各 13 项通过；Lint 0 错误、14 警告。未在 Android 手机、平板或鸿蒙卓易通中安装运行，不能据此宣称实机适配通过。

- [P0 修改与真实验证结果](docs/P0_VERIFICATION.md)
- [P0 设备验收清单](docs/P0_DEVICE_TESTS.md)
- [完整开发需求](docs/DEVELOPMENT_REQUIREMENTS.md)
- [动画与智能补全增补计划（P1/P2，待实现，与原需求共同生效）](docs/P1_P2_ANIMATION_COMPLETION_PLAN.md)
- [阶段 0 审查基线](docs/STAGE0_AUDIT_2026-10-15.md)
- [第三方依赖初步清单](docs/THIRD_PARTY_INVENTORY.md)

## 使用

1. 安装本轮 Debug APK，首次可以继续原有内部项目；旧内部目录未迁移或删除。覆盖安装和旧数据保留仍需实机验证，升级前先导出重要内容。
2. 工具栏“工作区”可选择真实手机目录、打开最近目录、回到内部项目或重新载入。目录由系统 SAF 授权，编辑器保存会写回该目录。“导入副本”与系统工作区是不同操作：副本保存不会覆盖导入来源。
3. 文件树可展开嵌套目录、打开多个标签；长按或右键管理文件/目录。支持新建、重命名、移动与确认删除；提供方不支持的操作会说明原因。
4. 每个文件分别保存 stdin，“清空输入”只清当前文件。“收键盘”主动隐藏 IME；“结果”切换运行面板，编辑代码时提供紧凑 Tab/反缩进/括号工具条。
5. 点击“运行”，运行时同一按钮变为“停止”。Python 使用预填输入、本地独立执行进程；C++ 首次运行需确认发送当前源码与 stdin 至第三方 Wandbox。
6. 编辑停止约 700 ms 后后台保存，约 200 ms 建立恢复草稿；离开时也排队保存。失败时缓冲区和恢复内容保留，可“导出”“重新载入文件”或进入“恢复草稿”；切工作区可明确选择保留草稿后切换。

## 当前能力

- 深色/浅色记忆；按窗口宽度分段布局，多文件标签及文件树。
- C++/Python 分语言词法高亮、基础补全、行号、自动缩进、括号/引号补齐与匹配；不是 LSP/IntelliSense。
- 按工作区和稳定文件身份保存 stdin、选区、横纵滚动及代码/stdin 焦点；应用内改名/移动保留对应状态。
- 内部工作区保留路径/符号链接边界、同名拒绝、根目录保护、删除确认、同步临时文件替换及 UTF-8 限量读取。
- SAF 真实目录授权、最近项目、提供方 CRUD；编辑器写回前核对版本、保存原文备份、写后读回验证；磁盘恢复草稿独立于提供方授权。
- Python 本地标准库、工作区模块与资源、traceback；后台独立进程可中止，运行状态跨 Activity 重建。
- C++17 云端真实编译执行，支持服务自带 STL/常用头文件和预填 stdin；编译/运行/网络/服务错误、超时、中止分别标记。
- 重复运行保护、取消后迟到结果隔离；错误跳转回原工作区文件和首个识别出的行。

## 键盘与鼠标

| 按键 | 功能 |
| --- | --- |
| Tab / Shift+Tab | 接受候选或缩进 / 反缩进 |
| Ctrl+Space | 基础补全 |
| Ctrl+S / Ctrl+N / Ctrl+O | 保存 / 新建 / 系统文件导入副本 |
| Ctrl+B | 切换文件管理器 |
| F5 / Ctrl+Enter | 运行；停止使用运行位置的“停止”按钮 |

保留原生 EditText 的光标、选区、复制粘贴及鼠标行为，文件树支持长按/右键菜单。中文组合输入、实体蓝牙键鼠和系统快捷键拦截仍须逐设备验收。

## 文件位置与权限

内部项目在 App 私有 files/workspace，卸载会清除；SAF 工作区由用户选取并授权，不申请全面存储权限或额外蓝牙权限。外部项目文件仍由提供方保存；提供方能否移动、改名、写入取决于其能力。

Python 运行外部项目时，会同步源码与实际数据资源到私有完整快照。**编辑器保存写回外部目录；运行代码生成或改写的文件不会自动同步回外部目录。** 私有运行快照、草稿和原文备份会随卸载清除，需要的数据应先导出。

提供方写入不能统一保证原子替换，版本检查和写入间仍有并发窗口；本实现保留草稿与原文恢复。保存失败时不要把状态理解成已写回。提供方异常缓慢时可能长时间等待，当前没有统一强制中止其存储调用的机制。

## 架构与构建

| 模块 | 职责 |
| --- | --- |
| MainActivity / CodeEditor | 原生界面、触摸/键鼠入口、IME、编辑与会话联动 |
| EditorFeatures / EditorActions | 词法高亮、补全、括号、可测试的缩进及编辑规则 |
| EditorSessions | 每工作区文件身份、stdin、选区、滚动和焦点 |
| Workspace / DocumentTree / SafGateway | 保留内部文件实现，连接 SAF 文档操作、版本核对与执行快照 |
| DraftStore / WorkspaceIo | 持久恢复草稿、串行后台文件操作 |
| ExecutionEngines / RunSession / RunControl | Chaquopy 桥接、Wandbox HTTP、取消、会话状态和错误分类 |
| PythonExecutionService / runner.py | 独立 :python 进程、按需初始化 CPython、输入输出捕获、模块刷新与 traceback |

JDK 17、Gradle 8.9、AGP 8.7.3、Kotlin 1.9.25、Chaquopy 17.1.0、Python 3.13；compileSdk / Build Tools 35，targetSdk 34，minSdk 24。仅打包 arm64-v8a，普通 x86_64 模拟器不能直接安装。

构建机需要包含 venv 的完整 Python 3.13；最小嵌入包不可直接代替。本次工具和依赖缓存位于忽略提交的 .tools，不改变系统 Java。通常命令：

```powershell
python -m unittest discover -s tests -v
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
# 本机已有便携环境时：
.\scripts\build-local.ps1 -ProxyHost 127.0.0.1 -UseLocalMirror
```

Linux/macOS 使用 bash ./gradlew。构建 Python 不在 PATH 时可传 -Pcodepocket.buildPython=完整路径。Windows 中文路径可能需要 -Pandroid.overridePathCheck=true；本地脚本已配置。正式依赖源保持 Google/Maven Central，临时镜像只在 .tools 中。

使用匹配的 Python 3.13 执行 scripts/verify_apk.py，可检查 APK 归档、ABI 和实际打包 runner.pyc；这是主机检查，不是 Android 启动验证。

## GitHub Actions

.github/workflows/build-apk.yml 保留 JDK/Python/SDK 准备、Python 与 Kotlin 测试、Lint、Debug 构建、签名/归档/对齐/字节码检查。远端成功后应能下载 CodePocket-v0.2-debug-apk 与 CodePocket-verification-reports。

本地已建立 Git 基线与分阶段记录，尚未配置远端或运行 GitHub Actions；没有自动 push、发布或上传 APK。

## 已知边界与后续

- 设备交互、真实 SAF 提供方、独立 Python 进程启动/停止、Android 原生库与鸿蒙卓易通均未验收。
- stdin 预填，输出结束后展示；没有实时交互、ANSI 终端、流式输出或 Shell。
- Python 字节码尽力限时 6 秒，父级会话总限时 10 秒含初始化；C++ 连接 10 秒/读取 30 秒，父级等待 45 秒。云端取消不代表服务器任务已终止，未知退出码显示“—”。
- 文本最多 20 万字符，运行代码最多 10 万字符，stdin 最多 2 万字符；超过 45000 字符停用高亮/括号匹配。SAF 最多 1000 项、20 层目录；Python 快照单资源 1 MB、总计 8 MB。
- 草稿为约 200 ms 检查点；检查点写入前直接强杀/掉电仍可能丢失最后少量输入，不承诺每次按键即时落盘。
- Python 不是安全沙箱，独立进程仍具有本 App 的 UID 权限；不保证后台长期常驻。运行后继续编辑可能使历史错误行号偏移。
- 外部项目结构被其他应用改变后须重新载入。C++ 仍为单文件，不上传本地头文件或链接多个 cpp；本地 C++ 方案和批量用例属于 P2。
- 设置、查找替换、独立语法主题、可拖动面板和程序终端属于 P1，尚未实施。第三方许可证完整通知包仍待完善。
- 2026-10-15 增补：动画系统和 VS Code 风格补全界面归入 P1；C++ / Python 深度语义补全归入 P2。Sora、clangd、Jedi 等仅列为待评估候选，尚未集成；当前仍为基础词法补全。动画须尊重系统减少动画及 App 开关，60 FPS/高刷新率与输入法、光标、弹窗配合均待实测。

后续先完成 P0 设备验收与缺陷回修；进入 P1 前等待用户确认。历史构建和阶段 0 审查保留在 docs 和 verification 中。
