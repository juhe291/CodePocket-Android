# 同一个真实终端中的 Python 运行（2026-10-18）

基线 fe4c709；用户要求继续直到完成终端接入，动画保持暂停，设备验收统一后置。此轮完成代码接线、主机测试和 APK 检查；**Android PTY/前台桥/跨进程 FD 传递/Chaquopy 的整条链路尚未在手机或模拟器实际运行，不能据此承诺设备通过。**

## 已修改的行为

- 底部仅终端与问题。会话选择器只列真实 Shell，移除“最近运行任务”和第二个只读程序输出入口。旧 output/info 状态回到当前真实终端，保留最多8个独立 Shell、新建/改名/结束及错误导航。
- 运行按钮默认将 Python 接入当前终端；输出直接写真实 PTY，`input()`/`sys.stdin`/二进制标准流读取终端。状态行显示文件、执行引擎及等待输入，按 Enter 提交，Ctrl+D 发送真实 EOT。仅点击终端/状态行才请求软键盘。
- Ctrl+C 有选区优先复制，无选区停止当前 Python/云端等待；触屏控制键保留。终端获得焦点后 Ctrl+S/N/O/B 和 F5 仍走原 IDE 操作，其余终端编辑键保持直通；F5 不重复启动正在执行的任务。
- Python 预填输入是可记忆的可选模式，原有每文件输入内容保留，设置和终端更多菜单均可切换；切换只影响下一次执行。C++ 始终使用真实 Wandbox、预填输入，完成后把远端 stdout/stderr/退出信息追加到同一个成熟终端，明确云端边界。
- 阅读历史输出时不自动拉回底部，提供“到底部”；切换会话保留历史行偏移，调整窗口时尽量保留相对偏移。清空按钮仅清终端显示，不向运行程序注入 Ctrl+L；鼠标中键粘贴也经过运行输入门控。
- 原字体/字号初始化闪退修复、启动异常保护、独立 Python 可终止进程、保存/SAF/草稿/源码定位门控均保留。动画功能没有扩展。

## 真实数据链路与保护

现有 /system/bin/sh 在真实 PTY 中执行私有 sourced 启动脚本，启动系统 app_process 的 Java 前台桥。桥进程继承的 fd0/1/2 通过同 UID、随机256位令牌认证的 LocalSocket/SCM_RIGHTS 交给 App，再经私有 Messenger/PFD 传给 :python 服务。服务把它们 dup2 到自己的0/1/2；runner 复制并使用真实描述符执行 Python。最终真实结果仍供问题页/行定位使用，程序输出不再事后重放。父进程完成后回传真实退出状态，桥退出让原 Shell 继续显示其提示符。

CPython3.13 的 input 在标准 fd+TTY 条件下可能直接走 C readline；本适配保留 isatty，但向 Python 暴露实际复制后的 fd，避免绕过托管 readline 等待事件。参照[CPython3.13.9 input 实现](https://github.com/python/cpython/blob/v3.13.9/Python/bltinmodule.c)，并增加真实子进程 FD/等待事件回归；这项 Android 推断仍须设备确认。

- 启动限30秒；Python 字节码有效执行限6秒，父进程累计有效执行限10秒；托管 stdin 单次等待最多5分钟。等待与执行分开计时，重复/过期序号不能延长预算，run ID 隔离旧消息。直接 native read、自建 stdin 或其他线程读取不提供独立等待识别，仍受父级截止约束。
- 原有每流50000字符限制保留，终端执行另有220000字节总预算；Termux 队列/2000行历史有界，按实际读取块追加，不重画完整输出字符串。普通 Shell 自身遵循成熟组件的有界队列/历史，没有6秒运行器限制。
- 只对私有 Python 进程尝试建立独立组，终止前核对 PGID=已认证 PID；不改变编辑器或 Shell 进程组。实现使用 JNI，Android SDK 未公开 getpgid/setpgid。停止/结束所属终端会取消运行并关闭描述符/桥/临时启动文件。
- 通过不可见 OSC133 提示符边界及实际 PTY 前台进程组判断 Shell 是否空闲；不把启动脚本发给已有前台命令。已输入但未提交的 Shell 行会先清除，再提交本次启动脚本。用户完全替换 PS1 导致标记丢失时会明确失败/超时，不假造运行结果。
- 这不是 Termux 完整 userland：尚无可直接键入的 python/pip/clang 命令，也没有手机 C++ 工具链。Python PID 位于 Android 独立服务，前台桥代表其 Shell 作业；没有承诺完整 Unix 作业控制、任意子进程脱离会话后的清理或长期后台保活。OEM 禁止 app_process/FD 传递时会显示真实失败，不静默退回演示。

## 文件范围

| 文件/模块 | 修改 |
| --- | --- |
| MainActivity.kt、ui/MainToolbar.kt | 删除任务终端入口，运行接入当前会话，保留预填与诊断入口 |
| TerminalIdeKeys.kt | 终端焦点下的 IDE 快捷键与 PTY 控制键分流 |
| RunSession.kt、TerminalRunClock.kt | 运行所有权、身份/序号、启动/等待/执行截止、真实桥与清理 |
| PythonExecutionService.kt、ExecutionEngines.kt | PFD/Messenger、私有进程实际标准流与 Chaquopy 调用 |
| terminal/bridge/*.java | 前台 app_process 入口、认证 Socket/FD 所有权、命令引用及 JNI 进程组 |
| runner.py | 实时真实描述符流、无换行刷新、等待事件、输出预算、保留原批量执行 |
| ShellRuntime.java、ShellTerminalPane.java、ShellLaunchPlan.java | 前台会话、提示符边界、键鼠/复制/EOF、状态/历史/安全清空 |
| AppSettings.kt、SettingsDialog.kt、strings.xml | 默认交互设置、迁移与说明 |
| third-party/termux | 最小协议/会话/视图/JNI 钩子；PORTING.md及累计 codepocket-port.patch |
| tests/test_terminal_runner.py、scripts/verify_apk.py | 真实管道子进程，并在 APK 实际 runner.pyc 上复跑 |
| app/src/test、终端核心测试 | 新终端操作、计时、引用、输入、原生View/历史与快捷键主机回归 |

Workspace/DocumentTree/DraftStore/CodeEditor/EditorSessions、Manifest、Gradle、GitHub Actions、原始及增补需求未改；旧 APK 哈希保持。UPSTREAM.json、原 GPL/Apache 许可及 App 许可资产保持，累计源码补丁可随仓库交付，不把修改后的上游源文件称为原样。

## 实际验证结果

- 最终 `:terminal-emulator:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` 成功：57s。此前两个编译错误轮日志保留：SDK隐藏 API 不可用、Termux TerminalView 为 final；分别改用 JNI 和正式 View.OnKeyListener。没有放弃构建错误。
- App **325通过 / 1既有Windows符号链接跳过**，共326；基线305个保留用例状态相同，1项旧“任务显示”测试按新要求替换为不重放结果/保持真实会话，并新增空会话检查。ShellLaunchPlan 原测试更新为带 OSC 标记的固定 PS1，同时保留路径引用与目录边界断言。净增20项。
- 终端核心 **146/146**，全部145个原用例状态保留，新增碎片化 OSC133 解析。App新增实际 Termux View/Emulator 的历史不回底、切换保留偏移和清空不注入 stdin 主机测试；未执行 JNI/PTY。
- Python源码 **29/29**，APK真实 `runner.pyc` **29/29**；每套含16项实际 CPython 子进程+OS管道，覆盖无换行提示、多轮输入、真正关闭stdin的EOF、traceback、Unicode/ANSI、binary流、os.write、输出限制、等待计时、CPU超时、等待时硬停止及新进程重跑。是Windows系统管道测试，未用它冒充 Android PTY/SCM_RIGHTS。
- 实际生成模板：Python **7/7**、主机 C++17 **7/7**。C++主机 g++ 不是手机编译器，此轮未发送新的 Wandbox 外网任务。
- Lint **0错误/11警告**；既有 OldTargetApi1、ChromeOsAbiSupport1、DataExtractionRules1、SetTextI18n8。新增设置说明使用资源字符串，没有增加警告。
- APK CRC、Python嵌套归档、arm64-v8a、v2签名、16KB ZIP/ELF对齐、新桥类定义和JNI符号、未打包测试库、稳定生产类与许可资产、旧产物哈希检查通过。
- 现有 Actions 的 `unittest discover` 自动覆盖新增源码测试，`verify_apk.py` 自动覆盖打包字节码交互测试；原有APK与报告上传步骤保留，**本轮未运行远端 Actions、push 或发布**。

交付：`dist/CodePocket-live-terminal-debug.apk`，22,678,342字节（21.63MiB），Android7.0+ / arm64-v8a。SHA256 `b01843b3bce932605053fd905496e017a32068aaba2b32538531ab8ed60acafc`。

全部证据：`verification/terminal-live-2026-10-18/` 中 before.json/修改前备份、失败及成功轮日志、verify-final.ps1、源码/打包字节码日志、JUnit/Lint、apk-inspection.json、results.json、签名/对齐/元数据、原始上游归档哈希及补丁文件清单。保留旧 .tools、dist、verification 和设备备份。

## 统一实机验收（尚未执行）

1. 安装后打开/冷启动终端不闪退，真实 pwd/echo/ls；已有前台 read/cat/sleep 时点击运行拒绝覆盖，多个会话切换无重复输出。
2. Python依次两次 input（首个提示无换行）、逐步打印、Unicode与ANSI、EOF、异常定位；自然结束回到同一个Shell提示符，echo $? 对应状态。
3. 等待超过10秒仍可继续；单次5分钟输入截止、纯计算6/10秒、大量 native 输出限制、等待时Ctrl+C/结束所属会话、立即重跑及迟到消息隔离。
4. 触屏/中文IME/蓝牙键鼠：不自动抢键盘，Ctrl+C选择复制优先、无选择停止、Ctrl+D、Tab/方向键、鼠标粘贴/选择/滚动；终端聚焦 Ctrl+S/N/O/B、F5。
5. 手机/平板净窗口、旋转、分屏、前后台与主题切换，预填设置取消/保存、每文件输入保留；Android OEM/鸿蒙卓易通的 app_process、LocalSocket/SCM_RIGHTS、Chaquopy和进程组限制。
6. C++明确云端预填/等待，网络失败/取消/问题定位；无假交互输入。

前台桥和本地Python的代码集成已完成，设备端可用性仍是上述待验收项。原P1完整UI/动画/默认编辑器迁移及P2/P3要求保留；不把本次终端改动称为整个项目完成。
