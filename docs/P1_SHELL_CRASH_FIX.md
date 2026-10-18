# Shell 点击闪退：复现与修复（2026-10-18）

基线 `edf3cf4773cb9451dab26c6025755d1d389d38fa`。用户要求先暂停动画、排查点击Shell终端闪退，当前手机无法连接。仅修复启动问题与补充诊断，没有把测试输出当作真实Shell。

## 已复现的原因

旧代码在`ShellTerminalPane.changed()`中先调用`TerminalView.setTypeface(Typeface.MONOSPACE)`，再调用`setTextSize()`。固定Termux v0.118.3的构造器没有创建`mRenderer`；`setTypeface()`第504行直接读取`mRenderer.mTextSize`，而`setTextSize()`才会创建绘制器。因此首次出现会话、创建视图时确定触发空指针，尚未走到attachSession/终端尺寸初始化/PTY进程创建。

此外，原异常保护只包住attachSession/onScreenUpdated，字体设置在保护之外。ShellRuntime的try/catch位于工作线程，捕获不到稍后主线程Runnable里的异常，因此该异常直接向主线程传播。

实际旧版回归栈（证据reproduction-junit）：

```text
java.lang.NullPointerException: Cannot read field "mTextSize" because "this.mRenderer" is null
    at com.termux.view.TerminalView.setTypeface(TerminalView.java:504)
    at com.codepocket.ide.terminal.shell.ShellTerminalPane.changed(ShellTerminalPane.java:125)
```

Robolectric4.14.1 / Android SDK34在主机执行实际TerminalView/ShellTerminalPane初始化代码：3项初始测试中2项启动测试失败，原始调用顺序的异常断言通过。旧源码和编译class、完整XML/日志已保留，随后同一正向启动用例在修复代码通过。没有伪造View/绘制器来获得通过结果；Android平台部分由Robolectric提供，不能等同手机执行。

## 修复

- 先setTextSize创建绘制器，再setTypeface；局部ready视图完整配置字体后才赋值terminal，避免保留半初始化对象。
- 整个会话视图创建/绑定/刷新进入异常保护，主线程创建回调也在自身执行位置捕获RuntimeException/LinkageError。
- 延迟失败回调携带对应TerminalSession身份；布局错误取视图实际绑定的会话，过期失败不能关闭替代会话。失败一次性发布FAILED状态，不先发出EMPTY中间状态。
- 新增ShellFailureLog：仅记录最后一次已捕获异常、SDK/ABI和时间，不收集终端正文或上传；应用私有terminal-last-error.txt，写入/读取均最多64KiB。菜单“查看上次终端错误”可阅读、复制，无需USB。只对当前修复版本今后捕获的错误生效，不会补回旧版本已发生的崩溃日志；文件写入失败保留Logcat记录，不保证掉电原子性。
- 未修改上游组件源码、JNI、Manifest、文件/SAF/草稿/编辑器/原Python与Wandbox运行器。当前测试不需要连接手机。

## 实际验证

- 旧代码复现轮：4m9s，3项中2失败；真实空指针栈如上，未删掉失败用例。
- 首次修复完整验证：1m1s；随后将布局失败绑定实际视图会话，最终完整验证57s，152任务26执行/126未变化。
- App287项：286通过、1既有Windows符号链接跳过；原278项名称/状态均保留。新增5项Robolectric初始化/会话复用/迟到失败/可读错误状态回归及4项真实文件记录/Unicode限长/有界读取/缺失记录测试。原终端核心145项无源码变化，Gradle复用已通过结果。
- Lint0错误/11既有警告，无新增警告。Python源码和实际APK runner.pyc各13/13，实际生成模板Python/主机C++17各7/7；主机C++不代表手机本地C++。
- APK CRC、v2签名、16KB ZIP对齐通过；旧稳定DEX类保留，新增ShellFailureLog。布局回调从匿名FrameLayout移入共享方法，因此一个ExternalSyntheticLambda类替换为新的合成类，已逐项记录。Robolectric/JUnit/测试类没有打包，libtermux.so和终端许可资产与旧APK逐字节相同，全部历史dist哈希保持。
- 修复包：`dist/CodePocket-shell-crash-fixed-debug.apk`，22,678,342字节（21.63MiB），SHA256 `96cd81703e515190afb16aed4e03ad1e72368edf5581c846cde3c985e055c8a3`。
- 证据在`verification/shell-crash-2026-10-18/`：before.json/源码与class、失败/成功日志、reproduction-junit、junit、terminal-junit、results.json、Lint、签名、包内Python/模板回归、前一成功APK、verify-fix.ps1与audit_fix.py。制品审计脚本先补齐辅助函数导入，再核对并明确记录合成回调类迁移，没有放宽稳定类检查。

## 未验证与接续

没有设备日志，无法证明用户手机没有第二个故障；没有运行Android PTY/Shell、验证设备JNI权限、进程退出、IME/旋转/键鼠/卓易通或帧率。此处已确认并修复一个可独立复现、足以导致首次打开闪退的Java故障，不能据此声称手机已验收通过。捕获Java/链接异常不等于可捕获native SIGSEGV/SIGABRT或系统杀进程。

动画保持暂停。用户已明确要求统一为一个底部真实终端；旧输出入口与Shell并存的界面整合、Python/Wandbox接入同一区域仍待后续完成，不能直接删除原结果功能导致输出丢失。当前没有新Wandbox请求、设备连接、远端Actions、push或发布。
