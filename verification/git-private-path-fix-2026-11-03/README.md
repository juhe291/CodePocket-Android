# Git 私有目录别名与手机菜单崩溃修复

用户手机图显示Git状态workspace_changed/Failed requirement，日志明确GitCredentialVault.kt:51经源码管理更多入口闪退；点击内部项目同样闪退。优先修这些阻塞问题，原C/D暂不展开。

直接原因链：Context给出的可信filesDir/cacheDir/noBackupFilesDir可以是合法别名，旧代码把它们与canonicalFile做字面相等检查；凭据/推送回执、内部克隆列表、本地/远端作业及两类恢复目录共七处均沿用了这一不合适的根目录假设。它并不表示用户真实切换工作区。

baseline.log/baseline-junit：实际8项全部失败，1分8秒。GitAliasedApplication用真实存在的trusted-alias/..目录模拟相同canonical差异，无需主机符号链接特权；两个真实Main按钮分别触发凭据库和克隆父目录构造异常，另验证本地status/init返回workspace_changed。基线测试与生产源码原文已保存，断言保持。

修复只对系统Context提供的根做canonicalFile，再在规范根下构建私有子目录；后续托管目录、UUID、文件、作业和恢复记录的链接/越界约束保持。Main构造远端控制器失败时给原生错误并保留草稿；读取内部克隆列表失败仍可使用真实系统目录/内部项目选择。没有清数据、换签名或降低加密保护。

新增正常远端snapshot、真实存储哨兵冲突与脏编辑保护。target.log/target-first-junit实际124项123通过/1失败，5分42秒；原8项崩溃基线及原凭据/SAF/服务/恢复检查通过。新增远端snapshot因测试后台get阻塞了PAUSED主机Main消息循环，真实Messenger回包未被处理，返回network；对照现有远端测试的等待方式，已修正夹具为等待期间推进Main，并保持结果断言，修复前源码/原JUnit保留。target-fixed完整11项全通过，原8条基线身份/断言保持；最后全量再次覆盖全部相关身份。不是设备Keystore或Android Git进程运行验证。

本阶段之前P3-E集中记录保留在verification/web-sidebar-delivery-2026-11-03：1138项1136通过/1既有Windows失败/1旧跳过，Lint零错误（30/1/0警告），实际6分14秒/退出1，App确有一次打包。额外依赖记录脚本因旧resolvedArtifacts触发Android项目制品类型歧义失败；旧脚本、日志保留，本目录改用现代artifactView仅筛外部模块，不改生产依赖图。中间APK冻结在.tools/web-sidebar-before-device-fix，摘要见before-fix.json，未交付或冒称含本次修复。

最终1149项1147通过/1既有Windows失败/1旧跳过，新40通过；Lint零错误（30/1/0警告），6分3秒、退出1、一次实际打包。原签名/CRC/16KiB/C++闭包/DEX/许可、25原生库/50运行资产/101旧APK保持通过。最终APK：dist/CodePocket-git-private-path-fix-2026-11-03-debug.apk，SHA256 72501cda734f4346ad4ee697b9e8aeec261c8a09acac60cc444b067d1598a4f5。冻结479输入、results.json/final-junit为最终判据。三处Manifest编号经真实资源表解析仍为原主题/图标，Core工厂由固定AAR验证；汇总修复未再次构建，详见collector-note.md。设备验收后置，不要求连接设备，不推送/发布/触发远端任务。
