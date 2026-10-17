# P1-C 首批：编辑器分析隔离与输入稳定性

2026-10-17，在 `62c3750` 的 UI/操作接续结果上推进，没有重新创建工程。**本批代码、主机回归及 Debug APK 构建已完成；P1-C 整体尚未完成。** 实机按用户要求后置，没有连接设备。

## 修改与原因

1. 原 `refreshEditor` 和 `loadActive` 在主线程调用词法扫描，括号匹配又重复扫描。新增 `editor/EditorPort.kt` 的只读 UTF-16 快照/装饰边界、`EditorAnalysisController.kt` 单后台线程和 `NativeEditorPort.kt` 原生适配器；MainActivity 实际使用该路径。工作线程只接收不可变字符串，不操作 View/Editable。光标/主题变化复用同一文档的扫描结果；应用颜色仍需在 UI 线程执行。
2. 接收结果时核对工作区/稳定文件身份、文档版本、实际内容、语言、主题及选区；请求代号阻止已排进 UI 队列的迟到结果。输入/选区变更、文件操作、暂停/失焦及销毁取消旧请求，恢复/回焦和改名后重排刷新。关闭编辑器时释放线程、监听与回调。
3. 组合区存在时不发起或应用装饰。原路径只依赖文字或选区变化，可能漏掉只移除组合标记的提交；原生适配器增加 `SpanWatcher`，组合结束后延迟通知刷新，不改写输入连接或从 span 回调编辑文本。主机测试覆盖状态转换，真实输入法回调仍未验收。
4. 原代码移除全部前景/背景色 span，可能误清其他功能或系统样式。本批只增删私有 `SyntaxSpan` / `BracketSpan`，标记为 `NoCopySpan`，保留原生文本/选区/滚动/焦点及编辑历史路径。装饰接口不执行替换、保存或移动光标。
5. **真实复现并修复长字符串栈溢出**：新增约 44000 字符的引号字面量测试在原正则抛 `StackOverflowError`，首次定向测试失败，报告保留。将普通字符串重复改为占有型重复，避免 Java 正则的递归回溯；长闭合/未闭合字符串、单/双引号及大量转义符均回归通过。该异常已在主机 JVM 复现；Android 的正则实现与设备表现仍需实测，旧同步路径若出现未处理扫描异常会影响 UI。
6. 保留 45000 字符高亮上限，新增最多 6000 个着色 span 的预算；高密度短文件超预算时退回正文颜色，括号仍使用完整词法屏蔽范围。超过字符上限则停用颜色/括号，不阻止文件继续编辑、保存或运行。降级类型在分析结果中独立记录。

## 文件

| 文件 | 改动 |
| --- | --- |
| MainActivity.kt | 接入后台分析、生命周期取消/恢复、文件载入与改名刷新，移除同步颜色/括号操作 |
| EditorFeatures.kt | 复用已有 token 的括号接口、长字符串正则修复；去除 Android span 渲染耦合 |
| editor/EditorPort.kt | 不可变文档/选区、结果适用校验、扫描缓存、预算与组合状态 |
| editor/EditorAnalysisController.kt | 单线程/最新请求优先、UI 调度及取消/释放 |
| editor/NativeEditorPort.kt | 原生快照、私有 span、组合结束监听与释放 |
| EditorAnalysisTest.kt | 20 个有实际边界的回归，包括真实异步线程和迟到 UI 队列 |
| README、开发进度/接续、UI 重设计计划及本报告 | 当前结果与剩余工作 |

既有 CodeEditor、缩进/自动括号、补全/片段/参数核心、会话、Workspace/SAF/草稿、runner/运行控制、Gradle/Manifest/Actions 和三份需求保持原字节哈希（EditorFeatures 是本批明确修改项）。校验清单在 results.json；既有 UI 类及新编辑分析类也在 APK 的 Dex class_defs 中核对。未加依赖、权限或存储迁移。

## 真实测试与产物

| 检查 | 结果 |
| --- | --- |
| 首次定向测试 | 24 项中 1 失败：长字面量正则栈溢出，focused-failure-01.xml 保留 |
| 修复后定向测试 | 25/25 通过，含新增转义/未闭合回归 |
| 完整 Gradle | `testDebugUnitTest lintDebug assembleDebug --offline`；1m 8s，62 任务中 19 执行 / 43 未变化；未强制全量重跑 |
| Kotlin | 173 项：172 通过、1 Windows 符号链接环境跳过，0 失败/错误；新增 20 项均通过 |
| Python 源码 / APK 内 runner.pyc | 各 13/13，均真正执行 |
| 实际生成模板 | Python 7/7；主机 g++ 的 C++17 编译/执行 7/7，不是设备本地 C++ |
| Lint | 0 错误、11 警告；现有 SetTextI18n / OldTargetApi / ChromeOsAbiSupport / DataExtractionRules |
| APK | ZIP CRC、6 个 Python 内包、v2 Debug 签名、arm64-v8a、API24/target34、16KB ZIP 对齐、新旧关键类定义检查通过 |
| 设备/模拟器/截图/帧率/新 Wandbox 网络请求/远端 Actions | 本批均未执行 |

[本批 Debug APK](../dist/CodePocket-editor-analysis-debug.apk)：**22,033,627 字节，21.01 MiB**，SHA256 `3ee15564b1bd745d786cde8af8318f5b04ba80c602204504029a3d0f7bb1001c`。保留原有 dist APK、verification 证据、.tools 和设备备份。

证据目录：`verification/editor-analysis-2026-10-17/`。包括首次失败与修复后测试、源码前后哈希/原件、完整构建/JUnit/Lint、Python/模板、归档/签名/对齐、机器可读结果和复跑脚本 `verify-editor-analysis.ps1`。复跑脚本接受当前真实仓库路径，使用现有便携 JDK/SDK/Python/Gradle 缓存，不改系统环境。当前结果不代表远端 CI 已运行。

## Sora 评估与下一步

本轮重新查阅 [Sora 上游](https://github.com/Rosemoe/sora-editor) 与 [官方接入指南](https://project-sora.github.io/sora-editor-docs/guide/getting-started)。上游提供增量高亮、撤销、补全和诊断等成熟能力，声明 LGPL-2.1-or-later；官方指南标记 0.24.4，列出 Java/JDK17、核心最低 API21、LSP 模块最低 API26，TextMate 在 API33 以下需 core library desugaring。该指南不等于某个待选 AAR 的兼容证明，主分支依赖也不可直接等同发布制品。

**本批没有引入 Sora AAR、隔离调试页面或宣称兼容通过。** 已落实的是现有原生编辑器的首段分析边界；事务编辑、撤销/重做、视口与诊断的完整 EditorPort 契约，以及固定版本 AAR/POM/ABI/Kotlin 元数据/许可/体积核验、隔离候选组件仍须后续完成。不能把此批标为已迁移 Sora。

下一批按原顺序继续该隔离验证和完整契约，其后完善高亮/手势/动画、设置和 Python 真实流式会话。P2 的 Jedi/clangd、成员语义、多文件/本地 C++ 与 P3 原需求保留。本批仍为词法扫描，不是增量语法树或 IntelliSense，C++ 原始字符串/Python f-string 等复杂语法仍有词法局限。

手机/平板/鸿蒙卓易通的真实 IME、组合提交/撤销、鼠标选区、键盘候选定位和视口等需要最后统一实机联合验收。6000 span 预算只限制工作量，不证明达到 60 FPS 或高刷新率流畅；新原生 span 监听/渲染目前只有编译/Lint 和纯模型边界测试，未在 Android 运行时验收。
