# 主工作台 SAF、草稿恢复与运行错误定位（2026-10-23）

本批接续 I2，在现有 MainActivity / Sora / Workspace / DraftStore / RunSession 中补上主工作台链路验证。没有发现新的生产缺陷，因此未修改运行、文件保存或编辑器生产实现；没有扩展独立实验界面。接下来推进 I3 精确光标动画与手机/平板界面，I2整体清单随最终主包继续核对。

## 实际修改

- 新增 `app/src/test/java/com/codepocket/ide/MainSafRecoveryIntegrationTest.kt`：14项实际 MainActivity 与 Sora 集成回归，ContentResolver / ParcelFileDescriptor 连接真实主机文件。
- 更新 README、RESUME、DEVELOPMENT_PROGRESS 与主程序集成计划，保留原 P0/P1/P2/P3 及历史结果。
- 本批完整日志、基线哈希、夹具纠正记录及构建/产物审核脚本在 `verification/main-app-saf-recovery-2026-10-23/`。本地提交，不推送、不发布。

## 通过的主程序链路

| 范围 | 实际检查 |
| --- | --- |
| 系统目录入口 | 只读返回权限在替换工作区前被拒；完整读写标记经过现有授权入口，实际读取不透明文档ID |
| SAF保存 | 中文/emoji真实写入提供方文件并读回验证，正确清理对应草稿；保存前原文恢复副本实际存在 |
| 权限撤销/只读 | 保存失败保留原文件、当前Content身份、脏状态和持久草稿；阻止工作区切换；重新授权/可写后重试成功 |
| 提供方故障 | 写后不一致时阻止标签切换、保留恢复原文和草稿；另一个文件读取失败时保住原标签/选区/Content；无效UTF-8工作区不替换当前内部编辑器 |
| 离线恢复 | 授权撤销时可先保留草稿再切内部项目，恢复授权后还原稳定文件ID与草稿；平板尺寸Activity重建且启动授权失效时，草稿仍持久保留，重新载入后拒绝覆盖外部新版本 |
| 匹配检查点 | 与磁盘内容相同的旧检查点恢复为干净状态，更新保存基线，之后可正常继续编辑保存 |
| 运行错误定位 | 使用现有进程级运行结果、绝对源码路径、稳定文件键/哈希；UTF-16/emoji行偏移正确，源码变更拒绝跳转，外工作区/越界路径不定位；另一文件通过现有保存/切换链定位 |

这里的提供方是测试 ContentProvider，故障由夹具注入，实际字节读写经过 Android API；不代表设备系统权限或第三方网盘提供方已经实测。诊断用例是运行结果元数据夹具，验证真实导航逻辑，不伪装成程序执行；真实 Python/C++ 运行检查独立记录在下表。

## 执行结果

| 检查 | 结果 |
| --- | --- |
| 新增14项主工作台测试 | 14/14；目标回归共57/57 |
| 主App全量 | 606项：605通过、1既有Windows符号链接跳过；原592项状态逐项保持 |
| 共享内部适配139项 | 未修改，Gradle `UP-TO-DATE` 缓存结果，未重跑 |
| App Lint | 0错误、10既有警告（OldTargetApi 1、ChromeOsAbiSupport 1、DataExtractionRules 1、SetTextI18n 7） |
| editor-sora Lint | 0错误、1既有固定版本警告，沿用缓存 |
| 全量Gradle任务 | 成功，1分43秒；258任务，12执行、246缓存 |
| Debug APK | assembleDebug成功且UP-TO-DATE；产物与上批逐字节相同，重新执行内容/签名检查 |
| Python源码/资源 | 44/44，实际执行 |
| APK中Python字节码 | 39/39（runner29、diagnostics10），从本批APK读取后实际执行 |
| 生成Shell启动脚本 | 实际Git Bash执行4/4；不替代Android PTY/桥接运行验收 |
| 代码片段 | 主机真实Python7/7、g++ C++17 7/7 |
| 实际APK | CRC、重复/越界路径、唯一MainActivity入口、包名/ABI/原签名、16KB对齐、46矢量/137路径、DEX及许可检查通过 |
| 版本保护 | 55旧APK、912非目标tracked文件、28份native/Python归档、20份共享语法/许可资源、181个原IDE顶层类保持 |

首次测试编译因直接调用protected回调失败，已改为测试反射调用。随后三项诊断夹具因只设置Activity结果、使用相对路径而失败；改用实际RunSession数据契约后全部通过。生产导航代码没有修改，也未删除断言/增加跳过；原始日志和夹具保留，详见 `EVIDENCE_ORIGIN.md`，这些失败不被归为已修应用Bug。

## 产物与边界

[本批已核验主APK](../dist/CodePocket-main-saf-recovery-2026-10-23-debug.apk)：26,606,143字节，SHA256 `a1e2c8bd8f962d5af9901062890ab80274741d499cc14b760b70dfc2c869c3d8`。与上批文件/设置APK字节一致，本批没有新界面或功能变更，无需为本批重复安装。

包名 `com.codepocket.ide`，版本0.2.0/code2，minSdk24/target34，arm64-v8a；原签名SHA256 `d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`。Gradle、Manifest、GitHub Actions保持；没有执行远端Actions或新增远端Wandbox调用。

主机布局覆盖手机411×800dp、平板1000×800dp，不属于人工完整视觉验收。真实设备IME、蓝牙键鼠、第三方提供方、API24、卓易通及FPS继续统一后置；本轮不新增Android真实Python/PTY执行结论。I3约60ms键入/80ms近距离移动及柔和闪烁尚未完成，完整语义补全仍为P2，不把现有词法辅助称为LSP。
