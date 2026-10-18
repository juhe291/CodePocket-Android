# P1 终端工作流首批：统一入口与多Shell会话（2026-10-18）

基线`834fb974f927e312ebc66c02143c4585021f2801`。接续用户关于“当前Shell逻辑与VS Code差别大”的反馈，先做终端操作和会话管理；动画保持暂停。在原项目上修改，保留旧运行能力。

## 本批行为

- 底部顶层只有“终端 / 问题”，去掉独立“输出 / 错误 / 运行信息 / Shell终端”四标签。终端内部通过选择菜单切换Shell或“最近运行任务”，新建按钮/会话菜单提供新建、重命名、结束、键盘、复制粘贴、清屏、重启、诊断及许可说明。嵌入时隐藏原Shell独立标题栏，避免叠出第二套会话工具栏。
- 每个Shell有独立ShellRuntime、PTY/TerminalSession和初始目录，目录保留在该会话；新建时使用当前内部项目，SAF项目使用应用终端HOME。切项目不会悄悄改变现有Shell的cwd。稳定ID不会复用，菜单迟到事件不能选中已删除的会话；最多8个Shell，避免无限创建线程/缓冲。
- 切换Shell不停止进程；隐藏/显示面板不结束会话。结束会话经确认，仅关闭对应Shell并切邻近会话，最后一个关闭后显示任务；退出IDE关闭注册的所有Shell并释放准备线程池。进程内重建界面复用同一注册表，不持有Activity或View。新一轮IDE启动不会复用上次已关闭的Executor。
- 默认Shell在其面板实际可见时才准备启动，不主动弹IME；选择/显式启动可重试已退出/失败会话。收起面板时若终端持有焦点，也按原编辑器视口恢复策略收起键盘，不把隐藏终端继续当作编辑器输入目标。
- 显式点击运行会选择同一底部面板的“最近运行任务”，合并展示真实结果的stdout、stderr、引擎、退出码、耗时；“问题”保留诊断和原安全跳转逻辑。运行结果回调保留当前Shell/问题页/面板开合选择，不后台抢回任务页。全局停止仍指运行任务，结束Shell是独立且有目标身份的操作。
- 原每文件预填输入完整保留，仅任务视图显示；从菜单展开预填输入会显式选择任务。清空只清任务显示，不删结果/输入。任务菜单打开后执行结束，“停止”不会意外变成“清空”；已切换会话的旧菜单不作用于新目标。

## 实现与边界

MainActivity负责底部入口、选择和运行事件路由；TerminalCatalog提供有界身份/名称/选择规则；ShellSessionRegistry只持有应用Context、独立Runtime和初始cwd；ShellTerminalPane接收指定Runtime、继续使用成熟原生组件。ShellRuntime增加独立构造与会话关闭后的线程池释放。上次“先字号后字体”的启动修复和错误记录保留。

**统一的是面板与会话工作流，并非所有执行后端已统一成PTY。** Shell仍是真实Termux视图+本地/system/bin/sh；“最近运行任务”继续使用原只读ANSI TextView展示现有Python/Wandbox执行器的真实批量结果，不伪装成可输入命令的Shell。Python仍预填stdin/完成后返回；C++仍明确云端执行。TaskTranscript说明两个输出流分别展示，不能推断时间交错顺序。未安装python/pip/clang命令，尚无Python实时流/多轮输入与终端桥，本地C++未实现；不能声称VS Code式完整开发环境已完成。

注册表仅在当前App进程存活，不承诺杀进程后恢复原Shell进程、标签或子任务；切换视图保留会话/协议缓冲，但终端滚动视口及输入法状态仍待联合验证。没有改文件系统安全边界或把SAF URI当可cd目录。原终端历史自动滚动、进程树退出、命令运行/编译工具环境和窗口启动策略仍需后续完善。

## 修改文件

- `MainActivity.kt`、`ui/MainToolbar.kt`、`values/strings.xml`：统一入口/菜单、运行回调保留选择、任务输入和焦点路由。
- `terminal/shell/TerminalCatalog.java`、`ShellSessionRegistry.java`、`ShellRuntime.java`、`ShellTerminalPane.java`：会话管理、独立Runtime、嵌入模式/共享许可入口。
- `terminal/TaskTranscript.kt`：原真实执行结果的明确批量展示。
- 4个新测试文件及README/RESUME/进度/本报告。Gradle、依赖版本、Manifest、Actions和全部上游Termux源码/JNI未改。

## 实际验证

- 第一轮完整构建1m13s：302项测试301通过/1既有跳过。
- 补实际界面方法测试后第二轮54s；再修任务菜单竞态，最终完整构建**1m9s**，152任务19执行/133未变化。App **306项：305通过、1既有Windows符号链接跳过**，原287项名称/结果保持，新增19项全部通过。
- 新增7项Catalog身份/邻居选择/删除/快照/界限/名称回归，4项Robolectric注册表目录/独立Runtime/重建/重新启动寿命回归，4项任务结果/明确非交互/清空/ANSI与Unicode回归，4项Robolectric实际MainActivity面板方法/真实Views回归。
- 界面方法测试覆盖顶层只有终端/问题、仅一个嵌入Shell面板、任务stdout/stderr显示、问题页返回同一Shell、后台结果不覆盖页面或重开面板。测试用最小未测量层级调用真实生产方法，未执行Activity.onCreate完整启动、原生PTY、输入法或帧渲染；任务fixture不构成Android程序执行证据。
- Lint0错误/11既有警告，无新增。Python源码与实际APK runner.pyc各13/13，生成模板Python/主机C++17各7/7。终端核心145项源码无变化，复用原通过结果。没有新增Wandbox调用。
- APK CRC、v2签名、16KB ZIP对齐、关键DEX定义通过；JUnit/Robolectric/测试类未打包。libtermux.so、许可资产逐字节保持；旧稳定类保留，两个showOutput匿名类因UI替换移位，精确名单在results.json。全部历史dist制品哈希保持。
- **APK**：`dist/CodePocket-terminal-workflow-debug.apk`，22,678,342字节（21.63MiB），SHA256 `7b3c0419ec05901b4502cde75a16b76a4d511d493b7bd879c9559861368e95ed`。
- 证据`verification/terminal-workflow-2026-10-18/`：基线/备份、三轮构建、JUnit/Lint、旧成功APK、签名、包内Python/模板运行、verify-final.ps1、audit_workflow.py、results.json。原文件/SAF/草稿/CodeEditor/执行器/运行令牌/需求文档及旧测试源码哈希保持。仅本地提交，无push、发布或远端Actions。

## 接续

先将Python真实流式输出和多轮输入接入同一终端，明确等待/EOF/停止/超时及旧消息隔离；C++继续云端批量结果。完善终端滚动与进程退出、窗口启动/布局策略，之后再恢复动画；原P1 UI/Sora/智能补全/P2/P3需求保持。真正的任务PTY/成熟终端渲染统一仍待实施，不以本轮入口合并替代。

手机目前无法连接，**没有Android Shell启动/PTY/执行、实机键鼠/IME、手机平板/卓易通或FPS验收**；主机通过不等于这些项通过。上次已复现的Java初始化错误回归仍通过，但不能据此排除设备其他native或系统行为问题。
