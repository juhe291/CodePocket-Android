# P1-C 生产编辑事务与历史入口首批

2026-10-17，基线 `f366ed1f827a2dc6c33a577e97a5ae945824db77`。接续原计划，没有重建项目或迁移默认编辑器。实机按用户要求统一后置。

## 本批改动

只读 `EditorPort` 继续服务后台分析；新增 `EditingEditorPort`、`EditorTransaction`、`EditResult` 与纯校验/提交分派器 `EditorTransactions`，默认 `NativeEditorPort` 实际实现。它们已被日常编辑路径使用，并非仅供测试的并行实现。

显式编辑保留来源文档key、revision、文本和选区，在UI线程落地前复查当前真实缓冲。文件切换/忙碌/关闭、过期文本或版本、选区变化、IME组合、非法范围、超20万UTF-16长度、断裂代理对或CRLF都拒绝。只计算新选区边界，不为每次校验额外拼接整个结果。全部替换允许仅光标移动，仍核对来源文本/版本/文件；选中项替换必须保持选区。

候选接受、内置片段、符号插入、缩进、查找替换和键入后的自动修正均接入。普通原生输入/粘贴/系统历史仍由TextWatcher跟踪；加载文档、会话选区/滚动恢复仍保留已有路径。显式事务使用原生batch edit；具体系统撤销分组待设备验证。

## 问题与修复依据

1. **超限缩进假修改**：旧 `applyChange` 在长度超限时直接返回，`indent` 仍无条件标脏、增加版本和排队保存。现在明确返回拒绝结果，调用方仅对实际文本修改更新版本/保存。23项新回归中含真实 `EditorActions` 生成的近上限缩进，确认不会分派到提交回调。
2. **无变化标脏**：反缩进无缩进的行、完成已完整单词或替换相同内容，旧调用方仍会增加版本。现在区分文本改变、仅选区变化和完全不变，后两者不replace正文、不额外排队保存。已有脏状态保留，不擅自清除。
3. **历史恢复再次自动修正**：原TextWatcher不区分输入与历史恢复；恢复单个左括号/换行也会产生新的补齐/缩进规则。`CodeEditor` 在系统undo/redo上下文调用期间标记来源，`EditorActions.typed(fromHistory=true)` 跳过自动改写，仍执行正常版本/草稿/保存观察。纯规则复现已测试，Android真实UndoManager尚未运行验收。
4. **CRLF与边界校验协调**：旧多行缩进为排除最后一行只减去末尾LF，替换范围会结束在CRLF中间。新增门控下会拒绝正常操作；本批改为排除整个CRLF，真实多行缩进/反缩进回归保持最后未选行与所有换行。
5. **统一结果与来源**：查找替换原有长度预检仍保留；现在进一步把计算来源快照传到提交点，且拒绝结果不显示“替换成功”。不要把原已存在的搜索长度预检归为本轮新增。

撤销/重做的触屏入口和实体Ctrl+Z/Y/Shift+Z经过相同焦点/忙碌/组合门控；stdin使用自己的系统历史，未改为代码编辑器历史。长按/上下文系统历史也能携带原生来源标识。既有键盘输入、SAF、文件安全和恢复模块保留。

## 文件

| 文件 | 改动 |
| --- | --- |
| `editor/EditorTransactions.kt`（新增） | 事务/结果/可编辑能力及纯校验与提交分派 |
| `editor/NativeEditorPort.kt`、`editor/EditorPort.kt` | 实际原生编辑/历史实现，保留读装饰接口 |
| `MainActivity.kt` | 共用修改结果处理、来源快照、真实修改才记版本和保存、键盘历史入口 |
| `CodeEditor.kt`、`EditorActions.kt` | 系统历史来源、跳过历史自动修正、完整CRLF边界 |
| `SearchDialog.kt` | 将原计算快照/选区策略传给提交者 |
| `res/values/strings.xml` | 四项拒绝编辑的中文说明 |
| `EditorTransactionsTest.kt`（新增） | 23项真实规则/事务分派回归 |
| `README.md`、`DEVELOPMENT_PROGRESS.md`、`RESUME.md`、本报告 | 构建、能力边界和下一执行点 |

## 实际验证

最终 `testDebugUnitTest lintDebug assembleDebug --offline` **BUILD SUCCESSFUL in 1m 10s**；62 actionable tasks: 18 executed, 44 up-to-date。

- Kotlin **196项：195通过、0失败、1 Windows符号链接用例跳过**；新增23全部通过，前批173项及通过/跳过状态逐项保留。
- 覆盖跨文件/工作区、同内容新版本、外部重载、过期选区/全部替换、组合区、拒绝时不提交、长度上限、无变化/仅选区、emoji代理对/非法UTF-16、CRLF与编辑接缝、真实候选/片段/键入规则和历史规则。
- Python源码 **13/13**；取实际APK `runner.pyc` 在匹配Python3.13主机执行 **13/13**，不是Android运行时验证。
- Kotlin测试实际生成的模板：Python **7/7**，主机 `g++ -std=c++17` **7/7**；不是Android本地C++或本轮Wandbox请求。
- Lint **0错误、11警告**：SetTextI18n8，OldTargetApi/ChromeOsAbiSupport/DataExtractionRules各1，与前批相同。编译仍有既有Android弃用API警告。
- 实际APK ZIP/嵌套Python归档CRC、arm64-v8a/API24/target34、Debug v2签名、16KB ZIP对齐通过；新事务/适配类有Dex类定义，上一默认APK全部类定义保留，没有Sora/实验模块混入。
- 原Gradle/Manifest/Actions、文件/SAF/草稿、runner、补全/片段/签名核心、配置与需求文件哈希保持；旧默认APK/实验APK/材料包字节哈希保持。未增加依赖、权限或做存储迁移。
- 最终源码完成后才执行最终构建；之后只更新报告。首轮构建1m11s及APK在 `round-01` 保留；新增历史保护后最终重新构建与验证。没有失败测试被删除或改为跳过。

[最新Debug APK](../dist/CodePocket-editor-transactions-debug.apk)：**22,101,433字节（21.08MiB）**。

SHA256：`6c84eda6c3bc92966af33b732ec10750050d74fe8304444fdb291465bab48689`。复制出的dist文件与构建产物哈希一致。

证据：`verification/editor-transactions-2026-10-17/results.json`、`verify.ps1`、`audit_transactions.py`、`build.log`、JUnit、Lint、源码输入哈希/修改前备份、签名/对齐/打包Python报告、实际模板源码与执行结果；旧产物和设备备份保留。本地保存阶段提交，不push/发布或触发远端Actions。

## 下一批与验证边界

生产Port仍缺独立选区/视口/诊断与完整文档/会话生命周期契约；仍有跳行/错误定位/会话恢复直接操作原生视图。下一批继续这些边界，再做Sora语言/候选/片段/参数/SAF会话适配与默认迁移评估。当前不能声称完整Port或Sora迁移完成。

原生TextWatcher和系统历史在Android实际运行时、中文IME/虚拟与蓝牙键盘、鼠标、焦点/选区恢复、补全定位、SAF联合保存、手机/平板/鸿蒙卓易通与FPS均待最终设备验收。主机事务回调与规则测试不能证明原生撤销分组、真实输入体验或60FPS。

完整动画/编辑手势、设置帮助许可页与真实Python流式终端继续P1；Jedi/clangd语义、本地/多文件C++、用例/格式化及P3 Git/主题扩展等原需求继续保留。本轮没有新Wandbox联网测试、设备安装、截图或远端Actions证据。
