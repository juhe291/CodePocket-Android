# P0 实现与验证记录

2026-10-15。已完成本轮 P0 代码实现、主机回归和 Debug 打包，**设备验收仍未完成，P1 尚未开始**。保留已有原生 View 编辑器、内部工作区、Python 执行模块与 Wandbox API，没有新建项目或替换整套编辑器。

## 本轮交付

| 范围 | 实现及涉及文件 |
| --- | --- |
| 软键盘与状态 | MainActivity.kt、EditorSessions.kt：新增顶部“收键盘”“结果”；API 30+ 使用 IME Insets，旧版保留可见区域回退；键盘弹出时收起详细工具栏/候选/状态，代码输入保留紧凑 Tab、反缩进及括号工具条。stdin 获焦时保留输入区，极矮窗口优先显示输入；不主动调用软键盘 show |
| 每文件会话 | EditorSessions.kt：按工作区身份 + 稳定文件 UUID 保存 stdin、选区两端、横纵滚动与代码/stdin 焦点；文件和目录在应用内重命名/移动时迁移映射；关闭重开和 Activity 重建恢复；增加清空输入。旧全局 stdin 只迁移到原活动文件 |
| 真实 SAF 目录 | SafGateway.kt、DocumentTree.kt、Workspace.kt、MainActivity.kt：目录选择、持久读写授权、最近 8 个目录、内部项目切换、重新载入；提供方真实查询、新建、写回、重命名、移动、删除；不支持的操作显示提供方限制，不伪造成功。没有增加全面存储权限 |
| 保存和恢复 | WorkspaceIo.kt、DraftStore.kt、MainActivity.kt：文件 I/O 与提供方操作使用串行后台队列；约 200 ms 检查点草稿、700 ms 自动保存，离开时排队保存；先保留草稿再写原文件，版本核对和保存结果核对；失败阻止隐式丢弃/关闭/运行；增加“导出”“重新载入文件”“恢复草稿” |
| 写回安全 | 内部文件保留原有同步临时文件 + POSIX rename。外部文件保存前检查内容版本并保留原文，写入后读回核对；提供方部分写入失败时保留草稿和原文备份。恢复入口可跨工作区导出草稿、归档草稿与外部原文，授权撤销后可从内部项目进入恢复 |
| Python 停止 | PythonExecutionService.kt、RunSession.kt、ExecutionEngines.kt、Manifest：CPython 改在未导出的 :python 绑定服务进程中按需初始化；先取得 PID 再发运行请求，避免停止发生在握手前时留下任务；停止/总超时结束已启动的执行进程；主界面进程不作为终止目标 |
| C++ 与运行状态 | RunControl.kt、RunSession.kt、ExecutionEngines.kt：可断开的 HTTP 请求、取消句柄、会话票据过滤迟到结果；增加“已中止”；HTTP 429/503 等服务错误与离线网络错误分开。未知退出码显示“—”，不编造程序退出值 |
| 运行资源 | 外部项目的 Python 每次使用独立完整快照，包含源码和实际文本/二进制资源，避免执行编辑器空占位文件。入口使用当前已保存代码；模块和资源在运行准备时同步。错误按原工作区身份和运行根目录映射回编辑器 |

本次修改原有 MainActivity.kt、Workspace.kt、RunSession.kt、ExecutionEngines.kt、AndroidManifest.xml；新增 7 个 Kotlin 模块及 4 个测试文件。CodeEditor.kt、EditorFeatures.kt、EditorActions.kt、runner.py 的原有逻辑保留。没有新增第三方库，Gradle 配置与 GitHub Actions 工作流不变；工作流会自动发现新增 JVM 测试。

实现依据：[Android SAF 文档](https://developer.android.com/training/data-storage/shared/documents-files)、[软键盘 Insets 文档](https://developer.android.com/develop/ui/views/layout/sw-keyboard)、[Chaquopy FAQ 的独立进程建议](https://chaquo.com/chaquopy/doc/current/faq.html)。文档依据、编译通过和主机测试均不能代替 Android 内运行验收。

## 实际测试与构建

| 检查 | 本次结果 |
| --- | --- |
| 最后完整 Gradle 调用 | **BUILD SUCCESSFUL in 51s**；62 个任务，18 执行、44 使用未变化结果；编译、testDebugUnitTest、lintDebug、assembleDebug 均完成 |
| Kotlin/JVM | **35 项：34 通过、1 跳过、0 失败/错误**。Windows 符号链接测试仍明确跳过，不能计为通过 |
| Python 源码 | **13/13 通过**，执行实际 runner.py |
| APK 中 runner.pyc | **13/13 通过**；从交付 APK 提取实际字节码，在主机 Python 3.13 中执行 |
| Android Lint | **0 错误、14 警告**：SetTextI18n 11、OldTargetApi 1、ChromeOsAbiSupport 1、DataExtractionRules 1；没有新增抑制或 baseline |
| APK | 签名验证、ZIP CRC、6 个 Python 归档 CRC、ABI、包信息、ZIP 对齐均通过；实际 Manifest 确认包含独立 Python 服务 |
| Wandbox 实际服务 | 本次预置三程序脚本 3/3 通过，GCC 13.2.0：STL/stdin 得到 8、语法错误 status 1、真实退出 7；没有上传项目源码 |
| ADB | devices -l 为空；未安装、未启动 App |
| GitHub Actions | 未连接远端，没有远端运行或 Artifact 下载证据；只完成本地对应任务 |

新增单元测试覆盖：文件/工作区间 stdin 隔离、重命名身份保持、删除后不复用旧输入、反向选区/滚动状态；提供方读写、目录 CRUD、外部修改冲突、恢复草稿的旧版本冲突、授权撤销、部分写入失败的原文保留、循环目录拒绝、源码读取失败；完整运行快照中的文本/二进制资源与更新；磁盘草稿恢复、原子替换失败、旧保存不能清除新草稿；重复运行、取消后的迟到结果、HTTP 错误分类和连接取消。

**提供方故障测试使用可控的 JVM 实现注入错误，不是手机 SAF 实测。** Python 的两组 13 项测试执行真实主机 CPython，但没有经过 Android Binder、Chaquopy 原生加载或 Process.killProcess 路径。

中间一次构建被 Lint WrongConstant 阻止：持久目录授权的动态 flags 未通过注解检查。现在先检查实际授予读写位，再向 takePersistableUriPermission 传明确的读写常量；保留原失败日志。随后两条 Context 静态引用警告改为明确持有 Application，未通过禁用检查掩盖。

## APK 与证据

[CodePocket-P0-debug.apk](../dist/CodePocket-P0-debug.apk)：**21,737,438 字节，约 20.73 MiB**。包名 com.codepocket.ide、版本 0.2.0/code 2，minSdk 24、targetSdk 34，仅 arm64-v8a，Debug 签名。

SHA256：`0fcec1617a9bfff419239530b8a77d941b77e5c5e6636b8d9fcb957486d3af46`。

旧 v0.2 与阶段 0 APK 保留。APK 内无 version-control-info/vcs 条目；构建源由本地 Git 基线与本轮代码提交记录追溯。本轮未 push、发布或上传 APK。

证据目录：[verification/p0](../verification/p0/)。其中：

- build-01-ime-sessions.log、build-02-saf-save.log、build-03-runtime.log、build-04-final.log、build-05-release-check.log、build-06-snapshot-final.log：逐批构建日志，最后一份为本轮完整成功日志。
- results.json、junit/、lint-results-debug.xml/.txt：本轮结果和报告快照。
- python-tests.log、packaged-python-tests.log、apk-inspection.json：真实源码与实际 APK 字节码回归。
- apk-signature.txt、apk-alignment.txt、apk-badging.txt、apk-manifest.txt：产物检查。
- wandbox-live.log、wandbox-result.json：本次真实接口结果；adb-devices.txt：无设备记录。

实际执行命令：

```powershell
.\scripts\build-local.ps1 -ProxyHost 127.0.0.1 -UseLocalMirror
& ./.tools/python313-full/tools/python.exe -B -m unittest discover -s tests -v
& ./.tools/python313-full/tools/python.exe -B scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk --output verification/p0/apk-inspection.json
& ./.tools/android-sdk/build-tools/35.0.0/apksigner.bat verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
& ./.tools/android-sdk/build-tools/35.0.0/zipalign.exe -c -P 16 4 app/build/outputs/apk/debug/app-debug.apk
& ./.tools/android-sdk/build-tools/35.0.0/aapt.exe dump badging app/build/outputs/apk/debug/app-debug.apk
& ./.tools/android-sdk/build-tools/35.0.0/aapt.exe dump xmltree app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml
& ./.tools/android-sdk/platform-tools/adb.exe devices -l
& ./.tools/python313-full/tools/python.exe -B scripts/check_wandbox.py --proxy http://127.0.0.1:7890
```

签名检查使用现有便携 JDK 17；构建脚本设置项目内 SDK/Gradle 用户目录和匹配的完整 Python 3.13。临时镜像仅在 .tools 中，正式配置仍为原官方依赖源。

## 使用与边界

1. 安装本轮 APK。旧内部项目路径保留；已有安装通常可以用同签名 Debug 包覆盖安装，仍需设备确认，不要为了升级先卸载包含未导出项目的旧安装。
2. 在工具栏“工作区”选择真实手机目录，或者继续内部项目；最近目录可重开，权限失效时重新选择原目录。选择空目录会创建 main.cpp 示例。
3. 每个文件有自己的 stdin；“清空输入”只影响当前文件。代码区使用“收键盘”“结果”和紧凑 Tab 工具条；输入法不被程序强制弹出。
4. “运行”开始后同一按钮变为“停止”。C++ 仍需首次同意上传当前源码/stdin。云端取消仅停止本地等待，不能保证中止服务器任务。
5. 保存失败可直接导出当前缓冲区；“恢复草稿”可导出以前的草稿和原文备份；“重新载入文件”先归档当前内容再读原文件。切工作区可以明确选择保留草稿后切换，不以写回成功作假提示。

已知限制和风险：

- **Android 手机、平板、鸿蒙卓易通均未实机验证。** IME、中文 composing、选区/滚动、蓝牙键鼠、真实 SAF 提供方、独立进程启动/停止、后台回收和旋转期间运行均待按 [P0_DEVICE_TESTS.md](P0_DEVICE_TESTS.md) 验收。ZIP 16 KB 对齐通过也不等于 16 KB 页面设备兼容通过。
- 提供方写入不保证原子替换，版本检查与写入间仍可能有并发窗口；原文和新草稿会保留恢复。提供方调用没有统一强制超时，异常缓慢提供方可能长时间等待；不保证所有云盘/厂商文件管理器的移动、改名能力。
- 编辑文本最多 20 万字符；SAF 扫描最多 1000 项、20 层目录；Python 运行资源单文件最多 1 MB、快照合计最多 8 MB。外部结构在其他应用改变后须重新载入，外部根目录 URI 改变可能需要重新授权。
- 外部项目 Python 在私有完整快照中运行；程序产生/改写的文件不会自动回写外部项目。编辑器保存会写回所选系统目录。此区别在选择目录时明确提示；不要把私有运行产物当成已写回数据。
- Python 仍是预填 stdin、执行完成后显示输出；停止会丢弃尚未返回的进程内输出。字节码限时约 6 秒，父进程总限时 10 秒含初始化；C++ 连接 10 秒/读取 30 秒，父级总等待 45 秒。准备运行快照的时间单独显示准备状态，不计入脚本执行耗时。
- 普通配置重建有 Bundle 兜底，磁盘草稿为约 200 ms 检查点；在检查点落盘前直接强杀/掉电，最后极短时间的输入仍可能丢失，不承诺每次按键立即持久化。
- Python 不是安全沙箱；独立进程仍具有本 App 的 UID 权限。当前实现也不保证后台长期常驻执行。
- P1 的设置、独立语法主题、查找替换、ANSI/流式终端、可拖动面板尚未实现；本地 C++、多 cpp/头文件构建、批量题目用例属于 P2。许可证完整通知包仍按初步清单继续完善。

本地 Git 已建立阶段 0 基线 `3094bfa`，P0 源码在其后单独提交；历史审查文件保留原时点事实。后续先做设备验收和缺陷回修；**进入 P1 前等待用户确认**。
