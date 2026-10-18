# P1 原生 Shell 终端首批（2026-10-18）

基线 `aa0d1939859700b28c92267773112d19ee2baabd`。用户确认选择“在 App 内嵌入成熟终端组件，连接真实 Shell/进程”；本批直接接入原生运行面板，不以 HTML、脚本化输入输出或结果框代替终端。

## 实现

- 固定 Termux `v0.118.3` / `5b657c6adf4304e5198951ce815fe0205dcac29c`，52项来源文件及许可按Git blob或SHA256核验；源归档保留在`.tools/terminal/upstream-source.zip`。原生Java `terminal-view`、协议/会话核心与JNI以本地模块构建，不需要JitPack、额外API Key或用户另装Termux。
- 运行面板增加 **Shell 终端** 标签，点击后准备目录并连接`/system/bin/sh -i`。真实键盘/IME输入交给组件，字节写入PTY，输出来自真实子进程。没有脚本化预设运行结果；底部提供Ctrl+C/D、Tab、Esc和方向键触屏入口，菜单提供键盘、复制记录、粘贴、Ctrl+L、结束会话和说明。
- 单个会话由进程级ShellRuntime持有，观察者为弱引用，不保留Activity/View；旋转/主题重建重新连接同一进程内会话，面板收起不结束Shell，退出整个IDE关闭会话。首次启动不主动抢焦点或弹键盘；终端焦点下IDE不截获Ctrl快捷键，键盘显示时按输入面板策略保留终端。
- 初始目录为当前内部项目，外部SAF项目使用私有terminal-home；HOME/TMPDIR在应用私有目录，路径作为独立cwd参数传入，没有拼接为Shell命令。当前会话不会因切工作区自动cd；标题明确显示初始目录。Shell可修改应用UID有权访问的文件，只能读取已保存正文，不是文件安全沙箱，也不是root；SAF仍由原文件管理器授权/写回。
- 原Python/云端C++结果页保留。Shell目前没有安装`python`、`pip`、`clang/g++`命令；Chaquopy的双向交互桥、本地C++工具链还未实现。点击IDE运行仍使用原有执行器。不能把主机构建JNI的NDK当作设备上的C++编译器。
- 使用原设置中的输出字号与动画开关；光标闪烁尊重系统动画缩放/省电及页面可见状态。本批尚未整体重做图标、文件树、设置中心或落实平板启动默认选择Shell；这些UI工作与P2/P3保持接续。

## 上游适配与许可

原始来源、逐项哈希、许可在`third-party/termux/UPSTREAM.json`、`LICENSE.md`及`licenses/`。没有将整个Termux项目笼统标为Apache：上游根许可证为GPLv3-only，并列出Android Terminal Emulator来源代码的Apache2.0例外；保留两份正文和来源说明，项目自身许可仍由所有者确定。没有正式发布，分发前完整对应源码和通知仍须继续核对。

仅修改5个上游文件，精确差异见证据`upstream-port.patch`：

1. TerminalSession使用公共ParcelFileDescriptor接口分别复制/拥有输入输出fd，移除隐藏字段反射及失败时System.exit；新增关闭标记，防止未初始化PID=0被误kill或关闭后因布局再启动。
2. JNI修正cwd对应的ReleaseStringUTFChars参数；fork/PTY准备失败关闭fd，chdir失败结束子进程，waitpid处理EINTR。
3. Android.mk明确16KB最大页对齐；两个Manifest的旧package声明移至Gradle namespace。

两个模块使用项目现有Java17/SDK35/Build Tools35/min24；NDK固定r27c `27.2.12479018`，仅arm64。Windows精简环境显式提供NDK的HOST_OS/HOST_ARCH，不修改系统环境。CI增加固定NDK安装和终端协议核心测试，原Debug APK上传保留；未触发远端工作流。

## 实际验证

- 最终Gradle成功：**1m 9s**，150任务27执行/123未变化。App **278项：277通过、1既有Windows符号链接跳过**，原270项名称/状态全部保留；新增8项真实目录/启动参数边界测试。
- 上游协议核心 **145/145通过**：真实解析/屏幕缓冲/ANSI控制、光标/历史/调整尺寸、Unicode和队列测试。JVM仅桥接Log、Base64与Color通道提取，未模拟View、输入法或PTY；三个桥接类未进入APK。未加入全局`returnDefaultValues`。
- Lint **0错误/11既有警告**；新增程序化构造View说明和字符串资源处理后，无新增警告。
- 原Python源码与实际APK runner.pyc **各13/13**；实际生成模板Python和主机C++17 **各7/7**。此主机C++不是设备本地C++执行。
- APK CRC、v2签名、16KB ZIP对齐、关键DEX定义及许可资产字节匹配通过。新增`libtermux.so`为真实AArch64 ELF，两段PT_LOAD对齐均为16384，JNI入口已检查；不据此声称整App或所有设备的16KB运行兼容已验收。
- 原有dist制品哈希保持，Workspace/SAF/草稿/CodeEditor输入/Python服务/运行器/Manifest及旧测试均保留。变化的旧DEX项仅为编译器重编号的匿名类/合成lambda，无稳定类删除。
- 可选Sora实验模块回归构建通过（21秒，测试/编译任务沿用未变化结果）；60项单元测试零失败，Lint 0错误/6警告。默认App运行时依赖仍是Kotlin1.9.25及原编辑器/新终端模块，没有引入Sora。
- 新APK：`dist/CodePocket-native-terminal-debug.apk`，**22,678,342字节**，SHA256 `def9ba82b438445276bda980a79dbe616c424fb833e150d117854f6b3224ab98`。此前成功版本保存在`round-04-debug.apk`。

## 失败记录与未验证项

首轮遗漏库模块Build Tools35；第二轮当前精简环境缺OS导致NDK尝试uname失败；第三轮新增菜单入口超过两项旧菜单长度约束。均已修复，保留失败日志；最终只从面板标签进入Shell，旧菜单和命令枚举恢复原样，没有放宽或删除失败测试。第四轮成功后修复新增Lint警告，第五轮成功。

**没有Android/模拟器/实机运行。** 因用户要求统一实机后置，本批未实际执行Android Shell命令、验证PTY权限/进程与子任务退出、旋转/后台进程死亡、IME组合/焦点、蓝牙键鼠、手机/平板/卓易通或帧率。因此可以确认源码接入、构建和核心主机测试，不能确认设备上Shell启动成功。没有新Wandbox调用、push或发布。

后续先做原生运行/编辑联动和UI余项，Python真实交互桥与设备会话验收；本地C++按P2独立验证，不以降低targetSdk或外观模拟替代。
