# P1-C：Sora真实项目与SAF文件会话（2026-10-21）

在 a3e9c7db3081019b990a71450bd1c1530d46168c 上继续现有P1-C，不重建项目。此批完成独立实验的真实文件会话基础，默认IDE仍为原生CodeEditor；完整生产Port和默认迁移尚未完成，原P2/P3继续保留。

## 已实现

1. 临时草稿页新增“项目 / SAF”，打开单独的非导出文件Activity。内部样例是真实私有文件，文件菜单可切换；“授权目录”通过系统ACTION_OPEN_DOCUMENT_TREE选择真实目录。保存写回提供方，未申请广泛存储或网络权限。
2. 保存/Ctrl+S、切文件前保存、返回离开前保存。400ms防抖及后台/旋转检查点独立保存恢复草稿，不把暂停等同于写回源文件。文件身份、20万字符上限、原子替换、外部冲突、提供方原文备份及写后校验全部复用原实现。
3. 读取期间实际Content身份、版本、文本或组合状态改变则取消交接；光标/滚动移动不会错误取消，提交前记录最新状态。保存期间继续输入，成功保存旧版后保留新编辑和新基线草稿，取消原先切换/退出。
4. 保留每文件稳定ID、选区、滚动、最后活动路径；活动窗口重建保留会话对象，进程重新打开内部项目可恢复磁盘草稿。重新打开外部目录后，按目录URI身份恢复草稿；提供方撤权不会丢掉私有草稿。
5. 新文件使用新Content/UndoManager；旧窗口回调、同名但不同Content、错误工作区缓冲和越界路径不能写入当前文件。换行/Unicode选区继续由共享边界规则和Sora真实Indexer处理。
6. 文件后缀自动选Python/C++/纯文本，复用共享词法辅助。实际输入超4.5万字符后切纯文本并显示原因；不截断源码。分析器先随编辑器释放，再关闭语法注册资源，迟到加载由已关闭控制器释放。

## 保留与边界

- 七份原存储Kotlin文件逐字节移动到workspace-core，包名/API、同步和校验正文不变；主程序和实验都依赖这一个本地Android库。共享实现不表示两个应用共享私有目录。
- 主IDE原文件树、多标签、菜单、设置、编辑器动画、单一真实PTY、Python桥与Wandbox继续保留。主程序APK无Sora，主运行时Kotlin1.9.25；实验固定Sora0.23.7/Kotlin2.1.21，不含运行器/native/Python归档。
- 实验文件页目前为文件列表，未迁入主工作台文件树CRUD、多标签、全部生产编辑器命令/装饰与运行联动。撤销历史不跨文件保留；文件页暂关闭光标动画，临时草稿对照仍保留原动画配置。新文件应在主IDE或系统文件管理器创建，然后重新打开目录。
- 原20万字符上限约束保存和草稿。Sora直接输入超限时保留当前显示内容并拒绝写入，必须缩减或复制；超限部分不能保证进程结束后恢复。SAF最多1000项/20层、严格UTF-8和路径保护保持。
- 当前辅助为词法/模板/内置参考，不是深度语义分析。没有新增代码上传、API Key、本地C++编译器或假运行结果。
- 系统文件选择器、真实持久授权、厂商提供方、IME/实体键鼠、卓易通、手机/平板真实布局与FPS仍待统一实机验收，本批没有连接设备。

## 修改文件

| 文件 | 修改 |
| --- | --- |
| settings.gradle.kts、app/build.gradle.kts、editor-lab/build.gradle.kts | 引入本地workspace-core；不变更插件、依赖版本或CI任务 |
| workspace-core/build.gradle.kts、src/main/AndroidManifest.xml、README.md | Android共享存储库；无权限声明 |
| workspace-core/src/main/java/com/codepocket/ide/{Workspace,WorkspaceIo,DocumentTree,SafGateway,DraftStore,DraftRecovery,EditorSessions}.kt | 从app原样移动，原目录删除同名重复实现；哈希对照见审计 |
| editor-lab/.../LabDocuments.java | 文件所有权、串行IO、持久会话、保存/草稿、并发与生命周期交接 |
| editor-lab/.../DocumentLabActivity.java、res/values/documents_strings.xml | 原生文件页、系统目录选择、保存/快捷键、语法状态与重建 |
| editor-lab/.../EditorLabActivity.java、src/main/AndroidManifest.xml | 实验入口与非导出Activity |
| editor-lab/.../SoraEditorAdapter.java | 为新载入文件排队恢复保存视口，使用新Content身份保护 |
| editor-lab/src/test/.../{LabDocumentsTest,DocumentLabActivityTest,HostAtomicLinux}.java | 25项真实控件/文件/提供方回归，test-only实际主机原子替换桥 |
| editor-lab/src/main/assets/SORA-NOTICE.txt | 更新第一方功能范围说明；原第三方许可段落保持 |
| README.md、editor-lab/README.md、docs/RESUME.md、docs/DEVELOPMENT_PROGRESS.md、本文件 | 最新入口、架构、测试和未完成项 |

## 构建与验证

| 验证 | 实际结果 |
| --- | --- |
| App单元/原生主机回归 | 540项，539通过/1既有Windows符号链接跳过；原540项状态保持 |
| Sora实验回归 | 133/133通过；原108项保持，新增25项全过 |
| Lint | 主App0错/11旧警告，实验0错/6旧警告，workspace-core0错/0警告；无新增警告 |
| Debug构建 | 两个APK成功；最终Gradle 42s，249任务中22执行/227缓存；本批首轮主App全测执行，最终App与终端核心为缓存、实验测试重跑 |
| Python/源码资源 | 源码与资源44通过；实际APK字节码39通过（runner29+diagnostics10） |
| Shell/模板 | 实际生成Shell启动4通过；生成Python7、g++ C++17模板7通过 |
| 原终端核心 | 146项既有通过结果UP-TO-DATE，本批未重新执行 |
| 产物检查 | 两包CRC/重复与越界项、签名/16KB ZIP对齐、Manifest/DEX/依赖图通过；主包46编译vector/137路径通过 |
| 保留核对 | 862份非目标文件、48旧APK、28份Python/native归档保持；主包原1889个DEX类保持，新增仅空资源类com.codepocket.workspace.R |
| 实验隔离 | 第三方资产逐字节保持；第一方说明更新。无一般存储/网络权限，无主IDE/运行器/native/Python/测试库；文件页非导出 |
| 签名 | 两包证书SHA256 `d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`，与原debug证书相同 |

- [主IDE APK](../dist/CodePocket-editor-sora-documents-2026-10-21-debug.apk)：22,682,338字节，SHA256 `5377762e7b77a98e9ee5521c4af231ce55c67d95b7118bb05067db6ca5b31487`。
- [独立Sora文件实验APK](../dist/CodePocket-Sora-documents-2026-10-21-lab-debug.apk)：5,070,233字节，SHA256 `797e21a64e1d76489cedc02573ade3d8ddbcc6b5f93cff06c0447b1969b76252`。


所有失败轮次均保留：Java字段声明顺序、Android测试编译API差异、Robolectric未实现rename、URI权限常量Lint。未通过关闭Lint、跳过旧测试或删改失败用例解决。修正真实输入时遗漏的语法预算通知和语法资源释放顺序后，重新构建最终交付APK。

新增25项包括内部真实保存/重新打开、恢复草稿、外部修改冲突、读文件时继续输入、读取时移动选区、保存后新输入、真实InputConnection组合、相同key不同Content、手机/平板窗口交接、完整平板Activity重建、撤销隔离、实际ContentResolver读写、只读/撤权/写后校验失败、无效UTF-8、主机原子替换本身、同工作区重载、越界/错误缓冲身份、超限输入、真实保存按钮/Ctrl+S/系统选择器入口、动态高亮预算。

SAF回归使用测试提供方和真实ContentResolver打开私有磁盘文件，主动注入只读/撤权/错误读回；没有把测试提供方当作设备授权机制验收。Robolectric4.14.1的Linux.rename原本未实现，test-only桥执行实际主机ATOMIC_MOVE/REPLACE_EXISTING，并验证字节与临时源文件消失；Android POSIX原子替换仍须设备验证。

证据保存在 verification/editor-sora-documents-2026-10-21/：before.json、原JUnit、失败轮次、最终JUnit/Lint/构建日志、Python/Shell/片段、签名/对齐、运行依赖图、编译图标/DEX/归档/受保护文件审计 results.json、差异和本地提交。没有push、发布、远端Actions或新增云端代码执行。

## 接续

继续把成熟编辑器适配到完整生产Port：生产设置、查找替换、装饰/错误定位、工作台多标签/文件树状态及运行入口一致性，再评估默认迁移。P2深度C++/Python语义、P3本地/多文件编译等原需求保留。实机与输入/键鼠/卓易通/FPS统一后置，不将主机通过写成设备通过。
