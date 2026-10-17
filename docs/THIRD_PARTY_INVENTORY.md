# 第三方依赖与许可证初步清单

2026-10-15 阶段 0。依据当前构建配置、本地实际制品 POM、Wrapper 声明和 APK 检查记录整理。当前为审查清单，尚不是完整发布通知包。

POM 原文快照及机器可读索引位于 [license-metadata.json](../verification/stage0-2026-10-15/license-metadata.json) 和同目录 license-metadata/；运行时依赖图见 runtime-dependencies.log。缓存中的其他旧版本制品不等于当前 App 依赖。

| 组件 | 当前版本/用途 | 本次可核验的声明及状态 |
| --- | --- | --- |
| Chaquopy Gradle 插件及 Java/原生桥接 | 17.1.0；构建与 Python 嵌入 | 所保存的 POM 声明 MIT；需保留对应版权及许可文本 |
| Chaquopy target 制品 | 3.13.16-0；APK 配置 Python 3.13 | 其包装 POM 声明 MIT；不能据此将 CPython/附带原生库统一标记为 MIT |
| CPython 及标准库 | APK 含 libpython3.13.so 和标准库 .imy | 设备精确运行版本未测；对应上游发布件的完整许可及组件通知尚待整理。主机 Python 3.13.9 自带 LICENSE.txt，不直接替代 Android 制品的通知 |
| libssl/libcrypto、SQLite 等 | Chaquopy APK 原生库 | 已确认存在；版本与各自许可、通知仍需逐个核对，不根据外层 POM 推定 |
| Kotlin stdlib | 1.9.25；App 运行时 | POM 声明 Apache License 2.0 |
| JetBrains annotations | 13.0；stdlib 传递依赖 | POM 声明 Apache License 2.0 |
| JUnit | 4.13.2；仅 testImplementation | POM 声明 Eclipse Public License 1.0；测试传递依赖的清单尚待补齐 |
| JSON-java | 20240303；仅 JVM 测试 | 该精确版本 POM 声明 Public Domain；设备端使用 Android 平台 org.json，不将缓存中的旧 JSON 版本当作已打包组件 |
| Gradle Wrapper 脚本 | 8.9；构建工具 | gradlew / gradlew.bat 保留原有 Apache 2.0 声明及版权头 |
| AGP / Kotlin 编译插件、JDK/SDK、CI Actions | 构建/测试工具 | 配置已检查；尚未编制所有构建工具和传递依赖的完整许可证清单；.tools 不提交或随 APK 分发 |
| Wandbox | 在线 C++ 执行服务 | API 使用与用户源码传输须在产品中清晰说明；不是把服务端代码打进本 App。首次运行已有上传同意，设置内说明/管理入口待实现 |

当前缺项：项目根目录未发现 LICENSE、集中 NOTICE 或 THIRD_PARTY_NOTICES，App 没有许可证页面；APK 顶层未发现名称含 license/notice/copyright 的条目。这里只检查了命名匹配，不能据此断言全部嵌套资源都没有许可文本。发布前需核对准确制品、版权文本、全部运行库及对应通知的呈现位置。

项目自身许可证由项目所有者确定。阶段 0 时没有 Git 历史；P0 已建立本地基线并保留后续修改提交，不能据这些新记录证明更早的完整原创开发过程。持续区分项目代码、生成文件、第三方运行库与构建脚本。新增终端渲染器或本地 C++ 工具链前，再核对选定版本的许可、分发内容和体积。

2026-10-16 P1 第一批未新增依赖或分发第三方编辑器。Sora 仅做官方资料初查，尚未集成，状态与来源见 [P1 报告](P1_SETTINGS_SEARCH.md)。


2026-10-17 新需求审查仅复核候选官方资料，**没有加入新依赖或资源**。详见 [编辑器/终端评估](UI_AUDIT_2026-10-17.md) 与 [计划](UI_REDESIGN_PLAN_2026-10-17.md)。Sora 上游声明 LGPL-2.1-or-later，发布页 0.24.6 为 Pre-release，不能直接称稳定或许可证组合已通过；Tree-sitter 核心、Jedi、xterm.js 的上游许可为 MIT，但 Android binding/语法包/传递依赖仍需按精确制品另核。Codicons 当前根许可证为 CC BY 4.0，不能默认所有图标都是 MIT。原项目自身 LICENSE/完整第三方通知仍未补齐，本轮不替项目所有者选择许可证。
