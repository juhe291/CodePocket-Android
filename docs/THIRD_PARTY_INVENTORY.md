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


2026-10-17 P1-C：新增显式启用的独立 `editor-lab`，默认App依赖和原APK入口不变。实验固定 `io.github.rosemoe:editor:0.23.7`（LGPL-2.1-or-later，上游版权头），真实AAR minSdk21/Java17/Kotlin元数据2.1.0/无原生.so，精确制品哈希与Maven module一致。实验runtime为AndroidX annotation1.9.1/collection1.5.0/Kotlin stdlib2.1.21及annotations13.0；POM与原内包许可资源指向Apache2.0，未把外层Sora许可套用于所有依赖。

实验APK含LGPL2.1正文、Sora版权/精确源码链接、依赖说明和原AndroidX许可资源；本地材料包提供AAR/POM/module/sources.jar及当前工程源。未取得完整上游Git仓库归档，正式分发的完整对应源码/重链接/通知仍需核验；项目自身许可证未替所有者选择。0.24.4曾因Kotlin编译/Lint元数据不兼容弃选，未分发为本批实验版本。详见[P1_EDITOR_LAB.md](P1_EDITOR_LAB.md)及verification/editor-lab-2026-10-17/results.json。


2026-10-18 Sora语法实验：[本批报告](P1_SORA_LANGUAGES.md)。仅editor-lab新增language-textmate0.23.7（LGPL-2.1-or-later，内含TM4E EPL-2.0及原MIT部分）；原AAR未修改，源码JAR SHA256 `ca06b725576c3de059d0625d7971803c08046cd4907b3235d88b82baa2d4311b`。Maven Central本机403，后用现有Aliyun镜像取得同版，实际Gradle AAR SHA256与下载件 `909576591348f999b318ef601568a208550ee1c0cae375d70626f70f2b7c63ba` 相同。

实际实验传递图增加Gson2.13.1/ErrorProne annotations2.38.0（Apache2.0）、Joni2.2.6/JCodings1.0.63（MIT）、SnakeYAML Engine2.9（Apache2.0）、Eclipse JDT annotation2.3.100（EPL2.0）；单独coreLibraryDesugaring2.1.5为OpenJDK子集，POM声明GPL2+Classpath Exception。默认App无这些实验依赖、仍Kotlin1.9.25；实验仍Kotlin2.1.21。构建期脱糖工具配置不等于全部工具代码进入APK，实际j$运行库已检查。

语法/配置原样来自microsoft/vscode1.95.3，附原MIT与ThirdPartyNotices（含MagicPython、better-cpp-syntax等归属），每文件URL/哈希在textmate/UPSTREAM.json。新增完整EPL/Apache、Joni/JCodings MIT和desugar GPL2+Classpath/Assembly Exception文本，从上游许可地址取得，许可下载URL/哈希在textmate/licenses/UPSTREAM.json；desugar许可地址沿POM的master链接，未冒充完整2.1.5源码档案。两套主题为本地资产。

实验APK20项asset复制与解压字节逐项匹配，并提供“许可/说明→语法许可”阅读入口。本次主机验证不构成完整法律/分发审查；历史源码材料ZIP保持原样，不含新增适配和语法模块。正式发布前仍需收齐精确对应源码、重链接交付及完整通知；未替项目所有者选择项目许可证、未push或发布。实验依旧不访问项目/SAF/运行器。


2026-10-18 原生Shell：[本批报告](P1_NATIVE_TERMINAL.md)。默认App新增本地Termux terminal-view/terminal-emulator，固定v0.118.3 / 5b657c6adf4304e5198951ce815fe0205dcac29c；52项原始源/许可按Git blob或SHA256核验，完整固定源归档保留.tools。根GPLv3-only及Apache2.0来源例外均保留，没有将所有文件统一标为Apache；UPSTREAM.json、原始/已移位的Gradle来源、五个已修改上游文件差异、GPL/Apache正文在third-party/termux及证据。APK中terminal-licenses含两份正文、上游说明和元数据，终端菜单有入口。项目自身许可证仍由所有者决定，正式分发前继续对应源码/通知义务核验。NDK r27c仅本机/CI构建，不打包；新增libtermux.so为源码实际构建的arm64 JNI。三个JVM utility桥仅src/test，不入APK。AndroidX annotation1.9.1固定；默认Kotlin/Chaquopy版本没有整体升级。

2026-10-18 Shell闪退回归：新增固定org.robolectric:robolectric:4.14.1，仅App testImplementation，启用Android测试资源。主机SDK34用于实际View初始化回归；Android平台由Robolectric提供，不是手机/PTY执行。Robolectric及传递测试依赖、下载的主机SDK均不进入Debug APK，DEX已检查；Termux源码/JNI/许可资产未改变。
