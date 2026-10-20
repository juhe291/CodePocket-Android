# 控制键闪退、查找替换与编辑反馈（2026-10-20）

## 交付

- [Debug APK](../dist/CodePocket-editor-feedback-2026-10-20-debug.apk)：Android 7.0+、arm64-v8a、同原 Debug 签名，可覆盖安装。
- [手机/平板、深浅主题原生主机预览](editor-feedback-preview/README.md)。示例代码含故意不匹配的括号，仅用于观察绘制，不是设备编译结果。
- 实际大小 **23,020,188 字节（约 21.95 MiB）**。
- SHA256：`c3499b07d0c21c4c7391afc0b8376bbf1764e486e80aeaea12136d009c4e0e2f`。
- 包名/版本保持 `com.codepocket.ide` / `2` / `0.2.0`，minSdk24、targetSdk34、compileSdk35。

## 已修复和新增

### 终端控制键

用户确认是点击“控制键”展开时闪退。实际原生 View 点击回归在修复前两个场景均触发同一 NPE：`String.contentEquals` 收到空 CharSequence。展开时遍历 dock 子项，其中空白占位 View 没有 contentDescription。现改为 `TextUtils.equals` 空值安全比较。

新增回归实际展开/收起 12 次，并逐个点击 Ctrl+C、Ctrl+D、Tab、Esc 和四个方向键；覆盖无进程和挂接真实 TerminalEmulator 的关闭会话。后者是主机 PID0 夹具，不宣称在 Android PTY 中执行了这些命令。真实 PTY、启动握手、键盘按钮、会话关闭确认和单一终端架构保留。

### 查找与替换

原纵向按钮/复选框大对话框改为顶部紧凑面板。输入约140ms后自动定位、显示“当前 / 总数”；上一项/下一项、Enter/Shift+Enter、Aa、全字匹配在同一处。查找中可直接展开替换，点击“替换”处理当前选中或下一个匹配，无需先手动查找并选中；“全部”一次提交、仍可使用原撤销功能。

字面量查找不解释正则。后台扫描、不可变文本快照、文档/修订/选区/条件检查、组合输入保护及20万字符替换上限继续使用；不匹配或失败保留输入，关闭不修改源码。

### 配色与红色波浪线

- `()`、`[]`、`{}` 共用嵌套栈，层级循环使用六种柔和颜色，匹配对同色。C++ 常见容器、已声明类型和转换表达式的模板 `< >` 采用保守识别，普通比较和位移不当成括号。
- 跳过字符串、注释；C++ 原始字符串和多行宏不参与基础括号诊断。Python f-string 作为字符串保留原有高亮，不在其中做猜测性的变量或括号分析。
- 变量、可识别参数、成员属性、常量命名、已声明类型分别配色。这是词法角色提示：同名参数的引用采用名称提示，未实现作用域/类型推断；不能等同于 LSP 语义高亮。
- 错误在可见代码区域绘制红色波浪线。点击对应位置打开只读浮层，显示来源和原消息，支持复制；不抢编辑器焦点/键盘。文字、选区、滚动、文件切换、失焦和生命周期变化会清理浮层或过时标记。
- Python 通过 Chaquopy 后台调用 CPython3.13 `compile` 做真实语法检查，包含缩进/语法错误；不执行用户代码，不导入用户模块，不读写用户文件，不复用终端运行过程的 stdin/stdout。
- C++ 编辑时只做本地基础括号检查。完整编译/运行错误在用户真实运行后映射到编辑器；必须匹配工作区、文件身份及源码摘要，旧结果、导入文件或网络错误不会错误地画到当前源码。不为诊断自动上传代码，原 Wandbox 发送提醒保留。
- 分析限4.5万字符、着色限6000 span、错误最多40项；绘制只处理可见行和窗口宽度，避免超长行生成庞大波浪路径。代码修改立即清理旧标记，迟到结果继续校验文档与选区，中文组合输入时不应用分析。
- Python 解释器桥不可用时显示“Python 语法检查暂不可用”；超长文件明确暂停。未实现未定义变量、类型错误、完整 C++ 即时编译、跨文件分析或 VS Code 的完整 Problems/LSP 功能，原 P2/P3 继续保留。

## 实际验证

| 检查 | 本轮结果 |
| --- | --- |
| 修复前控制键点击复现 | 2项均在实际点击展开时 NPE，日志保留 |
| App全量回归 | **517项：516通过、1原有跳过**；原496项状态保持，新增21项通过 |
| 新增21项 | 控制键2；括号/变量/缓存/上限/错误来源11；原生查找、浮层复制、手机/平板深浅主题绘制8 |
| 终端核心 | 146项历史通过结果，Gradle本轮UP-TO-DATE，未声称重跑 |
| Python源码与资源 | **44通过**，含10项新语法诊断、原29项真实运行及5项资源检查 |
| 实际APK中的Python字节码 | **39通过**：原runner29、新diagnostics10；CPython3.13主机执行，非Android执行 |
| Shell/模板 | 主机真实Shell启动4；生成Python模板7、C++17模板7，均通过 |
| Lint | **0错误 / 11原有警告**，无新增警告；SetTextI18n8，其他3类各1 |
| Debug APK | 原仓库离线构建成功；最终Gradle **1m29s** |
| 安装包 | CRC、v2签名、原证书、16KiB ZIP对齐、包信息、实际DEX类和编译vector检查通过 |
| 保护核对 | 204份非目标源码/配置保持、41个旧APK摘要保持、27份其他native/Python归档完全相同 |
| 原Python模块 | runner实际字节码主体完全相同；Chaquopy重打包仅更新pyc头的mtime；新增diagnostics.pyc |
| 图标 | 实际APK46个vector、137条path逐项核对，未引入新的矢量转换 |

证据位于 `verification/editor-feedback-2026-10-20/`：`before.json`、修复前后日志/JUnit、`build-repository.log`、`python-source.log`、`apk-inspection.json`、`signature.txt`、`certificates.txt`、`alignment.txt`、`results.json` 和主机PNG。阶段文件含本机绝对路径，不代替源码或设备证据。

## 修改文件

| 文件 | 作用 |
| --- | --- |
| `terminal/shell/ShellTerminalPane.java` | 控制键空值修复 |
| `SearchDialog.kt` / `EditorSearch.kt` / `res/values/strings.xml` | 紧凑即时查找、一步替换、计数和文本资源 |
| `editor/EditorDecorations.kt` | 括号深度与词法变量角色、基础括号问题 |
| `editor/EditorPort.kt` / `editor/NativeEditorPort.kt` | 不可变分析结果、后台缓存、额外配色和受保护应用 |
| `editor/PythonSyntaxChecker.kt` / `python/diagnostics.py` | 真实Python compile-only语法检查 |
| `editor/InlineRunDiagnostics.kt` | 实际运行错误与当前源码身份校验 |
| `editor/EditorIssueLayer.kt` / `CodeEditor.kt` | 可见区域红色波浪绘制、编辑时清除旧标记 |
| `DiagnosticPopup.kt` / `MainActivity.kt` | 可复制提示、状态说明、焦点和生命周期接入 |
| `EditorDecorationTest.kt` / `EditorFeedbackUiTest.kt` / `terminal/shell/TerminalControlsClickTest.kt` | 21项新增回归 |
| `tests/test_diagnostics.py` / `scripts/verify_apk.py` | 源码及实际APK字节码测试 |
| README及交接/计划/本报告、`editor-feedback-preview/` | 使用入口、已验证范围、后续需求与预览 |

Java/Kotlin路径均相对于 `app/src/main/java/com/codepocket/ide/`，测试路径相对于 `app/src/test/java/com/codepocket/ide/`。Gradle、工作流、文件保存、SAF、runner源码、运行引擎和终端底层未改。

## 未验证与后续

本轮没有连接设备，没有执行远端Actions，也未push/发布。Android主进程Chaquopy语法检查的首次加载、运行时输入法/实体键鼠、手机/平板真实触摸、查找与软键盘、大字体、卓易通、耗电及60FPS/高刷均需按此前安排统一实机验收。主机原生解析/布局/像素检查不代表上述项目通过。

使用本页最新APK。此前terminal-polish APK存在展开控制键闪退，历史APK仅保留用于追踪；不要将旧版主机通过记录当成这一缺陷已被覆盖。
