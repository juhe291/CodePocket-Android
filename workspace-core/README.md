# Shared workspace storage

Android Views 的原生 IDE 与独立 Sora 实验复用同一份工作区、SAF、会话和恢复草稿实现。Kotlin/AGP/JDK/minSdk 沿用主工程；保留原包名 `com.codepocket.ide`，调用方不改变 API。

本批七份 Kotlin 源文件从 app 原样移动，逐字节哈希与移动前相同。保存的临时文件同步/原子替换、冲突校验、提供方原文备份/写后校验、路径与符号链接保护保持原实现。库不包含编辑器、终端、运行器或权限声明。

共享代码不意味着共享应用私有目录：主包与实验包的 applicationId 和 Android UID 不同。只有用户分别授权的系统目录能通过 SAF 写回同一外部项目。

原有工作区/草稿/会话测试继续由 `:app:testDebugUnitTest` 执行；实验通过真实 Sora 与 ContentResolver 的文件会话测试覆盖新的调用路径。详见 [本批报告](../docs/P1_SORA_DOCUMENTS_2026-10-21.md)。
