# 主程序集成 I1：Sora 接入现有工作台

2026-10-21实施与构建，2026-10-22归档。基线 `c9fe974be241cad4e650eccfc696f95c223dbcd1`。后续顺序仍按[主程序集成计划](MAIN_APP_INTEGRATION_PLAN_2026-10-21.md)，没有重开阶段0或重新做组件选型。

## 本批完成与边界

主 `com.codepocket.ide` 的正常 MainActivity 入口已使用真实 Sora 0.23.7；继续使用同一工作台、文件树、标签、设置、项目/草稿控制器与原真实运行入口。没有搬入实验Activity、另建文件管理器或第二套终端。原内部项目目录、SAF授权/镜像、会话ID、设置JSON、文件版本与保存校验不变。

这是I1集成Debug批次，不能称I1–I4全部完成。I2还需把原生实现的括号分层色、词法角色色、完整SAF/草稿/设置/运行操作链逐项接通与核对；I3还需精确光标运动/柔和闪烁和手机/平板整体交互核对。当前Sora使用TextMate语法着色、原主程序词法候选/片段/内置参数参考与既有诊断模型；并非clangd/Jedi/LSP语义补全。

## 实际修改

| 文件范围 | 修改内容 |
| --- | --- |
| `editor-sora/build.gradle.kts`、Manifest、README、WorkbenchEditor.java | 新增供主App依赖的Java Android库；公开API仅用Android/Java与共享定位类型，不暴露Sora/Kotlin2类型；不声明应用入口、文件权限或运行器 |
| 7份Sora核心Java文件、20份语法/主题/许可资源 | 从editor-lab移动到editor-sora共享；4份适配源码与19份资源逐字节不变。SoraBuffer仅解除实验常量依赖；InputEditor增加不抢焦点选择作用域；SyntaxController接缩进设置；许可证通知改为主程序说明 |
| `app/.../editor/SoraEditorPort.kt` | 对接既有读取/事务/撤销/导航/文档/诊断接口，复用文件身份、revision、文本/选区与组合输入保护；使用Sora真实Content/Indexer/历史/诊断区域 |
| `app/.../MainActivity.kt` | 原编辑区使用共享控件；原补全/快捷键/保存/会话/运行流程从适配器取得正文和坐标；ContentChangeEvent接回dirty/revision/草稿/自动保存；Sora自身绘制行号；保留原文件与终端界面 |
| `DiagnosticPopup.kt`、`SettingsDialog.kt` | 错误详情改用View/EditorSurface边界；帮助与许可增加主包内Sora/TextMate/依赖通知与完整许可证入口 |
| `.gitattributes` | 为移动后的TextMate资源保留原始字节与上游许可空白，避免Git在新路径自动改写换行，资产不变 |
| `app/build.gradle.kts`、`settings.gradle.kts` | 接入库、API24脱糖；编译标准库固定1.9.25，运行时保持Sora要求的2.1.21，保留全部元数据检查 |
| `editor-lab/build.gradle.kts`、README | 内部工具依赖同一适配库；测试资源路径随资产移动，不增加实验功能页、不构建第二个交付APK |
| `SoraWorkbenchIntegrationTest.kt`、`HostAtomicOs.java` | 新增9项真实主Activity/控件/文件链路测试；主机静态Os.rename桥接执行真正的Windows原子替换，测试代码不进入APK |
| 4份既有主程序交互测试 | 用真实Sora控件继续验证原行为契约，等候真实异步布局；组合状态读取实际Sora状态，未删除断言或新增跳过 |
| `.github/workflows/build-apk.yml` | 主工作流增加生产适配库Lint/报告，继续生成并上传一个主Debug APK |
| README、RESUME、DEVELOPMENT_PROGRESS、主集成计划 | 更新当前主入口、实际结果、I2/I3未完成项；旧实验记录保留为历史 |

Java适配器保留历史 `com.codepocket.editorlab` 包名以复用同包内部回归；这是库中的类命名，不是第二个application。主APK没有EditorLabActivity、DocumentLabActivity或LabDocuments，只有原MainActivity启动入口。

## 发现并修复的问题

1. Sora运行依赖选择Kotlin标准库2.1.21，主工程1.9编译器无法读取其元数据。第一次主编译真实失败，日志保留。Java公开API隔离Sora实现，编译配置使用1.9.25标准库；实际运行配置保留2.1.21。依赖图确认主编译classpath不含Sora，未使用skip-metadata或Lint抑制。
2. 直接设置Sora `disallowSuggestions=true` 会将中文预编辑立即作为提交文字，导致Tab/字号操作误入组合输入。已保留真实组合事务；中文InputConnection回归、候选恢复和快捷键保护通过。
3. Sora设置选区会隐式请求焦点。项目载入与后台选区恢复使用同步焦点请求保护，已有实际终端/C++输入焦点不会被夺取；视口延迟恢复继续受实际Content所有者、版本和交互状态保护。
4. 新的主机保存测试暴露Robolectric默认Linux.rename没有实际替换文件；对Linux实例更换shadow又遇到跨配置缓存实例类型冲突。改为仅在测试中拦截静态Os.rename并调用真实Windows原子替换，重新核对实际磁盘字节、冲突和草稿；这不代表Android POSIX实机验证。
5. Sora重新布局期间暂时不提供InputConnection。主机测试等待组件真实布局结束后再输入，未强制开启组件内部编辑状态或模拟输入结果。

## 测试与构建结果

| 检查 | 实际结果 |
| --- | --- |
| 主程序完整JUnit/Robolectric | 549项，548通过、1既有Windows符号链接跳过；旧540项的结果均保留，新增9项通过 |
| 共享适配器内部回归 | 133/133通过，实际运行共享库实现；不将其算作新增主程序测试 |
| 主程序Lint | 0错误、11既有警告（旧target、ABI、备份规则、文本国际化） |
| editor-sora Lint | 0错误、1固定旧版本依赖提示；按计划固定0.23.7，未盲目升级 |
| Python/资源源码测试 | 44/44通过 |
| 实际APK Python字节码 | 39/39通过，runner29+diagnostics10，使用主机Python3.13执行实际打包字节码 |
| 实际生成Shell启动脚本 | 4/4通过，主机Git Bash；保留提示符/启动握手保护 |
| 实际生成片段 | Python7、g++ C++17共7项均通过；不是远程Wandbox调用 |
| Debug构建 | `:app:assembleDebug`实际成功；全量构建1分47秒、233任务/60执行/173缓存；最终复核1分17秒、259任务/17执行/242缓存 |
| 最终复核缓存说明 | 主包构建与Lint复用上轮结果；内部133项在最终复核实际执行，主程序完整测试已在全量及后续测试收尾批执行。未重复执行无关Termux核心146项，不把旧缓存说成新实测 |
| APK检查 | CRC/条目/DEX、原签名、包名/单启动入口、arm64-v8a、min24、16KB zipalign、实际资源路径检查通过 |
| 保护范围 | 847份基线tracked文件逐字节不变；50份旧APK、28份Python/终端原生运行归档不变；179个原IDE顶层类保留；46个图标/137条实际编译路径通过 |

新增9项主Activity回归覆盖：正常入口/原工作区、Ctrl+S实际写回和草稿清理、切文件保存/选区恢复/撤销隔离、外部写入冲突阻止切标签、旧请求和中文组合输入保护、查找替换事务/一次撤销、后台选区/视口不抢输入焦点、诊断区域/行定位/旧诊断拒绝，以及平板多栏/默认终端与字号操作保持会话。

视觉与行为分别记录：已运行手机/平板、深浅主题的真实主入口和原生图标绘制测试，以及主机操作链回归；没有新做人工界面截图审美验收，没有设备IME/蓝牙键鼠/卓易通或FPS实测。

## 一个主APK

- [本批主程序集成Debug APK](../dist/CodePocket-main-sora-integration-2026-10-21-debug.apk)，26,333,332字节（约25.11MiB），arm64-v8a。
- SHA-256：`b044f10519aa5937a74377438aa82ed8c0df9b63341a04469d1075a0c974bcd9`。
- 原Debug证书SHA-256：`d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`；具备原包签名一致性，未做本轮设备覆盖安装验证。
- 本轮没有另交付实验APK，没有推送GitHub或运行云端Actions；工作流保持主APK上传能力，本地主机构建不能替代云端任务结果。

## I2起的接续项

- 保留原生CodeEditor作为内部回归实现，继续在现有主工作台完成全部SAF授权/撤销授权、草稿恢复、重命名/移动/删除、设置应用/取消与主题重建操作链；不能靠实验页的通过替代主入口验证。
- 在TextMate之上接回原混合括号分层色、词法变量/参数/属性色和匹配括号强调，并补原编辑器同等断言。当前诊断模型与Sora诊断区域已接通；真实Python诊断、运行后错误跳转的完整主链仍需继续覆盖。
- 核对原生键入/大段粘贴的20万字符上限、候选显式请求遇到异步布局、片段连续替换和主题/换行/字号后定位；显式事务与文件写入上限保持，但尚不能宣称Sora所有原生输入路径的过滤都完成。
- 将App/系统/省电开关接到精确光标运动与柔和闪烁，核对60ms/80ms目标；本批只对接组件基础动画开关，不能称完整动画完成。
- 保留实际窗口阈值、单一真实PTY、Python无预填本地交互、C++真实Wandbox和第三方发送说明；本轮没有新增Android上的真实PTY/Python交互或远程Wandbox执行记录。
- I2/I3完成后再做I4最终完整主包验收；原P2/P3需求继续保留，设备验收按用户约定后置。

## 本地证据

`verification/main-app-editor-integration-2026-10-21/` 保存before.json、失败轮日志、主程序完整JUnit、final-round3.log、依赖边界JSON、Python/Shell/片段/签名/对齐/实际资源检查、artifact-audit.log、results.json及验证脚本。失败原因和修复保留，不以最终通过覆盖之前失败证据。
