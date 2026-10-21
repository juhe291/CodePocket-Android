# P1-C Sora 补全、片段与参数参考适配（2026-10-21）

基线 `bed60949deefe4c214d7cd7ebf12cffe7d0cbb6b`。继续上批成熟编辑器适配，不重建项目。默认IDE仍用原生CodeEditor，Sora为独立实验APK；实机验收仍统一后置。

## 已实现与边界

新增纯Kotlin/JVM `editor-assist` 模块，把现有词法补全、基础高亮、内置片段/字段会话、内置参数参考及TextChange搬入共享模块。保留原包名、数据模型、排序、模板和使用入口。AppSettings仅实现小型选项接口，JSON仍为version9；默认字号、主题、终端、保存、按键设置保持。共享模块不依赖Android、AppSettings、文件、运行或网络。

3份原规则文件（EditorFeatures、SnippetSession、SignatureHelp）与基线Git正文完全一致；CompletionModel和CodeSnippets只把AppSettings依赖改为编辑选项契约，并保留原缩进归一化规则。主应用与实验复用同一份实际提供方，没有抄写第二套候选列表。

Sora实验已接入：

- 自动/主动词法候选，来源、类型标记与简短说明；Ctrl+Space、上下方向键、Tab接受、Esc关闭。Enter默认交给成熟编辑器换行；软键盘/中文组合事件不用于接受候选。
- 现有两语言各6个真实内置片段，触屏选择、Tab/Shift+Tab和上一/下一字段按钮。接受通过真实Content批次形成一次撤销；字段内编辑追踪后续范围，越界、历史、组合或换文档保守结束临时字段会话，不改写用户文本。
- 实际共享的Python7项/C++17 5项内置参数参考，活动参数加粗、参数形式切换与来源；Ctrl+Shift+Space及触屏入口。未知函数/成员/复杂语法允许无结果，未添加语义推断。
- 一个非焦点候选/参考浮层，使用真实Sora布局、既有EditorSurface/PopupGeometry定位与IME可见边界；滚动/布局重定位，切文档/语言/失焦/暂停/关闭清理。禁用组件自带自动补全入口，避免同时出现两套列表。
- 分析延后120ms，单工作线程/最多1个待处理任务；取消与运行标识隔离旧结果。4.5万字符外在复制正文前暂停实验补全，后台不执行用户代码、不上传内容。
- 正文/实际光标由Sora立即处理。显示候选前取消视觉光标动画并使用逻辑布局位置，不等待候选再写入正文。

本批是**基础词法、现有模板和内置资料接线**，不是clangd/Jedi或完整IntelliSense。实验按键使用共享默认策略，尚未接生产设置中心、项目/SAF、保存/运行和完整EditorPort；不得据此称默认IDE已迁移。

## 发现与处理

真实原生窗口回归首次4项有1项失败：“加载草稿后立即打开片段列表”未显示。请求跟踪证明显式请求在布局未就绪时就返回，尚未进入分析线程。修复为最多2秒的布局就绪等待，保留请求模式、缓冲身份、版本、选区及输入门控；只做轻量检查，不抢焦点，不强制逐帧布局。过期或超时清理。新增回归明确强制真实异步折行并检查等待状态，触屏片段入口和实体Ctrl+Space均执行此验证，最终通过。

同时收紧无变化的finishComposingText、重复选区和重复resume通知，避免无意义地取消相同上下文。组合结束观察仍原样转发成熟InputConnection的结果，不接管输入法实现。

测试等待器也修正为等待真实Future：向有界discard-oldest队列注入哨兵任务可能挤掉真实请求，不能照搬原无界执行器夹具。这个夹具修正并未消除布局失败，后续跟踪和就绪修复才让原断言通过。中间失败、诊断及跟踪代码副本完整保留，最终生产源码无临时跟踪输出。

最终Lint发现3项新增候选文本拼接警告，改为Android字符串资源后重跑，恢复既有警告数量；没有关闭Lint规则。没有删旧测试、跳过新失败用例或放宽原断言。

## 文件清单

| 文件/范围 | 修改 |
| --- | --- |
| editor-assist/build.gradle.kts、src/main/kotlin/com/codepocket/ide/ | 新增共享模块；搬移5份原规则，拆出TextChange，新增AssistOptions/契约 |
| settings.gradle.kts、app/build.gradle.kts | 包含共享模块及默认IDE依赖；不升级插件 |
| app/.../AppSettings.kt、EditorActions.kt | 接选项契约、使用搬移的TextChange；JSON与编辑规则保持 |
| editor-lab/build.gradle.kts | 共享模块依赖；只在test增加同现有App版本的Robolectric4.14.1与资源测试配置 |
| editor-lab/.../SoraAssistance.java（新增） | 真实生产提供方结果、源/语言证明及轻量字段跟踪 |
| editor-lab/.../SoraAssistController.java（新增） | 有界后台分析、模式/生命周期、实体键与真实事务 |
| editor-lab/.../SoraAssistPopup.java（新增） | 候选和参数参考的非焦点原生窗口 |
| editor-lab/.../SoraBuffer.java、SoraEditorSurface.java、SoraInputEditor.java | 缓冲身份/长度、布局忙碌保护、透明按键与组合结束观察 |
| editor-lab/.../EditorLabActivity.java、res/values/assistance_strings.xml | 真实操作入口、状态、语言/焦点与字符串资源 |
| app/.../SharedAssistContractTest.kt（新增） | 4项跨模块配置、模板与设置JSON兼容性回归 |
| editor-lab/.../SoraAssistanceTest.java、SoraAssistInteractionTest.java（新增） | 20项真实核心+6项原生窗口回归 |
| README、editor-lab/README、DEVELOPMENT_PROGRESS、RESUME、本报告 | 架构、操作、交接与验证边界 |

`app/...`主源码相对路径为`app/src/main/java/com/codepocket/ide/`，测试为`app/src/test/java/com/codepocket/ide/`；`editor-lab/...`对应`editor-lab/src/main/java/com/codepocket/editorlab/`与`src/test/java/com/codepocket/editorlab/`。

## 实际验证

| 验证 | 真实结果 |
| --- | --- |
| 默认App | **540项：539通过、1原有Windows符号链接跳过**；原536项名称/状态保持，新增4项通过 |
| Sora实验 | **108/108通过**；原82项名称/状态保持，新增20核心+6原生窗口 |
| 原生窗口 | 手机/平板窗口的真实Sora View/布局/PopupWindow/InputConnection与native graphics；方向键/Tab、Enter/Esc、C++ Ctrl+Space、真实中文提交与字段导航、组合保护/新文档隔离、参数形式切换 |
| Lint | 主程序0错误/11旧警告；实验0错误/6旧警告，无新增 |
| Debug构建 | 首轮全量1m27s，收尾后最终39s；最终主程序测试UP-TO-DATE，实验重新执行 |
| 运行回归 | Python源码/资源44、实际APK内字节码39、主机真实Shell4、实际生成Python模板7/主机g++ C++17模板7全部通过 |
| 终端核心 | 146为UP-TO-DATE缓存结果，本批未重跑 |
| APK | 两份CRC、v2同原Debug签名、16KiB ZIP对齐、实际包名/API/DEX通过；默认arm64-v8a；实验无Python/native库、申请权限或主应用入口 |
| 完整性 | 851份非目标已跟踪文件、46个旧APK、28份Python/native归档及实验许可/语法资产保持；46个编译vector/137条path核对 |
| 类与依赖 | 原主程序1887个DEX类全部保留，仅新增2个选项类；默认Kotlin1.9.25/无Sora，实验Sora0.23.7/Kotlin2.1.21；两者运行依赖无Robolectric，测试/日志桥不进入APK |
| 共享规则 | 逐文件与基线Git正文比较；jdeps确认共享JAR无Android/AppSettings依赖 |

原生窗口测试执行了成熟组件的真实代码和本机图形库，不是只用手填坐标或mock View；仍然属于**主机测试**。没有物理输入法、蓝牙键鼠、实际设备OS或卓易通证据。没有新增截图/人工视觉验收或FPS测量，不报告设备兼容或60FPS通过。运行回归由匹配的Python3.13、g++与bash主机执行；本轮没有新增Android PTY/Chaquopy整链或Wandbox请求证据。

两份Actions文件、根Gradle插件版本、Manifest、运行/保存/SAF/草稿/终端源码及原需求文档保持。工作流沿用原任务，会编译新的本地依赖；本轮未触发远端Actions，没有push或发布。

## 产物与复现

- [当前主IDE Debug APK](../dist/CodePocket-editor-sora-assistance-2026-10-21-debug.apk)：22,682,338字节（约21.63MiB），SHA256 `3447e6017667e17bcdd2f91bd029dabdd0129a0be35a6f46126c64a2c8219381`。包名com.codepocket.ide / versionCode2 / 0.2.0，默认仍为原生编辑器。
- [Sora代码辅助实验 APK](../dist/CodePocket-Sora-assistance-2026-10-21-lab-debug.apk)：5,007,724字节（约4.78MiB），SHA256 `69cbb32af41b557ef84a84c8d02d69407feee4af91b38ea14c27127b6546aec7`。独立包名com.codepocket.ide.editorlab / 0.1-experiment，临时草稿，进程结束不保存。

两者min24、target34、compile35，同Debug证书SHA256 `d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`。原主程序全部ZIP条目保留，内容变化为共享模块相关DEX，不用体积变化代替功能保护检查。

在实际仓库执行 `verification/editor-sora-assistance-2026-10-21/verify-repository.ps1 -Repository <实际仓库绝对路径>`，使用现有.tools的JDK17/SDK35/Gradle8.9/Python3.13离线环境。原始日志、逐项JUnit、首轮失败与请求跟踪、Lint、依赖树、APK检查/基线及`results.json`位于同目录；`audit_artifact.py`执行严格源/归档/旧测试保护检查。没有覆盖旧APK，新增文件名含本批标识。

## 接续

下一批继续项目/SAF文档会话、文档身份与完整生产Port适配，再评估默认编辑器迁移。候选/片段临时状态不跨文档或旋转携带；基础草稿正文/选区/换行的旧交接保留，进程死亡和项目草稿尚未接入。

原P2的真实clangd/Jedi成员/类型分析、本地/多文件C++、批量用例、格式化，以及P3继续保留。默认IDE既有Tab/按键配置、字号、UI动画、单一真实PTY和Python/Wandbox运行保持。Android手机/平板、真实IME/键鼠、鸿蒙卓易通和帧率最终验收仍按约定后置。
