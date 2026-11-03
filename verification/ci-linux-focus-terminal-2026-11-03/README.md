# Linux CI 两项失败修复的证据（2026-11-03）

对应报告：[docs/CI_LINUX_FOCUS_TERMINAL_FIX_2026-11-03.md](../../docs/CI_LINUX_FOCUS_TERMINAL_FIX_2026-11-03.md)。本目录只保留真实远端与本地复现证据，不包含生产源码副本。

## 远端真实依据

- `latest-remote-reports.zip`：GitHub Actions 制品 `13223450909`（run 41618338816），253409 字节，SHA256 `23dd021d2fa87330b59c1085b83471b542422c2e2173dc0f155d4832ce7d9519`，与交接记录一致。
- `download_ci_artifact.py`：只读下载脚本；使用本机已有凭据，只在 api.github.com 带 Basic Auth，跟随重定向到制品存储时去除 Authorization，不打印令牌。本次下载仅用于读取失败报告，未推送/上传/发布。
- `final-remote-ci.log`：该 run 的完整控制台日志（1173 tests completed, 2 failed；后续 Lint/APK 跳过）。
- `com.codepocket.ide.SoraWorkbenchIntegrationTest.html`、`com.codepocket.ide.TerminalDockIntegrationTest.html`、`remote-report-index.html`：远端 Gradle HTML 报告中的失败页与索引，保留原始堆栈。

## 本地复现与修复证据

- `probe-before-fix.log`：修复前、同一重建场景下注入进程级旧 `SERVICE` 结果时，`PROBE staleServiceResult pane=false selectedOutput=1`，复现远端 `shellPane == null` 签名（临时探针已删除，未进入提交）。
- `targeted-after-fix.log`：修复后定向回归（`SoraWorkbenchIntegrationTest` 32、`TerminalDockIntegrationTest` 5、`MainRunIntegrationTest` 12、`TerminalWorkbenchViewTest` 12，共 61 项，全通过，退出 0）。
- JUnit XML：上述四个类的真实结果（含新增的陈旧结果回归测试）。
- `probe-recreate2.log`：本机配置变更探针，证明本机正常重建后真实终端面板非空，失败具有平台/状态条件。
- `changed-source-sha256.txt`、`SHA256SUMS.txt`：本批改动源文件与证据文件摘要。

## 边界

- 本机无 WSL/Docker 与 Linux Robolectric，远端未推送、无法复跑新 CI；不能把本目录当作“远端 CI 已通过”。
- 修复依据是远端真实堆栈 + 随包 Sora 字节码 + 本机同签名复现；远端复跑需用户允许推送后进行。
- 未重跑完整 1173 项与 Lint，未重建 APK；设备验收后置。
