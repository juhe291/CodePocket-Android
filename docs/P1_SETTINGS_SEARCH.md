# P1 第一批：设置、查找与基础过渡（2026-10-16）

> 历史批次报告。2026-10-17 已继续完成原生词法候选列表和按键配置，最新构建/能力见 [第二批报告](P1_COMPLETION.md)。本页测试和“横向候选行”描述只代表第一批时点。

依据用户最新指示“等所有的都弄完了再实机吧，继续”，**实机验收统一后置，开发不再以设备接入作为前置条件**。继续 P1 → P2 → P3 的顺序，分批执行主机测试、Lint、Debug APK 构建和产物检查。本报告只表示 P1 第一批完成，不表示全部需求或完整动画/IntelliSense 已完成。

## 本批已实现

| 功能 | 实际行为与入口 | 文件 |
| --- | --- | --- |
| 可保存的设置 | 顶栏齿轮和工具栏“设置”；16 项设置保存到独立 JSON，兼容原有深浅主题值，不清除项目/会话偏好；配置损坏或越界回退 | AppSettings.kt、SettingsDialog.kt、MainActivity.kt |
| 界面与语法主题 | 界面深色/浅色/跟随系统；词法配色可独立选择 Dark+/Light+ 风格，代码区底色与文字同步；仍保留原有高亮种类与 45000 字符限制 | AppSettings.kt、MainActivity.kt |
| 字号与显示 | 代码字号 10–28 sp、输出字号 10–24 sp、行号开关、自动换行；换行时按原生 Layout 的视觉行生成行号/续行空位，不将软换行当成源码新行 | MainActivity.kt、EditorSearch.kt |
| 可配置编辑 | 2/4/8 列缩进、空格或 Tab，自动缩进/括号引号补齐分别可关闭；Tab 显示通过原生 TabStopSpan 设置 | EditorActions.kt、MainActivity.kt |
| 输入与保存选项 | 基础词法补全自动触发开关，Ctrl+Space 仍可主动触发；符号栏开关、IME 展开时收起面板开关；停顿保存开关和延迟。关闭停顿保存后仍保留草稿、手动保存与离开页面保存，界面明确说明 | AppSettings.kt、EditorSessions.kt、MainActivity.kt |
| 当前文件查找替换 | Ctrl+F / Ctrl+H 与触屏入口；纯文本、区分大小写、全字匹配、上下循环查找、替换选中项和全部替换。替换前预检 20 万字符上限，美元/反斜杠按字面处理 | EditorSearch.kt、SearchDialog.kt |
| 后台查询保护 | 查询在后台执行；文档身份、编辑版本、原文、选区与查询条件过期时丢弃结果。关闭窗口或销毁 Activity 时关闭任务，避免迟到结果改写其他缓冲区 | EditorSearch.kt、SearchDialog.kt、MainActivity.kt |
| 跳转行 | Ctrl+G 与工具栏“跳转行”，按源码逻辑行计算；越界不跳转，正确处理 CRLF、Unicode 和末尾空行 | EditorSearch.kt、MainActivity.kt |
| 轻微工作区过渡 | 文件树展开、侧栏显示、标签切换和运行面板显示采用短淡入；动画开关、系统动画关闭/省电、IME 出现时跳过或中止。只改变装饰视图透明度，不延迟文本编辑和逻辑光标 | MotionPolicy、MainActivity.kt |
| 使用说明与窄屏标题 | 工具栏中文说明真实 Python/C++、预填 stdin、无 Shell 的能力边界；标题单行省略，避免窄屏换行 | MainActivity.kt、strings.xml |

“设置保存”不是每项都触发源码写入；只有界面明暗真正切换仍沿用现有安全保存后 Activity 重建路径。原目录写回失败时不会强行重建丢弃编辑内容。其它设置更新显示/编辑行为并保留文件会话。

## 构建与测试

- 最后完整构建：**BUILD SUCCESSFUL in 54s**，62 任务，24 执行、38 未变化。
- Kotlin/JVM：**53 项，52 通过、1 Windows 符号链接用例跳过、0 失败/错误**；比 P0 新增 12 个用例。覆盖旧设置迁移、损坏与越界、独立主题、设置持久化、减少动画优先级、缩进/Tab/独立编辑开关、IME 面板策略、纯文本/反向/循环/全字查找、Unicode/CRLF、替换容量及过期后台结果。
- Python 源码 **13/13**、实际 APK 内 `runner.pyc` 字节码主机回归 **13/13**，均通过。
- Lint **0 错误、14 警告**，与 P0 数量相同；第一轮新增的文案警告通过 Android 字符串资源修复，没有新增抑制/baseline。
- APK 的签名、ZIP CRC、6 个 Python 归档 CRC、包信息、arm64 ABI 与 ZIP 对齐通过。
- 本批没有启动 ADB、安装应用、测试实体键鼠或测量帧率；主机通过不等于 Android UI、鸿蒙卓易通、60 FPS 或高刷新率已验收。GitHub Actions 未远端运行。

[调试 APK](../dist/CodePocket-P1-settings-search-debug.apk)：21,765,550 字节，约 20.76 MiB；版本 0.2.0/code 2，API 24+ / target 34，仅 arm64-v8a。旧 P0 APK 保留。本次先留存产物，按用户要求在最终实机阶段统一安装验证。

SHA256：`29dff4a9771cc325d68a056a49e61b0f61e8de18a684e2f73e1f4e9386a7dc6c`。

证据：[verification/p1-settings-search](../verification/p1-settings-search/)，含两轮构建记录、JUnit、Lint、两组 Python 回归、APK 检查和源码哈希 `results.json`。本机完整环境和参数见 `verify-p1.ps1`，使用现有便携 JDK/SDK/Python 与离线依赖缓存。

## 技术选择与后续

- 本批继续使用成熟的 Android EditText、原生设置控件与 ViewPropertyAnimator，没有引入第三方编辑器或自建光标/选区引擎。初步查阅了 [Sora 官方项目](https://github.com/Rosemoe/sora-editor)，其列有补全、片段、撤销、搜索、换行能力及 LGPL-2.1 许可声明；尚未固定版本、集成或进行兼容原型测试，不能据 README 判定卓易通兼容。后续补全/光标工作继续评估成熟组件。
- 系统动画判断依据 [Android ValueAnimator 官方文档](https://developer.android.com/reference/android/animation/ValueAnimator#areAnimatorsEnabled())，API 26+ 有显式版本守卫；API 24–25 读取系统动画缩放作为回退，设备行为留待最终验收。
- 平滑光标/闪烁、树折叠/面板收起完整动画、主题柔和颜色过渡尚未实现；当前开关只控制已接入的工作区淡入，不能称为完整动画系统。
- 补全目前仍是原有横向基础词法候选；光标锚定弹窗、候选图标/说明、参数提示、可配置 Enter/方向键/Esc、片段占位符尚待下一批 P1。C++/Python 真实语义分析归 P2，不把词法结果冒充语义。
- 查找仅当前文件、纯文本，项目搜索/正则搜索待后续；设置暂未提供英文语言切换、设置搜索、运行超时等未接入项，避免无效开关。
- 可拖动/最大化面板、ANSI/流式程序终端、线性图标/更多高亮仍待 P1 后续；Python/C++ 执行模块、SAF、草稿和内部文件保护没有改写。
- Gradle、Manifest、Actions 和原始只读需求未改；未新增权限或依赖。继续分批开发，不因暂不实机而停止，也不将未验收项目标为通过。
