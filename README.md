# CodePocket · Android C++ / Python IDE

面向学习编程和算法练习的原生 Kotlin 轻量 IDE。保留现有 Android View 编辑器，Python 使用 Chaquopy 本地运行，C++17 使用 Wandbox 云端运行，不需要 API Key。

**2026-10-17：P1 第四批内置片段与占位符主机验证通过。** [当前 Debug APK](dist/CodePocket-P1-snippets-debug.apk) 20.81 MiB，Android 7.0+ / arm64-v8a。Kotlin 83 项中 82 通过、1 跳过；实际生成的 Python / C++17 模板各 7 个执行成功；Python 源码和 APK 字节码各 13 项通过；Lint 0 错误、13 警告。实机按用户要求统一后置，IME/撤销/帧率尚未设备验收。

- [当前开发进度与接续顺序](docs/DEVELOPMENT_PROGRESS.md)
- [P1 内置片段、占位符与真实模板验证](docs/P1_SNIPPETS.md)
- [P1 可调运行面板与验证结果](docs/P1_RUN_PANEL.md)
- [P1 光标补全列表与按键验证结果](docs/P1_COMPLETION.md)
- [P1 设置、查找与基础过渡结果](docs/P1_SETTINGS_SEARCH.md)
- [P0 后续回修与历史接续点](docs/P0_FOLLOWUP_2026-10-16.md)
- [初轮 P0 修改与验证结果](docs/P0_VERIFICATION.md)
- [P0 设备验收清单](docs/P0_DEVICE_TESTS.md)
- [完整开发需求](docs/DEVELOPMENT_REQUIREMENTS.md)
- [动画与智能补全增补计划（P1 部分实现，P2 待实现，与原需求共同生效）](docs/P1_P2_ANIMATION_COMPLETION_PLAN.md)
- [阶段 0 审查基线](docs/STAGE0_AUDIT_2026-10-15.md)
- [第三方依赖初步清单](docs/THIRD_PARTY_INVENTORY.md)

## 使用

1. 安装本轮 Debug APK，首次可以继续原有内部项目；旧内部目录未迁移或删除。上一轮在单台 Android 16 上覆盖安装、旧内部项目读取和冷启动通过；本轮 APK 待复测，升级前先导出重要内容。
2. 工具栏“工作区”可选择真实手机目录、打开最近目录、回到内部项目或重新载入。目录由系统 SAF 授权，编辑器保存会写回该目录。“导入副本”与系统工作区是不同操作：副本保存不会覆盖导入来源。
3. 文件树可展开嵌套目录、打开多个标签；长按或右键管理文件/目录。支持新建、重命名、移动与确认删除；提供方不支持的操作会说明原因。
4. 每个文件分别保存 stdin，“清空输入”只清当前文件。“收键盘”主动隐藏 IME；“结果”切换运行面板，□/▣ 最大化或恢复，× 收起；拖动分隔条或通过 ↕/↔ 菜单调整尺寸，底部与侧边分别记忆。紧凑布局仍可从面板菜单清空当前 stdin；编辑代码时提供紧凑工具条。
5. 点击“运行”，运行时同一按钮变为“停止”。Python 使用预填输入、本地独立执行进程；C++ 首次运行需确认发送当前源码与 stdin 至第三方 Wandbox。
6. 独立空白代码行上输入前缀选择“内置代码片段”，或点“片段”选择 C++ / Python 模板。Tab / Shift+Tab 跳转占位符，Esc 结束；“片段”菜单也能触屏导航。字段内 Ctrl+Space 可请求普通补全，有效候选优先于字段跳转。
7. 编辑停止约 700 ms 后后台保存，约 200 ms 建立恢复草稿；离开时也排队保存。失败时缓冲区和恢复内容保留，可“导出”“重新载入文件”或进入“恢复草稿”；切工作区可明确选择保留草稿后切换。

## 当前能力

- 顶栏设置：深/浅/系统主题、独立词法主题、代码/输出字号、Tab 宽度与空格/Tab、行号/换行、自动缩进/括号/补全、符号栏、IME 面板、停顿保存及基础过渡开关。
- 按实际工作区宽度停靠的运行面板；拖动/菜单调整大小、最大化/恢复/收起、独立尺寸记忆及 IME 临时约束；保留原编辑器、stdin 和输出视图。
- 光标锚定的原生补全候选列表、类别与简短资料；触屏点击、可配置 Tab/Enter/方向键、Esc 关闭；后台查询过期保护，明确词法能力与 P2 成员语义的区别。
- C++ / Python 各 6 个内置片段，保持缩进/换行；Tab/Shift+Tab 字段导航、触屏等价入口与文档版本保护；不属于语义分析。
- 当前文件纯文本查找替换、区分大小写/全字匹配、循环查找及跳转行；后台操作校验文档身份和版本，避免过期结果改写缓冲区。

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
| Tab / Shift+Tab | 有效候选优先，其次片段字段导航，最后缩进 / 反缩进 |
| Ctrl+Space | 主动触发词法列表；默认此后上下键导航候选 |
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
| MainActivity / CodeEditor | 原生界面、触摸/键鼠入口、IME、编辑与会话联动 |
| AppSettings / SettingsDialog | 持久化设置、边界校验与原生设置页面 |
| EditorSearch / SearchDialog | 字面查找/替换/跳转、换行行号及后台请求校验 |
| PanelLayout / PanelResizeHandle | 实测布局/独立尺寸、手势/键盘/无障碍与隐藏代码视口保护 |
| CompletionModel / CompletionPopup | 词法候选模型/后台结果契约、按键规则、光标窗口定位与原生列表 |
| CodeSnippets / SnippetSession | 内置模板、UTF-16 占位符、单次插入与临时导航/编辑契约 |
| EditorFeatures / EditorActions | 词法高亮、补全、括号、可测试的缩进及编辑规则 |
| EditorSessions | 每工作区文件身份、stdin、选区、滚动和焦点 |
| Workspace / DocumentTree / SafGateway | 保留内部文件实现，连接 SAF 文档操作、版本核对与执行快照 |
| DraftStore / DraftRecovery / WorkspaceIo | 持久恢复草稿、与已保存内容核对版本、串行后台文件操作 |
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

Kotlin 测试完成后，可用 `python scripts/verify_snippets.py --junit app/build/test-results/testDebugUnitTest --output verification/snippet-inspection.json --cpp-compiler g++` 真实执行生成模板。主机构建机需 g++（Windows 可传完整路径）；此编译器不打包至 App，App 中 C++ 仍使用 Wandbox。

使用匹配的 Python 3.13 执行 scripts/verify_apk.py，可检查 APK 归档、ABI 和实际打包 runner.pyc；这是主机检查，不是 Android 启动验证。

## GitHub Actions

.github/workflows/build-apk.yml 保留 JDK/Python/SDK 准备、Python 与 Kotlin 测试、Lint、Debug 构建、签名/归档/对齐/字节码检查；增加真实生成片段的 Python/C++17 主机执行与源样例报告。远端成功后应能下载 CodePocket-v0.2-debug-apk 与 CodePocket-verification-reports。

本地已建立 Git 基线与分阶段记录，尚未配置远端或运行 GitHub Actions；没有自动 push、发布或上传 APK。

## 已知边界与后续

- 上轮单台 Android 16 已完成部分交互与 Python 本地运行；本轮导航/草稿回修待复测。真实 SAF、进程停止、中文组合、实体蓝牙键鼠、平板和鸿蒙卓易通均未完整验收。
- stdin 预填，输出结束后展示；没有实时交互、ANSI 终端、流式输出或 Shell。
- Python 字节码尽力限时 6 秒，父级会话总限时 10 秒含初始化；C++ 连接 10 秒/读取 30 秒，父级等待 45 秒。云端取消不代表服务器任务已终止，未知退出码显示“—”。
- 文本最多 20 万字符，运行代码最多 10 万字符，stdin 最多 2 万字符；超过 45000 字符停用高亮/括号匹配。SAF 最多 1000 项、20 层目录；Python 快照单资源 1 MB、总计 8 MB。
- 草稿为约 200 ms 检查点；检查点写入前直接强杀/掉电仍可能丢失最后少量输入，不承诺每次按键即时落盘。
- Python 不是安全沙箱，独立进程仍具有本 App 的 UID 权限；不保证后台长期常驻。运行后继续编辑可能使历史错误行号偏移。
- 外部项目结构被其他应用改变后须重新载入。C++ 仍为单文件，不上传本地头文件或链接多个 cpp；本地 C++ 方案和批量用例属于 P2。
- P1 第一批设置、独立词法主题和当前文件查找替换已实现；面板拖动/最大化/恢复和原生词法候选列表已接入；内置片段与占位符已接入；完整动画、持续调用参数提示、项目搜索和流式程序终端仍待开发。第三方许可证完整通知包仍待完善。
- 2026-10-15 增补：动画系统和 VS Code 风格补全界面归入 P1；C++ / Python 深度语义补全归入 P2。Sora、clangd、Jedi 等仅列为待评估候选，尚未集成；当前为原生候选列表上的基础词法补全，成员语义未实现。动画须尊重系统减少动画及 App 开关，60 FPS/高刷新率与输入法、光标、弹窗配合均待实测。

当前继续按 P1 → P2 → P3 分批开发、主机验证和保存 APK，发现 P0 缺陷仍优先回修。依据 2026-10-16 最新指示，实机验收统一放到功能开发完成后，不作为继续开发的前置条件；历史记录保留。
