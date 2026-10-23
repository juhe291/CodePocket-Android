# I4 单一主 APK 主机整体验收（2026-10-23）

基线 `6309010ba23839dfad42f6b571ea5101e12a07cc`。本轮从默认工程重新检查当前主入口、生产依赖、运行前保存及构建/产物。I1–I4 的**主机整合验收通过**，可以继续原 P2/P3；设备验收、远端 Actions 和 P2/P3 并未因此完成，整个项目目标仍进行中。

## 主入口运行链新增验证

新增 `app/src/test/java/com/codepocket/ide/MainRunIntegrationTest.kt` 五项，全部从正常 MainActivity/Sora/真实主机文件和 WorkspaceIo/RunSession 进入，未加入替代生产后端：

1. F5 将主 Sora 当前源码保存到真实文件；RunSession 携带正确源路径、稳定文件身份、工作区与代码哈希，内容/选区保持。
2. 挂起真实文件队列，在保存待执行期间继续编辑；恢复后只启动最新已保存代码的运行请求，没有运行旧快照。
3. 源文件发生外部冲突时阻止 F5，保留编辑器、原文件与可恢复草稿，没有启动 RunSession。
4. C++ Ctrl+Enter 保存后要求第三方 Wandbox 发送确认；取消不启动网络请求、不记住同意，源码保持。
5. 空代码在进入 RunSession 前被拒绝。

**Windows/Linux 主机没有 Android `/system/bin/app_process`。** Python 路径真实进入 RunSession 后返回准确 SERVICE 错误，stdout 为空。测试断言这个缺失能力与正确清理，不将其冒称为 Python 成功输出。真正的 Android Chaquopy/PTY 运行按用户约定留待设备统一验收；runner 与打包字节码另有真实主机执行。

本批未发现新的生产 Bug，**没有改动任何生产源码、依赖、Manifest 或运行实现**。

## 按集成计划审计

| 要求 | 当前权威证据 | 结论和边界 |
| --- | --- | --- |
| I1 默认主入口使用成熟编辑器 | SoraWorkbenchIntegrationTest 正常 Main 入口；当前 APK DEX；默认 Gradle 项目图 | 主包内真实 Sora/生产 Port，唯一 MainActivity launcher；不需安装 lab App |
| 文件树/标签/新建/改名/移动/删除/保存 | MainFileSettingsIntegrationTest、WorkbenchViewsTest、WorkbenchWindowIntegrationTest，真实主机磁盘与 Sora | 原文件身份、路径、焦点和保存失败保护保持；设备文件系统体验待验 |
| SAF/草稿/运行错误导航 | MainSafRecoveryIntegrationTest、EditorDiagnosticsTest、DraftRecoveryTest | 授权/失效/只读/读写校验失败、草稿与版本保护通过主机回归；诊断导航夹具不冒称实际运行 |
| 输入/历史/补全/参数/片段/查找替换/跳行 | SoraWorkbenchIntegrationTest、CompletionInteractionTest、MainSurfaceIntegrationTest 与输入预算用例 | 实际 Content、InputConnection、UTF-16/过期结果保护；仍是明确标识的词法/内置参考，不是 P2 语义 |
| 设置应用/取消/配置恢复 | MainFileSettingsIntegrationTest、UiRedesignTest、WorkbenchWindowIntegrationTest | 草稿、搜索/分类、异步应用和布局状态通过主机回归 |
| 编辑到真实运行入口与保存保护 | 本批 MainRunIntegrationTest + RunControlTest/RunTickets + 原运行资源实际主机执行 | 运行请求使用最新主缓冲；重复/迟到隔离在既有回归中；本批没有新增 Android Python/PTY 或远端 Wandbox 成功结果 |
| 单一真实 PTY 与输入/焦点路由 | TerminalPanelFocusTest、ShellRunInputTest、TerminalControlsClickTest 等 | 原 Runtime/Termux 路由和键盘按钮保持；不能把 PID0/主机状态夹具当真实 Android PTY |
| I3 光标/动画/窗口/二级界面 | MainCaretMotionIntegrationTest、WorkbenchMotionTest、WorkbenchWindowIntegrationTest、MainSurfaceIntegrationTest；此前实际绘制截图 | 主机逻辑/位置/局部目视通过；IME、蓝牙键鼠、拖动体验、卓易通与 FPS 未实测 |
| I4 当前完整回归、Lint与APK | 当前 JUnit、Lint、默认工程日志、实际 APK/资源/签名检查 | 主机整合验收通过，原跳过/缓存如实保留 |
| GitHub Actions 主 APK 产物 | 当前 `.github/workflows/build-apk.yml`、`ci-source-inspection.json`、默认工程本地任务 | 工作流源码/任务/产物路径保持，远端运行及下载链接未取得；不声称 CI 已运行通过 |

这是主程序集成清单的核对，**不是对全部原始需求的项目完成声明**。P2 的两语言真实深度语义、多文件项目增强、本地 C++ 可行性、批量用例、格式化，以及 P3 Git/GitHub、主题/语言/导航扩展仍须继续完成。

## 实际执行与缓存

- 新 5 项主入口测试通过；相关 MainRunIntegrationTest/RunControlTest/TerminalPanelFocusTest 共 **17/17 实际执行**。
- 主 App 全量 **649 项：648 通过、1 既有 Windows 符号链接跳过**，原 644 项状态保持。首轮默认工程确实执行全量主 App 测试；修正审计脚本后的最后一轮使用未变化缓存，没有重复执行同样测试。
- Termux **146/146**：此次 `:terminal-emulator:testDebugUnitTest UP-TO-DATE`，沿用原缓存。共享适配器 **159/159** 沿用上批结果；默认工程未启用或构建 editor-lab。
- 主/共享 Lint **0 错误**，既有 10/1 警告保持，无新增。
- 首次默认工程运行 1分53秒，应用测试与 assembleDebug 任务完成，但新增审计脚本使用 resolvedArtifacts 请求歧义 Android 变体失败。修正为读取已解析组件图，**最终 BUILD SUCCESSFUL in 17s，238任务，9执行/229未变化**。失败日志保留，不隐藏工具错误；没有为此改生产依赖。
- `:app:packageDebug`/`:app:assembleDebug` **UP-TO-DATE**；本批生产代码未变，实际 APK 与6309010上一版**逐字节相同**，无需为这次测试新增重复安装。
- 本轮重新执行 Python 源码 **44**、实际 APK 字节码 **39**（runner29+diagnostics10）、真实 Bash **4**、Python/C++17 g++ 片段各 **7**，均通过。

默认工程图为 `:, :app, :editor-assist, :editor-sora, :editor-surface, :terminal-emulator, :terminal-view, :workspace-core`，不含 editor-lab。重新读取当前真实依赖解析：编译 Kotlin stdlib1.9.25 且无 Sora Kotlin2元数据暴露；运行 stdlib2.1.21，editor/language-textmate0.23.7，原 desugar2.1.5/SDK35/JDK17/Chaquopy17.1.0保持。未关闭元数据或 Lint 检查。

## 交付与保护

[当前集成主 APK](../dist/CodePocket-main-integrated-2026-10-23-debug.apk)：**26,429,643字节 / 25.21MiB**，`com.codepocket.ide` 0.2.0/code2，min24/target34，arm64-v8a，唯一 MainActivity launcher，原签名。

SHA256 `30395c3a2e7323f1f3fc23309391a9ee5930ca29b62954689763b46fa99e0d30`，与上一版 surfaces 主包完全相同。

证书SHA256 `d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`。CRC/安全路径/重复项/16KB对齐/Manifest权限/签名核验通过；测试类与lab Activity未打包。保留60旧APK、937非目标tracked文件、28 native/Python归档、20语法/许可资产、182旧IDE顶层类；46个vector/137条path与源码一致，DEX4285类。

主要证据在 `verification/main-app-final-2026-10-23/`：`main-run-first.log`、`final-production.log`（含审计失败）、`final-production-fixed.log`、`dependency-boundary.json`、`ci-source-inspection.json`、`results.json`、JUnit/打包执行与签名日志。只新增主入口测试、审计记录和本报告；README、RESUME、进度和计划更新当前接续。

## 继续工作

先进入 P2 真实语言服务：复核 Jedi/clangd 的固定版本、许可与 Android/Chaquopy3.13约束，取得真实成员/签名、不完整代码、未保存项目模块、中文/emoji位置及超时/过期请求证据后接入同一工作台；禁止把静态名称列表当语义结果或为补全执行用户代码。其余原P2/P3要求完整保留，不重开第二个App。

实机IME、实体键鼠、手机/平板、API24、卓易通、FPS仍按用户约定后置。远端Wandbox/Actions本轮未执行，无push/发布；以上未验证项不因主机验收通过而消失。
