# CodePocket 当前交接状态

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