# P3-A 本地 Git 接入批次（代码/主机/APK验证完成）

基线f1c4328，沿用../../docs/P3_IMPLEMENTATION_PLAN_2026-11-02.md四组范围。原Git/GitHub、设置搜索/主题和更多语言分析不缩减；设备后置，不推送/发布/创建任务。

## 最终收尾

[交付记录](../../docs/P3_LOCAL_GIT_DELIVERY_2026-11-02.md)与results.json为当前判据。服务/客户端、内部与SAF真实工作区、共享源码管理侧栏、受保存保护的暂存/提交/历史均已接入。52项定向通过，首轮全量1032中2失败；同批修复Sora异步布局期间撤销越界后85定向全部通过，最终1033中1031通过/1既有Windows失败/1旧跳过，原995身份保持、新38全通过。Lint0错误，27/1/0警告。两轮集中都真实退出1并实际打包，首轮中间包未交付；最终包已复制到dist/CodePocket-local-git-2026-11-02-debug.apk，SHA256 26ac0bec36f7a0a89108727452d1f96a8576086e5f9ee19d889987a568961075。

最终实际APK的原Python48/语义22/Git19/格式化20通过，签名/CRC/16KiB/Manifest/DEX/C++闭包通过；25原生库、45运行资产、99旧包保持。源码Python114输入摘要保持；Shell4/片段14按未变内容复用上批。5张原生图已看，主机和Android执行分开，整个P3目标仍未完成。

ui-first保留服务并发创建目录及真实菜单集合遗漏的失败；ui-integration-first包含平板差异前缀断言错误与编译期间新旧核心/测试快照不一致，不作为同一冻结源码成功证据。ui-integration-current是同一源码52通过，layout-gate-targeted为修复布局保护后的85通过；layout-gate-compile-failure未执行测试。full-first与final分别保存两轮完整JUnit及源摘要。首次包检查因Windows PowerShell原生stderr包装而提前中断，修正调用后verify-package.log完整通过；未因此重打包。原后端/SAF夹具及各轮失败日志全部保留。

内部标准Git锁和日志证明的重启清理、SAF未知部分写入备份与导出均有断言。SAF仍无跨文件原子性；未取得设备Git/IME/性能等验收。继续P3-B/C/D，无推送、发布或远端CI操作。

## 历史：后端与事务早期证据

- backend-baseline.log：生产后端缺失，15项全部失败；原断言保留。
- backend-first.log：实现后首次15项全部因未提交仓库的symbolic HEAD处理错误失败。backend-trace.log保留根因，修复后13通过/2失败。
- backend-unborn-fixed.log：两处差异问题——Windows子路径分隔符，以及Dulwich暂存区差异API写入临时tree对象。改为统一Git路径和直接读取真实blob后，backend-diff-fixed.log仍有1项Index.get不存在的API错误；index-trace.log保留详情，修正真实API后backend-final.log的15项通过。
- 增加完整只读字节保持和既有可执行索引位保护两项后，backend-mode-baseline.log的17项中1失败：Dulwich stage忽略core.filemode=false，丢失100755标志。适配后backend-mode-fixed.log的17项全部通过，无删断言/新增跳过。之后增加无执行权限信息提供方的模式保持与完整Unicode提交消息两项，backend-mode-provider.log的19项通过，原15项身份保持。backend-15/17-source-hashes记录当时源摘要。
- store-first.log/store-first-junit：新二进制快照7项和原PythonRunProviderTest 5项，共12通过。读取提供方新鲜字节、16MB单文件/128MB总体/10000项/32层边界、正文/元数据分离、失败只清理自有快照；根.git不进入编辑镜像和外部运行副本。原5项运行断言保持。

## 历史：事务接入检查点

元数据发布/恢复日志的store-transactions检查点18项通过。store-saf首次36项33通过/3新夹具失败，原因是Robolectric未做ContentResolver到DocumentsProvider的旧查询转换。保留原断言和JUnit，只在新测试夹具加入分派桥，实际DocumentsProvider的树校验/CRUD及PFD仍运行；未为夹具修改生产查询。增加同一日志的跨Store并发保护和提交后清理中断检查后，store-saf-bridge最终38项全通过：新存储15+新SAF3+原运行5+原Main SAF保存15，原20项身份/通过状态保持。Python全源码python-source-all.log的114项全部通过（原95+Git19），源摘要已封存。

快照与事务在workspace-core，SafGateway新增有界readBinary/writeBytes/createBinary，原文本保存仍走原保护。发布先验证整体来源、只批准本地Git元数据变更，持久备份/日志后先对象再refs；只回滚已知自身写入，未知部分写入保留备份并要求恢复，不宣称SAF跨文件原子性。成功清理先移除日志指针，私有孤立目录可在后续安全清理；同日志的发布/恢复串行化。

当前检查点checkpoint.json与store-saf-bridge源摘要为准，相关进程均已结束。后续仍需独立Git服务/客户端、工作区接入和同一原生源码管理侧栏。服务只接受cacheDir/git-jobs下自有work快照，调用run(root,requestJSON)，传modeAvailable（内部真实权限、SAF为false）；客户端在发布前校验请求代次/工作区和保存版本。编辑器及运行镜像跳过根.git，Git读取必须绕过旧镜像使用实际Gateway。

两个固定依赖已加入工作树requirements并从已核对wheel离线处理成功：Dulwich1.2.20、urllib3 2.8.0。原始许可在app/src/main/assets/git-licenses，尚需正式许可入口与实际包验证。before-apks.json记录99份历史APK；本批尚未整批回归/Lint/构建APK，最新已验证包仍为格式化候选59e13ca5…。

FileGitGateway父路径空值提示已随相关存储代码修改修正，后续编译无此新增提示。原部分写入/并发窗口的恢复限制继续记录；不把主机标准Git、Kotlin夹具或离线pip成功称作Android运行通过。
