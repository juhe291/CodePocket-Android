# P1 第五批：内置函数参数参考

2026-10-17。在上一批 `b688871` 基础上增量接入，继续使用原生 EditText、现有词法补全、片段与可调运行面板。实机统一在功能开发后验收。

## 本批能力

- Python 7 项：`print`、`input`、`len`、`range`、`enumerate`、`sorted`、`sum`。
- C++17 5 项：`std::sort`、`std::reverse`、`std::lower_bound`、`std::upper_bound`、`std::getline`。只识别明确列出的 std 限定名称；不解析 using、别名或成员类型。
- 在支持的调用括号内自动显示内置参考、高亮当前位置可映射的参数；识别普通括号/列表/字典/初始化列表嵌套、已闭合字符串和注释中的逗号，以及 Python 关键字参数。嵌套受支持调用优先，未知内部调用不冒用外层参考。
- 参考多种形式通过 ‹/› 触屏按钮切换，显示当前形式序号；这是用户选取文档参考，**没有类型推断或重载解析**。当前形式没有对应参数时不强行高亮，也不以此报告编译或调用错误。
- `Ctrl+Shift+Space` 或工具栏/IME 紧凑栏“参数”主动请求；设置提供独立的“自动显示内置函数参数参考”开关。关闭自动后仍可主动请求当前快照。配置从 version 2 默认迁移到 version 3，20 个设置字段保留原值。
- 原生非焦点弹窗不占用输入事件；Esc 或 × 关闭，同一快照不自动重开。Tab/Enter/方向键仍归补全、片段和原生编辑处理；中文组合及软键盘确认不被参数提示截获。
- 补全候选优先，参数窗口不与候选叠放。按光标、代码可见矩形、窗口和 IME 区域定位，滚动/布局更新位置；宽度不足 260 dp 或没有至少 144 dp 高度时收起，主动触发给出说明。
- 查询在独立单线程中执行，有文档/版本/选区/请求代次校验和取消；文本/光标、文件/页面、焦点、面板布局改变时关闭旧结果。只读分析，不执行用户代码、不联网、不改写源码、选区、stdin 或保存内容。
- 继续遵从 App/系统动画与省电策略；90 ms 淡入不参与文字输入或光标逻辑。实际帧率和焦点行为尚未实测。

## 数据来源及明确边界

Python 参考依据 [Python 3.13 内置函数文档](https://docs.python.org/3.13/library/functions.html)。C++ 依据固定的 C++17 N4659 草案：[sort](https://timsong-cpp.github.io/cppwp/n4659/alg.sort)、[reverse](https://timsong-cpp.github.io/cppwp/n4659/alg.reverse)、[lower_bound](https://timsong-cpp.github.io/cppwp/n4659/lower.bound)、[upper_bound](https://timsong-cpp.github.io/cppwp/n4659/upper.bound)、[getline](https://timsong-cpp.github.io/cppwp/n4659/string.io)。模型逐条记录来源 URL。

显示的是人工整理的参数名和短说明；C++ 未展开模板类型、执行策略及所有流/字符类型重载。Python 同名对象可能被用户重绑定，当前不做作用域分析，弹窗明确说明这一点。不能将本批称为 IntelliSense、语义补全或签名解析完成。

本批最多处理 45000 字符、128 层括号。语法不平衡、未知调用、成员调用、Python lambda/生成式/当前字段星号展开、调用中的 C++ 尖括号等有歧义时保守不提示。现有词法器不能完整处理 Python 格式化字符串和 C++ 原始字符串，因此在光标前检测到这些形式时本批直接降级，不尝试解析字符串内部。复杂作用域、宏、模板或不完整代码仍需 P2 成熟语言服务。

## 修改文件

| 文件 | 修改 |
| --- | --- |
| `SignatureHelp.kt` | 带来源的 12 项参考、参数映射、有限词法调用上下文及快照契约 |
| `SignaturePopup.kt` | 只读原生弹窗、参数着色、参考切换、可访问说明及光标/IME 几何 |
| `MainActivity.kt` | 后台请求、过期/焦点保护、按键/触屏入口、生命周期清理和候选优先 |
| `AppSettings.kt` / `SettingsDialog.kt` | version 3 独立自动参数提示设置，旧配置默认兼容 |
| `strings.xml` | 参考类型、来源/限制、快捷键及空间不足说明 |
| `SignatureHelpTest.kt` | 16 项新增回归 |
| README、进度、接续、计划、设备清单 | 实际功能、测试结果与剩余工作 |

没有新增应用依赖或权限。原始需求、Gradle、Manifest、CodeEditor、原片段/补全实现、runner.py、文件/SAF/保存实现与 GitHub Actions 保持原哈希；原 Actions 的 Kotlin 测试步骤自动包含新增测试，Debug 下载步骤保留。

## 真实构建与测试

第一轮构建 `BUILD FAILED in 26s`：新弹窗构造参数 background/foreground 在 View.apply 内被同名 View 属性遮蔽，导致颜色参数类型错误。已改为显式颜色名称；同时去掉每个调用复制整段前缀的实现，加入取消/大缓冲区回归。没有隐去失败日志。

| 检查 | 最终结果 |
| --- | --- |
| `testDebugUnitTest lintDebug assembleDebug` | `BUILD SUCCESSFUL in 1m 3s` |
| Kotlin | 99 项：98 通过、1 跳过、0 失败/错误；新增参数参考 16 项全部通过 |
| 新增测试覆盖 | 语言/来源、位置/可变参数、关键字、参考形式、嵌套/未知调用、忽略区、成员/声明排除、复杂语法降级、边界/深度、UTF-16/CRLF、版本、文字高亮范围、设置迁移、取消与 35000 字符前缀、Esc 门控 |
| Python 执行器 | 源码 13/13、实际 APK runner.pyc 13/13 |
| 既有模板回归 | 实际 Kotlin 展开 Python 7/7，主机 C++17 编译执行 7/7 |
| Lint | 0 错误、13 警告（10 SetTextI18n；OldTargetApi、ChromeOsAbiSupport、DataExtractionRules 各 1） |
| APK | ZIP CRC、6 个 Python 内包、arm64、v2 签名、16KB 对齐、badging 及新旧关键类打包通过 |
| 设备 / 在线服务 / 远端 Actions | 本批没有执行，不作成功声明 |

唯一跳过项为 Windows 无符号链接创建能力的既有文件边界测试；主机测试不等于实机 IME/光标/鼠标、性能或卓易通兼容通过。已有模板编译也不能被当成新参数资料在 Android 上进行语义解析的证据，App 中 C++ 仍通过 Wandbox 执行。

## APK 与证据

[本批 Debug APK](../dist/CodePocket-P1-signatures-debug.apk)，21,835,664 字节（20.82 MiB），API 24+ / arm64-v8a，versionName 0.2.0 / versionCode 2。

SHA256：`c3fd49ba556eb76e259d8c71f53333b76f9b0a77623ffbff747a69a1529db638`。

证据目录 `verification/p1-signatures-2026-10-17/`：首次失败和最终日志、完整 JUnit/Lint、Python 源码/字节码、真实模板源文件与执行结果、签名/对齐/badging、源文件和保护基线哈希 `results.json`。旧 APK、历史证据和设备备份保留。本批只本地提交，没有 push、发布或上传。

## 接续

下一批继续 P1 状态栏与编程快捷键栏的完善，再推进高亮、文件树宽度、项目搜索、完整动画和真实程序终端。P1 未全部完成，P2 的成熟语言服务/深度语义、多文件和本地 C++ 以及 P3 仍待开发。发现 P0 缺陷继续优先回修，设备验收统一后置。
