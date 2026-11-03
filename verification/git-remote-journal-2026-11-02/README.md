# P3-B 原工作区正文事务阶段

从远端引擎/清单阶段继续，[前阶段证据](../git-remote-workspace-2026-11-02/README.md)保留。此阶段新增GitWorktreeTransaction及GitWorkspaceStore.publishRemote，实际写回原生内部目录或真实SAF文档流；还没有Android远端服务、凭据或同步UI。不能把存储层完成称App同步可用。

## 四组修改

1. 独立的version2远端日志，旧version1本地Git事务范围不变。原工作区入口recover/description/export按版本分发，未知版本/其他scope保留记录并失败。原工作台继续使用同一filesDir/git-recovery目录及串行WorkspaceIo；并发发布/恢复持有同一日志锁。私有日志与前后文件备份写后sync；成功保留committed记录到下一次安全清理，不删除成功发布的用户文件。
2. 原工作区写回。再次核对清单/完整备份/原工作区，先建立必要元数据目录、持有标准Git锁，再写不可变新对象；正文删除、空目录移除/新建、二进制写回和模式处理完成后才写索引与引用，HEAD最后。支持文件/目录互换并保留未跟踪内容。内部原子文件替换保留已有执行位，SAF不调用不存在的chmod接口。GitRemotePolicy仍限定每种操作/选中分支及对象/配置范围。
3. 恢复与不确定结果。每一步先记录意图与目标身份，回读后记录确认；已确认自身写入可按备份恢复，已确认删除可重建原字节，重建后未完成的已知空文件可继续恢复。未确认删除、未知部分内容、外部身份替换或提供方改名保留备份并停止，不猜测原文件未改，也不静默覆盖。恢复包包含目录/步骤记录及before/after/current字节；SAF不宣称跨文件原子性或外部提供方绝对无故障。
4. 原生Git锁与测试。远端发布持有index/HEAD/config/packed-refs及选中的heads/remotes引用锁；nonce写入恢复记录，重启只清理日志证明属于自身的锁，外部替换保留。恢复只给文件引用加锁，不给目录误加引用锁。实际TLS引擎生成的4份结果复用于内部/不透明ID提供方/真实SafGateway+ContentResolver/PFD测试，无真实账号网络操作。

## 验证记录

- targeted-first.log：57项中54通过/3失败，first-junit及first-sources.json保留。3个失败均为测试把真实Git文件的CRLF写死为LF，实际发布字节正确；GitRemoteJournalTest-before-crlf.kt保留旧测试。改为与真实夹具Base64解码字节精确比较，未标准化换行、删文件、删断言或新增跳过。
- targeted-saf.log：62项全部通过。新GitRemoteJournalTest19项、SafGitRemoteJournalIntegrationTest3项；原清单18/工作区19/SAF3项保持。包括已删除文件重建时中断与未知部分恢复、真实SAF权限丢失及真实文件流截断。saf-junit/saf-sources.json归档对应输入与结果。
- broad.log：92项全部通过，进一步核验新恢复分发与旧GitWorkbench/服务/Main/ShellUi路径。原本地工作台52项身份及前阶段清单18项保持，本阶段新增22项全部通过；无新增跳过。数值及身份比对以broad-results.json、broad-junit和checkpoint.json为准，归档只在进程真实结束后执行；不拿上轮JUnit充当本轮结果。
- Python源码及TLS引擎未改，151项结果按前阶段源摘要复用，没有重称本轮执行。record.py逐项核对Python输入哈希。新包未构建，旧P3-A APK26ac0bec…摘要由checkpoint.py重新核对，仅证明该旧文件保持。

测试进程每次均观察到终止后才改对应生产/测试源码；没有仅因一次等待无输出而重启。纯后端阶段没有新增UI绘图/视觉验收，不执行整批主App回归/Lint/打包；这些仍留给P3-B服务与UI接入后集中运行。

## 下一步与范围

继续私有远端服务（超时/取消/作业租约）、默认临时及明确选择保存的Keystore凭据、持久推送意图/回执、克隆新内部/SAF目标与未保存/恢复草稿保护，再接共享原生界面。内部POSIX克隆检出的执行位语义仍要核对，当前Windows主机及SAF索引保持测试不能替代该项。恢复日志是整份原子替换，大项目的记录频率/提供方性能留待规模测试，不宣称已做移动设备性能验收。

原P3-E网页侧栏、P3-C设置/主题、P3-D更多语言均保留；设备后置，不要求连接设备，不推送/发布/触发远端CI或创建自动任务。整体目标未完成。
