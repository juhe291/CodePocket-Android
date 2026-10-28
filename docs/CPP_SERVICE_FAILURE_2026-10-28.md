# 本地 C++ 服务错误排查（2026-10-28，设备根因待确认）

后续用户补充了“编译错误（130ms）”及退出码 signal 6，已转入 [SIGABRT 与地址空间限制修复](CPP_SIGNAL6_FIX_2026-10-28.md)。下文是此前“服务错误（5ms）”阶段的记录，保留当时的验证边界；当前修复候选与结果见新报告。

用户反馈本地 C++ 显示“服务错误（5ms）”，同版本 Python 正常；“问题”页只反馈“执行失败”。现有信息能缩小到 C++ 专用链路，但不能从时间或摘要确定设备根因。没有要求连接设备；本机 ADB 查询没有已连接设备，没有安装或运行新 APK。

本批围绕一个故障合并四项工作：规范化可信私有根目录、补充工具准备约束的具体说明、保留执行服务的失败阶段与异常原因、在新服务错误到达时主动显示“问题”正文。保留原生 Views/Sora、保存与恢复、头文件 Tab、真实 Python/C++/PTY、取消及进程所有权；暂停零碎外观打磨，没有扩展新功能。

源码另发现工具链准备对 Context.filesDir 使用 absoluteFile 后直接要求等于 canonicalFile；可信应用目录带合法别名时会立即失败。新增 CppRuntimePreparationTest 使用实际固定头文件/支持资产和路径别名验证该条件，同时保留不能覆盖已有私有文件的断言。修改前 2 项中 1 失败/1 通过，失败明确位于 CppRuntime.prepareArchive 第 19 行，原 JUnit/日志保留在 verification/cpp-startup-investigation-2026-10-28/runtime-baseline-*。这是独立可验证的兼容性缺陷，尚未证实是用户这次故障的原因。

随后修改：只在 Context 提供的可信私有根入口进行 canonical 归一化，子目录仍拒绝重定向，原固定文件/归档校验保持；为工具缺失、缓存创建和不可执行补充具体错误文字。runtime-targeted.ps1 已结束，14 项中 13 通过/1 失败。原根入口条件已通过，剩余失败移到 CppHeaderArchive.extract 的 createNewFile；固定归档包含 8 组仅大小写不同的 Android 头文件，本机实际文件创建探测确认大小写不敏感。失败日志/JUnit 和 host-case-sensitivity.json 已保留，未改断言、归档或添加跳过；这不是 Android 解压执行结果，不宣称用户设备 C++ 已修复。

服务异常现在保留最后阶段（校验运行副本、连接真实终端、准备工具链或后续阶段）、异常类型与原始说明。Messenger 测试验证失败回传前先释放自身子进程，原取消、重放和跨任务所有权测试保持。主工作台对新到达的服务错误自动打开“问题”正文；用户随后切换输出页，重复渲染不会再抢回。实际 Main/Sora 的 F5 缺失主机 app_process 路径验证此行为，不能把该主机错误当成用户设备错误。

定向记录 failure-reason-targeted-* 为 30 项全部通过；这是补充缓存夹具及截图之前的范围结果。修改前 Main 行为基线 8 项中 1 失败，服务原因基线 3 项中 1 失败，均保留。新增已完成缓存夹具隔离入口校验与 Windows ZIP 限制，不替代原完整解压测试。只对仓库内独立空测试目录启用大小写敏感被 Windows 拒绝，没有改变归档或跳过测试。

整批回归、Lint、Debug APK 集中命令使用 --continue，使回归失败时其他检查仍能完成。实际命令退出 1，BUILD FAILED，不能称整批通过。884 项中 878 通过/5 失败/1 旧跳过；旧 874 项身份和结果完全保持，无新增跳过。5 失败为上述完整准备测试的主机大小写限制及未实现的 Python 多文件 4 项。终端 146 项通过（Gradle 使用有效 UP-TO-DATE 结果），editor-assist 为 NO-SOURCE，相关契约在 App 测试中；Lint 0 错误，App 21/Sora 1 警告。

同一命令确实完成 packageDebug/assembleDebug。诊断候选包为 [CodePocket-cpp-startup-diagnostic-2026-10-28-debug.apk](../dist/CodePocket-cpp-startup-diagnostic-2026-10-28-debug.apk)，115,421,743 字节，SHA256 `7d6835f9b7adf28df9038d3f9a86ccc877fa843178e6c88e9836643ed93d5e5b`。原签名、CRC、16KiB 对齐、Manifest/权限及实际 C++ 闭包检查通过；25 原生映像/48 运行资产和 93 旧 APK 保持。实际包内 Python 39 项、语义 22 项本轮重新检查通过；语义首次因主机系统临时目录清理权限产生 30 个错误，原日志保留，设置仓库 TEMP/TMP 后另存重试结果 22 通过。不能将首次失败覆盖掉。此前 Python 源码 66、Shell 4/片段 14 没有重跑，不当作本轮结果。

[原生手机错误正文截图](../verification/cpp-startup-investigation-2026-10-28/previews/phone-service-failure.png)已查看，是真实 Main/Sora 缺失主机桥的失败展示；主机截图不证明设备视觉或 C++ 执行。最终结构化结果见 verification/cpp-startup-investigation-2026-10-28/results.json。此候选包不替换最后一份完整通过的设置触摸修复版；用户设备根因和效果仍待确认。

Python 多文件批次的两份新测试与计划完整保留。Python 基线本轮实际运行 5 个方法，1 通过/4 个方法报错（共 7 个子测试错误），原因是尚未实现 overlays_path，不是 7 个独立测试。本批不修改 Python 多文件生产代码，Kotlin 新测试在整批回归中照常执行，不能通过排除它们称“全绿”。后续多文件、批量用例、格式化和原 P3 继续保留。

所有记录位于 verification/cpp-startup-investigation-2026-10-28/。用户设备的根因和修复效果仍未验收，本批不宣称 C++ 已在手机运行成功；不推送、不发布、不新建自动任务。
