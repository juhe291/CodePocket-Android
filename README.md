# CodePocket

Android 上的轻量 C++ / Python 学习 IDE：原生 Kotlin 界面 + Sora 编辑器，真实本地运行、真实终端，打开即用，不依赖 Termux 或账号。

## 下载

| 版本 | 文件 | 大小 | SHA-256 |
| --- | --- | --- | --- |
| 0.2.3 (5) | `dist/CodePocket-0.2.3-release.apk` | 108,820,432 B | `70f9ea67f033a5af1b2c14dcb937997d5c861658d17a1ff33c8571011e99dca0` |

- 正式 Release 构建，沿用仓库原有 Debug 证书，可直接覆盖安装旧版（数据保留）。
- 未发布 GitHub Release；升级前请先导出重要内容。
- 安装后在 **设置 → 检查更新** 可验证更新链路（根页与“帮助与许可”均有入口）。

## 功能

**编辑**
- Sora 编辑器、多标签、文件树、项目搜索、查找替换、跳转行。
- 深/浅/跟随系统主题；Dark+ / Light+ / 可导入 JSON 的自定义 TextMate 主题（真实作用于编辑器和预览）。
- 自动保存 + 恢复草稿 + 冲突提示，保存失败不丢正文。

**运行**
- Python 3.13 本地运行，真实终端交互输入，Ctrl+C 停止。
- C++ 两种方式：**设备本地 Clang**（真实终端、可键入输入、不联网）或 **Wandbox 云端**（确认后发送源码与输入、预填输入框、完成后返回）。
- 单一真实 PTY 终端：Shell、Python、本地 C++ 共用；面板可调大小、最大化、收起。
- Python 断点：点行号栏标记，F5 后可用 `p` / `n` / `s` / `c` / `q` 调试。

**辅助**
- 词法补全、函数参数参考、代码片段、C++ 头文件 Tab 补全。
- Python Jedi 语义补全与 C++ clangd 语义分析（明确区分语义/词法，失败自动降级）。
- 本地 Git 状态/提交/历史；HTTPS 远端克隆、获取、快进、推送。
- 网页辅助栏：系统 WebView、必应/百度搜索、左右停靠、地址输入可收起。

**设置与更新**
- 分类设置中心，支持多词/别名搜索。
- 启动时自动检测更新（默认关闭，每进程一次，仅新版本提示）。

## 使用提示

- 切换到 C++ 本地方式：**设置 → 终端与运行 → C++ 运行方式**。
- 收起网页地址栏：网页辅助栏标题栏左侧折叠按钮；状态会记忆。
- 检查更新：**设置根页 → 检查更新**，或 **设置 → 帮助与许可 → 检查更新**。
- 自定义主题：**设置 → 外观 → 语法主题 → 自定义**，编辑或载入模板 JSON → 验证 → 应用。

## 构建

需要 JDK 17、Android SDK 35 / Build-Tools 35.0.0、NDK 27.2.12479018、Gradle 8.9、Python 3.13。仓库自带离线工具缓存；构建不会隐式下载依赖。

```powershell
# Debug
./.tools/gradle-8.9/bin/gradle :app:assembleDebug --offline --no-daemon `
  -Pkotlin.compiler.execution.strategy=in-process `
  -Pcodepocket.buildPython="$PWD/.tools/python313-full/tools/python.exe" `
  -Pcodepocket.semanticWheels="$PWD/.tools/semantic-wheels" `
  -I .tools/mirror.init.gradle -I scripts/host-tests.init.gradle

# Release（沿用原 Debug 证书，正式候选用）
./.tools/gradle-8.9/bin/gradle :app:assembleRelease --offline --no-daemon ...（参数同上）

# 测试 + Lint
./.tools/gradle-8.9/bin/gradle :app:testDebugUnitTest :app:lintDebug --offline --no-daemon ...
```

- Windows 非 ASCII 路径下必须加 `-Pkotlin.compiler.execution.strategy=in-process`。
- 本机 App 回归当前 1193 项：1191 通过、1 个既有 Windows 头文件大小写夹具失败、1 个旧跳过；完整命令退出 1，不称全绿。
- Release 变体使用原 Debug 证书仅为可覆盖升级；正式上架需另建发布密钥（不在仓库生成）。

## 状态与边界

- Android 设备验收（真实 PTY/IME、断点调试、主题观感、更新网络、C++ 本地工具链）仍待进行；主机/Robolectric/CI 结果不等于设备通过。
- 远端 CI（Linux）：Python/静态分析、Kotlin + Lint、Shell/片段、APK 构建与签名、包内 Python/语义。结果以 [Actions](https://github.com/juhe291/CodePocket-Android/actions) 真实记录为准。
- P3-D（JavaScript + tree-sitter 等多语言分析）已完成调研，尚未实现。

## 文档

- [交接状态与后续计划](docs/RESUME.md)
- [P3 实施计划](docs/P3_IMPLEMENTATION_PLAN_2026-11-02.md)
- [本版交付记录](docs/P3C_BREAKPOINTS_UPDATES_DELIVERY_2026-11-03.md)
