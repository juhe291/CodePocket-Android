# 欢迎区定向检查说明

首次 welcome-first 因 WorkbenchWelcome 的继承 LayoutParams 类型引用编译失败；没有复制旧 XML。修正为 FrameLayout.LayoutParams 后，welcome-implemented 真实执行 11 项：10 通过、1 失败；其中原始 5 条启动基线全部通过。

新增失败来自测试将“关闭文件后重新打开”误当成“同一个打开的文件切换侧栏”。当前 SoraEditorAdapter.replaceDocument 明确为每次打开创建新 Content，关闭并重新打开采用相同稳定 EditorSessions 文件 key，但不承诺复用已关闭的 Content。未修改原生编辑器架构；将这条本批新增测试更正为同时断言新 Content、同一 WorkbenchEditor、相同正式文件 key、相同正文和真实保存结果。保留原 5 条启动基线和原 3 条侧栏基线的身份及断言。welcome-implemented 原失败日志/XML 保留，不加入跳过。

旧编辑器/保存/侧栏测试原先依赖 Main 启动隐式样例。现在在测试调用 Main.setup 前显式 Workspace(context) 准备已有项目，使用原构造器且不覆盖已有文件；32 个位置清单见 explicit-project-fixtures.json。全新启动测试不调用它。FormSurface 平板搜索测试改为实际 sidebarSearchView 宿主，同时保留真实未保存词、结果数、路径/行/正文、dirty 断言并增加无弹窗断言。ShellUi 对欢迎状态列明可用入口，继续明确禁止无文档时保存、运行、导出、重命名、删除。

集中定向首次 127 项中 126 通过、1 失败。唯一失败为旧 MainFileSettingsIntegrationTest.deletingTheLastFileCreatesARealFallbackAndInvalidatesTheOldConnection，明确要求自动创建样例，与本批新需求相冲突。保留此历史测试身份，把相同数量的正文/磁盘/active/loaded 断言调整为空区且不创建文件，并保留旧 InputConnection 失效断言，增加根目录空和原生欢迎区断言。没有跳过或删去文件安全检查；原日志/XML保留。新增空 SAF 提供方项目、原 5 条启动基线与保存/冲突/关闭/重开等在此 127 项中均通过。

下一次定向 128 项 127 通过/1 失败：新加的“关闭项目后打开搜索”真正暴露了 hideCompletion(true)/hideSignatureHelp(true) 在无文件时请求空路径会话的缺陷。已在生产加正式已载入文档保护，同时保护旧片段弹窗回调；不改失败断言。另补平板搜索 Back 行为：返回应关闭当前搜索，而非切换打开文件树。AuxiliarySurface/ContextBatch 的旧平板弹窗及底部取消断言按新侧栏/48dp 顶部关闭更新，保留原实际数据、缓冲身份、定位和触摸尺寸检查。

首次完整 967 项中 958 通过、8 失败、1 旧跳过（full-first-*，退出 1）。除既有 Windows 头文件失败外：WorkbenchViews 的 4 条局部夹具未声明已打开项目/activeFile；TabletAnimationLayout 和 TerminalWorkbenchView 各 1 条原已有项目启动夹具缺 workspaceOpen；补齐夹具真实语义，不改原断言。同时 InteractionTest 捕获文件树菜单变 8 项，生产修复为关闭项目仅保留文件分类，文件树仍 7 项；加入实际被消费的 fileMenuItems 集合并保留全部命令/去重检查。首次包仅作中间检查不交付，首次包内 Python48/语义22/C++及签名通过，相关证据另存 first-build-artifact。

第一次包内语义检查因未沿用仓库临时目录，在 sandbox AppData 临时目录清理报权限错误（22 项/30 errors），原日志和 JSON 保留 semantic-apk-first-temp-permission.*。只把进程 TEMP/TMP 指向已存在 .tools/semantics-tmp 后原脚本/包/断言 22 项全通过；未改脚本或测试、未改系统权限。

第二次完整 967 项 965 通过/1 既有失败/1 旧跳过后，逆向边界审查新增 tablet-outgoing-baseline：真实 Main 的关闭快照 Flight 显示 left=48、dx=-24、rail=48，在 0.25 进度已覆盖活动栏。这不是夹具假设，原断言完整保留。生产仅去掉该快照的水平偏移，保留原位置的淡出、立即收起真实面板和动画取消；新增测试使最终完整用例应为 968 项，原 946 身份/跳过继续保留。需要第三次集中验证，前两次包均不作为最终交付。
