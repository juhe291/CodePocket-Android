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
- 版本：`0.2.2 (4)`（原 Debug 签名，未重建密钥）。本批最终 APK：`dist/CodePocket-p3c-breakpoints-updates-2026-11-03-debug.apk`，**118,221,699 字节**，SHA256 `f6a464a61435b91cfd041c95f7de8b5e6ba4ff9f43dc29b9c7b8d2f2cc09db6a`；929 entries；`apksigner` 验证、`zipalign -c -P 16 4`、`zipfile` 完整性、Manifest（versionCode 4 / versionName 0.2.2）通过；相对上一有效 Bug 包只改变 `AndroidManifest.xml`、`assets/chaquopy/app.imy`、`assets/chaquopy/build.json`、`classes2/3/6/7.dex`、`resources.arsc`、两个引用资源 ID 的 layout 二进制与签名容器，无新增/删除包条目。包内 Python/语义/Git/格式化/Web/C++/矢量/Shell/片段专项见同目录日志。
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
