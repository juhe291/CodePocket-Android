# CodePocket Editor Lab

> 2026-10-21计划调整：本模块冻结为内部回归工具，源码/旧测试/旧APK保留；不再扩展独立功能页或交付为第二个产品。后续直接将已验证实现接入主程序，按[主程序集成计划](../docs/MAIN_APP_INTEGRATION_PLAN_2026-10-21.md)执行。下文描述当前实验基线，当前仍未完成主程序集成；本次仅更新计划。

独立安装的 Sora 适配实验，applicationId 为 `com.codepocket.ide.editorlab`。默认 CodePocket 继续使用原生编辑器；本实验不连接 Python、Wandbox 或 PTY，不是主 IDE 的替代安装包。

## 当前两个入口

- 启动页是临时草稿：原生 EditText/Sora 对照、编辑事务、导航、离线语法、共享词法候选/片段/内置参数参考与光标定位。旋转保留；该临时草稿进程结束后清空，切换组件时历史不跨组件迁移。
- 启动页工具栏“项目 / SAF”进入真实文件实验：独立内部示例项目、文件切换、保存/Ctrl+S、系统目录授权、恢复草稿、每文件选区/滚动与最后活动路径。保存会真实写回授权目录；退出先保存，后台/旋转只做恢复检查点。手机与平板同为原生可调整窗口页面。

两个应用共享 workspace-core 的同一份工作区/SAF/草稿安全实现，七份原源码逐字节不变。实验自己的内部项目不属于主 IDE 私有项目；只能通过系统选择器授权访问外部目录，不申请广泛存储或网络权限。文件页为非导出 Activity。

[历史内部实验APK](../dist/CodePocket-Sora-documents-2026-10-21-lab-debug.apk) · [本批文件清单、测试、边界](../docs/P1_SORA_DOCUMENTS_2026-10-21.md)。133项通过（原108保持，新25）；Lint0错误/6旧警告。真实 Sora/Android View、ContentResolver 和主机磁盘操作已运行；实际设备输入法、蓝牙键鼠、卓易通与FPS未验收。

## 构建与依赖

`./gradlew :editor-lab:testDebugUnitTest :editor-lab:lintDebug :editor-lab:assembleDebug -Pcodepocket.editorLab=true`；Windows用 gradlew.bat。不传开关时 settings 不包含实验模块，默认 APK 不包含 Sora。

Java17；固定 Sora editor/language-textmate 0.23.7，实验运行时 Kotlin2.1.21，单独启用 coreLibraryDesugaring2.1.5。主程序与共享 Kotlin 源码仍为1.9.25。未升级整个工程或绕过元数据检查。workspace-core 为本地 Android 库，editor-surface 为纯Java库，editor-assist 为纯Kotlin/JVM库。

TextMate C++/Python语法和配置来自固定VS Code1.95.3，来源/哈希位于 src/main/assets/textmate/UPSTREAM.json；两套颜色主题为本地资产。后台加载、组合保护、4.5万字符预算与纯文本降级保持，未打包全部C++外部嵌入语法。自动候选/片段/参数参考复用生产规则；这不是 clangd/Jedi/LSP 或类型推断。

## 当前边界

文件实验采用文件选择列表，尚未接主工作台文件树 CRUD、多标签、设置/搜索/诊断/运行的完整生产Port；文件切换创建新Content，撤销历史不跨文件保存。文件页光标动画暂关闭，临时草稿页保留原系统/省电感知动画设置。不能将它称为默认编辑器迁移已完成。

普通文件和恢复草稿沿用20万字符限制。直接键入超限时保留屏幕文本并拒绝写入，不截断；须缩减或复制备份，超限部分不能保证进程结束后恢复。SAF项目最多1000项/20层，UTF-8严格读取和越界保护保持。项目文件列表在重新打开项目时刷新；外部改动的保存冲突会阻止切换。

Robolectric4.14.1没有实现Linux.rename；新测试仅在test目录把该调用接到实际主机原子文件替换，并专门验证替换后的字节与源临时文件消失。测试提供方通过实际ContentResolver读写磁盘文件，主动注入只读、撤权与错误读回。均为主机证据，不代表设备POSIX、系统授权器或厂商提供方验收。

APK包含原有LGPL2.1正文、Sora版权/源码链接、AndroidX及TextMate相关许可。第三方AAR、语法和许可证未修改；仅更新本项目的范围说明。历史源码bundle仅包含初始实验，正式分发的完整对应源码、重链接和所有通知仍待最终核验。

历史报告：[共享辅助](../docs/P1_SORA_ASSISTANCE_2026-10-21.md)、[导航](../docs/P1_SORA_NAVIGATION_2026-10-21.md)、[语法](../docs/P1_SORA_LANGUAGES.md)、[事务](../docs/P1_SORA_TRANSACTIONS.md)、[初始实验](../docs/P1_EDITOR_LAB.md)。旧报告保留其当时边界，当前状态以本文和最新报告为准。
