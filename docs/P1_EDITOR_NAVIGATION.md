# P1-C 生产选区、视口与错误导航

2026-10-17，基线 `241e0d1bc69ac2d277ce74933b6b804a36141041`。接续现有生产编辑事务与原需求，默认仍使用原生编辑器；Sora隔离原型保留。实机按用户要求统一后置。

## 已接入的生产路径

新增 `NavigatingEditorPort`，由现有 `NativeEditorPort` 实际实现。选区请求持有原文档快照，在落地前核对文件key、revision、文本、原选区和IME组合状态，并复用UTF-16代理对/CRLF边界校验。查找、项目搜索、跳行、片段字段、会话恢复与错误定位已实际调用该接口。

新增轻量 `EditorViewportSnapshot` 和生产实际使用的 `EditorViewportQueue`。面板/侧栏调宽、最大化与还原、切换标签、IME布局后的滚动恢复，先捕获文档/版本/选区/交互戳，再在原生pre-draw阶段执行；每个回调只消费一次，后来的显露光标请求优先。执行前再次校验，拒绝已修改、已切文件/工作区、已改变选区、组合输入、后续用户交互、视图不可见或已销毁的请求。

视口捕获不复制正文，避免面板拖动每次复制20万字符。恢复坐标由原生滚动范围约束；允许布局本身改变当前滚动位置，不把它误判为用户操作。触摸down/move、鼠标滚轮、实体按键与上下文操作使待恢复请求失效；原生事件处理继续委托给EditText。无障碍点击同样经performClick委托，不自行合成点击。恢复视口本身不抢stdin焦点；明确的展开/错误定位等入口才请求编辑器焦点。

保存选区可能来自外部修改前的旧偏移；恢复时先夹到正文范围，再避免拆分emoji/CRLF，保留正反选区方向，并更新会话保存值。原生输入、系统历史与独立stdin处理保留。

## 确定问题与修复

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| 迟到滚动覆盖用户新位置 | Activity.post闭包未统一追踪后续触摸、滚动和选区交互 | 生产队列使用交互戳、版本、选区和一次性消费；暂停/关闭取消 |
| 面板/标签恢复受布局时序影响 | 普通post不保证新布局已经完成，旧坐标可能提前被原生限制 | 在pre-draw落地并做可见性与原生范围校验 |
| 旧选区落在emoji或CRLF内部 | 会话偏移只按长度clamp | 归一化完整字符/换行边界，保留选择方向 |
| 错误行超出范围仍跳至末尾 | 逐行循环找不到下一换行便break | 严格lineOffset返回拒绝，空末行按真实内容处理 |
| 编辑源码后仍跳旧错误行 | 运行结果原先只有路径/行号，没有入口内容证明 | RunSession捕获入口稳定key和实际代码SHA256，返回结果时附加；核对后定位 |
| 搜索/错误回调跨工作区 | 文件打开异步回调缺少统一的来源复查 | 前后核对工作区对象/运行结果和当前文档，再分派选区 |

运行引擎仅增加结果的可选来源字段，RunSession在已接受运行时记录并在结束时附回；Python服务IPC、实际执行算法、Wandbox上传内容和网络/超时控制保留。不是模拟运行或新增云请求。

错误文件仍经过Workspace边界与真实文件检查；SAF私有运行快照路径先验证canonical根前缀，再映射回当前工作区。相邻根、目录、未映射与越界路径拒绝。

**入口文件**同时核对key与源码摘要，内容变化拒绝旧定位；**导入模块**暂未记录运行版本，允许并明确提示“仅按行定位”。缺少/仅部分来源证明也不显示已验证版本。仍只定位首个识别出的行错误，未实现语义诊断列表、波浪线或完整多文件诊断。

## 文件改动

| 文件 | 职责 |
| --- | --- |
| `editor/EditorNavigation.kt`（新增） | 选区/轻量视口契约、UTF-16恢复与可测试的生产调度队列 |
| `editor/EditorDiagnostics.kt`（新增） | 行错误状态、入口来源校验、安全运行路径映射 |
| `editor/NativeEditorPort.kt` | 原生选区/布局后恢复、交互与selection监听、生命周期清理 |
| `CodeEditor.kt` | 原生触摸/键鼠/无障碍事件委托及滚动范围约束 |
| `MainActivity.kt` | 搜索/跳行/片段、面板/会话/IME与错误导航集成 |
| `SearchDialog.kt`、`editor/EditorTransactions.kt` | 查询选区返回接受结果、共用边界校验 |
| `RunSession.kt`、`ExecutionEngines.kt` | 入口文件身份与实际源码摘要附回真实运行结果 |
| `res/values/strings.xml` | 过期/超范围/内容变化和按行降级中文反馈 |
| `EditorNavigationTest.kt`、`EditorDiagnosticsTest.kt`（新增） | 18项导航与12项错误/路径主机回归 |
| `README.md`、`DEVELOPMENT_PROGRESS.md`、`RESUME.md`、本报告 | 当前能力、真实证据与接续边界 |

## 实际验证

最终 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --stacktrace --no-daemon --max-workers=4`：**BUILD SUCCESSFUL in 48s**；62 actionable tasks: 18 executed, 44 up-to-date。

- Kotlin **226项：225通过、0失败、1 Windows符号链接用例跳过**。新增30全部通过，原196项及通过/跳过状态逐项保留。
- 导航18项覆盖实际生产队列的延后执行、后请求优先、跨文件/工作区、版本/选区/交互失效、布局原生clamp、组合/隐藏/关闭、负坐标与回调重复消费；选区使用真实EditorSearch与UTF-16/CRLF边界规则。
- 错误12项覆盖实际内容摘要/文件身份、内容变化拒绝、按行降级/部分证明、超范围与末尾空行、UTF-16/CRLF、组合与过期来源，以及真实临时Workspace文件的内部/SAF映射和目录越界拒绝。
- Python源码 **13/13**；实际APK的 `runner.pyc` 在匹配Python3.13主机执行 **13/13**。不是Android运行时验收。
- Kotlin测试实际生成模板：Python **7/7**，主机 `g++ -std=c++17` **7/7**；不是Android本地C++或本轮Wandbox执行。
- Lint **0错误、11既有警告**：SetTextI18n8，OldTargetApi/ChromeOsAbiSupport/DataExtractionRules各1，和上批一致。最终在dispatchTouchEvent观察输入并补齐performClick委托后重跑，没有新增ClickableViewAccessibility警告；未抑制Lint或合成重复点击，编译仍有既有弃用API警告。
- APK/嵌套Python归档CRC、arm64-v8a/API24/target34、Debug v2签名、16KB ZIP对齐通过；dist与构建产物逐字节SHA256一致。1464个Dex类定义，新增导航/错误类实际打包；旧具名类定义保留。减少3个D8生成的MainActivity回调类（编号57/58/59），新原生队列回调类单独记录；不能以生成编号判断业务类丢失。默认APK不含Sora/实验模块。
- 原Gradle/Manifest/Actions、Python源码/服务、Workspace/SAF/草稿、补全/片段/参数核心、配置和三份需求保持输入哈希。旧事务APK、默认原生APK、Sora实验APK与材料包SHA256保持；未增加依赖/权限或迁移存储。

保留首轮测试编译失败日志（跨模块可空属性smart cast），修正局部变量后继续；首轮完整回归中两项Windows夹具使用renameTo覆盖已有文件失败，改用仓库既有Files.move原子替换方式后通过。随后产物审计发现新增onTouchEvent监听的无障碍Lint警告，单独补performClick仍触发要求直接调用的静态规则；最终改在dispatchTouchEvent观察输入，完全交由EditText检测并触发点击，保留performClick委托，重新执行完整回归。中间成功构建及APK保留在 `round-02-before-accessibility` 和 `round-04-native-click-delegation`。Windows PowerShell5将Python正常stderr日志作为错误而中断的验证记录保留在 `round-03-host-shell`，改用本机PowerShell7完成验证；没有删除失败测试或把它们改为跳过。

[最新Debug APK](../dist/CodePocket-editor-navigation-debug.apk)：**22,103,110字节（21.08MiB）**。

SHA256：`32141df155167f58c2c7837c56ecb4c7a2f325510d42c3813c4633d050baca9f`。

证据：`verification/editor-navigation-2026-10-17/results.json`、`verify.ps1`、`audit_navigation.py`、最终/失败/中间日志、JUnit、Lint、APK检查报告、修改前备份、前后输入哈希、阶段补丁与实际生成模板。保留.tools、旧产物与设备备份；仅本地阶段提交，无push/发布/远端Actions。

## 接续与验证边界

下一批继续完整文档/会话生命周期与组件适配，然后接Sora语言、候选/片段/参数及SAF会话、评估默认迁移。生产导航批已经接入，但不等于完整EditorPort或Sora迁移完成。

原生pre-draw/滚动/焦点/组合输入/UndoManager、虚拟与蓝牙键盘、鼠标、候选/片段/SAF/保存联合行为、手机/平板/鸿蒙卓易通与FPS均待最终设备验收。主机生产队列模型和路径测试不能证明真实布局时序或60FPS；本轮没有安装设备/模拟器、截图、新Wandbox请求或远端Actions。

P1完整动画/手势、设置帮助许可页、真实Python流式会话仍待；P2 Jedi/clangd语义、本地/多文件C++、批量用例/格式化与P3 Git/主题等继续保留，P0缺陷优先。原需求未删减。
