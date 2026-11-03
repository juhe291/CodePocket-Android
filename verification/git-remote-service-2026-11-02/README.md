# P3-B 私有服务、受保护凭据与推送回执

从[原工作区事务阶段](../git-remote-journal-2026-11-02/README.md)继续。新增源代码尚未通过实际工作台入口让用户操作，当前没有同步UI或新APK；不把后台组件与主机证据当作完整App交付。原B→E→C→D顺序与原生Views/Sora/真实运行/保存恢复保持。

## 本阶段代码

- GitRemoteService为非导出:git_remote服务，自己的git-remote-jobs命名空间/租约，不清理:git仍在使用的作业。一次明确网络操作独占服务，后续请求返回busy，不自动取消或替换正在推送的请求。只允许自己新建的work快照；固定操作/字段，不能指定任意输出路径、force或TLS绕过。120秒硬截止，原通道默认35秒不变，新客户端等125秒；取消AtomicBoolean交给Python新run_android入口，每个有界步骤查询。主机测试覆盖RPC/寿命/取消/超时/命名空间，未执行Android上的Chaquopy Java对象桥。
- GitRemoteAddress提供无DNS的HTTPS规范化身份，覆盖UTF-8路径、IDNA、默认端口及IPv6；userinfo、query、fragment、路径穿越及非法编码拒绝且不回显原输入。GitRemoteProtocol约束回复字段、错误码、分支/OID、快照与清单指针；令牌回显检查同时处理JSON转义及键名，异常推送结果返回push_unknown。
- GitCredentialVault默认只在当前对象内临时保存，明确remember才写AES/GCM密文；Android入口使用noBackupFilesDir与AndroidKeyStore中的独立AES256密钥。每次新IV，仓库URL作为AAD，密文绑定来源；用户名/令牌不以明文写项目、偏好、日志、恢复包或后备文件。读取失去密钥不生成替代密钥，不自动删无法解密文件；保存失败不变成明文或隐式临时成功。提供清除单项、全部与临时会话方法。UI接入需复用会话对象并处理错误，不把令牌写入窗口保存状态。
- GitPushLedger在发送IPC前写持久意图，服务按作业/快照/来源/分支/本地与远端OID核对后记started；没有匹配意图不调用推送。服务器报告先落盘再回UI，超时/取消/页面失效保留不确定结果，确认结果不被迟到unknown覆盖。主进程与服务使用实际FileChannel文件锁。记录不包含凭据；清理只删除已确认记录，未知记录保留。记录上限1000，达到上限须明确清理已确认记录，不自动丢弃未知意图。

生产Keystore的依据为Android官方[Keystore说明](https://developer.android.com/privacy-and-security/keystore)及[KeyGenParameterSpec](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec)。JVM测试使用实际AES/GCM和测试密钥，验证数据/失败边界；未证明设备密钥不可导出或硬件安全级别，也没有声称执行了设备Keystore。

## 真实发现与保留记录

1. targeted-first.log：首轮36项全部通过，first-junit/first-sources.json归档。包含新21项及旧默认IPC/本地服务/工作台15项，主机RPC实际调用固定Python模块做远端配置并生成真实清单；网络推送服务生命周期用明确标注的模拟服务器报告，实际TLS/push另由Python夹具验证。
2. targeted-fixed.log：增加独立JVM锁测试后测试编译失败，变量java遮蔽java.util包名。GitRemoteSecurityTest-before-java-name-fix.kt保留旧源码；本轮没有新JUnit，不复制旧结果。修改变量名后才继续。
3. 推送报告边界：git_remote-before-report-retention.py保留修复前生产模块，report-before-baseline.py将2项新测试真正跑在旧模块上，两项均失败（protocol/network）。服务器已确认后再做本地status或连接清理会丢失成功报告；现保留已验证的本地状态及明确服务端报告，清理失败也不吞成功。原“服务端接受但报告未取得”的push_unknown断言保持，未改成假成功。python-source-all.log为早期153通过，python-report-retained.log为新增回归后的全部155通过；旧151身份保持。
4. targeted-cross.log：37项中36通过/1失败，跨进程锁已通过；失败为已记unknown的异常推送仍回IPC泛化backend码。已将回执与返回状态统一为push_unknown，并保留cross-junit/源摘要及原完整断言；不把后端异常说成远端未改。
5. 最终扩大120项全部通过，见targeted-broad.log、broad-junit、broad-results.json和checkpoint.json，原92项及默认IPC6项身份保持，新22项通过。只有命令真实结束后才归档；没有观察超时就重启活进程，也没有删断言或新增跳过。

跨进程锁测试启动仓库现有JDK的独立JVM，仅持有临时ledger.lock，确认本进程写入等待并在释放后完成；GitPushLedgerLockHolder.java无网络或用户源码访问。所有网络测试仅操作自有回环TLS仓库，没有真实账号授权/上传本项目/触发远端CI。

本地检查点暂存检查发现Git换行规范化会改变部分原始JUnit XML字节（首个混合CRLF失败XML也触发空白提示）。为本阶段原始XML添加限定范围的-text/cr-at-eol属性并重新索引，未改写原始文件；verify-staged.py逐项确认所有暂存XML/log与磁盘原始字节相同，并检查没有APK、密钥或其他工作混入。源码的正常空白检查及测试断言保持。

## 未完成项

仍需GitRemoteWorkbench实际把服务结果、原工作区事务和工作区IO串行队列连接起来，接新内部/SAF克隆目标、未保存/恢复草稿保护、凭据表单/清除、来源/分支/OID确认、推送回执查看及当前面板刷新。内部POSIX克隆执行位语义仍待核对。非push的意外桥接异常目前统一错误码的阶段细分，留待实际UI接入时处理；真实网络域错误已有独立代码，未用原始带令牌异常文字替代。

本阶段没有新界面视觉检查、整批主App回归/Lint或APK；最新已验证APK仍为P3-A本地Git26ac0bec…，本阶段只重新核对旧文件摘要。原设备/API24/IME/运行验收后置；网页辅助栏、设置/主题、更多语言均未从目标移除。
