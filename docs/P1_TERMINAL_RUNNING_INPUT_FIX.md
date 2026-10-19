# Python 启动期间终端键盘与桥接环境回修（2026-10-19）

基线 `59551f1`。用户反馈上一版仍不能输入，进一步确认点击时显示等待 Shell 提示符 / 前台桥连接 / 初始化 Python。本轮在现有单一真实终端上修改，没有重建项目。**完成代码修正和主机验证；没有设备确认，不声称手机问题已经闭环。** 动画仍暂停，原 P1/P2/P3 需求保留。

## 发现与修改

1. **启动阶段错误地拒绝键盘请求。** `ShellTerminalPane.showKeyboard()` 以 `runtime.acceptsInput()` 作为是否弹键盘的条件，Python 的 STARTING 状态必为 false。点击正文和键盘按钮因此都被静默忽略。现在键盘只检查当前终端、可见性与活动宿主；向进程发送字节的保护独立保留，启动完成前不会把输入误写到 Shell 命令行，Ctrl+C 仍能停止。这不表示允许在进程尚未启动时提交标准输入。
2. **真正的 Android 运行环境被清掉。** Termux JNI 创建子进程会 `clearenv()`；原 `ShellLaunchPlan` 只恢复少量变量，没有 ART/I18N/TZDATA/APEX 根目录和 boot classpath。[AOSP AndroidRuntime 源码](https://android.googlesource.com/platform/frameworks/base/+/86c4c20c60a87b03e478bd784d6c0fc0a2dcb503/core/jni/AndroidRuntime.cpp)在缺少这些必要目录变量时直接返回，前台 `app_process` 桥因此可能在连接前结束。现在从 App 的实际系统环境按明确名单继承必要变量，不猜测不同 Android 版本的 APEX 路径；HOME/TMPDIR/PATH/ENV/PS1 继续由应用设置，不继承任意令牌、CLASSPATH 或 LD_PRELOAD。这是确定的配置缺陷；用户本机究竟停在哪个阶段尚无精确设备记录，不能称为唯一设备根因。
3. **等待真实窗口焦点再申请输入法。** `TerminalKeyboard` 同时检查 View 焦点、窗口焦点和附着状态；菜单关闭时若窗口尚未取回焦点，最多保留一次显式请求 2 秒。用户切走焦点、移除 View、收起面板或暂停时取消。仅显式点击清除已有 HIDDEN/ALWAYS_HIDDEN 状态，保留 adjustResize；不使用 SHOW_FORCED，不在状态更新或返回前台自动打开键盘，按返回键关闭后不重开。依据 [Android 输入法可见性说明](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility)。
4. **桥提前退出尽早报错。** 实际 sourced 启动脚本先输出 OSC133 C 的命令执行边界；只有观察到该边界、随后提示符返回且内核确认 Shell 已重新成为前台，才通知对应运行。Python 仍在 STARTING 时立即结束并显示当前阶段，不继续空等 30 秒。仅编辑命令时的提示符重绘、程序自己输出 OSC 标记或旧运行清理均不能触发误判；原运行标识、顺序、超时和停止规则保留。

Shell 与 Python 仍使用同一个 Termux View / PTY。没有追加隐藏 EditText、模拟输出、第二个运行终端或修改成熟组件的输入类型；Python 保持真实交互，C++ 保持真实 Wandbox 云端预填限制。目录边界、保存、草稿、SAF 和独立历史输入数据保持。

## 已执行验证

| 检查 | 结果 |
| --- | --- |
| App JUnit / Robolectric | 357 项：356 通过、1 项既有 Windows 符号链接跳过；原 342 项及状态全部保留，新增 15 项 |
| 新增覆盖 | 真正附着的终端触摸/键盘按钮/隐藏宿主/中文组合与 Enter 转发 5 项；窗口焦点、仅清隐键盘标记、请求过期、失焦或移除取消 4 项；系统环境继承与名单隔离 2 项；生成命令边界导出 1 项；桥退出/重绘/旧运行隔离 3 项 |
| 终端核心 | 146 项既有通过记录，本轮代码未改、Gradle 复用通过缓存 |
| 真实主机 Shell | 执行实际 Java 生成的 rc / 命令边界 4/4；不是 Android mksh / PTY |
| Python 源码与实际 APK runner.pyc | 各 29/29，包括每套 16 项真实主机管道子进程测试 |
| 实际代码模板 | Python 7/7；主机 g++ 的 C++17 7/7；不是手机 C++ 或新 Wandbox 调用 |
| Lint | 0 错误，11 项既有警告；没有新增警告 |
| 最终 Debug APK | 原仓库构建 1 分 22 秒，152 任务中 21 执行 / 131 未变化 |
| 产物检查 | ZIP CRC、Python资产、v2签名、16KB ZIP/ELF对齐、必要桥接类/JNI、稳定生产类、未打包测试库、许可字节检查通过 |
| 回归边界 | 旧 dist 哈希保持；编辑器/文件/草稿/SAF/运行器/Python服务与桥内部/Manifest/Gradle/Actions/原需求保持源哈希；仅本地开发，无 push 或远端 Actions |

测试夹具明确隔离 native 创建：真实 Termux View、手势识别器与 InputConnection 保留，Robolectric LEGACY 字体间距为零时仅提供确定性字体度量；会话关闭标记阻止 JNI。输入转发测试短暂使用 PID 哨兵启用现有内存字节队列，验证中文组合和回车内容后立即归零，没有启动或声称运行了 Android 进程。没有用通用 EditText 测试代替整个终端集成测试，但这些仍是主机测试，不能证明物理输入法已经可用。

首轮记录保留了夹具字体度量/单击确认时钟问题与旧代码门控失败，修正夹具后完成回归；第一版 355 项（354 通过、1 项既有跳过）构建后，又补上“先确认启动脚本执行”的屏障，避免将 readline 重绘误判为桥退出，最终重新通过全部验证。没有删除旧测试或增加跳过项。中间 APK / 日志在本轮证据的 `round-01/`。

## APK 与修改文件

- APK：`dist/CodePocket-terminal-running-input-debug.apk`，22,678,342 字节（21.63 MiB），Android 7.0+ / arm64-v8a。
- SHA256：`ae4faaf4fdca1955cd9fb5fbb1fc559be01797907b78d0e139c27dfb66508978`。
- 与上一版同包名、同 Debug 证书，证书 SHA256 `d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`。这是签名兼容性核对，不是本轮设备覆盖安装记录。
- 运行代码：`ShellTerminalPane.java`、`TerminalKeyboard.java`、`ShellLaunchPlan.java`、`ShellRuntime.java`、`RunSession.kt`、`BridgeCommand.java`。
- 回归：`ShellLaunchPlanTest.java`、`ShellRunInputTest.java`、`TerminalKeyboardTest.java`、新增 `ShellTerminalTouchTest.java`、`scripts/verify_shell_startup.py`。现有 Actions 自动执行该 Shell 脚本验证步骤并保留 Debug APK 下载能力。
- 文档：README、DEVELOPMENT_PROGRESS、RESUME、本报告。
- 本轮证据：`verification/terminal-running-input-2026-10-19/`，包括基线哈希、失败/通过日志、JUnit、Lint、证书、最终 APK 检查与 `results.json`。旧产物与证据保留。

## 仍未验证

未连接手机、平板或模拟器，未执行 Android PTY → app_process → SCM_RIGHTS/FD → Chaquopy 全链。软键盘在各厂商系统上实际显示、实体键鼠、卓易通、布局/旋转/字体缩放和帧率仍需统一设备验收。没有安装 python/pip/clang 命令或完整 Termux 发行环境；本地 C++ 和完整动画不在此次回修中。

后续设备检查先确认运行 print('hello') 能输出并返回提示符，再验证 input('姓名：') 后点击正文或键盘按钮、中文输入、Enter、多轮读取、Ctrl+D、Ctrl+C、停止后重跑和菜单关闭后的焦点。若仍失败，应按新 APK 的真实启动阶段与终端记录继续定位，不能把本轮主机通过写成设备通过。
