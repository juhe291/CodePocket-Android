# CodePocket · Android C++ / Python IDE

**2026-10-19：恢复P1工作台动画首批。** 文件树加入展开箭头、目录引导线和窄栏缩进预算；文件树/标签按路径复用，更新不反复重建；补全/参数窗口连续输入不重播淡入，取消/后台/IME时结算过渡。App398通过/1既有跳过（399项，新增26），Python源码/打包各29，Lint0错/11既有警告；Debug APK已构建并检查。[本轮APK](dist/CodePocket-workbench-motion-debug.apk)约21.63MiB；[修改、测试与边界](docs/P1_WORKBENCH_MOTION.md)。终端运行与输入链路保持；本批不是完整动画交付，光标/主题/完整面板过渡及设备FPS仍待完成。

**此前2026-10-19：继续完善终端焦点与会话切换。** 最大化/还原保留终端输入；切换Shell时正确交接焦点；旧输入法连接在失焦、关闭、重启或更换会话后不能写入新终端。修复前9项失败已复现，最终App372通过/1既有跳过（373项），新增16项；源码/打包Python各29、Shell4，Lint0错/11旧警告，最终Debug构建1分11秒。[本轮APK](dist/CodePocket-terminal-focus-debug.apk) 21.63MiB；[修改与验证记录](docs/P1_TERMINAL_FOCUS_LIFECYCLE.md)。保留上版已获用户可用反馈的启动/键盘修复；本版没有新增设备或帧率验收，原P1/P2/P3继续保留。

**此前2026-10-19：继续修正“Python 运行时点击终端不弹键盘”。** 用户确认仍停在启动阶段；已拆开键盘显示与进程输入门控，保留必要 Android ART/boot 环境，补齐窗口焦点等待及桥提前退出识别。App356通过/1既有跳过（357项），新增15项；真实主机Shell4、源码/打包Python各29，Lint0错/11旧警告；原仓库Debug构建1分22秒。[此前APK](dist/CodePocket-terminal-running-input-debug.apk) 21.63MiB；[原因、修改、测试与设备边界](docs/P1_TERMINAL_RUNNING_INPUT_FIX.md)。同一真实终端，原数据与功能保留；手机/平板IME和Android全链仍未复测，不能视为设备故障已闭环，动画仍暂停。

**此前2026-10-19：修正系统 Shell 覆盖提示符导致的 Python 启动等待，补齐终端触屏键盘与短窗口布局。** 同一个真实PTY承载Shell和Python，不增加第二个运行终端；新增“键盘”按钮与焦点延后请求，紧凑/大字体窗口保留终端，恢复页面时保留终端焦点。App341通过/1既有跳过、核心146、源码/实际打包Python各29、Shell启动3项、模板各7；Lint0错/11既有警告，原仓库Debug构建1m9s。[此前APK](dist/CodePocket-terminal-prompt-ime-debug.apk) 21.63MiB；[原因、修改、证据与设备边界](docs/P1_TERMINAL_PROMPT_IME_FIX.md)。Android整链、手机/平板IME与卓易通仍待实测，动画保持暂停。

**此前2026-10-18：Python启动等待修正，取消Python预填模式。** 修复Shell依赖终端View测量及代码键盘隐藏面板导致等待的路径；点击运行显示终端，Python只在同一真实终端交互，C++仍云端预填。[此前Debug APK](dist/CodePocket-terminal-startup-debug.apk) 21.63MiB；[修正与验证](docs/P1_TERMINAL_STARTUP_FIX.md)。App327通过/1既有跳过、核心146、实际Python字节码29通过，Lint0错/11既有警告。手机上的输出恢复仍待验证，动画暂停。

**此前：Shell视图初始化闪退修复（2026-10-18）。** 字号/字体初始化顺序导致空绘制器，新增实际View主机回归、完整启动异常保护及可复制的本机错误记录。[修复版Debug APK](dist/CodePocket-shell-crash-fixed-debug.apk) 21.63MiB；App286通过/1既有跳过、Lint0错误/11既有警告。见[崩溃栈、修复及验证范围](docs/P1_SHELL_CRASH_FIX.md)。尚无手机日志/PTY实测，不能据此排除设备其他故障；动画暂停，统一终端界面待接续。

**此前：原生 Shell 终端首批（2026-10-18，含本轮已修复的启动Bug）。** 运行面板选择“Shell 终端”连接真实`/system/bin/sh`（Termux原生组件/PTY，无需另装Termux）。[Debug APK](dist/CodePocket-native-terminal-debug.apk) 21.63MiB；App277通过/1既有跳过、终端核心145通过，Lint0错误/11既有警告。构建与产物通过，**设备上的Shell/IME/键鼠/卓易通仍待统一实机验收**。当前没有python/pip/clang命令，原Python仍Chaquopy、C++仍Wandbox；本地C++未实现。见[实现、验证与边界](docs/P1_NATIVE_TERMINAL.md)。

**2026-10-18：Sora实验已接入离线C++ / Python TextMate语法着色。** 提供语言/纯文本选择、深浅色规则、后台加载与着色预算降级，保留草稿和历史；属于语法作用域着色，不是语义IntelliSense。见[本批结果](docs/P1_SORA_LANGUAGES.md)；[事务](docs/P1_SORA_TRANSACTIONS.md)、[定位](docs/P1_EDITOR_SURFACE.md)保留。默认IDE仍为原生CodeEditor，尚未完成生产Sora迁移。

面向学习编程和算法练习的原生 Kotlin 轻量 IDE。保留现有 Android View 编辑器，Python 使用 Chaquopy 本地运行，C++17 使用 Wandbox 云端运行，不需要 API Key。

**此前 [默认IDE Debug APK](dist/CodePocket-editor-sora-languages-debug.apk)** 20.87MiB，Android7.0+ / arm64-v8a。强制重编译1m 26s、64/64任务执行；原270项中269通过/1既有跳过，Python源码/字节码各13/13，模板各7/7，Lint0错误/11既有警告。生产源码和依赖未改，签名/CRC/对齐及关键类定义检查通过。

[Sora语法实验APK](dist/CodePocket-Sora-languages-lab-debug.apk) 4.66MiB，独立包名/临时草稿；最终离线构建29s，60/60主机测试（新增19），Lint0错误/6既有警告。真实运行TM4E/Joni、Content/Cursor/UndoManager；测试仅把Android日志转发到stderr，未执行Android View/InputConnection。C++部分内嵌语言规则缺失，警告与边界已记录。新源码在当前仓库；[历史源码/许可材料包](dist/CodePocket-Sora-editor-lab-bundle.zip)不含后续适配。实机验收统一后置。

- [当前开发进度与接续顺序](docs/DEVELOPMENT_PROGRESS.md)
- [P1 Sora离线语法与主题验证](docs/P1_SORA_LANGUAGES.md)
- [P1 Sora事务、草稿更换与历史适配验证](docs/P1_SORA_TRANSACTIONS.md)
- [P1 生产弹窗与Sora共享定位适配验证](docs/P1_EDITOR_SURFACE.md)
- [P1 文档交接、异步读取与标签安全验证](docs/P1_EDITOR_DOCUMENTS.md)
- [P1 生产选区、视口与错误导航验证](docs/P1_EDITOR_NAVIGATION.md)
- [P1 内置函数参数参考与验证](docs/P1_SIGNATURE_HELP.md)
- [P1 内置片段、占位符与真实模板验证](docs/P1_SNIPPETS.md)
- [P1 可调运行面板与验证结果](docs/P1_RUN_PANEL.md)
- [P1 光标补全列表与按键验证结果](docs/P1_COMPLETION.md)
- [P1 设置、查找与基础过渡结果](docs/P1_SETTINGS_SEARCH.md)
- [P0 后续回修与历史接续点](docs/P0_FOLLOWUP_2026-10-16.md)
- [初轮 P0 修改与验证结果](docs/P0_VERIFICATION.md)
- [P0 设备验收清单](docs/P0_DEVICE_TESTS.md)
- [2026-10-17 完整需求与重构任务](docs/DEVELOPMENT_REQUIREMENTS_UI_2026-10-17.md)
- [原完整开发需求（保留）](docs/DEVELOPMENT_REQUIREMENTS.md)
- [动画与智能补全增补计划（P1 部分实现，P2 待实现，与原需求共同生效）](docs/P1_P2_ANIMATION_COMPLETION_PLAN.md)
- [阶段 0 审查基线](docs/STAGE0_AUDIT_2026-10-15.md)
- [第三方依赖初步清单](docs/THIRD_PARTY_INVENTORY.md)

## 使用

1. 安装本轮 Debug APK，首次可以继续原有内部项目；旧内部目录未迁移或删除。上一轮在单台 Android 16 上覆盖安装、旧内部项目读取和冷启动通过；本轮 APK 待复测，升级前先导出重要内容。
2. 点资源管理器（窄窗可从主菜单找回）→标题更多→打开/切换工作区，选择系统SAF目录、最近项目或内部项目。外部项目保存会写回所选目录；导入副本仍不会覆盖导入来源。标题的新建文件/目录按钮和文件树长按/右键管理保留。
3. 文件树可展开嵌套目录、打开多个标签；长按或右键管理文件/目录。支持新建、重命名、移动与确认删除；提供方不支持的操作会说明原因。
4. C++文件的云端stdin独立保存，默认收纳；Python直接在终端交互，不显示预填框。面板输入图标或面板更多→编辑预设输入展开。清空预设输入与清空输出显示分开，后者仅清显示，不向程序发送输入，仍保留问题定位结果。面板可最大化/还原/关闭/调尺寸，关闭后主菜单可找回；矮宽窗自动左右分屏。符号栏在键盘上方，stdin焦点不会修改源码。
5. 点击“运行”，运行时同一按钮变为“停止”。Python 默认在当前真实终端等待输入，Enter提交、Ctrl+D结束输入、Ctrl+C停止；点击终端或“键盘”按钮调出输入法，运行时不自动抢焦点；已取消Python预填模式，本地独立进程仍保留；C++ 首次运行需确认发送当前源码与 stdin 至第三方 Wandbox。
6. 独立空白代码行上输入前缀选择“内置代码片段”，或从“编辑器工具栏更多→内置代码片段”选择 C++ / Python 模板。Tab / Shift+Tab 跳转占位符，Esc 结束；“片段”菜单也能触屏导航。字段内 Ctrl+Space 可请求普通补全，有效候选优先于字段跳转。
7. 受支持调用的括号内可查看内置参数参考；Ctrl+Shift+Space 或“编辑器工具栏更多→内置参数参考”主动请求，‹/› 切换参考形式，Esc/× 关闭。设置可独立关闭自动参考。当前为文档资料与词法位置，不推断同名对象类型或解析全部重载。
8. 编辑停止约 700 ms 后后台保存，约 200 ms 建立恢复草稿；离开时也排队保存。失败时缓冲区和恢复内容保留，可“导出”“重新载入文件”或进入“恢复草稿”；切工作区可明确选择保留草稿后切换。

9. 编辑器工具栏提供保存、撤销/重做、文件内查找；更多提供替换/跳行/补全/片段。顶部搜索图标或Ctrl+Shift+F打开项目搜索，支持当前未保存缓冲与结果定位。

## 当前能力

- 主菜单→设置：深/浅/系统主题、独立词法主题、代码/输出字号、Tab 宽度与空格/Tab、行号/换行、自动缩进/括号/补全、符号栏、IME 面板、停顿保存及基础过渡开关。
- 按实际工作区宽度停靠的运行面板；拖动/菜单调整大小、最大化/恢复/收起、独立尺寸记忆及 IME 临时约束；保留原编辑器与每文件预填输入，Shell和程序共用真实终端，问题页单独显示诊断。
- 光标锚定的原生补全候选列表、类别与简短资料；触屏点击、可配置 Tab/Enter/方向键、Esc 关闭；后台查询过期保护，明确词法能力与 P2 成员语义的区别。
- 补全/参数窗口使用共享EditorSurface接口，不直接读取EditText布局或正文；组件提供裁剪到文本区/窗口的实际光标坐标，窗口复核文件/版本/UTF-16偏移。Sora目前仅在隔离实验中实现此接口，未迁移整个IDE。
- C++ / Python 各 6 个内置片段，保持缩进/换行；Tab/Shift+Tab 字段导航、触屏等价入口与文档版本保护；不属于语义分析。
- Python 7 项 / C++17 5 项内置参数资料，当前参数标识、参考形式切换、后台快照保护、独立自动开关和光标弹窗；复杂表达式保守降级，非语义签名。
- 当前文件纯文本查找替换、区分大小写/全字匹配、循环查找及跳转行；后台操作校验文档身份和版本，避免过期结果改写缓冲区。

- 深色/浅色记忆；按窗口宽度分段布局，多文件标签及文件树。
- C++/Python 分语言词法高亮、基础补全、行号、自动缩进、括号/引号补齐与匹配；不是 LSP/IntelliSense。
- 按工作区和稳定文件身份保存 stdin、选区、横纵滚动及代码/stdin 焦点；应用内改名/移动保留对应状态。
- 切文件、重新载入与切工作区时，后台读取结果必须通过来源缓冲与版本交接校验；读取期间新代码保留，stdin/选区取最新状态。同一工作区重载保留当前会话，活动标签在替代文件成功载入后关闭；最后一个文件标签继续保留并说明。
- 内部工作区保留路径/符号链接边界、同名拒绝、根目录保护、删除确认、同步临时文件替换及 UTF-8 限量读取。
- SAF 真实目录授权、最近项目、提供方 CRUD；编辑器写回前核对版本、保存原文备份、写后读回验证；磁盘恢复草稿独立于提供方授权。
- Python 本地标准库、工作区模块与资源、traceback；后台独立进程可中止，运行状态跨 Activity 重建。
- C++17 云端真实编译执行，支持服务自带 STL/常用头文件和预填 stdin；编译/运行/网络/服务错误、超时、中止分别标记。
- 重复运行保护、取消后迟到结果隔离；错误跳转回原工作区文件和首个识别出的有效行。入口文件核对运行时身份/源码摘要，修改后拒绝旧定位；导入模块缺少运行版本证明时明确提示仅按行定位。
- 查找/跳行/片段选区及布局后视口恢复统一经过生产导航接口；文档/版本/选区/组合状态及后续触摸、键盘、鼠标操作使迟到恢复失效。保存选区避免拆分emoji/CRLF；原生输入与独立stdin保留。

## 键盘与鼠标

| 按键 | 功能 |
| --- | --- |
| Tab / Shift+Tab | 有效候选优先，其次片段字段导航，最后缩进 / 反缩进 |
| Ctrl+Space | 主动触发词法列表；默认此后上下键导航候选 |
| Ctrl+Shift+Space | 主动显示内置函数参数参考（非语义分析） |
| Enter / Esc | 默认换行（设置可接受明确选中的候选）/ 关闭候选或结束片段导航 |
| Ctrl+F / Ctrl+H / Ctrl+G | 查找 / 替换 / 跳转行 |
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
| editor-surface（本地Java库） | View/文本无关的光标锚点、屏幕可用区域与弹窗放置规则，原生/Sora共用 |
| editor-lab（显式启用） | 同一工程的独立Sora/原生比较与共享定位探针；不连接项目或运行器 |
| ui/MainToolbar / IdeCommandMenu / IdeCommands / WorkspaceLayoutPolicy | 实测宽度的单排主栏、原生命令入口与焦点/状态策略 |
| ui/EditorToolbar / InteractionState | 上下文编辑入口、返回/状态/只清显示规则 |
| search/ProjectSearch | 有界、可取消的后台项目文字搜索 |
| terminal/AnsiParser / TerminalView | 问题页的诊断文字样式与原生文字选择；不再作为程序终端 |
| terminal/shell、terminal/bridge、Termux | 同一个真实PTY/Shell及Python前台桥，实时标准流、独立会话与云端结果渲染 |
| ui/DesignTokens / IdeIcons / IdeControls | 外壳配色/尺寸、原创线性图标和原生触控组件 |
| editor/EditorPort / EditingEditorPort / NavigatingEditorPort / DocumentEditorPort / NativeEditorPort | 生产分析/装饰、事务/历史、导航及显式文档替换/交接快照；完整组件适配待做 |
| editor/EditorDocuments | 文件/工作区读取交接门控、一次性请求与同工作区最新会话选择 |
| editor/EditorNavigation / EditorDiagnostics | UTF-16选区校验、轻量视口队列、过期交互保护、入口源码证明和安全路径映射 |
| MainActivity / CodeEditor | 原生界面、触摸/键鼠入口、IME、编辑与会话联动 |
| AppSettings / SettingsDialog | 持久化设置、边界校验与原生设置页面；旧Python预填开关忽略 |
| EditorSearch / SearchDialog | 字面查找/替换/跳转、换行行号及后台请求校验 |
| PanelLayout / PanelResizeHandle | 实测布局/独立尺寸、手势/键盘/无障碍与隐藏代码视口保护 |
| CompletionModel / CompletionPopup | 词法候选模型/后台结果契约、按键规则、光标窗口定位与原生列表 |
| SignatureHelp / SignaturePopup | 内置资料来源、词法调用/参数位置、只读光标弹窗和请求保护 |
| CodeSnippets / SnippetSession | 内置模板、UTF-16 占位符、单次插入与临时导航/编辑契约 |
| editor/EditorPort / EditorAnalysisController / NativeEditorPort | UTF-16 快照、后台词法/括号分析、过期/组合区门控与原生私有装饰 |
| EditorFeatures / EditorActions | 词法高亮、补全、括号、可测试的缩进及编辑规则 |
| EditorSessions | 每工作区文件身份、stdin、选区、滚动和焦点 |
| Workspace / DocumentTree / SafGateway | 保留内部文件实现，连接 SAF 文档操作、版本核对与执行快照 |
| DraftStore / DraftRecovery / WorkspaceIo | 持久恢复草稿、与已保存内容核对版本、串行后台文件操作 |
| ExecutionEngines / RunSession / RunControl | Chaquopy 桥接、Wandbox HTTP、取消、会话状态和错误分类 |
| PythonExecutionService / runner.py | 独立 :python 进程、按需初始化 CPython、真实终端标准流/可选预填、模块刷新、等待事件与 traceback |

JDK 17、Gradle 8.9、AGP 8.7.3、Kotlin 1.9.25、Chaquopy 17.1.0、Python 3.13；compileSdk / Build Tools 35，targetSdk 34，minSdk 24。仅打包 arm64-v8a，普通 x86_64 模拟器不能直接安装。

构建机需要包含 venv 的完整 Python 3.13；最小嵌入包不可直接代替。本次工具和依赖缓存位于忽略提交的 .tools，不改变系统 Java。通常命令：

```powershell
python -m unittest discover -s tests -v
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
# 本机已有便携环境时：
.\scripts\build-local.ps1 -ProxyHost 127.0.0.1 -UseLocalMirror
```

Linux/macOS 使用 bash ./gradlew。构建 Python 不在 PATH 时可传 -Pcodepocket.buildPython=完整路径。Windows 中文路径可能需要 -Pandroid.overridePathCheck=true；本地脚本已配置。正式依赖源保持 Google/Maven Central，临时镜像只在 .tools 中。

Kotlin 测试完成后，可用 `python scripts/verify_snippets.py --junit app/build/test-results/testDebugUnitTest --output verification/snippet-inspection.json --cpp-compiler g++` 真实执行生成模板。主机构建机需 g++（Windows 可传完整路径）；此编译器不打包至 App，App 中 C++ 仍使用 Wandbox。

使用匹配的 Python 3.13 执行 scripts/verify_apk.py，可检查 APK 归档、ABI 和实际打包 runner.pyc；这是主机检查，不是 Android 启动验证。

## GitHub Actions

.github/workflows/build-apk.yml 保留 JDK/Python/SDK 准备、Python 与 Kotlin 测试、Lint、Debug 构建、签名/归档/对齐/字节码检查；增加真实生成片段的 Python/C++17 主机执行与源样例报告。远端成功后应能下载 CodePocket-v0.2-debug-apk 与 CodePocket-verification-reports。

本地已建立 Git 基线与分阶段记录，尚未配置远端或运行 GitHub Actions；没有自动 push、发布或上传 APK。

## 已知边界与后续

- 上轮单台 Android 16 已完成部分交互与 Python 本地运行；本轮导航/草稿回修待复测。真实 SAF、进程停止、中文组合、实体蓝牙键鼠、平板和鸿蒙卓易通均未完整验收。
- 已接真实Termux/PTY/Shell及Python交互桥，Android端桥接整条链路尚未实测；宿主机管道通过不代表手机通过。没有python/pip/clang命令或完整Termux userland，C++只提供云端预填输入。
- Python字节码有效执行尽力限6秒，父级累计有效执行限10秒；终端启动限30秒，托管stdin单次等待限5分钟且不消耗有效执行预算；C++ 连接 10 秒/读取 30 秒，父级等待 45 秒。云端取消不代表服务器任务已终止，未知退出码显示“—”。
- 文本最多 20 万字符，运行代码最多 10 万字符，stdin 最多 2 万字符；超过 45000 字符停用高亮/括号匹配；最多应用 6000 个着色 span，超预算退回正文颜色。SAF 最多 1000 项、20 层目录；Python 快照单资源 1 MB、总计 8 MB。
- 草稿为约 200 ms 检查点；检查点写入前直接强杀/掉电仍可能丢失最后少量输入，不承诺每次按键即时落盘。
- Python 不是安全沙箱，独立进程仍具有本 App 的 UID 权限；不保证后台长期常驻。入口文件编辑后会拒绝旧错误定位；导入模块仍只有明确提示的按行定位，不能证明对应运行时内容。
- 外部项目结构被其他应用改变后须重新载入。C++ 仍为单文件，不上传本地头文件或链接多个 cpp；本地 C++ 方案和批量用例属于 P2。
- P1 第一批设置、独立词法主题和当前文件查找替换已实现；面板拖动/最大化/恢复和原生词法候选列表已接入；内置片段与占位符已接入；常用内置函数调用参数参考已接入；后台项目搜索已接入；Sora隔离原型已接入；完整生产EditorPort/迁移适配、语言包、动画、语义签名仍待开发；流式程序终端已接代码，待统一设备验收。第三方许可证完整通知包仍待完善。
- 2026-10-15 增补：动画系统和 VS Code 风格补全界面归入 P1；C++ / Python 深度语义补全归入 P2。Sora已有隔离实验，尚未迁移默认IDE；clangd、Jedi语义服务仍待评估接入。当前为原生候选列表上的基础词法补全，成员语义未实现。动画须尊重系统减少动画及 App 开关，60 FPS/高刷新率与输入法、光标、弹窗配合均待实测。

2026-10-17 审查后用户已确认继续；P1-A 手机竖屏外壳已完成代码与主机验证，P1-B与本批UI接续均已开发并做主机验证，继续P1余项。继续按 P1 → P2 → P3 分批开发、主机验证和保存 APK，发现 P0 缺陷仍优先回修。依据 2026-10-16 最新指示，实机验收统一放到功能开发完成后，不作为继续开发的前置条件；历史记录保留。

独立实验的手动构建工作流为 `.github/workflows/editor-lab.yml`；不改变原默认IDE的构建/上传流程。精确依赖和已验证范围见[P1实验报告](docs/P1_EDITOR_LAB.md)。
