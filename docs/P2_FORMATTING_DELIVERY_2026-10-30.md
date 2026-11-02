# P2 格式化批次交付记录

2026-10-30建批，2026-11-02接续收尾；基线50c1288。沿用[四组计划](P2_FORMATTING_BATCH_2026-10-30.md)，没有重建项目或扩展本批功能。代码、主机检查和候选APK分别记录，Android实际执行仍后置。

## 实际修改

| 范围 | 当前实现与保护 |
| --- | --- |
| 离线引擎 | Python使用固定autopep8 2.3.2、pycodestyle 2.14.0，wheel SHA锁定并保留原MIT通知；C++复用原clangd 21.1.8内嵌格式化，不增加另一套原生工具。缩进跟随2/4/8空格或Tab设置 |
| 私有工作 | 非导出:formatting服务、串行工作和最新排队请求，冷启动30秒/已热引擎8秒期限。只管理自己的进程和clangd子进程，不停止分析、程序运行或PTY；不接受原工作区路径，不读取项目配置、不写原文件 |
| 正式编辑 | 未保存正文为真源，响应校验来源摘要、引擎、UTF-16边界与大小。绑定文件key、版本、全文、选区、组合状态和选项，拒绝迟到结果；通过同一Sora事务应用，可一次撤销且保留原焦点 |
| 入口与保存 | 编辑分类和实体Shift+Alt+F；重复按键不重复排队。保存时格式化默认关闭，设置草稿/取消/应用沿用原流程。保存只尝试一次格式化，失败或过期仍保存当前正文，继续原版本冲突、草稿和文件保护 |

Python采用非激进规则，额外校验AST、字符串和注释，保留主要换行形式；语法错误保留原文。C++使用私有固定样式，禁止include/using排序、字符串拆分和注释重排。输入上限20万UTF-16单位、JSON45万；原语义分析2MB帧/45000字符边界不变，只有独立格式化连接允许8MB帧。

主要文件为CodeFormatting.kt、CodeFormattingService.kt、code_formatting.py，及MainActivity、CppLspEngine/Connection/Runtime、Sora事务、命令/设置和AppSettings。设置结构升至11，旧设置迁移和其他属性保留。

## 真实验证与失败记录

完整命令见[full-build.ps1](../verification/formatting-2026-10-30/full-build.ps1)：离线运行主App回归、editor-assist、终端回归、App/Sora/Workspace Lint和assembleDebug，使用--continue保留全部结果。

- 最终主App **995项：993通过、1既有失败、1旧跳过**。原968项身份和跳过集合保持，新增27项全部通过；未删断言或新增跳过。最终命令4分3秒，真实退出1，不能称整批全绿。
- 唯一失败仍是CppRuntimePreparationTest.trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs：固定头文件归档含大小写不同名称，Windows大小写不敏感文件系统无法新鲜提取xt_connmark.h等条目。原归档、生产代码与断言保持。旧跳过仍为Workspace递归删除符号链接用例。
- Lint **0错误**；App24、Sora1、Workspace0警告。相对前批App新增2处SetTextI18n，来源为保存状态追加格式化结果，登记为非阻塞项，没有屏蔽规则。终端146项有效UP-TO-DATE，editor-assist为NO-SOURCE。
- 实际主机C++格式化预检8通过，其中5组真实编译/执行前后输出一致；Python全源码95通过。定向入口23和完整事务/界面37通过；主机Shell4、片段14本批实际重跑通过，未复用旧批次数字。
- 包内实际Python48、语义22、格式化20检查通过。格式化检查直接加载本APK的后端和依赖字节码，核对固定wheel源码、字节码及原始许可；它仍是主机执行证据。

原始失败全部保留：缺入口基线2失败；Python选区末端误含新增空格的20项基线1失败，修复后20通过；新测试一处Kotlin语法错误未执行；35项定向的2处新视觉夹具失败经明确窗口布局和真实DOWN/UP修正后37通过。首次平板图主题夹具错误也已纠正为实际浅色偏好并增加调色断言。

四次集中命令中仅首次实际打包：首次新视觉测试引用不存在的palette导致测试编译失败，但--continue继续生成APK；第二次995项中3失败，除既有失败外，两处旧测试仍断言设置结构10，改为11并增加默认关闭断言，其他迁移断言保持；第三次995项仅既有失败；第四次只调整新主机夹具的跨平台工具路径后确认同一结果。后续packageDebug/assembleDebug均UP-TO-DATE，APK摘要始终相同。夹具七个测试正文与第三次逐字节一致，有源摘要证明。

新夹具沿用原C++缓存和按操作系统选择可执行后缀，Python支持主机解释器覆盖、Windows离线解释器及Linux python3，依赖缓存可选。现有工作流本来已安装固定requirements；本批未修改或触发工作流，未取得干净Linux CI结果。另发现旧RealCaseBatchTest仍固定Windows Python路径，先登记，不能据本批夹具修正宣称整个CI跨平台通过。

依赖下载早期DNS11001与主机Shell沙箱DLL初始化失败有原日志；未修改系统DNS/代理。同一官方依赖重试核对摘要后取得；Shell经主机权限重跑4项通过。

最终包格式化检查器首次在autopep8导入时失败：它会重载pycodestyle，而检查器只手动加载模块、没有将临时字节码目录加入搜索路径。保留formatting-apk-first-error.log，补上该目录后只重跑实际包格式化检查，20项通过；未改生产代码或APK。收尾源摘要同时保留修正前检查器和精确差异，原包检查不受影响。

## 候选 APK 与界面检查

- [本批最终候选](../dist/CodePocket-formatting-2026-10-30-debug.apk)：**121359886字节**，arm64-v8a，Android API24起。
- SHA256：`59e13ca581f0806ba25df937772228e3802c5ee1896aebb091cf80e174756668`。
- 原Debug证书SHA256：`d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`。签名、CRC、16KiB对齐、元数据、权限和实际DEX/Manifest检查通过；只新增非导出的:formatting服务。
- 原25份原生库、45份未变运行资产及98份历史APK逐字节/摘要保持；另3份Chaquopy资产只批准新增格式化模块、两个固定依赖及对应构建摘要，原runner/diagnostics/semantics有效载荷不变。检查器默认严格行为保留，只能显式开启本批差异检查。
- 比前包增加5938143字节；归档成员压缩数据净增231906字节，另有增量打包空隙，见package-size-accounting.json。没有为体积优化重新打包，这一非阻塞问题保留。

已人工查看三张Robolectric原生绘制图：[手机编辑菜单](../verification/formatting-2026-10-30/previews/phone-format-edit-menu.png)、[手机大字体设置](../verification/formatting-2026-10-30/previews/phone-large-format-setting.png)、[平板浅色设置](../verification/formatting-2026-10-30/previews/tablet-light-format-setting.png)。320×640/1.5倍字体及1280×800均检查，设置触摸还覆盖实际DOWN/UP、草稿与应用。没有把这些图称为设备截图。

机器结果与证据在[results.json](../verification/formatting-2026-10-30/results.json)、同目录最终JUnit、源摘要和检查日志。当前批次完成代码/主机/候选包验证；Android服务、JNI、实际C++ signal6修复效果、IME/键鼠/API24/卓易通验收仍后置。原P3的Git/GitHub、设置搜索细化、主题扩展和更多语言分析继续，整体目标尚未完成。不推送、发布或新建自动任务。
