# P3-B 远端引擎与发布校验基础

从提交5b13a7b继续。生产新增git_remote.py：在自有私有快照执行明确的HTTPS远端配置、引用查询、克隆、获取、安全快进及单分支非强制推送；git_https.py继续使用前一阶段固定Dulwich1.2.20/urllib3 2.8.0。测试只向自有回环TLS临时仓库传输，不向真实账号/用户项目推送。

这只是完整P3-B的中间检查点。Android私有远端服务、正文写回事务、Keystore凭据、网络操作UI尚未接入；不能称App同步可用。P3-E网页、P3-C设置/主题、P3-D更多语言保留；本阶段没有APK构建、Lint或新UI视觉检查。最新候选仍为P3-A本地GitAPK，SHA256为26ac0bec36f7a0a89108727452d1f96a8576086e5f9ee19d889987a568961075，不包含这些新增生产源码。

## 实际改动

- 远端克隆只接受空私有目标；fetch只取选中分支的真实对象/跟踪引用。fast_forward核对本地/远端OID、祖先关系、索引特殊标志、正文/暂存区及未跟踪碰撞，支持二进制、Unicode、文件与目录互换。不调用外部Git/钩子，不合并、重置或force；标准Git仅在测试结束独立检查fsck。
- 推送核对明确的本地提交与远端旧OID；认证/超时等错误脱敏。服务器已接收而报告丢失时返回push_unknown，不把本地取消等同于远端未改。Android持久推送意图/回执尚待实现。
- 生产引擎可生成result.plan，固定写在私有work外的job目录，排他创建；请求JSON不能指定路径。清单包含每个文件的SHA256/长度、全部目录、操作/来源/分支、前后提交与快照指纹，不包含凭据。生成后重核实际指纹；失败不生成可成功发布的响应，既有文件不被覆盖。
- GitWorkspaceStore.capture新增显式backupWorktree选项，默认仍只备份.git。正文更新要求完整二进制备份和空目录备份。GitRemotePlan/GitRemotePolicy逐项核对请求/响应、完整清单、实际快照、备份和HEAD，限制各操作可变更的正文/元数据/分支，拒绝覆盖旧对象、无索引pack、非选中引用或无关元数据目录。原metadataChanges/publish的本地Git范围没有放宽；此阶段校验器没有写入用户原工作区。

## 证据

- backend-baseline.log：17项缺失基线全部失败。
- backend-first.log：首次实现17项中5通过/12失败。backend-before-head-check-fix.py保留修复前源码；原因是检出新索引后提前与旧HEAD比较。backend-head-check-fixed.log为17项通过，原断言保留。
- backend-boundaries-first.log：新增边界后21项中20通过/1失败。修复已有SSH origin阻断显式改HTTPS的读取顺序；backend-boundaries-host.log为21项通过。文件/目录互换、外部过滤器、索引隐藏标志等断言全部保留。
- plan-baseline.log：新增清单功能3项缺失错误。backend-plan-host.log为25项通过；python-source-all.log为全部151项通过，旧126项身份由record-checkpoint.py逐项比对。
- native-plan-host.log、native-first-junit/：首轮原生39项通过，新增GitRemotePlanTest17项、原GitWorkspaceStoreTest19项及SafGitBinaryIntegrationTest3项。native-first-sources.json归档对应源码；编译期间没有改这些生产/测试输入。
- capture-native-fixtures.py只在.tools/git-remote-probe新建自有测试目录，通过真实TLS/固定生产引擎生成克隆、获取、快进、配置4份结果。native-engine-fixtures.json保存二进制Base64与实际清单/请求/响应及源摘要，没有真实令牌。Kotlin测试显式迁移唯一的私有root字段，指纹/文件/目录/HEAD保持原结果，用这些字节独立检验原生发布边界；不是凭手写清单判断两端兼容。
- 新增跨语言检查的第一轮native-plan-engine.log为40项中39通过/1失败，native-engine-failed-junit/及native-engine-failed-sources.json保留对应JUnit与源码。实际清单已正确包含新建的空.git/info/exclude，测试用optString比较把“不存在”与“空内容”都算作空字符串，导致预期集合漏文件；修复比较同时核对has存在性，完整集合断言保持，不删空文件。最终40项全部通过（新18、旧22），见native-plan-engine-fixed.log、native-engine-junit/和checkpoint.json；相应目录仅在命令真实结束并核验后由record-native.py/record-checkpoint.py建立，不能拿首轮JUnit冒充后续执行。

## 失败与验证边界

backend-boundaries-fixed.log是在沙箱内执行，网络操作返回timeout，21项中18失败；不将它称代码通过。经独立主机执行同一代码的自有回环TLS测试后21项通过。native-plan-first.log明确为无法连接Gradle守护进程，真实退出1；之后获准使用主机及原离线缓存执行，39项真实通过。没有仅因观察超时而重启活进程。

新增清单测试的第一次主机请求一度因自动审批配额用尽而未执行（审核未能完成，并非不安全判定）；提示恢复时间之后重新走相同审批流程获准，backend-plan-host.log为实际执行结果，没有绕过审批。record-native-before-log-encoding-fix.py保留初次记录器：将PowerShell5 UTF-16日志误读为UTF-8触发AssertionError；记录器按BOM读取后成功归档，未重跑或伪造测试。

record-checkpoint-before-root-import-fix.py保留身份归档初次错误：从verification目录启动脚本没有把仓库根加入Python导入路径，tests/test_vector_resources.py不能导入原scripts包，收集数量断言失败。补齐ROOT路径并明确拒绝loader.errors后，收集151个唯一身份、保留旧126身份、源摘要及现有APK摘要全部核对通过。没有因此改测试或重跑已通过的测试。

未执行Android服务/Keystore/Chromium/设备验收，不把主机TLS、SAF提供方测试或源码编译当成实机成功。整批P3-B收敛后仍须统一主App回归、Lint、APK及签名/运行资产检查；原Windows头文件失败和旧跳过保留。

## 下一步

1. 新正文事务与原恢复入口衔接：对象先写、正文创建/修改/删除与文件目录互换、索引与引用后写；已知自身版本可恢复，未知部分/并发替换保留原备份并导出，SAF不声称跨文件原子性。标准锁扩展到远端引用，旧元数据事务不能误读新日志。
2. 原生远端服务与生命周期、克隆新内部/SAF项目、当前未保存/恢复草稿保护、推送持久意图/回执，及Android内部目录可执行位语义；目前只验证SAF索引可执行位保持，不能据此宣称内部POSIX检出模式已验收。
3. 默认临时凭据、明确记住时Keystore加密、清除和失败状态；共享原生UI实际操作。然后按B→E→C→D继续，不创建远端任务、不推送或发布。
