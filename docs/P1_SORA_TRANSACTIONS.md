# P1-C Sora编辑事务、文档更换与历史适配首批

2026-10-18，基线 `c5f5bf1f7da4b222cc1d64ec622f93902193e725`。按原计划接续，不重建项目；默认IDE仍用原生CodeEditor，实机统一后置。

## 实现与修复

把生产EditorTransactions已有的范围、长度、替换UTF-16、新选区、emoji/CRLF边界与无变化分类移入本地Java库TextEdits，保留原Kotlin API、返回枚举和源快照门控。默认编辑器与Sora使用同一规则，旧测试全部保留；不再维护两套简化后会漂移的校验。规则读取CharSequence，不复制整篇正文；快照仅在显式命令建立，热输入/光标监听仍不做整文档拷贝。

实验新增SoraBuffer，对真实Content/Cursor捕获实例身份、key、Content.getDocumentVersion、正文和选区。提交前检查旧实例/版本/内容/选区/组合/关闭状态，然后执行真实Content批次替换；无变化不调用replace、不清Redo，只有选区变化不加文字历史。新输入、Undo后回到相同内容仍使旧快照失效。版本直接读Content，不再由Activity另计，避免同一次替换/历史在两处计数。

SoraEditorAdapter实际连接实验工具栏插入、撤销/重做、同步草稿更换及共享光标锚点。历史能力经同一保护后调用CodeEditor.undo/redo，选区经实际setSelectionRegion通知组件与IME；没有通过直接静默改Cursor替代设备行为。主机用真实Content/UndoManager验证核心调用；这些Android通知本轮仅经过真实编译与Lint。

从精确0.23.7 sources.jar核对getDocumentVersion、Content初始化/批次、CodeEditor.setText(CharSequence,boolean,Bundle)及undo/redo。更换草稿创建全新Content并以reuseContentObject=true传给组件，旧缓冲关闭，历史不跨文档。重置的确认回调也复查组合状态，避免原实验reset路径绕过组合保护。原型原来只裁剪保存的选区数值，现在也避开emoji和CRLF内部。

ScratchEdits兼容入口保留，委托共享校验，补足原简化入口没有拒绝的不配对替换UTF-16和替换后CRLF缝隙。原有文本加载保持原样，包括超限/不配对UTF-16草稿；20万限制只约束显式编辑结果，允许删除到预算内。没有给直接键入增加新的截断规则，也不因旋转/切换丢掉原临时文本。

这仍是独立临时实验：没有接项目/SAF/文件保存/运行器、语言包或语义服务，尚不是完整生产EditorPort或默认Sora迁移。同步草稿替换没有后台I/O；后续异步项目切换须继续采用既有源快照交接门控。

## 修改文件

| 文件 | 内容 |
| --- | --- |
| editor-surface/.../TextEdits.java（新增） | 共享UTF-16事务校验/恢复选区，无Android或第三方依赖 |
| app/.../editor/EditorTransactions.kt | 原API和源保护保留，文本规则委托共享Java |
| editor-lab/.../SoraBuffer.java、SoraEditorAdapter.java（新增） | 真实Content事务、快照、历史保护及组件接线 |
| editor-lab/.../EditorLabActivity.java、ScratchEdits.java | 工具栏/重置/切换接适配器，共享规则与生命周期 |
| SharedTextEditsTest.kt、SoraBufferTest.java（新增） | 6项默认回归、22项真实Sora核心回归 |
| README、editor-lab/README、DEVELOPMENT_PROGRESS、RESUME、本报告 | 最新构建、边界与下一批 |

## 实际验证

默认 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline`：**48s，64任务19执行/45未变化**。

- **270项中269通过、0失败、1既有Windows符号链接跳过**；原264项及状态保留，新增6项全过。覆盖共享与原生产恢复规则的一致性、跨缝隙边界、不配对替换、既有异常字符原样保留、无正文复制、无变化分类和预算。
- Python源码13/13；实际APK里的runner.pyc以匹配Python3.13主机执行13/13；实际Kotlin生成模板Python7/7、主机g++ C++17 7/7。这不是Android运行时或Wandbox新请求。
- Lint0错误/11既有警告：SetTextI18n8、OldTargetApi/ChromeOsAbiSupport/DataExtractionRules各1，与上批相同。

实验 `:editor-lab:testDebugUnitTest :editor-lab:lintDebug :editor-lab:assembleDebug -Pcodepocket.editorLab=true --offline`：**24s，49任务20执行/29未变化**。

- **41/41**，原19项名称/状态保留，新增22项实际使用Sora Content/Cursor/UndoManager。覆盖真实替换/选区/单步撤销/重做、直接输入版本、同文不同实例、过期/组合/关闭门控、无变化与纯选区的历史、超限、UTF-16/CRLF缝隙、新文档历史隔离和原样加载。
- 不使用View mocks或模拟运行结果；没有在主机实例化Android CodeEditor View。选区回调测试操作真实Content.Cursor；设备版调用CodeEditor.setSelectionRegion及undo/redo，这一层仍待实机。
- Lint0错误/6既有警告：OldTargetApi、GradleDependency、DataExtractionRules、MissingApplicationIcon各1，SetTextI18n2。无Kotlin元数据不兼容日志。

本轮两轮验证均首轮成功，没有通过删测试或屏蔽检查处理失败。两个APK均通过ZIP CRC/v2 Debug签名/16KB ZIP对齐、包名/API24/target34与关键Dex类定义/复制哈希检查；默认arm64-v8a，不含Sora/实验类；实验无Python/native.so或申请权限。默认旧类全部保留；实验仅减少2个生成lambda名称，稳定类未删。

runtime graph仍为默认Kotlin1.9.25/无Sora，实验Sora0.23.7/Kotlin2.1.21。本批不改任何Gradle/Manifest/Actions，无新依赖/权限/存储迁移。默认CodeEditor输入事件、文件/SAF/草稿/会话/运行/Python源码、旧测试、三份需求原文保持输入哈希；旧8项APK/实验材料包SHA256保持。保留.tools和设备备份，仅本地提交，无push/发布或远端Actions。

## 产物与复现

[默认IDE APK](../dist/CodePocket-editor-sora-transactions-debug.apk)：**22,106,015字节**，SHA256 `cf5064df34b52cd1f841c714448c598f186142f0796f48291b2b61af009199a2`。

[Sora事务实验APK](../dist/CodePocket-Sora-transactions-lab-debug.apk)：**1,668,943字节**，SHA256 `73e17c28ed44dd920fb511128b5fb5a5d4f6f8783e27abe58a04a2046a3e9aee`。

证据 `verification/editor-sora-transactions-2026-10-18/`：results.json、两份verify脚本/构建日志/JUnit/Lint、runtime-dependencies、APK CRC/字节码/签名/对齐、输入哈希与原文备份、精确Sora API原文、阶段补丁/提交记录。Windows可用PowerShell7直接运行verify-native.ps1与verify-lab.ps1；使用现有.tools SDK/JDK/Gradle和Python3.13离线环境。历史材料包不含新适配源码，不冒充本批源码包；新源码在当前仓库。完整上游对应源码/重链接交付核验仍待，没有正式发布。

## 接续与未验证

下一批固定Sora语言包/语法装饰，再接完整生产Port/导航、现有候选/片段/参数与SAF项目会话，之后评估默认迁移。P1完整动画/手势/设置帮助许可/真实Python流式后端、P2语义与本地/多文件C++、P3原需求保留，P0问题优先。

实机统一后置：Android/模拟器、实际CodeEditor View/InputConnection、组合输入时序、系统历史与IME通知、焦点/触屏/蓝牙键鼠/定位/RTL/折行/滚动/帧率、手机/平板/鸿蒙卓易通未验收。本轮没有新Wandbox或远端Actions证据；主机核心通过和APK编译不能证明设备兼容或60FPS。实验原有草稿仅旋转保留，进程死亡不保存，项目草稿机制没有迁入。
