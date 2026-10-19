# 终端提示符、触屏键盘与窗口回修（2026-10-19）

基线 `02ca258`。在既有 Kotlin / Android Views / Termux PTY / Chaquopy 架构上修正，不重建项目；动画继续暂停，原 P1/P2/P3 需求保留。

## 用户反馈与原因

用户在手机上看到 `:/data/data/com.codepocket.ide/files/workspace $`，点击 Python 运行后一直等待 Shell 提示符，最终超时；平板相同。上一轮修正了终端 View 尚未测量时不创建 PTY 的依赖，但漏掉了系统 Shell 启动配置重设提示符的路径。

[AOSP 的系统 mkshrc](https://android.googlesource.com/platform/external/mksh/+/master/mkshrc)会重新赋值 PS1，其目录提示符形态与反馈一致；[mksh 启动源码](https://raw.githubusercontent.com/MirBSD/mksh/master/main.c)在交互启动时通过 ENV 选择初始化文件。旧代码仅从环境提供带 OSC 133 A/B 的 PS1，系统配置随后覆盖它，Shell 已运行却不能发出启动门控等待的标记。因此 Python 没有进入启动命令，连只有 print 的程序也不会输出。等待期间的前台输入保护又拒绝输入请求，表现为点击终端不弹键盘。

这是已确认的代码缺陷及与设备记录一致的解释；本轮没有接入设备，不将它写成所有 Android 运行路径已验收。

## 实现与保留的工作方式

- ShellLaunchPlan 在应用私有终端主目录创建每会话独立 rc 文件，通过 ENV 传给真实 `/system/bin/sh -i`；先加载系统配置，再设置应用的 PS1 标记。关闭、退出、失败、过期启动及 native 库加载失败时清理该会话 rc，目录范围校验保留。
- ShellTerminalPane 的点击和新增“键盘”按钮统一经过 TerminalKeyboard：先切换焦点，下一次主线程消息再请求 IME；需要时重启当前 InputConnection，API 30+同时使用系统 Insets 请求。焦点离开、隐藏或销毁后取消迟到请求。运行、输出和后台状态更新仍不自动弹键盘。
- 终端成为实际焦点时才采用 Termux 的终端输入属性；保留组件本身的 NO_FULLSCREEN。上次失败测试错用了 NO_EXTRACT_UI，现已更正断言，没有为通过测试改写上游编辑输入逻辑。
- EditorViewState 的焦点白名单补入 terminal；恢复设置/页面状态时对已有真实终端恢复焦点，不再强制转回编辑器，不显示IME或排队迟到的焦点请求。会话ID、历史stdin与其他视口数据保持。
- 面板按字号与系统字体缩放计算终端行空间；短窗口或输入法下隐藏可选标题/标签。四行所需空间无法与编辑器共存时，已打开的终端临时使用完整工作区，而不是整个消失；关闭面板、编辑器输入时收起以及已记住的比例/最大化选择仍保留。
- 同一个真实终端保留 Shell 命令、Python 实时输出、多轮 input、Enter、Ctrl+D、Ctrl+C、ANSI、选择/复制/历史滚动；没有再添加只读的运行终端。Python 无预填 UI；C++仍明确为 Wandbox 云端预填、完成后返回，未伪装成本地流式运行。
- 未安装 python/pip/clang 命令或完整 Termux 发行环境；Python 运行按钮连接已有 Chaquopy 独立进程和实际 PTY。SAF 授权、项目文件、草稿、保存、错误来源校验、执行预算、输出上限、重复运行及旧会话隔离保持。

## 验证

| 检查 | 结果 |
| --- | --- |
| App JUnit/Robolectric | 342 项：341 通过、1 项既有 Windows 符号链接跳过，0 失败/错误；旧328项及状态保留，新增14项 |
| Termux 核心 | 146/146；验证副本实际执行，原仓库相同未变代码复用通过缓存 |
| Shell 启动脚本 | 3/3；执行实际 Java 生成的文件，复现旧提示符覆盖、验证恢复标记与 hello、验证系统 rc 先于应用提示符 |
| Python 源码 / 最终 APK runner.pyc | 各29/29；各含16项真实主机管道子进程测试，覆盖无换行提示、多轮输入、EOF、停止后重跑、超时、异常、原生写入及输出上限 |
| 实际代码模板 | Python 7/7、主机 g++ 的 C++17 7/7；不是手机 C++ 或新 Wandbox 在线调用 |
| Lint | 0错误、11既有警告 |
| Debug APK | 验证副本构建4分19秒；最终原仓库构建1分9秒，152任务中22执行/130未变化 |
| 产物 | CRC、Python资产、v2签名、16KB ZIP/ELF对齐、桥接类/JNI、稳定生产类/未打包测试库及许可检查通过；旧dist保持 |

新增测试为启动文件与会话独立性2项、初始化/输入属性及清理3项、真实Android View焦点/IME请求4项、窗口/字号/布局3项，以及保存的终端焦点/真实View恢复2项。焦点测试属于 Robolectric，不代表物理键盘或真实 IME 已验证。

Windows 沙箱曾在 Java ZipFS 关闭 JAR 的路径解析中报访问拒绝，另有审批服务容量/超时阻碍。先在系统临时目录建立相同源码的验证副本；获准后通过完整检查，再校验原仓库内容未变化、应用四个后续修正，并在原仓库重新完成构建与回归。最后确认终端焦点归一化缺陷，再次补测并在原仓库构建最终版本。没有通过改写 Gradle/SDK 源码来掩盖环境问题。

## 交付

- APK：`dist/CodePocket-terminal-prompt-ime-debug.apk`，22,678,342字节。
- SHA256：`22ff560ab19c4f3b56e005497e68c81c5d57dd49d3791978fb135ca4bbf84109`。
- 与旧 APK 使用同一 Debug 证书，适合覆盖安装；不是本轮已实测覆盖安装的声明。
- 本轮证据：`verification/terminal-prompt-ime-2026-10-19/`。`build-repository-focus.log`是包含焦点回修的最终原仓库构建，`build-repository.log`保留此前通过结果，`results.json`汇总最终检查，旧失败和验证副本记录保留。
- CI 增加实际生成的 Shell rc 主机回归及对应报告；保留 Debug APK 下载步骤。没有 push、发布或触发远端 Actions。

## 仍需设备验收

手机和平板的 Android PTY → app_process → FD 传递 → Chaquopy 全链尚未在本轮实际执行；软键盘、蓝牙键鼠、卓易通、旋转、触摸/选择与帧率仍待统一实机。主机管道不能替代 Android PTY。

设备验收应包括：首次打开终端及仅 print 的运行能返回提示符；点击终端或键盘按钮只把输入送到当前终端；无换行提示与多轮 input；Ctrl+D EOF、Ctrl+C 停止和随后重跑；历史滚动时新输出不抢位置；窗口/字号变化、多个会话和面板收起后保持状态；平板启动展开与手机启动收起；旧会话消息不干扰新运行。若有剩余设备故障，按准确启动阶段与真实终端记录继续定位，不能用演示结果代替。

## 修改文件

- `ShellLaunchPlan.java`、`ShellRuntime.java`：rc 与提示符、会话文件清理。
- `ShellTerminalPane.java`、新增 `TerminalKeyboard.java`：触屏键盘、焦点、紧凑状态与行空间。
- `MainActivity.kt`、`PanelLayout.kt`：按可用空间/字号选择终端布局；`EditorSessions.kt`仅补齐焦点值兼容。
- `PanelLayoutTest.kt`、`ShellLaunchPlanTest.java`、`ShellTerminalInitializationTest.java`及新增 `TerminalKeyboardTest.java`，新增 `TerminalFocusStateTest.kt`：14项新回归。
- `scripts/verify_shell_startup.py`、`.github/workflows/build-apk.yml`：真实生成脚本的主机执行与CI。
- README、当前进度、RESUME与本报告。
