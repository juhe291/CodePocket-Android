# CodePocket · Android C++ / Python IDE

原生 Android（Kotlin + Android Views）的离线学习用 IDE：正式 Sora 编辑器、Python/C++ 真实运行、PTY 终端、词法/语义辅助、项目与 Git 工作台、网页辅助栏。面向手机与平板，不依赖 Termux 或云端账号。

## 当前版本

**0.2.3 (5) · 正式 Release 构建候选（2026-11-03）**

- 安装包：`dist/CodePocket-0.2.3-release.apk`
- 大小：108,820,432 字节
- SHA256：`70f9ea67f033a5af1b2c14dcb937997d5c861658d17a1ff33c8571011e99dca0`
- 签名：仓库原有 Debug 证书（与历史版本同证书，可直接覆盖升级；release 变体不可调试）
- 未发布 GitHub Release / 公开下载；升级前建议导出重要内容
- Android 设备验收（真实断点、主题、更新网络、PTY/IME）仍后置；本仓库的 CI 是主机/Robolectric 验证，不等于设备通过

本版改动：

- 运行与权限说明、Shell 关于页、状态栏与诊断文案不再一律写“C++ 云端”，明确区分 **设备本地 C++** 与 **Wandbox 云端**，并说明本地可交互、不联网。
- 设置根页新增“检查更新”按钮；“帮助与许可”页保留同一手动入口。
- 网页辅助栏顶部地址输入支持收起/展开，并记住状态；收起后仍可导航、刷新。
- 新增正式 release 构建；沿用原 Debug 证书，不重建密钥。

本机验证：App 1193 项中 1191 通过、1 个既有 Windows 头文件大小写夹具失败、1 个旧跳过；Lint 0 error；Release APK 包内 Python 51 项、语义 22 项通过，签名/16 KiB 对齐/归档/版本检查通过。完整命令真实退出 1，不称本机全绿。

## 功能

**编辑与工作台**

- 正式 Sora 编辑器（TM4E TextMate 语法着色），多标签、文件树、项目搜索、跳转行/查找替换。
- 深/浅/跟随系统主题；Dark+/Light+ 及有界自定义 TextMate 主题（JSON 导入、模板、验证、预览，真实作用于 Sora 与原生预览）。
- 保存/恢复保护：停顿保存、恢复草稿、冲突与失败提示，切工作区不丢编辑。

**运行与终端**

- Python：Chaquopy 本地进程在真实 PTY 运行，支持 `input()` 交互、Ctrl+C 停止；按文件的断点标记与真实 `pdb` 调试（`p/next/step/continue/quit`）。
- C++：两种方式——**设备本地 · Clang**（真实终端、可直接键入输入、不联网）或 **Wandbox 云端**（明确确认后发送源码与 stdin、预填输入、完成后返回）。
- 单一真实终端：Shell、Python、本地 C++ 共用；终端配色、字号、面板尺寸/最大化/收起；运行面板独立输入区仅用于云端 C++。

**代码辅助**

- 词法补全、内置参数参考、C++/Python 代码片段、头文件路径 Tab 补全（均为明确的非语义能力，失败降级保留基础功能）。
- Python 语义补全/参数（Jedi）+ C++ clangd 语义（按 P2/P3 批次）；补全、参数与诊断均不自动上传代码。

**Git、文档与网页**

- 本地 Git：状态/差异/暂存/提交/历史；HTTPS 远端：克隆、获取、安全快进、单分支非强制推送、凭据管理。
- Markdown 预览、Python/C++ 标准库参考；网页辅助栏（系统 WebView，默认关闭，必应/可选百度，左右停靠、调宽、专注视图）。

**设置与更新**

- 分类设置中心：多词/别名搜索、取消/应用、恢复草稿、即时预览。
- 更新：设置 → **检查更新**（根页按钮或“帮助与许可”入口）→ 读取本仓库正式 Release；默认关闭的“启动时自动检测更新”，每进程一次，仅发现新版本时提示。
- 字体、缩进、补全按键、自动保存、终端、动画/光标等偏好；通知/网络权限遵守最小化与明确提示。

## 安装与升级

1. 安装 `dist/CodePocket-0.2.3-release.apk`；与 0.2.x 同签名，可直接覆盖旧版。
2. 首次启动可继续使用内部项目；重要内容请先导出。外部项目通过系统 SAF 目录授权。
3. 设置 → 检查更新可验证更新链路（未发布 Release 时会明确提示）。

## 使用要点

- **运行 C++**：设置 → 终端与运行 → C++ 运行方式，选择“设备本地 · Clang21”或“Wandbox 云端”。云端首次运行会弹确认；取消不会发送任何内容。
- **检查断点**：打开 `.py` 文件，点击行号栏设置/取消断点；F5 运行后命中断点可在终端输入 `p 变量`、`n`、`s`、`c`、`q`。
- **收起网页地址栏**：打开网页辅助栏后，点标题栏左侧的折叠按钮（地址输入右侧）；再次点击展开，状态会随窗口保存。
- **自定义语法主题**：设置 → 外观 → 语法主题选“自定义”，编辑/载入模板 JSON；“验证主题”后应用。仅支持有界离线 TextMate 子集，不支持 include/URL/脚本。
- **更新**：设置根页“检查更新”或“帮助与许可 → 检查更新”；“启动时自动检测更新”默认关闭。

## 构建与测试

环境：JDK 17、Android SDK 35 / build-tools 35.0.0、NDK 27.2.12479018、Gradle 8.9（离线缓存）、Python 3.13（Chaquopy）。仓库自带 `.tools` 工具与缓存；构建脚本不会隐式联网。

```bash
# Debug APK
./.tools/gradle-8.9/bin/gradle :app:assembleDebug --offline --no-daemon \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Pcodepocket.buildPython=<repo>/.tools/python313-full/tools/python.exe \
  -Pcodepocket.semanticWheels=<repo>/.tools/semantic-wheels \
  -I .tools/mirror.init.gradle \
  -I verification/cpp-local-integration-2026-10-27/host-tests.init.gradle

# 正式 Release APK（release 变体沿用原 Debug 证书；需 .tools/android-user/debug.keystore）
./.tools/gradle-8.9/bin/gradle :app:assembleRelease --offline --no-daemon \
  -Pkotlin.compiler.execution.strategy=in-process ...（其余参数同上）

# App 单元测试 + Lint
./.tools/gradle-8.9/bin/gradle :app:testDebugUnitTest :app:lintDebug --offline --no-daemon ...
```

说明：

- 完整 App 回归当前为 1193 项：1191 通过、1 个既有 Windows 头文件大小写失败、1 个旧跳过，故命令真实退出 1；不称“全绿”。
- Python 源码测试另跑 `tests/`（需设置 `PYTHONPATH` 指向 `.tools` 内语义/格式化/Git 探针与 `app/src/main/python`）；APK 包内 Python/语义由 `scripts/verify_apk.py`、`scripts/verify_semantics_apk.py` 校验。
- CI：GitHub Actions 在 Linux 上跑 Python/静态分析、Kotlin+Lint、Shell/片段、APK 构建与签名、包内 Python/语义。0.2.2 最终提交的 [run 41655045210 attempt 2](https://github.com/juhe291/CodePocket-Android/actions/runs/41655045210) 曾真实全绿；0.2.3 推送后的结果以真实 Actions 为准。

## 目录

- `app/`：App 主体（原生 UI、设置、运行、更新、终端桥接、Python 资源）
- `editor-sora/`：Sora 生产适配与自定义 TextMate 主题
- `editor-assist/`、`workspace-core/`、`terminal-view/`、`third-party/termux/`：辅助与终端组件
- `scripts/`：构建准备与 APK/包内运行时验证
- `tests/`：宿主 Python/Git/格式化/终端测试
- `verification/`：各批真实日志、JUnit、APK 校验与截图证据
- `docs/`：交付记录、交接状态、历史；**旧 README 历史记录已存至 [docs/HISTORY.md](docs/HISTORY.md)**
- `dist/`：本机构建 APK（不提交 Git）

## 已知边界

- 未在 Android 设备执行：真实 PTY/IME、断点调试、自定义主题观感、更新网络与安装、C++ 本地工具链；需要后续设备验收，不能把主机/Robolectric/CI 结果当设备通过。
- P3-D（更多语言分析：JavaScript + tree-sitter 等）仅完成调研，尚未实现；P3 目标未完成。
- Release 变体使用原 Debug 证书以便覆盖升级，不是 Play 商店生产签名；真正上架前需另建发布密钥（不在本仓库生成）。
- 网页辅助栏受系统 WebView 限制；下载、权限请求等明确拒绝并提示。

## 相关文档

- [当前交接与状态](docs/RESUME.md)
- [P3-C 交付与远端 CI 记录](docs/P3C_BREAKPOINTS_UPDATES_DELIVERY_2026-11-03.md)
- [P3 实施计划](docs/P3_IMPLEMENTATION_PLAN_2026-11-02.md) · [P3-D 语言调研](docs/P3_D_LANGUAGE_RESEARCH_2026-11-03.md)
- [历史 README（整理前原文）](docs/HISTORY.md)
