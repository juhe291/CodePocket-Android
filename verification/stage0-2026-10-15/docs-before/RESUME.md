# CodePocket 当前状态

2026-10-15 已恢复工作，完成本地测试、Lint、Debug APK 构建与产物检查。完整记录见 [VERIFICATION.md](VERIFICATION.md)。

- 可交付文件：[CodePocket-v0.2-debug.apk](../dist/CodePocket-v0.2-debug.apk)，20.67 MiB。
- Kotlin 16 项：15 通过、1 个 Windows 符号链接用例跳过；Python 源码与打包字节码各 13 项通过。
- Lint 0 错误、12 条已记录警告；签名、ZIP CRC、ABI、包信息和 ZIP 对齐检查通过。
- 未连接安卓设备，实机测试未执行；没有远端仓库，GitHub Actions 未实际运行。
- 项目实际路径为 C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android，已从原 OneDrive 桌面位置迁移。

本机构建：

    .\scripts\build-local.ps1 -ProxyHost 127.0.0.1 -UseLocalMirror

脚本优先使用 .tools/python313-full/tools/python.exe，不能改回缺少 venv 的最小嵌入 Python。临时镜像和全部工具缓存留在 .tools 中，不提交。下一阶段是按 DEVICE_TESTS.md 验证实际设备行为，不要把主机打包检查视为设备验收。
