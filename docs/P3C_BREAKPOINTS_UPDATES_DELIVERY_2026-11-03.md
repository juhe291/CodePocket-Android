# P3-C 设置搜索/自定义主题、断点与自动检测更新交付（2026-11-03）

从 `1503fdf`/CI 修复提交 `39c7834` 接续。本批完成用户要求的三件事：**P3-C 设置多词/别名搜索 + 可保存且真正作用于 Sora 与预览的自定义语法主题**、**断点功能（先做真实可用的本地 Python 交互断点 + 全部文件可持久化标记）**、**启动时自动检测更新**（此前只有手动检查更新）。用户已明确：这些做完、无回归后提交并推送 GitHub、整理仓库并加标签；本批确实完成了本地修改、验证、提交与推送，P3-D 后续再实现（调研见 [P3 D 调研](P3_D_LANGUAGE_RESEARCH_2026-11-03.md)）。

## 1. 设置多词/别名搜索（P3-C-1）

- 新增 `SettingsSearch`：稳定 `Spec.key` + 分类/分组 + 中文/英文别名统一查询；NFKC 全半角标准化、大小写不敏感、空白分词后每个词都需命中；结果保持原目录顺序且每项只出现一次。
- 示例真实可用：`自动 保存`、`terminal font`、`ＴＥＲＭＩＮＡＬ　ＦＯＮＴ`、`terminalFontSize`、`C++ 本地`、`Tab 补全`、`word wrap`、`terminal theme`。
- 无匹配时保留原空态并新增“清除搜索”原生入口；搜索结果仍使用原真实开关/选项与完整设置草稿，没有第二套设置页。
- 基线六项（4 搜索 + 2 主题）原为封存移交时 4 通过/2 失败；本批六项全部通过（`SettingsSearchAndThemeBaselineTest`），原断言保留。

## 2. 自定义语法主题（P3-C-2/3）

数据与校验（`editor-sora/.../SyntaxThemeDefinition.java`）：

- 有界离线 TextMate 子集：`name`、`type(dark/light)`、必要的 `colors`（`editor.background/foreground` 必填，另支持 cursor/selection/lineHighlight/lineNumber）与 `tokenColors`（≤128 条，scope 使用点分范围，支持 `#RRGGBB`、`fontStyle`）。
- 拒绝外部 include、URL、脚本/命令、未知字段、重复字段、超 16 KiB、超深嵌套；错误定位到具体字段（如 `tokenColors[3].scope`）。
- `AppSettings` 增加 `customSyntaxTheme` 与 `SyntaxTheme.CUSTOM`（保留原三个枚举身份/顺序）；设置 JSON 版本 12，旧设置迁移保持。
- 保存与取消：解析失败时设置页阻止“应用”，保留上一次有效主题与草稿；只有整页应用成功才持久化；返回/取消/Activity 重建保留未提交 JSON。

真实 Sora 与原生预览共用（不是只给预览上色）：

- `AppSettings.customTheme()` 解析一次；同一结果传给 `workbench.editor.configureLanguage(..., customTheme)`，由 `SoraSyntaxController` 用真实 TM4E `ThemeModel` 载入 pinned Sora 引擎，设置 `TextMateColorScheme`；背景、正文、光标、选区、当前行、行号颜色来自该主题；TM4E 失败时显式回退内置配色并在状态里说明。
- 原生预览 `SyntaxThemePreview.create(..., definition)` 用同一解析结果与 token 颜色；设置页“语法主题 → 自定义”提供 JSON 编辑器（等宽、16 KiB 限制）、`载入 Dark+/Light+ 模板`、`验证主题`、`复制主题 JSON`、实时错误与预览；演示用真实 Sora 读取自定义 token 颜色（`def` 的 storage.type、`return` 的 keyword 均断言为自定义色），预览背景/关键字色与主题一致，见 [4 张原生图](verification/p3-settings-themes-2026-11-03/previews)。
- 不改工作台/终端既有独立配色偏好；不做主题商店/在线下载。

## 3. 断点（新增功能）

范围与诚实边界：

- **编辑器断点标记**：`BreakpointGutter` 覆盖在真实行号左侧，点击切换并绘制红点；不编辑正文、不进撤销历史；断点按文件保存在 `EditorSessions`（1-based、去重、排序、≤500、越界项忽略/裁剪），跨标签切换、保存/恢复与 Activity 重建后恢复。
- **真实命中断点（本地 Python 交互运行）**：Python 运行器接收断点行清单，编译后发布有界行缓存并用真实 `bdb`/`pdb` 以 `set_break` + 全局 trace 命中；pdb 直接使用现有 PTY stdin/stderr，`p/next/step/continue/quit` 都是真实终端输入输出；`quit` 明确显示“已退出断点调试器”，不伪装成功。
- 非交互/预填输入运行会忽略断点（无终端可应答调试器）；C++ 云端/本地运行明确提示“断点仅支持本地 Python 交互运行”，不声称 C++ 断点。Android 端真实 PTY 交互验收仍后置。
- 宿主真实 CPython + OS 管道测试覆盖：命中后 `p x`/`next`/`p y`/`continue` 输出与变量、`quit` 标记、非交互忽略断点；共 3 项新增（`tests/test_terminal_runner.py`）。

## 4. 启动时自动检测更新

- 设置 → 帮助与许可新增“启动时自动检测更新”，**默认关闭**，开启后仅在应用启动时后台检查一次正式 GitHub Release（复用现有有界、无凭据、固定 HTTPS 更新源与同一文案/操作；“禁止重定向/大小/超时/限流/格式”等边界不变）。
- 每个进程最多一次，Activity 重建不重复请求；只有**确实有更新版本**时弹出与手动检查同一套原生只读对话框（版本/说明/下载或发布页），最新版/找不到 Release/失败/限流保持安静；设置关闭时不发起网络请求。
- 手动“检查更新”入口与行为保持不变；新增 4 项测试覆盖关闭不联网、更新时弹窗、最新版静默、重建不重复、取消不回调。

## 5. 本批实际验证（以真实命令/结果为准）

- Python 源码测试：**175 项全部通过**，退出 0（含新增 3 项断点真实 pdb 测试；依赖路径使用仓库既有 `.tools/semantic-probe/site`、`formatting-probe/site`、`git-probe/site`）。
- 定向 Android/Robolectric：设置搜索/主题、真实 Sora、终端、运行入口、更新、断点等 **131 项全通过**（17 个类；含基线 6/6、Sora 32、终端 5、更新 4+6+2、断点 2、会话 6 等）。
- 集中全量回归、Lint、Debug 构建结果（本批最终数字见下方“集中验证”与 [目录内结果](verification/p3c-breakpoints-updates-2026-11-03/)）：
  - App **1191 项：1189 通过、1 个既有 Windows 头文件大小写冲突失败（`CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs`）、1 个旧跳过**；完整命令真实退出 1，不称全绿。termux terminal-emulator **146 项全通过**；editor-assist `NO-SOURCE`。
  - Lint **零错误**：App 37 warnings（含既有 4 条更新对话框 `SetTextI18n` 登记 + 本批新增 3 条主题/更新文案拼接提示），editor-sora 1 warning（既有 `GradleDependency` 0.23.7→0.24.4 提示），workspace-core 0 error/0 warning。新增断点自定义 View 的 2 条 Lint 警告已修复（`ViewConstructor`/`ClickableViewAccessibility`）。
  - 集中构建 12m15s，245 tasks（40 executed / 205 up-to-date），只实际打包一次最终 APK。
- 版本：`0.2.2 (4)`（原 Debug 签名，未重建密钥）。本批最终 APK：`dist/CodePocket-p3c-breakpoints-updates-2026-11-03-debug.apk`，**118,168,893 字节**，SHA256 `140e4d16092bddf83a5e4d094ac51adbc89fdacb2a44da54ee9f6591aa751b16`；929 entries；`apksigner` 验证、`zipalign -c -P 16 4`、`zipfile` 完整性、Manifest（versionCode 4 / versionName 0.2.2）通过；相对上一有效 Bug 包只改变 10 个条目（`AndroidManifest.xml`、`assets/chaquopy/app.imy`、`assets/chaquopy/build.json`、`classes2/3/6/7.dex`、`resources.arsc`、两个引用资源 ID 的 layout 二进制与签名容器），无新增/删除包条目；25 原生库、requirements-common.imy、C++ 运行资产与其余全部条目字节一致。注：同源首次增量打包曾出现 620 KB 孤儿本地条目（不在中央目录、不改变运行内容，但使包变大到 118,842,926 字节）；已删除输出并重新 `:app:assembleDebug` 生成干净包，旧中间包不交付。
- 原生视觉实际查看：手机深色自定义主题设置（含 JSON 编辑器/预览）、平板浅色自定义主题设置、手机深色真实 Sora 自定义配色、手机断点标记；未发现荧光/光晕/彩虹渐变或重复入口。
- 设备/Android 实际执行：断点命中、Sora 主题、自动更新网络与对话框、终端 PTY 均**后置**，不以主机测试冒称设备通过。

## 6. 已知限制

- 断点仅本地 Python 交互运行；C++/云端明确不支持；预填输入运行忽略断点。
- 自定义主题是有界子集（无 include/类型扩展/主题商店），超出子集的主题会明确报错而不是静默降级。
- 启动自动检查默认关闭，一次进程一次；GitHub 未发布 Release 时保持安静。
- Linux CI 两项修复（`39c7834`）仍需推送后由真实 CI 复核；本批推送会触发既有工作流，结果以远端实际状态为准。

## 7. 涉及文件

生产：`AppSettings.kt`、`SettingsDialog.kt`、`SettingsSearch.kt`、`AppUpdateDialog.kt`、`AppUpdateAutoCheck.kt`、`MainActivity.kt`、`ExecutionEngines.kt`、`PythonExecutionService.kt`、`RunSession.kt`、`strings.xml`、`workspace-core/.../EditorSessions.kt`、`editor-sora/.../WorkbenchEditor.java`、`SoraSyntaxController.java`、`TextMateBundle.java`、`SyntaxThemeDefinition.java`、`SyntaxThemePreview.java`、`BreakpointGutter.java`、`app/src/main/python/runner.py`。
测试：`SettingsSearchAndThemeBaselineTest.kt`、`P3CVisualEvidenceTest.kt`、`BreakpointIntegrationTest.kt`、`AppUpdateAutoCheckTest.kt`、`EditorSessionsTest.kt`、`SoraWorkbenchIntegrationTest.kt`、`TerminalDockIntegrationTest.kt`、`tests/test_terminal_runner.py`。
证据：`verification/p3c-breakpoints-updates-2026-11-03/`（真实 JUnit/Lint/APK 检查/包内运行时/推送/CI 记录）与 `verification/p3-settings-themes-2026-11-03/previews/`（4 张原生渲染图）。

## 8. 推送、仓库整理与远端 CI（用户已授权）

- 推送：`master` `2a93d3d..fe39907`（含 CI 修复提交 `39c7834` 与本批 `fe39907`），附 annotated tag `v0.2.2`；远端 ref 已用 `git ls-remote` 核对。本机 git 直连 TLS 的 schannel 路径不可用且本地代理已关闭，实际使用本机已配置的 `git -c http.sslBackend=openssl -c http.proxy=` 完成推送，凭据未打印、未上传任何密钥。
- 仓库整理：补充 topics（android/android-ide/ide/kotlin/python/cplusplus/code-editor/sora-editor/chaquopy/offline-ide），保留原有描述与默认分支，未发布 Release、未上传 APK。
- 远端 CI：[run 41641563765](https://github.com/juhe291/CodePocket-Android/actions/runs/41641563765)，触发提交 `fe39907`；真实结果以 `verification/p3c-breakpoints-updates-2026-11-03/remote-ci-run.json` 为准。**不预先把该 run 写成通过；若远端失败/未完成，将在下一次接续记录中如实说明并修复。**

## 9. 推送后远端 CI 首轮结果与二次修复（2026-11-03）

推送后远端 [run 41641563765](https://github.com/juhe291/CodePocket-Android/actions/runs/41641563765) **失败**：Python172/SDK/C++ 准备通过；App **1191 项中 2 项失败**（0 ignored，2m35s），Lint/APK/后续步骤未执行；报告制品 `13233935986`（258877 字节，SHA256 `4375c7be…305f00`）已下载核对。**本批先前两项 Linux CI 修复（Sora 后台选择焦点、终端重建面板）在该 run 中已通过**；新暴露的两个失败是既有 Sora/终端 Linux 时序问题，不是本批主题/更新/断点功能断言：

1. `CompletionInteractionTest.fontZoomDoesNotDestroyTheNativeUndoHistory`：`ArrayIndexOutOfBoundsException: index=0, length=0`，真实堆栈在 pinned Sora `LineBreakLayout.afterInsert(147)` ← `Content.insert` ← `commitText`（首次输入，尚未缩放）。根因是 Sora 异步重建行表期间提交文本与会话布局竞争。修复：`SoraInputEditor.commitText` 在布局未稳定（布局忙/行表为空）时**保留真实文本与顺序，延迟到布局可用后提交**（16ms 重试，1s 上限，不丢弃输入），不再直接写半构建布局。
2. `CppTerminalRunnerTest.interruptedCoordinatorStillJoinsItsRelaysBeforeReleasingOwnership`：参与者线程被中断时，原代码在循环顶部把“取消”与“中断”都抛成 `CancellationException`，测试按文档契约期望 `InterruptedException`。修复：区分 `cancelled()` 与 `Thread.currentThread().isInterrupted`，中断明确抛 `InterruptedException`，释放 relay/子进程所有权后再传播；取消仍保持 `CancellationException`。

修复后定向：`CompletionInteractionTest` 33、`CppTerminalRunnerTest` 8、`SoraWorkbenchIntegrationTest` 32、`BreakpointIntegrationTest` 2、`MainRunIntegrationTest` 12、`TerminalDockIntegrationTest` 5、`CompletionTest` 10，共 102 项全通过。随后再次集中全量回归/Lint/构建，结果与第二次远端 CI run 以证据目录为准；不把首轮失败说成通过，也不把本地结果冒称远端通过。

第二次推送 `b51ace6` 后远端 [run 41651272650](https://github.com/juhe291/CodePocket-Android/actions/runs/41651272650) 仍失败，App 1191 项中 2 项失败（0 ignored，3m04s），Lint/APK 未执行；报告 `13238072609`（258969 字节，SHA256 `c34995bc…37b16d`）已核对。失败为另一组时序问题：

1. `AppUpdateHttpTest.cancellingAnActiveStreamReleasesItsRequestAndProducesNoSuccess`：`Cancellation must not block the caller` 断言失败（>1.5s）。根因是 `HttpURLConnection.disconnect()` 可能等待读线程持有的请求锁。修复：`cancel()` 只原子置位取消标记，并把实际 `disconnect()` 放到有界 daemon 线程，保证调用方立即返回；读线程仍通过取消标记/超时收敛，不改变结果类型。
2. `SoraWorkbenchIntegrationTest.textMateReanalysisAndStyleRefreshKeepTheLexicalLayer`：`onCreateInputConnection` 在 Linux 返回 null 导致 NPE。该测试直接创建 IME 连接但未确保真实编辑器持有焦点；修复测试在创建连接前显式 `editor.requestFocus()` 并断言，保留原词法刷新断言。没有修改生产焦点行为来掩盖问题。

两项修复后定向 83 项全通过（Sora32、AppUpdateHttp8、AppUpdateUi6、AppUpdateAutoCheck4、CompletionInteraction33）。第三次推送 `768a1f1` + tag `v0.2.2` 后，远端 [run 41655045210](https://github.com/juhe291/CodePocket-Android/actions/runs/41655045210) 第一次尝试在 `Install SDK 35` 因远端 SDK 下载失败中止（非代码失败，测试未执行）；对该失败 job 使用 GitHub API `rerun-failed-jobs` 重跑 **attempt 2 真实全部通过**：Python 执行/静态分析、SDK/NDK、固定 C++ 准备、**Kotlin 1191 项回归 + Android Lint**、Shell 启动、片段、Debug APK 构建、APK 归档/签名、包内 Python、语义后端与上传全部 `success`（run conclusion=`success`）。制品：`CodePocket-v0.2-debug-apk`（115446293 字节）与 `CodePocket-verification-reports`（301392 字节）。记录见 `verification/p3c-breakpoints-updates-2026-11-03/remote-ci-run-third-rerun.json`。这是本项目首次真实远端 CI 全绿；Android 设备验收仍后置，不把 CI 当设备通过。


## 10. 0.2.3 正式 Release 候选补充（2026-11-03 晚）

用户后续要求：修正“运行与权限说明”等仍写 C++ 云端的文案、增加“检查更新”按钮、网页辅助栏顶部输入可收起、构建正式版、整理 README。本补充记录在 0.2.2 CI 全绿代码之上的收尾改动：

- **文案**：`python_terminal_help`（运行与权限说明）、`settings_save_help`、`shell_help`、Shell 关于页、终端任务说明、行内诊断、状态栏与输入提示均明确区分设备本地 C++（真实终端、可交互、不联网；CPU/会话限制）与 Wandbox 云端（确认后发送源码/输入、预填输入、完成后返回）。本地 C++ 模式下不再显示“编辑 C++ 云端预填输入”，状态栏显示“设备本地 Clang”。
- **检查更新按钮**：设置根页新增“应用与更新 → 检查更新”（tag `settings-root-check-updates`，带当前版本说明）；“帮助与许可 → 检查更新”原入口保留。两处都调用同一 `showAppUpdates()`，不新增网络逻辑。
- **网页地址收起**：`WebAuxiliaryPanel` 标题栏新增折叠/展开按钮（tag `web-address-toggle`），收起时隐藏地址输入行并清焦点，展开恢复原草稿；状态写入 `webAuxiliaryState.addressCollapsed`，随窗口/进程恢复；浏览器模式下保存的地址与导航不受影响。
- **正式 Release 构建**：`app/build.gradle.kts` 增加 `buildTypes.release.signingConfig = signingConfigs.getByName("debug")`，即沿用仓库原 Debug 证书（d58e4cd7…，不重建密钥），release 变体不可调试；版本升为 0.2.3(5)。构建需 `-Pkotlin.compiler.execution.strategy=in-process`（非 ASCII 工程路径下避免 Kotlin daemon 参数文件乱码）。产物 `dist/CodePocket-0.2.3-release.apk`，108,820,432 字节，SHA256 `70f9ea67f033a5af1b2c14dcb937997d5c861658d17a1ff33c8571011e99dca0`；v2 签名、16 KiB 对齐、920 entries、25 原生库、Python/C++/终端资产齐全；release 包内 Python 51 项、语义 22 项通过。
- **README**：重写为当前状态/功能/安装/使用/构建/边界结构；整理前原文已删除、不再提交历史堆叠。
- **验证**：App 全量 1193 项 1191 通过/1 既有 Windows 头文件失败/1 旧跳过、Lint 0 error；新增/修改定向（AppUpdateEntryBaseline 3、WebAuxiliaryBaseline 6、WebAuxiliaryIntegration 12、SettingsSurfaceVisual 4、MainRunIntegration 12、TaskTranscript 4、CppSemantic 7）全部通过；Release APK 检查见 `release-*.json/log`。Android 设备验收后置；0.2.3 尚未推送时不以旧 CI 结果冒充本版通过。

## 11. GitHub Release 发布（2026-11-04）

按用户要求创建 GitHub Release，提供一键下载资产（此前只有 CI artifact、没有对外的 Release 下载）：

- Release：<https://github.com/juhe291/CodePocket-Android/releases/tag/v0.2.3>（id 433346245，公开、非预发布）。
- 资产：`CodePocket-0.2.3-arm64-v8a-release.apk`，108,820,832 字节，SHA256 `7f573f08bd3a33f36d1638137d69e53e1da192663749ba179f03f66afb81f045`；GitHub 返回的资产 digest 与本地哈希一致。
- 直链：<https://github.com/juhe291/CodePocket-Android/releases/download/v0.2.3/CodePocket-0.2.3-arm64-v8a-release.apk>；README 已加“一键下载”。
- tag `v0.2.3` 指向最终提交（annotated tag），`v0.2.2` 历史 tag 保留；Release 页面即显示对应标签。
- 应用内更新检测读取本仓库正式 Release：资产名含 `arm64-v8a`、tag 可解析，符合现有有界 `AppUpdateSource` 约束；旧版检查更新将看到 0.2.3。
- 未上传密钥；Release 资产沿用原 Debug 证书，属于本地/学习分发而非商店生产签名。


