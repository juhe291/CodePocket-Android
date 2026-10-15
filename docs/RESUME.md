# CodePocket 当前状态

2026-10-15：已按新需求完成阶段 0 审查并重新构建，**P0 尚未开始，等待用户确认**。完整结果与文件级计划见 [STAGE0_AUDIT_2026-10-15.md](STAGE0_AUDIT_2026-10-15.md)，确认后的需求原文见 [DEVELOPMENT_REQUIREMENTS.md](DEVELOPMENT_REQUIREMENTS.md)。

- 当前实际路径：`C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android`；原 OneDrive 桌面路径已失效。
- 新基线 APK：[CodePocket-stage0-2026-10-15-debug.apk](../dist/CodePocket-stage0-2026-10-15-debug.apk)，21,609,140 字节，20.61 MiB，arm64-v8a / API 24+。
- 本次 Gradle 强制重跑：53 秒，62/62 任务执行，BUILD SUCCESSFUL。Kotlin 16 项：15 通过、1 个 Windows 符号链接用例跳过；Lint 0 错误、12 警告。
- Python 源码与 APK runner.pyc 主机回归各 13/13 通过；签名、CRC、ABI、包信息和 ZIP 对齐通过；本次 Wandbox 三个真实接口用例通过。
- 无已连接 Android 设备；未实测 App、手机/平板 IME、键鼠或鸿蒙卓易通。无 Git 仓库/远端，Actions 未实际运行。
- 关键 P0：键盘及窗口状态、真实 SAF 目录/可靠保存、按文件 stdin、运行中止及服务错误分类。
- 当前 stdin 是全局共享，SAF 仅单文件导入副本，Python 原生阻塞不能强制超时；不要把这些标成已完成。
- 本阶段未修改应用、测试或构建配置；源码前后哈希相同。日志、报告快照、许可证元数据、有限超时复现和哈希清单位于 verification/stage0-2026-10-15。

本机通常构建：

```powershell
.\scripts\build-local.ps1 -ProxyHost 127.0.0.1 -UseLocalMirror
```

脚本优先使用 .tools/python313-full/tools/python.exe，不能改为缺少 venv 的最小嵌入 Python。临时镜像和工具缓存位于 .tools 中；正式仓库仍使用官方源。本次额外的 --rerun-tasks 命令记录见审查报告及 invocation.json。

旧 dist/CodePocket-v0.2-debug.apk 和 [VERIFICATION.md](VERIFICATION.md) 保留历史结果；两次包大小与哈希不同，以本次阶段 0 APK 和报告作为后续比较基线。下一步需用户确认进入 P0，随后按报告的小批次实施和测试。
