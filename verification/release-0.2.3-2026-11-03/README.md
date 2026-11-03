# CodePocket 0.2.3 正式 Release 候选证据（2026-11-03）

对应交付记录：[docs/P3C_BREAKPOINTS_UPDATES_DELIVERY_2026-11-03.md](../../docs/P3C_BREAKPOINTS_UPDATES_DELIVERY_2026-11-03.md) 第 10 节与 [README](../../README.md)。

## 产物

- `dist/CodePocket-0.2.3-release.apk`（不提交 Git）：108,820,432 字节，SHA256 `70f9ea67f033a5af1b2c14dcb937997d5c861658d17a1ff33c8571011e99dca0`。
- 版本 0.2.3(5)，release 变体不可调试；沿用仓库原 Debug 证书（证书 SHA-256 `d58e4cd7…d10408`，与 0.2.2 Debug 包相同），可直接覆盖旧版。
- v2 签名、`zipalign -c -P 16 4`、920 entries、25 原生库、Python/C++/终端许可证资产齐全；记录见 `apk-signature.txt`、`apk-zipalign.txt`、`apk-badging.txt`、`apk-inspection.json`。
- release 包内 Python 51 项、语义 22 项通过（`release-packaged-python.*`、`release-semantic.*`）。

## 代码与验证

- 本批代码改动：运行/权限/Shell/诊断文案区分本地 C++ 与 Wandbox 云端；设置根页“检查更新”按钮；网页辅助栏地址输入收起/展开并记忆；release 构建配置；README 重排（旧原文见 `docs/HISTORY.md`）。
- App 全量 1193 项：1191 通过、1 个既有 Windows 头文件大小写失败、1 个旧跳过（JUnit 在 `final-junit/`），Lint 0 error（`lint-*.xml/txt`）；完整命令真实退出 1，不称全绿。
- 定向（`settings-web-targeted.log`）：AppUpdateEntryBaseline 3、WebAuxiliaryBaseline 6、WebAuxiliaryIntegration 12、SettingsSurfaceVisual 4、MainRunIntegration 12、TaskTranscript 4、CppSemantic 7 全通过。
- 构建命令见 `release-build.log`；非 ASCII 工程路径下 release 编译需 `-Pkotlin.compiler.execution.strategy=in-process`，否则 Kotlin daemon 参数文件会把中文路径转义错。

## 边界

- 未在 Android 设备安装/运行；断点、主题、更新网络、PTY/IME、本地 C++ 均仍需设备验收。
- 未推送 0.2.3 前，不以 0.2.2 的远端 CI 结果代表本版；推送后以真实 Actions 记录补充。
- 未发布 GitHub Release，未上传 APK 或密钥。
