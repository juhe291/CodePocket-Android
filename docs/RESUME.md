# CodePocket 当前交接状态

## 当前：P1 第一批已完成，继续开发（2026-10-16）

用户最新指示：“等所有的都弄完了再实机吧，继续”。**实机统一后置，不再作为阶段推进前置条件**；以下历史记录中的“先实机/进入 P1 前确认”不再是当前阻塞点。原需求和优先级不变，进度见 [DEVELOPMENT_PROGRESS.md](DEVELOPMENT_PROGRESS.md)。

- 已完成 P1 设置、独立语法主题/字号/行号/换行、2/4/8 缩进与输入开关、当前文件查找替换/跳转行、基础工作区淡入。详见 [本批报告](P1_SETTINGS_SEARCH.md)。
- 最后构建 54 秒；Kotlin 53 项中 52 通过、1 跳过；Python 源码/打包字节码各 13 项通过；Lint 0 错误、14 警告，APK 签名/CRC/ABI/对齐通过。
- 新产物：[CodePocket-P1-settings-search-debug.apk](../dist/CodePocket-P1-settings-search-debug.apk)，21,765,550 字节；SHA256 29dff4a9771cc325d68a056a49e61b0f61e8de18a684e2f73e1f4e9386a7dc6c。证据在 verification/p1-settings-search。
- 本批没有连接设备、安装 APK 或测帧率。上轮 P0 单台手机记录仍保留；完整兼容和输入体验不能据主机结果宣称通过。
- 下一批继续 P1 光标锚定补全列表、键鼠配置和可调面板；其后继续终端/动画/高亮，再进 P2 真实语义、多文件及本地 C++ 原型。不要重做已完成的阶段 0/P0，不重新创建项目。


## P0 回修时点记录（2026-10-16，历史）

P0 后续回修和主机验证已完成，本轮按用户选择先做主机测试；**最新 APK 未安装到手机，P0 实机验收未完成，P1 未开始**。详见 [本轮验证报告](P0_FOLLOWUP_2026-10-16.md)。下方初轮与增补记录保留其当时结论。

- 新 APK：[CodePocket-P0-resume-2026-10-16-debug.apk](../dist/CodePocket-P0-resume-2026-10-16-debug.apk)，21,738,146 字节；SHA256：04f9be7a951a0ecf370e96e190810a9a8a2339b531c7219d32fde7b2779af83b。
- 完整构建 54 秒；Kotlin 41 项中 40 通过、1 跳过；Python 源码/打包字节码各 13/13；Lint 0 错误、14 警告；签名/CRC/ABI/对齐通过。证据在 verification/p0-resume-2026-10-16。
- 修复同内容旧 checkpoint 引发的假冲突及 Bundle 重复标脏；导航、工作区与稳定文件 ID 在操作完成后一起排队保存，减少强制结束返回旧活动文件的问题；SharedPreferences 异步落盘仍存在极短窗口。
- 上轮手机为 25060RK16C / Android 16，已覆盖安装和冷启动、真实本地 Python 输出、2 个 py 的独立 stdin、数字输入自动保存。**旧 checkpoint 恢复测试重启回 main.cpp，失败；其后的旋转、停止、traceback 未执行，不能算通过。**
- 本轮初始 ADB 无设备。下次接入先复测活动标签落盘、强制结束/恢复后继续保存，再做横屏、Python 停止/错误定位、真实 SAF；只操作独立测试项目，现有数据备份在 .tools/device-backups。
- 原需求、Gradle、Manifest、Actions、runner.py 未改；P1 动画/候选 UI、P2 语义服务计划保留。本地已单独提交增补计划 a667a13；本轮 P0 代码另行留存，不 push/发布。

## 初轮 P0 记录（2026-10-15 主机验证时点）

2026-10-15：本轮 P0 实现、主机测试和 Debug 打包已完成；**设备验收未完成，P1 尚未开始**。下一步优先按 [P0_DEVICE_TESTS.md](P0_DEVICE_TESTS.md) 验证手机、平板和鸿蒙卓易通；进入 P1 前等待用户确认。

- 当前目录：C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android。原 OneDrive 桌面路径失效。
- 本轮 APK：[CodePocket-P0-debug.apk](../dist/CodePocket-P0-debug.apk)，21,737,438 字节，20.73 MiB，API 24+ / arm64-v8a。
- SHA256：0fcec1617a9bfff419239530b8a77d941b77e5c5e6636b8d9fcb957486d3af46。
- 最后完整构建 build-06-snapshot-final.log：BUILD SUCCESSFUL in 51s；62 任务，18 执行、44 未变化；Kotlin 35 项中 34 通过、1 Windows 符号链接用例跳过；Lint 0 错误、14 警告。
- Python 源码与实际 APK 字节码各 13/13 通过；签名/CRC/ABI/对齐通过；实际 Manifest 含私有 :python 服务；本次 Wandbox 预置三程序探测通过。
- ADB 无设备，没有 Android App 启动、IME、SAF 提供方或独立进程中止的设备运行证据。主机测试不能替代这些验收。
- Git 已建立阶段 0 基线 3094bfa，之后单独记录 P0；无远端、无 push/发布，GitHub Actions 未远端运行。

实现及限制详见 [P0_VERIFICATION.md](P0_VERIFICATION.md)。需求原文和历史 [阶段 0 审查](STAGE0_AUDIT_2026-10-15.md) 保留。

关键实现：稳定文件会话及独立 stdin、收键盘/小窗口布局、SAF 真实目录与最近项目、后台保存及磁盘草稿、外部冲突/部分写入恢复、完整 Python 运行资源快照、独立 Python 进程及中止、C++ 请求取消与错误分类/迟到结果隔离。

重要边界：外部编辑保存写回提供方；Python 在私有完整快照中执行，其运行产物不自动回写。SAF 不保证原子替换/所有移动能力，存储提供方没有统一强制超时。Python 6 秒字节码/10 秒父级总限时；C++ 45 秒父级等待；当前仍是预填 stdin 和结束后输出，不是实时终端。

本机构建：

```powershell
.\scripts\build-local.ps1 -ProxyHost 127.0.0.1 -UseLocalMirror
```

脚本使用 .tools 内便携 JDK/SDK、完整 Python 3.13.9 和依赖缓存；不能换回缺少 venv 的最小 Python。临时镜像仅在 .tools，正式依赖源不变。日志和报告快照位于 verification/p0，旧 APK 和阶段 0 产物保留。

## 2026-10-15 需求增补（仅文档）

- 已将动画系统、补全列表/按键/片段界面纳入 P1，将 C++ / Python 深度语义补全纳入 P2；详见 [增补计划](P1_P2_ANIMATION_COMPLETION_PLAN.md)。原有各阶段需求保留，与只读原始需求共同生效。
- 要求动画总开关及系统减少动画优先、平滑光标与面板过渡、候选随光标并避让 IME；性能以 60 FPS 为目标，输入、选区与文件安全优先。
- Sora、clangd、Jedi 等尚未选型、集成或验证；当前 EditText + 词法候选的能力没有变化。P2 必须用真实语言分析提供成员/参数/类型信息，失败时明确词法降级。
- 本次未改源码、Gradle 或 Actions，未新增 APK/重新构建；上文构建结果属于前一轮 P0。手机、平板、卓易通的动画/语义/键盘联合验收均待实施，不得据此标为通过。
- 接续点不变：先完成 P0 设备验收及回修，进入 P1 前等待用户确认；不重做阶段 0，不在本次增补中启动编辑器迁移。原始需求末尾的阶段 0 启动语是历史指令，不改变当前接续点。