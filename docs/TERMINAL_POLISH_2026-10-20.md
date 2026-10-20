# 终端界面、关闭快捷按钮与 Tab 补全修复（2026-10-20）

在 `1948e94` 启动闪退修复版上继续开发。保留同一个真实PTY、Chaquopy/Python与Wandbox运行、文件安全和此前P2/P3要求。本批交付新的终端工具栏/控制键设计、关闭当前终端快捷按钮，以及已复现的补全显示问题修复。

## 终端的实际变化

- 合并重复的面板标题与“终端/问题”导航：使用类型图标、细选中线、统一48dp操作目标与克制的灰蓝色。
- 按终端区域实际宽度重排：至少600dp且字体缩放不超过1.3时，会话选择器与操作合并到单行；手机和大字体使用独立会话行。原工作台840dp多栏和600×480dp启动35%面板规则继续保留。
- 会话行放置选择器、Lucide加号和垃圾桶；窄窗口/输入法紧凑模式把垃圾桶移到顶栏，保持直接可用。原来两个不同的“更多”入口合并，尺寸、新建、结束、配色等仍可从菜单访问。
- 垃圾桶表示**关闭当前终端会话**，会先确认；取消保留会话，确认调用既有registry.end，只结束被确认的会话及其前台程序。向下箭头表示收起面板，进程继续保留。关闭最后一个会话后保留新建入口，不静默创建另一个终端。
- 底部统一为键盘、控制键开关、复制记录、回到底部；展开后独立显示可横向滚动的Ctrl+C、Ctrl+D、Tab、Esc及方向键。48dp触控区域内使用内收的键帽，避免把密集文字按钮挤在一行。
- 运行状态改为简短的非交互状态行；键盘入口继续常驻。实际文本留白由父容器控制，避免Termux自绘忽略子View padding时文字贴边。提示符、ANSI与五种配色保留。
- 窗口重复测量时复用会话布局参数，不重复创建相同对象；本批新增的4个DrawAllocation和1个UnusedResources提示已修正，没有压制Lint。

## 补全列表为什么不出现

完整启动真实MainActivity后输入Python `pri`，提供器能够返回候选，但弹窗定位失败。原生TextView的 `totalPaddingBottom` 包含短文件末行下方的空白，原来的NativeEditorSurface把它当成不可用区域扣掉；一行代码的编辑区虽然很高，最终可用锚点却只有约一行。

已改用**文本视口的compoundPadding边界**进行裁剪，光标绘制坐标继续使用实际文字坐标。短窗口再按空间隐藏说明/标题，至少能放下一条48dp候选时就显示。触屏Tab使用Activity外部点击管理，避免PopupWindow先收到外部触摸并清空候选，随后Tab只能缩进；其他外部点击仍会关闭列表。

没有修改词法候选提供器或把它称为语义IntelliSense。没有候选时Tab仍缩进，Shift+Tab仍反缩进；中文组合输入保护、文档/版本校验、Tab设置和实体键盘行为保留。

## 修改文件

| 文件 | 用途 |
| --- | --- |
| `ui/TerminalChrome.kt` | 统一终端标签、会话选择器、操作按钮与控制键 |
| `ui/MainToolbar.kt` | 自适应单行/双行标题、紧凑模式关闭入口、参数复用 |
| `MainActivity.kt` | 接入会话栏、关闭确认、统一菜单、触屏Tab保护与面板最小尺寸 |
| `terminal/shell/ShellTerminalPane.java` | 底部操作区、展开控制键、内容留白和状态行 |
| `editor/NativeEditorSurface.kt`、`CompletionPopup.kt` | 修复短文件补全视口、紧凑候选与外部触摸行为 |
| `ui/IdeIcons.kt`、`res/drawable/lucide_plus.xml`、`res/values/strings.xml` | 固定版本加号、原生资源及准确操作说明 |
| `third-party/lucide/icons/plus.svg`、`UPSTREAM.json` | 官方0.468.0原始资源与SHA256，保留现有许可证 |
| `CompletionInteractionTest.kt`、`TerminalChromeDesignTest.kt` | 17项新增原生交互、布局及图像回归 |

Java/Kotlin路径以 `app/src/main/java/com/codepocket/ide/` 为基准；测试位于 `app/src/test/java/com/codepocket/ide/`。README、交接文档、计划状态和预览说明同步更新。

## 执行结果

| 检查 | 结果 |
| --- | --- |
| 全部App测试 | 496项，495通过/1既有跳过；原479项状态保持，新增17项通过 |
| 补全8项 | Python自动出现、实体Tab、短窗口、触屏Tab、外部点击、C++、中文组合保护、组合提交后恢复 |
| 终端设计9项 | 手机双行、平板单行/恢复、紧凑模式、大字体、安全关闭取消/确认、4种原生预览 |
| 上版启动7项 | 所有图标native加载/绘制及手机/平板深浅主题完整启动继续通过 |
| Python与资源源码测试 | 34/34通过 |
| 最终APK的Python字节码 | 主机CPython3.13实际执行29/29通过 |
| 生成Shell启动脚本 | 主机4/4通过 |
| 生成模板 | 主机Python7/7、C++17 7/7通过 |
| 终端核心146项 | 源码保持，任务UP-TO-DATE；不计作本轮重新执行 |
| Lint | 0错误、11既有警告，无新增警告 |
| Debug构建 | 原仓库离线成功，1m8s |
| 最终APK | CRC、v2签名、16KB ZIP对齐、必要类定义及无测试类检查通过 |
| 编译图标 | 46个vector、137段路径从安装包提取，与源码逐项一致、参数有效 |
| 运行/历史保护 | 28份native/Python归档字节不变，203份受保护文件不变，40份旧APK哈希保持 |

前期完整交互测试也暴露了主机测试准备差异：标准AlertDialog回调需要等待Looper；紧凑/普通关闭按钮两个实例中需选择可见者；主机无真实IME，触屏Tab用显式可见的真实符号栏做窗口场景。最终保留并修正这些测试，未降低生产校验或用模拟提供器替换补全。所有中间失败日志均保留。

## 原生预览与实机边界

[手机/平板深浅主题原生控件预览](terminal-preview/README.md)。四图来自本轮生产控件和Termux绘制器，已逐张检查；不是HTML样稿，也不是设备截图。图片顶部标明“主机预览/示例终端内容”，终端字节由测试注入以检查ANSI和排版，没有执行图中的程序；它们不作为Python运行成功的证据。

未连接手机或模拟器。本轮没有新增实机IME、实际蓝牙键鼠、Android真实PTY/Python整链、卓易通、60FPS/高刷证据。主机触屏Tab场景不能替代真实输入法的窗口事件验收。原按约定后置的实机项目和P2/P3继续保留；没有添加系统python/pip/clang命令或本地C++。

## 安装与证据

[下载本轮Debug APK](../dist/CodePocket-terminal-polish-2026-10-20-debug.apk)

- Android7.0+ / arm64-v8a，包名 `com.codepocket.ide`，versionCode2/versionName0.2.0。
- 22,682,338字节，SHA256 `94fe4856bf09d57d4f791c7667e85c3f6ed76c4b42f579fb3762dcca2205a55c`。
- 与旧包相同的Debug证书，可覆盖安装，无需卸载或清数据。
- 完整证据在 `verification/terminal-polish-2026-10-20/`：基线哈希、补全失败/修复、各轮JUnit/日志、原生图片、最终APK审计及 `results.json`。最终源码、原生终端库、构建脚本和Actions均在原仓库；未推送、发布或触发远端工作流。
