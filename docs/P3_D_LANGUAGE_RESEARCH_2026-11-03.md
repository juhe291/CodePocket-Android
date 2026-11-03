# P3-D「更多语言分析」技术选型调研（2026-11-03，只读）

本文件记录只读调研结论，**尚未开始实现**，不代表 P3-D 已完成。P3-C/断点/自动更新批次完成并推送后再进入 P3-D；原 P3 计划完成判据保持：先固定语言/后端/许可/体积，再以真实未保存正文验证诊断、候选、位置，明确区分语法诊断、词法参考与语义推断，不扩大宣传。

## 结论

- **首选**：JavaScript（ES2022+，`.js/.mjs/.cjs`；`.jsx/.ts` 后置），后端 **tree-sitter 0.27.1 + tree-sitter-javascript 0.25.0（均 MIT）**，源码自建 arm64 JNI 闭包。
- **备选**：Java + JavaParser core 3.28.2（Apache-2.0，纯 JVM，1.49 MB jar）；风险是 22 个类引用 `java.nio.file`（API 26），minSdk 24/25 需门控。
- **不做**：TypeScript 全类型（typescript.js 9.1 MB + JS 引擎）、Java 完整类型推断（symbol solver 约 6.1 MB + NIO）、esprima（仅 ES2017，现代语法误报，不可作为正式诊断后端）。

## 关键事实与本次实测

| 项 | 结果 |
| --- | --- |
| tree-sitter core | v0.27.1，tarball SHA256 `982cd3d4…f0f333`，MIT |
| tree-sitter-javascript | v0.25.0，tarball SHA256 `9712fc28…428b86`，MIT |
| arm64 JNI `.so` | NDK 27.2.12479018 交叉编译成功，**622,168 B**（strip），SHA256 `917a1c1c…ac5570`；AArch64 DYN，LOAD 对齐 0x4000，NEEDED 仅 libdl/libc |
| 解析能力 | `?.`、`??`、class fields 实测零错误；`let x = ;`、`function f( {` 返回真实 ERROR 节点 |
| 语言 ABI | grammar LANGUAGE_VERSION 15；core TREE_SITTER_LANGUAGE_VERSION 15、MIN_COMPATIBLE 13（注意 0.27 改名 `ts_language_abi_version`） |
| JavaParser | jar 1,493,312 B；D8 min-api24 dex 1,982,096 B / 16,188 method refs；host 解析通过；NIO API 26 风险 |
| 接入面 | `SemanticTransport`/`AndroidSemanticTransport(serviceClass)` 已参数化，可直接加 `:js_analysis` 服务；文件快照复用 `PythonSources/PythonProjectContext` 模式；诊断走 `EditorAnalyzer → SoraEditorPort` |

## 推荐能力口径（防止把词法叫着色成语义）

- **L1 语法诊断**：真实解析器 ERROR/MISSING 节点，UTF-16 范围，可下划线/详情。
- **L2 词法参考与候选**：真实 token/关键字/文件内标识符；明确标「参考」。
- **L3（有限）语义**：作用域/模块/导入/名称解析；**不含类型推断**。UI 禁用「类型检查/语义补全/IntelliSense」等词，使用「符号/名称解析」。
- 若首批只做到 L1+L2，必须在 UI/文档明确「本版未提供符号/成员解析」，不得显示「语义候选」标题。

## 首批最小验收清单（摘要）

真实未保存正文；跨文件 import/export；未保存导入文件/多草稿；UTF-16/中文/emoji/组合字符/CRLF；代理对边界拒绝；取消与迟到结果；用户已选候选不被替换；服务/JNI/解析失败回退词法且正文不变；错误位置映射；内部项目与 SAF；组合输入时不请求；主线程禁止分析；`.so` ABI/16KB/许可正文随包；API 24 arm64 设备验收后置。

## 实施顺序建议

1. P3-D-1：`Android.mk` + JNI 桥 + 固定源码/SHA + `JsSyntaxChecker` + `EditorAnalyzer` 分支 + 词法回退 + 许可入口；先交付 L1+L2。
2. P3-D-2：`:js_analysis` + `JavaScriptSemanticClient`/协议 + L3 作用域/导入解析，复用现有取消/迟到/回退链路。
3. 语言二值标志（`cpp`）需在三值化（PYTHON/CPP/JAVASCRIPT）后整批回归，不能只测新语言。

## 官方依据

- tree-sitter v0.27.1：https://github.com/tree-sitter/tree-sitter/releases/tag/v0.27.1 ；MIT：https://github.com/tree-sitter/tree-sitter/blob/v0.27.1/LICENSE
- tree-sitter-javascript v0.25.0：https://github.com/tree-sitter/tree-sitter-javascript/releases/tag/v0.25.0 ；MIT：https://github.com/tree-sitter/tree-sitter-javascript/blob/v0.25.0/LICENSE
- tree-sitter 查询文档：https://tree-sitter.github.io/tree-sitter/using-parsers/queries/
- JavaParser 3.28.2：https://repo1.maven.org/maven2/com/github/javaparser/javaparser-core/3.28.2/ ；许可：https://github.com/javaparser/javaparser/blob/master/LICENSE
- Android `java.nio.file`（API 26）：https://developer.android.com/reference/java/nio/file/package-summary
- TypeScript 5.9.3：https://www.npmjs.com/package/typescript/v/5.9.3

> 上述体积/交叉编译/解析结果来自一次只读调研实测；**未在 Android 设备执行**，设备验收按项目惯例后置。真正实现前仍需在实施批内重新固定制品并复算 SHA256。
