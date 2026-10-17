# P1 第四批：内置代码片段与占位符

2026-10-17。在现有原生编辑器、词法补全和运行面板基础上增量接入，没有替换项目或编辑器组件。前一批提交为 `57eb82b`。按用户安排，本批只做主机验证和 APK 构建，实机统一后置。

## 已实现

- C++：`for`、`if`、`while`、`func`、`cout`、`main`；Python：`for`、`if`、`while`、`def`、`print`、`main`，各 6 个模板。候选标注 `{}` /“内置代码片段”，与关键字、文件名称候选分别显示；仍为基础词法补全。
- 输入名称前缀后选择模板，或在空白代码行通过全工具栏/IME 紧凑栏“片段”插入。模板只替换独立行上的标识符；表达式、成员位置、注释、字符串、选区及超过 45000 字符文件不展开。
- 插入遵循现有空格/Tab、2/4/8 宽度、当前行缩进和 CRLF/LF；选中首个占位符。以 UTF-16 范围追踪字段替换、删除、自动括号和普通补全，后续位置随实际长度调整。
- Tab/Shift+Tab 跳转字段，最后一次 Tab 到模板末尾；Esc 结束导航，保留文本。“片段”菜单提供触屏上一项/下一项/结束入口，提示当前字段。
- 按键优先级：有效可见补全候选 → 占位符跳转 → 缩进；Shift+Tab 反向跳转。字段内自动候选暂停，Ctrl+Space 仍可显式请求普通候选；不启动嵌套片段。Enter 保留既有配置。组合输入、软键盘事件及 Ctrl/Alt/Meta 修饰不被片段导航截获。
- 编辑离开当前字段、跨文件/工作区、stdin 焦点、查找/设置、后台和撤销快捷键会结束临时导航。保存与恢复沿用既有文本、选区及草稿路径；片段导航不跨页面或重启持久化。
- 片段文本通过一次范围替换及原生 batchEdit 插入；本批不替换 InputConnection 或重写组合区。实际系统撤销栈和输入法联动仍须实机验证。
- 修正触屏缩进/括号入口的焦点和组合输入保护，避免 stdin 获得焦点时修改源码。Esc 结束后抑制同位置候选重弹，字段跳转刷新括号提示。

## 修改文件

| 文件 | 作用 |
| --- | --- |
| `CodeSnippets.kt` | 分语言结构化模板、上下文/容量校验、单次展开与 UTF-16 占位符 |
| `SnippetSession.kt` | 文档/版本绑定、字段范围追踪、导航与按键门控 |
| `CompletionModel.kt` | 独立片段类型、真实插入结果、普通候选兼容 |
| `MainActivity.kt` | TextWatcher 增量追踪、键鼠/触屏入口、焦点与生命周期清理 |
| `strings.xml` | 片段帮助、进度及错误说明 |
| `SnippetTest.kt` | 12 项新增契约测试，并导出真实 Kotlin 模板输出 |
| `scripts/verify_snippets.py` | 读取 JUnit 实际模板，调用真实 Python 执行器和主机 C++17 编译器 |
| `.github/workflows/build-apk.yml` | 保留 Debug APK 下载，增加模板主机回归与源样例报告 |
| README、进度、接续、计划、设备清单 | 更新事实、顺序与待验收范围；原需求保持原哈希 |

Gradle、Manifest、`CodeEditor.kt`、文件/SAF/保存实现和 `runner.py` 均未修改；没有新增应用依赖或权限。

## 真实验证结果

| 检查 | 结果 |
| --- | --- |
| Gradle `testDebugUnitTest lintDebug assembleDebug` | `BUILD SUCCESSFUL in 55s` |
| Kotlin | 83 项：82 通过、1 跳过、0 失败/错误；新增片段 12 项通过 |
| 新增契约范围 | 身份/版本、上下文拒绝、容量、CRLF/emoji/缩进、空字段、正反导航、字段增减/删除、自动括号、普通补全、组合/软键盘门控 |
| 生成模板执行 | 7 个 Python + 7 个 C++17 实际执行成功，0 失败；包含 CRLF/Tab 嵌套和 Unicode 偏移变体 |
| Python 执行器 | 源码 13/13；实际 APK `runner.pyc` 13/13 |
| Lint | 0 错误、13 警告（10 SetTextI18n；OldTargetApi、ChromeOsAbiSupport、DataExtractionRules 各 1） |
| APK | ZIP CRC、6 个 Python 内包、arm64、v2 签名、16KB 对齐、badging 及新旧关键类打包通过 |
| GitHub Actions | 工作流已更新；没有远端运行或 push，不能称远端构建已通过 |
| 设备/性能 | 本批未连接设备，没有实机、60 FPS 或卓易通兼容结论 |

唯一跳过项仍为 Windows 无符号链接创建能力的既有文件边界测试，不是片段测试。主机使用 Python 3.13.9 与 `g++.exe (x86_64-win32-seh-rev0, Built by MinGW-Builds project) 15.2.0`。

模板不是手工复写到测试脚本：Kotlin 单测导出实际展开字符串，脚本为其补齐合法驱动、头文件、`values` 和调用后真实执行。C++ 范围 for 的空默认体产生 1 条未使用变量警告，编译和执行成功。**主机 g++ 只用于回归，不打包到 APK；App 中 C++ 仍走 Wandbox。** 本批没有新增 Wandbox 在线调用，也不能从这些主机结果推断 Android 原生执行已通过。

## APK 与证据

[本批 Debug APK](../dist/CodePocket-P1-snippets-debug.apk)：21,817,451 字节，20.81 MiB，API 24+ / arm64-v8a，versionName 0.2.0 / versionCode 2。

SHA256：`c285ced69595ba4f3537bcecadb20e04f7df3a805433da84f655ebe9e839ed1f`。

`verification/p1-snippets-2026-10-17/` 保存最终构建日志、12 项新增 JUnit 及全部回归、Lint、真实模板源文件和执行结果、Python 源码/字节码报告、APK 签名/对齐/badging、`results.json` 源文件哈希及保护基线。第一轮日志单独留存，最终证据以无 first-pass 后缀的文件为准。旧 APK 和设备备份保留。

## 能力边界与接续

- 只有内置独立字段模板，没有自定义 VS Code 片段语法、联动镜像、嵌套会话、变换或 LSP 片段。没有作用域/类型分析；函数模板仍须在合法位置插入，C++ cout 需 iostream、范围 for 需真实可迭代对象。
- 光标/IME/原生撤销栈、触屏对话框焦点、实体 Tab 与 popup、保存/SAF 联合行为需要手机/平板/卓易通最终验收；组合样式的纯模型测试不等于实测中文输入法。
- P1 尚未全部完成。下一批继续可靠来源的内置函数调用参数提示，再推进状态/符号栏、高亮、文件树调宽、项目搜索、完整动画和真实程序终端。P2 的 clangd/Jedi、成员语义、多文件/本地 C++ 与 P3 仍未开始。
- 输入、独立 stdin 和文件安全优先；发现 P0 问题随时先修。继续分批构建/留存，不重建项目，不自动上传或发布。
