# Python 启动等待与终端输入方式修正（2026-10-18）

基线 d84dae0。用户反馈点击 Python 运行后一直“正在连接”并最终超时；随后要求 Python 不再提供预填输入，采用终端交互。手机仍未接入，本轮没有设备日志，不能证明只有一个设备故障。

## 已确定的代码问题与修正

原 ShellRuntime 只构造 TerminalSession，直到 TerminalView.updateSize 的视图测量才实际创建 PTY/Shell。PanelLayoutPolicy 在代码输入法显示、编辑器有焦点时会临时隐藏终端；面板逻辑上已打开但实际上 GONE 时，原 runCode 又不会重新展开。由此存在“Python等待Shell提示符、Shell等待界面尺寸”的确定启动依赖。这与用户反馈相符，但没有手机日志时不将它当作唯一根因。

修正 ShellRuntime 会话创建后立即用80列24行建立真实PTY；显示或调整视图后仅调整同一个PTY尺寸。进程生命周期不再依赖View的首次测量。运行操作收起代码软键盘，按面板实际可见状态重新展开；不自动弹出终端输入法。字体初始化和异常隔离保持。

新增启动阶段：准备本地终端→创建本地桥→等待Shell提示符→等待app_process连接→连接Python独立服务→初始化Python运行时。启动超时包含实际阶段；取消/关闭会丢弃该次调度回调，旧会话身份门控保持。不再只有笼统的连接信息。

没有用原批量结果视图替代真实输出，也没有伪造输出或把失败切换为演示。Chaquopy初始化和FD路径另行复查，没有证据确认其为本次原因，因此没有改动该后端。

## 输入方式按用户最新要求调整

- Python运行固定交互，移除预填输入框、工具栏输入按钮、预填菜单和输入模式开关；input()在同一真实终端中输入并按Enter，Ctrl+D结束输入，Ctrl+C停止。设置中仅保留使用说明。
- 忽略旧pythonInteractive=false设置，其他设置正常迁移。已有每文件stdin数据保留，不清理用户会话；Python不再读取它。
- C++仍为Wandbox真实云端、预填stdin后完成返回。仅C++文件保留明确的云端输入入口；没有伪装成运行中交互。若后续要C++也使用本地交互，仍需原计划中的手机C++工具链，当前未实现。

## 修改文件

ShellRuntime.java：主动建立PTY、启动脚本发出回调及取消清理。
RunSession.kt：启动阶段及分阶段超时信息。
MainActivity.kt：运行时显示面板，Python固定交互，按语言隐藏输入框/入口和恢复焦点。
AppSettings.kt、SettingsDialog.kt、strings.xml：取消Python预填模式，兼容旧设置并更新说明。
ui/MainToolbar.kt：输入按钮按语言可用性隐藏。
TerminalWorkbenchViewTest.kt：新增实际原生Views的Python输入框/按钮隐藏、C++云端输入保留回归。
TerminalRunClockTest.kt：把已撤销的预填开关持久化测试替换为旧配置兼容性回归；其余计时测试保持。
README、进度及接续文档同步更新。没有修改原保存/SAF/草稿/CodeEditor、Manifest、Gradle、Actions、Python源码或上游Termux协议/JNI。

## 真实验证与边界

- 两轮 Debug 构建成功，最终1m6s；App 327通过/1既有跳过，共328。前批除被撤销模式测试以外325项状态保持，新增两项语言UI回归；终端核心146通过，结果复用未改动核心的通过缓存。
- Lint 0错误/11既有警告；APK实际runner.pyc仍29/29，真实生成模板Python7/7、主机C++17 7/7。
- ZIP CRC、Python资产、v2签名、16KB ZIP/ELF对齐、新桥类/JNI、未打包测试类、原许可证及旧dist哈希检查通过。
- 新APK：dist/CodePocket-terminal-startup-debug.apk，22,678,342字节，SHA256 45b938879b7fa15d1ee636c044c7007924ac876a64922a8ec1a0e530a8a59e76。
- 本轮没有Android PTY、app_process、SCM_RIGHTS、Chaquopy、IME或鸿蒙卓易通的设备执行。新的头部启动顺序通过代码检查和Android编译，不能称为手机输出恢复已验收。
- 后续设备应覆盖代码键盘展开且面板临时隐藏的首次运行、首次无View尺寸启动、两轮input/print、停止/EOF、多个终端与旋转；若仍连接超时，读取新版本显示的准确阶段和真实终端错误继续定位。
- 证据在verification/terminal-startup-2026-10-18：before.json、两轮构建、打包字节码/签名/模板/对齐日志、JUnit/Lint、results.json。没有push/发布/远端Actions/设备连接，动画仍暂停。
