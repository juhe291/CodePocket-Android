# P3-B 远端工作台与原生界面阶段

从d95a97f继续，实际变化为GitRemoteWorkbench连接私有服务/原文件事务/WorkspaceIo、独立内部和真实SAF克隆目标、原Main项目导航与草稿保护、远端菜单/表单/凭据/推送确认和回执。原本地Git、Sora、设置草稿和真实运行保持。

- targeted-first.log：129项128通过/1失败，first-junit/first-sources.json归档。旧120全部通过，8个新工作台用例通过；Windows Git拒绝Robolectric长目录，见first-failure.md。修正后9个工作台用例全通过，原fsck断言保持。
- ui-compile-first.log：Kotlin宿主对象的status方法遮蔽Main属性引用，编译未通过，没有复制旧JUnit冒充新执行。限定Main接收者后继续。
- targeted-ui.log：133项129通过/4失败，ui-junit/源摘要/两张首轮原生图保留。3项因克隆后的activePath为空暴露旧默认项目导航条件误用于新内部项目；另1项暴露凭据销毁清理仅依赖异步回调。修复生产条件和同步清空，原断言不变。
- targeted-broad.log：134项全部通过，旧120与首轮129/133身份保持；9个工作台及5个实际Main/Sora界面用例通过。新增真实原生推送确认到持久回执，不使用真实账号。broad-results.json/broad-sources.json归档当时输入。
- python-first.log：全157通过，旧155身份保持。python-results.json确认Python输入自运行以来未变；新增纯本地snapshot和POSIX/SAF执行位配置测试，不假称Windows执行了POSIX chmod。

serve-fixture.py只创建本仓库.tools下的自有UUID TLS仓库。HostRemoteGit通过真实Robolectric Messenger服务调用固定CPython/Dulwich，执行回环TLS获取/推送；不等同Android Chaquopy或真实GitHub账号验证。所有进程在测试结束后退出，未因观察超时重启活进程。

随后加入已确认回执清理入口，并将其完整断言放入[集中流程](../git-remote-delivery-2026-11-03/results.json)：1109项1107通过/1既有失败/1旧跳过，Lint零错误，新76项全通过，一次实际新包；不能把本134阶段结果当作加入清理后的冻结源摘要。新APK见[交付记录](../../docs/P3_GITHUB_SYNC_DELIVERY_2026-11-03.md)。
