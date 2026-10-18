# CodePocket Editor Lab

现有工程的可选Sora隔离原型，仅用临时草稿比较原生EditText与Sora基础编辑。默认IDE继续使用原生编辑器，独立应用ID为com.codepocket.ide.editorlab，不连接项目/SAF/运行器。

构建：`./gradlew :editor-lab:testDebugUnitTest :editor-lab:lintDebug :editor-lab:assembleDebug -Pcodepocket.editorLab=true`。Windows使用gradlew.bat。未传开关时settings不包含本模块；正常App及原CI依赖图不变。

固定Sora0.23.7，实验使用Java17和库要求的Kotlin2.1.21运行库；默认App仍为Kotlin1.9.25。0.24.4在当前工具链触发过编译器/Lint元数据问题，日志保留。本模块不绕过元数据检查，不升级整个项目。

41项测试（原14项Content、5项定位与22项事务）实际调用Sora Content/Indexer/Cursor/UndoManager及适配规则，不执行Android View/InputConnection。插入/历史通过SoraEditorAdapter，正文与选区边界复用原生的TextEdits；版本来自Content本身。重置/切换防组合覆盖，新Content不带旧历史，旧快照不能跨实例重放。实验草稿旋转保留，进程结束清空；切换组件仅传递文本和归一化选区，历史不跨组件迁移。基础编辑、主题和系统/省电感知光标动画仍需实机；未接语法包/语义服务/生产EditorPort。

最新构建、测试和边界见[事务适配报告](../docs/P1_SORA_TRANSACTIONS.md)；[初始阶段报告](../docs/P1_EDITOR_LAB.md)与[定位报告](../docs/P1_EDITOR_SURFACE.md)保留。已有超限/不配对UTF-16草稿原样加载，20万字符与替换文本合法性只约束显式编辑命令，不截断旋转恢复的文本。APK内含LGPL2.1正文、Sora版权/源码链接及AndroidX许可资源，未决定项目自身许可证；正式分发仍须核验完整对应源码、重链接和全部通知。
