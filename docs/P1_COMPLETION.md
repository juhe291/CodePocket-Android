# P1 第二批：光标补全列表与按键（2026-10-17）

> 2026-10-20：默认候选方向键已改为“候选可见时导航”，包含旧EXPLICIT迁移及Ctrl加减代码字号；见[修改与验证](KEYBOARD_NAVIGATION_ZOOM_2026-10-20.md)。下面早期默认策略属于历史记录，完整语义与设备验收等原需求继续保留。

本批继续现有 Kotlin/View 编辑器，**没有重新创建项目、替换编辑器底层或新增第三方依赖**。上一批设置、查找与基础过渡已留存本地提交 `8cff187`。本批仍属于 P1，P2/P3 未开始。按用户要求，手机、平板和鸿蒙卓易通的联合实机验收统一后置。

## 本批实际改动

| 文件 | 已接入的行为 |
| --- | --- |
| `CompletionModel.kt` | 请求携带工作区/文件身份、编辑版本、原文和 UTF-16 选区；词法候选模型、类型标识、替换范围、按键决策、弹窗几何计算。只提供关键字、常用名称及当前文件出现过的名称，未推断作用域/类型。 |
| `CompletionPopup.kt` | 使用原生非聚焦 PopupWindow + ListView；可滚动、点击插入、类别字形和简短说明，候选列表仍明确标记“基础词法补全”。依据原生 Layout/滚动和实际可见窗口计算光标位置；优先下方、空间不足向上翻转，仍不足时收起；空间小先隐藏说明区。 |
| `MainActivity.kt` | 词法扫描在单线程后台执行，取消/请求代次和返回时校验共同拒绝旧结果；编辑、选区改变、切文件/工作区、失焦、页面退出时立即收起旧候选；滚动和布局变化重新定位。Ctrl+Space 及工具栏主动触发，IME 紧凑工具栏也有补全按钮。 |
| `AppSettings.kt`、`SettingsDialog.kt` | 配置升为 version 2，兼容旧设置；新增 Tab 接受、Enter 接受和上下键导航模式 3 项，累计 19 项真实设置。 |
| `EditorFeatures.kt` | 复用原语言词表并给词法候选分类，继续保留现有高亮、括号和兼容入口；没有引入类型分析器。 |
| `strings.xml` | 补全类别/无结果/空间不足/成员待实现等提示及使用说明。 |
| `CompletionTest.kt` | 新增 8 个有实际边界意义的测试，见下文；原有文件、保存、会话、运行与编辑规则回归继续执行。 |

修正原候选代码的两处问题：接受的是当前选中项，避免总用第一项；在单词中间补全时替换完整标识符，避免 `pri|nt` 变成 `printnt`。候选在代码超过 20 万字符上限时拒绝接受，不依靠 LengthFilter 截断后假装完整插入。一次接受仅调用一次文本替换并使用原生 batch edit；撤销的实机体验仍待验收。

扫描时排除注释/字符串等文本中的名称；超过 45000 字符不扫描文件标识符、不自动触发，Ctrl+Space 可主动使用常用名称降级。大文件降级不分析上下文，不能当作完整词法/语义分析。

### 输入与按键

- Tab 默认接受当前候选，否则按现有配置缩进；可关闭接受。Shift+Tab 保持反缩进。
- Enter 默认换行；打开接受选项后，也须先用上下方向键明确选中候选才接受。软键盘标志的按键及组合输入交给原生输入链处理，触屏直接点击候选。
- 方向键默认仅在 Ctrl+Space/“补全”主动触发后导航；可设为候选可见即导航或始终移动代码光标。默认自动候选不抢上下移动光标。
- Esc 收起；记录关闭时的请求上下文，防止定时刷新把同一候选立即重新打开。后续编辑、移动光标或主动请求可以再次触发。
- 输入焦点在 stdin 时不请求代码补全、不切回代码，Ctrl+S/N/O/B、F5 和 Ctrl+Enter 保留。设置/查找等对话框内的按键交给对话框处理。
- 不改写 IME 组合区。原生输入法、选区和鼠标基础能力保留；这些是代码路径与主机规则检查结果，尚不能宣称设备交互通过。

补全窗口可用时有 90 ms 轻淡入，尊重 App 开关、系统动画设置和省电模式；IME 可见时跳过动效。关闭即时生效，未加入淡出、平滑光标或主题逐帧颜色过渡。

## 构建与真实验证

- 最终完整构建：**BUILD SUCCESSFUL in 53s**。第一轮曾因颜色参数 `foreground` 与 Android View 同名属性冲突编译失败，已改名并复建；失败日志保留为 `build-01-compile-error.log`。
- Kotlin/JVM：**61 项，60 通过、1 Windows 符号链接用例跳过、0 失败/错误**。新增测试覆盖 UTF-16/emoji/CRLF 与词中替换、第二候选接受、文件/版本/原文/光标/选区过期保护、容量限制、两语言隔离、成员位置拒绝伪语义、文字区排除、大文件降级、按键配置/软键盘放行、空间/翻转/窗口偏移和设置迁移。
- Python 源码 **13/13** 通过；本次实际 APK 内 `runner.pyc` 主机回归 **13/13** 通过。没有用模拟输出替代执行。
- Lint **0 错误、13 警告**（10 SetTextI18n，另 OldTargetApi/ChromeOsAbiSupport/DataExtractionRules 各 1），比上一批少 1；遗留警告未全部消除。
- APK：ZIP CRC、6 个 Python 归档 CRC、v2 签名、包信息、arm64 ABI 与 16 KB ZIP 对齐检查通过；另检查实际 DEX 中含本批补全类。
- 未启动 ADB、安装应用或采集帧率；未执行远端 GitHub Actions。本批未重新网络运行 Wandbox，运行模块和 Actions 文件保持原哈希。

[Debug APK](../dist/CodePocket-P1-completion-debug.apk)：**21,786,370 字节（20.78 MiB）**；包名 com.codepocket.ide，0.2.0/code 2，API 24+/target 34，仅 arm64-v8a。旧 P0 和 P1 设置 APK 保留。

SHA256：`00ffbf0a5918e3e3960cfae4b2e9b46fa02f98a6880e2855058824d41c798024`。

证据：[verification/p1-completion-2026-10-17](../verification/p1-completion-2026-10-17/)，包含各轮构建、JUnit、Lint、源码和打包 Python 回归、签名/对齐/包信息、`results.json` 及本机重现脚本 `verify-p1-completion.ps1`。脚本使用现有 .tools 便携 JDK/SDK/Python、Gradle 离线缓存，不修改系统 Java。

## 技术依据与剩余范围

原生窗口的焦点/IME 和坐标设置参考 [Android PopupWindow 官方 API](https://developer.android.com/reference/android/widget/PopupWindow)，光标锚点使用 [TextView 原生 Layout 与 padding](https://developer.android.com/reference/android/widget/TextView)。API 29 的绝对屏幕坐标接口有版本守卫，API 24–28 做窗口偏移转换；真实分屏/卓易通坐标差异仍需实机验证。

只为 print、len、input 提供了少量 [Python 3.13 官方内置参考](https://docs.python.org/3.13/library/functions.html)，并提示局部同名对象可能不同。它们是固定的内置参考，**不是对用户代码分析得出的函数签名，也不是键入调用后持续跟随参数的签名帮助**。C++ 常用名称未猜测重载签名。

C++ `.`、`->`、`::` 和 Python `.` 成员位置当前不显示全局词法候选；主动请求时明确提示语义成员补全待 P2。尚无 clangd/Jedi/LSP、作用域推断或跨文件语义；不能称为完整 IntelliSense。

P1 仍待：内置片段及占位符、调用参数持续提示、弹窗说明可展开、完整动画及平滑光标、可拖动/最大化运行面板、图标/状态与高亮完善、项目搜索及真实流式/ANSI 程序终端。继续先做可调面板和片段，再推进余项；保持分批可编译、可回滚。

最终联合实机需覆盖：中文组合/确认、蓝牙键鼠、软/硬 Enter、候选展开后切标签与 stdin、长行/换行/emoji、滚动及 IME 避让、窄屏/横屏/分屏、系统减少动画、保存/恢复/SAF/停止回归。**主机测试不代表 UI 正确、60 FPS 达标或卓易通兼容通过。**
