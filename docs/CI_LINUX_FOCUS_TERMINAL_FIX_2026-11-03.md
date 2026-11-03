# Linux CI 两项失败修复（2026-11-03 接手批）

从本地 `1503fdf` / 远端最后已推送 `2a93d3d` 接续。用户指令：先处理 GitHub CI 剩余两项，再继续 P3-C/D；**不推送**，本批只本地提交。设备验收继续后置。

## 真实远端依据（本轮实际下载核对）

- CI：[run 41618338816](https://github.com/juhe291/CodePocket-Android/actions/runs/41618338816)，Linux App 1173 项中 2 失败，后续 Lint/APK 步骤未执行。
- 测试报告制品：id `13223450909`，253409 字节，SHA256 `23dd021d2fa87330b59c1085b83471b542422c2e2173dc0f155d4832ce7d9519`，与交接记录一致。用本机已有 GitHub 凭据只读下载（`download_ci_artifact.py`，已做跨域重定向去除凭据处理），只下载报告，未推送、未上传、未发布。
- 完整控制台日志：`final-remote-ci.log`（299532 字节），与交接的 `verification/cpp-source-download-fix-2026-11-03/final-remote-ci.log` 相同。
- 报告 HTML 中的真实失败堆栈：
  - `SoraWorkbenchIntegrationTest.delayedViewportAndBackgroundSelectionNeverStealTerminalOrInputFocus`，`SoraWorkbenchIntegrationTest.kt:116`，`AssertionError`（`assertTrue(input.hasFocus())` 失败）。
  - `TerminalDockIntegrationTest.resizingAndControlToggleKeepSessionBufferAndPreference`，`TerminalDockIntegrationTest.kt:49`，`NullPointerException: Cannot invoke "android.view.View.getContentDescription()" because "it" is null`；即配置变更重建后 `shellPane == null`，`capture()` 取不到真实终端面板。

## 根因与修复

### 1. Sora 后台选择抢焦点（生产契约缺陷）

`CodeEditor-javap.txt` 对随包 Sora 字节码的实际核对显示：`setSelectionRegion(int,int,int,int,boolean,int)` 方法开头无条件 `invokevirtual requestFocus()`。原 `WorkbenchEditor.select(...)` 仅在“编辑器当前未聚焦”时才用 `withoutFocusRequests` 抑制：

```java
if (!focus && !editor.hasFocus()) return editor.withoutFocusRequests(...);
```

`focus=false` 的调用方（`restoreSession()` 恢复保存的选区、延迟后台选择）明确要求不抢焦点；一旦此刻编辑器仍有焦点，就会走未抑制分支，被 Sora 的 `setSelectionRegion` 把焦点从 stdin/终端抢回编辑器。修复为：`focus=false` 一律在 `withoutFocusRequests` 中执行，不再依赖当前焦点状态；`focus=true` 行为不变。

该缺陷在 Windows 主机测试中因焦点状态时序不触发，Linux CI 触发；修复后本机 `SoraWorkbenchIntegrationTest` 32 项、`MainRunIntegrationTest` 12 项、`TerminalWorkbenchViewTest` 12 项全部通过，原断言未删除、未跳过。

### 2. 重建后终端面板丢失（`selectedOutput` 被陈旧结果切走）

新 Activity 第一次 `renderRunState()` 时实例字段 `result` 初始为 null，而进程级 `RunSession.result` 可能已有旧结果（上一轮运行/测试、重建前遗留）。原逻辑：

```kotlin
val previous = result
result = RunSession.result
if (!running && result?.kind == RunKind.SERVICE && result !== previous) selectedOutput = 1
```

在“重建后首次渲染 + 进程级旧 SERVICE 结果”时把非空旧结果误判为新结果，强制把恢复的终端通道（3）切到“问题”（1），`showOutput(1)` 因此不再调用 `ensureShellPane()`，`shellPane` 保持 null。用户真实场景（旋转/进程内重建后终端消失）同样受影响。

修复：只有本 Activity 已经完成过一次真实渲染（`runStateRendered`）后，检测到结果“变化”才自动切到问题通道；重建后首次渲染保留恢复的通道。`MainRunIntegrationTest` 原有“新 SERVICE 结果到达后切到问题通道”的行为测试仍通过（12/12）。

可复现证据（本机，修复前/后同场景探针，探针文件已删除，不加入生产/测试）：
- 修复前：`PROBE staleServiceResult pane=false selectedOutput=1`，与远端 `shellPane == null` 完全同签名；测试失败。
- 修复后：`pane=true selectedOutput=3`，通过。

同时把该场景固化为长期回归测试 `TerminalDockIntegrationTest.staleServiceResultDoesNotDropTheRestoredTerminalChannel`；并把配置变更测试改为跟踪真实重建后的 Activity（`ActivityLifecycleCallbacks`）并等待真实终端面板 `awaitShellPane()`，失败信息包含 `selectedOutput/panelVisible/destroyed/registrySelected` 状态。未删除任何原断言、未增加跳过。

## 本批验证范围（实际执行）

- 定向：`:app:testDebugUnitTest` 过滤 `SoraWorkbenchIntegrationTest`（32）、`TerminalDockIntegrationTest`（5，含新增 1）、`MainRunIntegrationTest`（12）、`TerminalWorkbenchViewTest`（12），共 61 项全通过，退出 0。命令与日志见本目录 `targeted-after-fix.log`；修复前探针日志见 `probe-before-fix.log`。
- 本机此前最新完整主体仍为本交接记录：1173 项 1171 通过、1 既有 Windows 头文件大小写冲突、1 旧跳过，Lint 零错误；本轮未重跑全套，不冒称。
- APK 未重建。

## 已知限制

- 本机无 WSL/Docker，无法在真实 Linux 运行 Robolectric；远端 CI 未经推送，无法复跑确认。上述两项修复基于真实远端堆栈、随包字节码和本机可复现机制；远端复跑结果仍需在用户允许推送后以新 CI 为准，不能现在宣称远端已通过。
- 设备验收（终端重建、焦点、旋转）继续后置。

## 涉及文件

生产：`editor-sora/src/main/java/com/codepocket/editorlab/WorkbenchEditor.java`、`app/src/main/java/com/codepocket/ide/MainActivity.kt`。
测试：`app/src/test/java/com/codepocket/ide/TerminalDockIntegrationTest.kt`。
证据：`verification/ci-linux-focus-terminal-2026-11-03/`（远端制品 SHA256、失败页、日志、JUnit、源文件 SHA256）。
