# P1-C 文档交接保护首批

2026-10-18，基线 `fa5cd60b54982b5308862936c85eb4888f03348e`。在现有原生编辑器、保存与SAF架构上增量开发；不重建项目，原始需求/动画与语义补全计划继续生效，实机统一后置。

## 发现与修复

1. **后台读取覆盖新输入**：原switchFile在保存、捕获会话后开始异步读取目标文件，旧编辑器仍可输入；读取完成便setText。reloadActive与startWorkspace也存在类似窗口。现在生产实际调用EditorDocumentTransitions，在完成回调落地前复查文件key、编辑revision、完整源码及Activity编辑epoch；源码变化、组合输入或文档可用性变化时保留当前缓冲，拒绝旧交接，排队草稿并恢复自动保存。输入仍由原生组件处理，没有靠阻塞UI消除竞争。
2. **最新stdin/视口丢失**：原会话只在读取前捕获。现在在门控通过后、改变路径/工作区前再次捕获stdin、选区、滚动及焦点。仅选区/焦点/stdin变化不拒绝读取；同一工作区重载用EditorDocumentSessions选择刚更新的当前会话，避免准备阶段的旧对象覆盖它。不同工作区和首次启动使用各自准备的会话。
3. **活动标签提前消失**：原closeTab先移除活动标签，再尝试打开替代文件。现在替代文件加载成功才删除旧标签；loadActive明确返回是否成功，原生拒绝替换时还原先前路径与标签列表，且不执行成功回调；读失败或交接被拒时保留活动标签。延续至少保留一个文件的既有策略，回退仍是当前文件时说明已保留最后一个标签。
4. **文档替换边界分散**：MainActivity不再直接editor.setText，改经DocumentEditorPort/NativeEditorPort显式替换；替换前取消旧导航与组合结束通知，期间不暴露快照，结束后重新观察Editable并清除装饰缓存。保留原生setText机制，未另造历史栈；真实系统撤销与IME仍待设备验证。Main的quietEdit由try/finally还原，成功后才递增编辑版本。
5. **读取失败后保存定时器失效**：文件操作期间到期的自动保存会被workspaceBusy拒绝。读取失败或交接拒绝后现在再次排队草稿与自动保存；双阶段“保留草稿后切工作区”覆盖两个异步交接窗口。实际磁盘/提供方写入仍可能失败，原有错误反馈/恢复入口保留。
6. **删除期间新输入**：删除经过原确认与保存；删除已完成的UI回调在移除文件会话前，先为其期间新输入排队恢复草稿。不会撤销已确认的磁盘删除，也不把“已排队”写成“已持久保存”；恢复草稿写入失败仍按既有方式提示导出。

## 生产接口与范围

`DocumentEditorPort`继承已有编辑/导航能力，提供可在文件I/O暂停交互命令时读取的交接快照，以及显式整文档替换。普通分析/编辑/导航仍经过available门控；没有把繁忙时的只读捕获开放成写入入口。

`EditorDocumentTransitions`由Activity持有；请求以对象身份绑定所有者，仅允许最新请求消费一次。校验前已消费请求，异常、拒绝或重复回调都不能重放；Activity销毁关闭门控。通过后捕获最新会话并执行原加载路径，保留SAF、草稿恢复和运行算法。文件I/O失败会取消待交接请求。

本批不是完整Sora迁移或全部文档生命周期完成；默认仍为CodeEditor。保留独立stdin、词法补全/片段/参数、错误源码证明、恢复草稿和原生输入。首个错误行及导入模块按行降级的边界沿用上批。

## 文件

| 文件 | 改动 |
| --- | --- |
| `editor/EditorDocuments.kt`（新增） | 文档Port、一次性交接门控、结果与同工作区会话选择策略 |
| `editor/NativeEditorPort.kt` | 区分交接捕获/交互可用性，显式文档替换与旧回调/装饰状态清理 |
| `MainActivity.kt` | 文件/重载/工作区交接，最新会话、标签关闭、失败后草稿/自动保存，删除后新输入排队恢复 |
| `res/values/strings.xml` | 交接拒绝、文档替换失败、保留最后标签的中文反馈 |
| `EditorDocumentsTest.kt`（新增） | 20项门控、真实文件读取和会话策略主机回归 |
| `README.md`、`DEVELOPMENT_PROGRESS.md`、`RESUME.md`、本报告 | 当前能力、真实测试与接续点 |

## 实际构建与测试

最终 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --stacktrace --no-daemon --max-workers=4`：**BUILD SUCCESSFUL in 1m 10s**；62 actionable tasks: 18 executed, 44 up-to-date。

- Kotlin **246项：245通过、0失败、1 Windows符号链接用例跳过**，新增20全部通过。上一批226项及通过/跳过状态逐项保持。
- 真实临时文件、单线程读取任务和闩锁复现“读取期间编辑”，通过生产门控验证新代码和活动标签不被提交回调覆盖；另有真实缺失文件读取失败、会话JSON往返与同/跨工作区策略。
- 覆盖编辑后撤销仍算新版本、相同revision但内容变化、文件/工作区身份变化、选区变化可捕获、开始/结束组合、首次启动无文档、文档出现/消失、无文档期间epoch变化、最新请求优先、跨所有者、取消/关闭、重复拒绝/异常回调及双阶段重入。
- Python源码 **13/13**；实际APK runner.pyc在匹配Python3.13主机 **13/13**；实际生成模板Python **7/7**、主机g++ C++17 **7/7**。未使用模拟运行结果，不是Android本地C++或本轮Wandbox测试。
- Lint **0错误、11既有警告**：SetTextI18n8，OldTargetApi/ChromeOsAbiSupport/DataExtractionRules各1，和上批一致；仍有既有Android弃用API编译警告。
- APK及嵌套Python归档CRC、API24/target34/arm64-v8a、Debug v2签名、16KB ZIP对齐、复制后哈希通过；1478个Dex类定义，新Port/门控/会话策略实际打包，无Sora/实验模块混入。
- 旧具名业务类保留；新增构造lambda使buildEditor匿名监听类重编号，完整类定义差异记录于results.json，审计另核对该方法除预期构造参数变化外源码相同。未把生成类编号差异当作业务删除。
- 原CodeEditor、Workspace/DocumentTree/DraftStore/EditorSessions格式、运行/Python服务、词法/片段/参数/导航/事务模型、旧测试、Gradle/Manifest/Actions和三份需求均保持输入哈希。旧导航/事务/原生/Sora APK与材料包五项SHA256保持；不增加依赖、权限或存储迁移。

首轮完整构建1m11s，244项中243通过/1跳过，新增18通过；随后补同工作区最新会话选择与失败后保存恢复，再补原生拒绝替换的路径/标签回滚后完整重跑。中间20项通过的成功版本及APK保留在round-02。首轮APK与所有报告保留在 `verification/editor-documents-2026-10-18/round-01/`，没有移除或跳过失败测试。

[最新Debug APK](../dist/CodePocket-editor-documents-debug.apk)：**22,106,581字节（21.08MiB）**。

SHA256：`7d38f8d3c32c9802879670e787390a793065b870789f2093703379d213a46fe4`。

证据目录：`verification/editor-documents-2026-10-18/`，包含results.json、verify.ps1、audit_documents.py、最终/首轮日志、JUnit/Lint、APK签名/对齐/字节码报告、生成模板、输入前后哈希、修改前备份及阶段补丁。保留.tools、旧产物、设备备份；仅本地阶段提交，无push/发布/远端Actions。

## 仍需接续与实机边界

下一批继续Sora生产组件/语言包适配，联动现有候选/片段/参数/SAF会话和其余文档生命周期，评估默认迁移。P1完整动画/手势、设置帮助许可页及真实Python流式会话；P2语义、本地/多文件C++、批量用例/格式化与P3 Git/主题仍待，原需求未删减。

Android实际整文档替换、InputConnection/中文组合/UndoManager、虚拟/实体键鼠、文件与工作区切换焦点、后台/旋转、SAF慢提供方、删除后回退读取失败等恢复分支、手机/平板/鸿蒙卓易通与FPS未设备联合验收。主机真实文件/门控与JSON回归只能证明相应生产规则，不能证明Android回调时序或60FPS。实机继续按用户要求统一后置，无设备/模拟器、截图、新Wandbox或远端Actions证据。
