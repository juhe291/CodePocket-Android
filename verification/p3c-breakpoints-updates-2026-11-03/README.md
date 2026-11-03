# P3-C 主题/搜索、断点与自动更新批次的真实证据（2026-11-03）

对应报告：[docs/P3C_BREAKPOINTS_UPDATES_DELIVERY_2026-11-03.md](../../docs/P3C_BREAKPOINTS_UPDATES_DELIVERY_2026-11-03.md)。

## 结果摘要

- **App 全量 1191 项**：1189 通过、1 个既有 Windows 头文件大小写冲突失败（`CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs`）、1 个旧跳过；完整命令真实退出 1。最终 JUnit 在 `final-junit/`（156 个类）。
- termux terminal-emulator **146 项全通过**（`terminal-junit/`）。
- Lint **0 error**：`lint-app.xml` 37 warnings（既有更新对话框 SetTextI18n + 本批文案拼接提示）、`lint-editor-sora.xml` 1 warning（既有 GradleDependency）、`lint-workspace-core.xml` 0 warning。
- **Python 源码测试 175 项全部通过**（`source-python-tests.log`，退出 0）；包含 3 项真实 pdb 断点测试。
- 包内 Python/Git/格式化：`packaged-python.log`（51 项 runner/project/diagnostics，0 失败）、`packaged-runtimes.json`（真实 APK 内 `git_workspace.pyc` 21 项、`code_formatting.pyc` 20 项全通过，dulwich/urllib3/autopep8/pycodestyle 版本与来源来自包内实际字节）。
- 语义/矢量/Shell/片段：`semantic-apk-inspection.json`、`vector-resources.json`、`shell-startup-inspection.json`（4/4）、`snippets.json`（Python 7/7、C++ 7/7）。

## 最终 APK

- `dist/CodePocket-p3c-breakpoints-updates-2026-11-03-debug.apk`，**118,168,893 字节**，SHA256 `140e4d16092bddf83a5e4d094ac51adbc89fdacb2a44da54ee9f6591aa751b16`，0.2.2 (4)，原 Debug 签名。
- `apk-signature.txt`（apksigner verify）、`apk-zipalign.txt`（16 KiB）、`apk-archive.txt`、`apk-badging.txt`、`certificates.txt`、`permissions.txt`、`manifest-tree.txt`、`resources.txt`。
- `apk-inspection.json` / `runtime-delta.json`：相对上一有效 Bug 包（6949b5f6…）只改变 10 个条目（Manifest 版本、app.imy/build.json、4 个 dex、resources.arsc、2 个引用资源 ID 的 layout 二进制与签名容器），无条目新增/删除；25 个原生库、requirements-common.imy、C++ 运行资产与其余全部条目字节一致。同源首次增量打包出现 620 KB 孤儿本地条目，已清理输出后重新 `:app:assembleDebug`，旧中间包不交付。

## 远端 CI 最终结果（真实全绿）

`master` 最终 `768a1f1` + tag `v0.2.2` 的 [run 41655045210](https://github.com/juhe291/CodePocket-Android/actions/runs/41655045210)：第一次尝试在 `Install SDK 35` 因远端 SDK 下载失败中止（测试未执行，非代码失败）；通过 API 重跑失败 job 后 **attempt 2 全部步骤 success**（Python 执行/静态分析、SDK/NDK、固定 C++ 准备、Kotlin 1191 项回归+Lint、Shell、片段、Debug APK 构建、APK 归档/签名、包内 Python、语义、上传）。完整记录 `remote-ci-run-third-rerun.json`，APK 制品 `CodePocket-v0.2-debug-apk` 115446293 字节、报告 301392 字节。推送前两轮真实失败（`remote-ci-run-first.json`/`remote-ci-run-second.json`、`remote-failure-*.html`、`remote2-failure-*.html`、两份报告 zip）均保留，没有删除失败证据；四个 Linux 时序问题已在 `docs/P3C_BREAKPOINTS_UPDATES_DELIVERY_2026-11-03.md` 第 9 节按真实堆栈记录修复。Android 设备验收仍后置。

## 视觉

原生 Robolectric 渲染图（非设备截图）：`verification/p3-settings-themes-2026-11-03/previews/`
- `phone-dark-custom-theme-settings.png`：手机深色自定义语法主题设置（JSON 编辑器/预览/真实 Sora 预览）。
- `tablet-light-custom-theme-settings.png`：平板浅色同源设置。
- `phone-dark-custom-sora.png`：手机深色真实 Sora 编辑器使用自定义主题色（背景/`def`/`return` 断言自定义色）。
- `phone-dark-breakpoints.png`：手机深色断点标记（第 1、3 行）。

## 边界与未验证

- APK 只在主机与 Robolectric 校验；**未在 Android 设备执行**断点命中、Sora 主题、自动更新网络/对话框、真实 PTY。设备验收后置。
- 固定历史基线的 `verify_git_apk.py`/`verify_formatting_apk.py`/`verify_cpp_apk.py`/`verify_web_auxiliary_apk.py` 要求它们各自的旧候选 SHA，不适用于新批次；本批用 `runtime-delta.json` + 包内真实运行时测试提供等价证据，不冒称这些脚本已通过。
- 推送后 GitHub Actions 的真实结果另行核对；本目录不代表远端 CI 或设备通过。
