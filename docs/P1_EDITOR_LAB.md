# P1-C：Sora 隔离原型与兼容性验证

2026-10-17，在 `ffe8249` 上接续。**已完成可选实验模块的代码、真实库测试和 APK 构建；默认编辑器仍是原生 CodeEditor，P1-C 整体未完成。** 本轮没有设备或模拟器，实机继续统一后置。

## 实际实现

- 在现有 Gradle 工程增加 `editor-lab`，仅显式 `-Pcodepocket.editorLab=true` 才包含该模块。原 `:app`、Gradle/AGP/Kotlin/Python 版本及默认 Actions 流程保留；没有重建 IDE。实验模块用 Java 调用 Sora，独立运行时保留库要求的 Kotlin 2.1.21，主应用继续 Kotlin 1.9.25。模块之间没有代码或依赖引用。
- 独立应用 ID `com.codepocket.ide.editorlab`，标签 `CodePocket Editor Lab`，可与正式 IDE 并存。实验入口可切换原生 EditText/Sora，提供临时文本与选区传递、插入括号/空格缩进、各自撤销/重做、深浅色及 Sora 光标动画开关。光标动画检查系统动画和省电状态，暂停时关闭，恢复时重算。
- 原型草稿只在内存中：旋转通过 retained state 保存文本/选区，进程结束清空；切换组件会重新开始目标组件历史，选区方向归一化。修改后退出有确认。它不接收外部文本/URI、不读写 CodePocket 项目或 SAF、不连接运行器或设置；APK 权限检查为空。
- 触屏命令先检查组合区、UTF-16 代理对和 CRLF 边界、有效范围及 20 万字符命令预算，拒绝半个代理对/换行的改写；Sora 替换通过真实 Content 批次提交。键盘/鼠标和输入连接仍交给组件自身处理，未另造编辑内核。直接输入/粘贴的组件限制不等同该工具栏命令预算。逐键/选区状态回调只读长度与选区，不复制全文或查询系统设置；动画策略在恢复/窗口回焦/开关变化时更新，避免实验外壳干扰输入测量。
- 包含 Sora LGPL 2.1 正文、版权/精确源码链接、依赖说明和可查看的许可页；保留 APK 中 AndroidX 原有许可资源。实验构建源和 Maven 原始 AAR/POM/module/sources.jar 随本地材料包提供。完整上游 Git 仓库归档未取得，正式分发的完整对应源码/重链接及全部通知仍需专项核验；本批没有选择项目自身许可证。

## 实际发现的兼容问题

首先固定并下载 `io.github.rosemoe:editor:0.24.4`，核对 [Maven Central POM](https://repo.maven.apache.org/maven2/io/github/rosemoe/editor/0.24.4/editor-0.24.4.pom) 与 module/AAR/sources SHA256。它要求 Kotlin 2.2.21，直接加到当前 App 后真实触发 `metadata 2.2.0, expected 1.9.0`；探针只用临时 init 脚本，没有提交该依赖。

Java 隔离后，0.24.4 文本层 14 项通过并能生成 Debug APK，但旧 Lint 打印 `metadata 2.2.0, expected 2.0.0`。即使 Gradle 返回成功，也没有将该结果当作完整兼容。最终实验选择 **0.23.7**，保留失败/中间日志，没有加 `-Xskip-metadata-version-check`、强制降低库的 stdlib 或升级整个项目。

选定的 [0.23.7 POM](https://repo.maven.apache.org/maven2/io/github/rosemoe/editor/0.23.7/editor-0.23.7.pom) / AAR 实测：核心 minSdk21、CodeEditor class major61（Java17）、Kotlin module metadata2.1.0、无 `.so`，依赖 annotation1.9.1 / collection1.5.0 / stdlib2.1.21。Gradle 构建与 Lint 日志均无元数据错误；这仍不等于 API24/卓易通运行兼容已经验收。编译器及 [D8/R8 的 Kotlin 支持](https://developer.android.com/build/kotlin-support) 需要分别评估。

选定 AAR SHA256 `373c784fbf14d8288dca973d8938fdf4b134724413783c9e55d91dc4edeb2f11`；sources.jar `0429f8853930a0ada2936bf6f8740cfaa22358009fd56f0f49111dff9709e7d8`。均与 Maven module 声明相符，归档 CRC 正常。实验没有引入 TextMate/Tree-sitter/LSP/native binding/desugaring；尚未判断这些模块的兼容性。

## 测试与构建

| 检查 | 实际结果 |
| --- | --- |
| Sora 文本层 | **14/14**；实际加载库的 Content/Indexer/UndoManager，无 View mock、无关闭断言、无模拟编辑后端 |
| 覆盖范围 | 中文/emoji/UTF-16 索引、CRLF 保真/边界、空文档、多行替换、批次撤销/重做/重做失效、长文本、越界与超预算拒绝 |
| 实验 Gradle | `27s`；47 任务中15执行/32未变化；test/lint/assemble 成功；Lint 0错误/6警告，未出现元数据不兼容日志 |
| 实验 Lint 警告 | OldTargetApi、GradleDependency、DataExtractionRules、MissingApplicationIcon各1，SetTextI18n2；版本固定是兼容性选择，没有屏蔽该提示 |
| 默认 IDE Gradle | `--offline --rerun-tasks` **1m 56s，62/62 任务实际执行**；Kotlin173项中172通过/1 Windows符号链接条件跳过，0失败/错误 |
| 默认 Python | 源码13/13、实际 APK runner.pyc13/13；生成模板 Python7/7、主机g++ C++17 7/7 |
| 默认 Lint | 0错误/11警告；现有警告保留 |
| 两个 APK | CRC、v2 Debug签名、16KB ZIP对齐、不同包名、启动 Activity 与关键 Dex 类定义检查通过；实验无Python/native库，默认APK无Sora/实验类 |
| 依赖隔离 | 同一工程同时启用实验模块后分别读取实际 runtime graph，App为stdlib1.9.25/无Sora，实验为Sora0.23.7/stdlib2.1.21 |
| 保护检查 | 83项既有app源码/原测试/脚本/Gradle/原Actions哈希未变；三份需求继续保留 |
| CI | 原工作流未改；新增仅手动的 `editor-lab.yml`，SnakeYAML解析及触发/构建/上传配置检查通过；没有远端运行或push |
| 设备 / 界面截图 / 输入法 / FPS / 新Wandbox请求 | 本轮均未执行 |

默认 APK 与上批大小不同，已逐项核对：ZIP项集合相同，Dex分片重排并清掉一个编译器生成且不再被引用的 `IdeCommandMenu$EntriesMappings` 辅助类；其他类定义保留，新菜单字节码直接调用 `IdeCommand.getEntries()`。Python runner 的16字节头之后 payload相同，头部8/9/10字节和配套构建元数据有差异。源码83项哈希相同，不把该体积差异当作本批功能优化。

## 产物和复跑

- [默认 IDE Debug APK](../dist/CodePocket-editor-native-debug.apk)：21,861,894字节（20.85MiB）；SHA256 `7408f728e5ada1a0b07208d314ea15b31e73cfa06b506e6d8f33cebf7c0ec4a2`。
- [Sora 实验 Debug APK](../dist/CodePocket-Sora-editor-lab-debug.apk)：1,650,202字节（1.57MiB）；SHA256 `15ad39d0969a186b8f67582bbbc9cb798e86b93480604cf78afb1a27e4e44e01`。用于后续统一验收，没有IDE的文件/保存/运行能力。
- [实验 APK 与源码/许可材料包](../dist/CodePocket-Sora-editor-lab-bundle.zip)：含当前工程源文件、实验APK、精确Maven制品和许可材料。具体成员哈希见 evidence 的 bundle-inspection.json。

普通环境复跑（已有JDK17/SDK35/Gradle8.9）：

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew :editor-lab:testDebugUnitTest :editor-lab:lintDebug :editor-lab:assembleDebug -Pcodepocket.editorLab=true
```

Windows使用 `gradlew.bat`；中文路径可加 `-Pandroid.overridePathCheck=true`。本机便携环境及证据复跑见 `verification/editor-lab-2026-10-17/verify-native.ps1`、`verify-lab.ps1`、构建日志与本轮保存的 init 脚本。原型CI可在获得用户授权后由用户手动运行新增工作流，完成后下载 `CodePocket-Sora-editor-lab-debug-apk`；默认工作流的原Debug APK下载能力保留。本轮没有触发远端。

## 后续接续点

**Sora 隔离原型已具备代码/主机构建证据，默认迁移仍待决定。** 现有生产 EditorPort 仍只有快照/装饰，实验尚未接入完整的文件会话、事务/撤销/视口/诊断契约或现有补全/片段/参数弹窗。下一批继续共享编辑事务与完整Port契约、语言适配和候选定位，再推进其他P1动画/手势/设置和真实Python流式后端；P0缺陷优先，P2语义/本地多文件C++与P3原需求保留。

实机仍按用户安排统一后置。原生对照只是基础EditText，不等于已将整个CodePocket功能迁移到Sora；主机Content测试不验证InputConnection、光标位置、窗口键盘遮挡、触屏/蓝牙键鼠、渲染或帧率。完整语义分析和LSP服务本批未实现。
