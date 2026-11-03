# 手机Git入口闪退与私有目录路径修复

用户设备日志在GitCredentialVault.kt:51抛IllegalArgumentException，触发链是SourceControlPanel更多按钮→远端控制器→凭据库构造；另报告顶部“内部项目”点击闪退。设备图的workspace_changed/Failed requirement来自本地Git错误展示，不能据此认定用户真实切换了工作区。

## 原因与修复范围

旧Git代码把系统Context给出的filesDir/cacheDir/noBackupFilesDir直接拼接私有子目录，然后要求absoluteFile与canonicalFile完全相同。可信系统目录若以合法别名呈现，会在任何网络或Keystore操作前触发这一条件。根目录关系应先解析，随后对App自建子级保持严格链接和越界保护。

按这个边界修复七处：临时/加密凭据、推送回执、内部克隆项目、Git本地作业、Git远端作业、本地恢复目录、远端恢复目录。没有更换密钥、存储位置的物理目标、项目标识、文件版本或加密格式，也没有把所有子路径一律canonical化后放行。

Main另保留存储失败边界：凭据库构造失败显示原生错误，当前正文/恢复草稿保持；内部克隆列表不可读仍能打开真实工作区选择。取消系统目录选择不再无必要地构造远端凭据控制器；待恢复的非敏感表单/目录选择保留。

## 验证进度

证据目录verification/git-private-path-fix-2026-11-03。修改前8项全部失败，包含两个实际Main按钮；使用真实存在的trusted-alias/..产生同类canonical差异，不要求主机符号链接权限。修改后首组124项123通过，仅新增远端Messenger测试因暂停主机消息循环的夹具等待失败；原失败原文保留并修正等待方式，全部断言保持，完整11项随后全部通过，8条原基线身份及断言保持。

最终集中1149项1147通过/1既有Windows头文件解压失败/1旧跳过，旧1109身份/跳过保持、新40全通过（网页29+本修复11）；Lint零错误，App30/Sora1/Workspace0警告。实际6分3秒，完整命令因既有失败退出1；最终App真实打包一次。此前网页首轮中间包未交付，本轮收到设备阻塞问题后必要重建，共两次实际打包。

[最终修复APK](../dist/CodePocket-git-private-path-fix-2026-11-03-debug.apk)：123882556字节，SHA256 `72501cda734f4346ad4ee697b9e8aeec261c8a09acac60cc444b067d1598a4f5`，沿用原Debug签名。原签名/CRC/16KiB/C++闭包、真实生产DEX与许可通过；25原生库/50运行资产逐字节保持、101旧APK保持。Python源码152、包内Python48/语义22/Git及格式化82、Shell4/片段14以未变内容/源摘要复用，矢量5项本轮重跑；终端146有效UP-TO-DATE。Manifest新增的CoreComponentFactory来自固定Core1.1.0，三个资源引用按实际资源表解析后仍是原主题/图标，其他组件和权限保持。

最终证据为results.json、冻结479输入、final-junit、实际包检查及collect-delivery.log。原依赖脚本失败、远端夹具等待失败和Manifest资源编号汇总失败均保留；最后一项只修汇总，没有再构建APK。不把当前代码修复或主机测试称为手机已通过；Android Keystore、真实Git服务和设备验收继续后置。后续设置/语言工作暂不展开，先完成这次修复交付。
