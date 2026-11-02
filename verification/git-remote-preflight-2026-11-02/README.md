# P3-B HTTPS Git 前置与生产传输检查点

从5b13a7b继续 [四组计划](../../docs/P3_GITHUB_SYNC_BATCH_2026-11-02.md)，整个P3目标保留。当前只有生产传输基础，远端操作/工作树事务/加密凭据/UI/新APK未完成，不能称App同步已交付。

## 实际结果

- 独立纯Python/Dulwich/urllib3 HTTPS夹具8项通过：真实TLS和pack/符号HEAD、匿名公共读取/私有认证、拒绝不受信任证书、禁止重定向/跨来源、真实单分支快进上传、标准Git fsck、拒绝陈旧远端和传输上限。只更新本机自有临时仓库，没有远端账号或真实项目推送。
- `app/src/main/python/git_https.py`已加入生产工作树：HTTPS规范化、凭据与仓库来源绑定、严格证书/主机名验证、无外部凭据助手/环境代理、无静默跳转、限定Git HTTP路径、单连接有界池/字节/请求/时间/取消保护，错误码不包含凭据。`receive-pack`开始发送后记录不确定结果标志，最终的持久推送意图/确认仍需Android调用方实现。
- 新生产模块的12项缺失基线全部失败，原断言保留；实现后12项全部通过。全Python **126项通过**，旧114项身份保留、新12项；不是Android执行证明，也不是完整安全推送业务验证。`source-hashes.json`与`python-test-identities.json`绑定当前输入。
- 从已交付APK中读取真实 `assets/chaquopy/cacert.pem`：240216字节、121张根证书、SHA256 9cc2a774b5198dcff14d9be1e66091f538975d867ce029a96bce15a55dfd730f。使用这一原有CA包严格TLS只读获取GitHub官方公共示例的引用通告，HTTP200，无凭据、无跳转。不是通过新文档推测证书可用。
- 当前APK仍为 `dist/CodePocket-local-git-2026-11-02-debug.apk`，SHA256 26ac0bec36f7a0a89108727452d1f96a8576086e5f9ee19d889987a568961075；未构建/重新标记新包，原Debug签名未动。测试TLS证书/私钥仅在.tools/git-remote-probe/tls，是隔离夹具，不是APK签名密钥或生产信任绕过。

## 原始失败与诊断

`first.log`为固定Dulwich异常类导入错误，未执行测试；按实际client.py修正。`import-fixed.log`对应原PID20136/exec46655：首项真实fetch等待单连接池，无连接回收且池等待没有上限。进程通过命令行/PID确认仍活跃，没有因观察超时直接重启；独立10秒诊断`fetch-diagnostic.log`证明线程阻塞在urllib3连接池`queue.get`。只结束已确认的自有夹具进程，终态退出-1，缓存保留。不是App服务卡住。

`pool-fixed.log`的8项主体通过，1项teardown因普通PoolManager没有close失败；results-pool-fixed.json保留。补充显式close/release及pool_timeout、修正不同池的清理后final.log为8项通过。负面传输上限用例主动断开连接时服务器可能记录WinError10053，原文保留，其用例及进程真实退出均通过；不将这些诊断堆栈删掉以美化日志。

`public-ca-first.log`保留沙箱DNS11001失败；一次授权的主机只读重试成功，public-ca-host.log/public-ca-result.json/public-refs-advertisement.bin留档。未修改DNS、代理、CA、系统配置或用户账号。

## 下一步

生产远端操作只在自有Git快照中执行；克隆的新目标和快进正文发布要扩展原事务，真实内部/SAF都保护原正文和恢复草稿。推送使用明确远端/分支/OID与非强制CAS，取消/超时需持久记录可能已接受的结果。Android Keystore加密令牌、清除入口和原生表单/共享侧栏完成后，再集中主App全量/Lint/APK及实际包检查。P3-C/D、设备后置和不推送/发布/新建自动任务约定保持。
