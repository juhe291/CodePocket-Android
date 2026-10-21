# CodePocket Editor Lab

## 2026-10-21 导航适配首批

新增“跳转行”和“自动换行”：行号从1开始，非法或已过期输入保留并显示错误；重排通过真实Sora布局/滚动接口恢复视口。选区校验覆盖UTF-16/CRLF、版本、缓冲身份和组合输入；可取消的pre-draw队列与生产原生编辑器共用。换行选择随临时草稿旋转/切换交接，实验进程结束不保存。

[下载本批实验APK](../dist/CodePocket-Sora-navigation-2026-10-21-lab-debug.apk) · [完整报告](../docs/P1_SORA_NAVIGATION_2026-10-21.md)。真实Sora核心回归82/82，Lint0错误/6旧警告，构建与APK检查通过。Android View/InputConnection和实际IME/折行/旋转/焦点/触屏键鼠/FPS仍未执行，不将主机核心测试当作设备结果；本实验仍不接项目、SAF、真实候选或运行，不代表默认IDE迁移。


现有工程的可选Sora隔离原型，仅用临时草稿比较原生EditText与Sora基础编辑。默认IDE继续使用原生编辑器，独立应用ID为com.codepocket.ide.editorlab，不连接项目/SAF/运行器。

构建：`./gradlew :editor-lab:testDebugUnitTest :editor-lab:lintDebug :editor-lab:assembleDebug -Pcodepocket.editorLab=true`。Windows使用gradlew.bat。未传开关时settings不包含本模块；正常App及原CI依赖图不变。

固定Sora0.23.7，实验使用Java17和库要求的Kotlin2.1.21运行库；默认App仍为Kotlin1.9.25。0.24.4在当前工具链触发过编译器/Lint元数据问题，日志保留。本模块不绕过元数据检查，不升级整个项目。

60项测试（原14项Content、5项定位、22项事务与19项TextMate语法/主题）实际调用Sora Content/Indexer/Cursor/UndoManager及适配规则，不执行Android View/InputConnection。插入/历史通过SoraEditorAdapter，正文与选区边界复用原生的TextEdits；版本来自Content本身。重置/切换防组合覆盖，新Content不带旧历史，旧快照不能跨实例重放。实验草稿旋转保留，进程结束清空；切换组件仅传递文本和归一化选区，历史不跨组件迁移。基础编辑、主题和系统/省电感知光标动画仍需实机；已接离线TextMate语法，未接语义服务/完整生产EditorPort。

最新构建、测试和边界见[语法适配报告](../docs/P1_SORA_LANGUAGES.md)；[事务适配报告](../docs/P1_SORA_TRANSACTIONS.md)、[初始阶段报告](../docs/P1_EDITOR_LAB.md)与[定位报告](../docs/P1_EDITOR_SURFACE.md)保留。已有超限/不配对UTF-16草稿原样加载，20万字符与替换文本合法性只约束显式编辑命令，不截断旋转恢复的文本。APK内含LGPL2.1正文、Sora版权/源码链接及AndroidX许可资源，未决定项目自身许可证；正式分发仍须核验完整对应源码、重链接和全部通知。


TextMate固定0.23.7；使用VS Code1.95.3原始C++/Python语法及配置，来源/哈希在src/main/assets/textmate/UPSTREAM.json。语法按钮切Python/C++/纯文本，只改变规则；后台加载，失败/超4.5万字符正文明确纯文本，不截断草稿。两套本地主题不复制VS Code主题；C++部分SQL/GLSL/汇编/正则嵌入规则未打包，警告保留；非语义补全。真实引擎测试只把Android日志转发到stderr，没有View或Grammar mock；日志桥只在test目录，APK审计确认未打包。

实验单独开启coreLibraryDesugaring2.1.5以支持库的java.time/Java集合API；Dex接口的Duration已改写，D8平台转换桥仍有java.time引用。API24运行仍需实机。语法/许可离线打包，从许可/说明→语法许可可读MIT/EPL/Apache/GPL+Classpath文本；来源与精确版本见第三方清单。历史bundle不含后来适配代码，正式分发完整源码/重链接核验仍待。
