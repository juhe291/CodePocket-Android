# P3 本地 Git 引擎前置验证

生产基线fbb9d60，完整范围按[P3实施计划](P3_IMPLEMENTATION_PLAN_2026-11-02.md)。本轮是独立缓存中的引擎能力验证，没有修改app/requirements.txt、Gradle或Manifest，没有生产Git入口，没有构建P3 APK。当前可用候选仍为格式化包59e13ca5…。

## 实际固定输入

| 制品 | 字节数 | SHA256 |
| --- | --- | --- |
| dulwich-1.2.20-py3-none-any.whl | 758582 | ab521391c3475e7c507f14aeacad0cdf26e97ba5050d8010a1ff34e417c9a842 |
| urllib3-2.8.0-py3-none-any.whl | 135717 | 0cf3cae568d36aa9576b28dfb35f11328f1cb974ca7647d9475ebb86c75ac6e3 |

来源是官方PyPI元数据与files.pythonhosted.org原始wheel，实际大小、SHA、ZIP CRC、纯Python标记和成员路径已核对，两个轮子没有.so/.pyd/.dll/.exe。只解压在.tools/git-probe/site，未安装全局依赖；两个包要求Python>=3.10，本机实际使用现有3.13.9。Dulwich双许可Apache-2.0 OR GPL-2.0-or-later、urllib3为原wheel内MIT通知，原始许可逐字节保存在验证目录；尚未加入生产许可页。

实际元数据表明在Python3.13下Dulwich的必需附加依赖只有urllib3；typing_extensions只用于<3.12，SSH/GPG/可选合并等extras没有安装。首次取得urllib3时官方当前元数据为2.8.0，脚本随后改为固定2.8.0地址，不在后续执行中自动升级。搜索缓存曾展示旧版本，最终以下载制品和本地记录为准。

## 主机实际结果

[probe-local-git.py](../verification/git-preflight-2026-11-02/probe-local-git.py)以独立临时仓库运行，10项全部通过：

1. 固定纯Python模块从隔离缓存导入，未加载Dulwich原生扩展。
2. 真实初次提交，标准Git读取同一HEAD/正文，fsck --full --strict通过。
3. 已暂存、未暂存、未跟踪、删除及.gitignore均来自真实索引/工作树。
4. 分别比较HEAD→索引和索引→工作树，内容没有混在一起。
5. 取消暂存不改变工作文件，删除提交不吞掉其他未暂存编辑。
6. 中文/emoji路径、CRLF及0..255二进制字节完整保留。
7. 标准Git实际repack和pack-refs后，纯Python后端可读历史并继续提交，Git再读并fsck通过。
8. 直接证明no_verify=True仍调用post-commit；用内存钩子记录调用，没有执行仓库脚本。
9. 显式隔离配置与钩子后，仓库声明的过滤器、签名和钩子不会被执行，配置文件本身不被重写。
10. 原index.lock存在时明确触发Dulwich FileLocked，索引、HEAD、工作正文和原锁均保持。

所有Dulwich操作期间禁止subprocess.Popen，标准Git只在操作外作为主机交叉检查工具，设备实现不得依赖主机Git。初版十项通过后将锁错误断言从Exception加强为FileLocked，再次10项通过；两份日志均保留。没有新增跳过或删除断言。

## 源码发现与接入要求

- 1.2.20实际API已把提交移至Repo.get_worktree().commit，旧Repo.do_commit不存在。最初API探查的AttributeError有记录，未照搬旧文档进入生产。
- worktree.py中的post-commit位于no_verify分支之外，因此必须禁止全部外部钩子，不能只传no_verify。提交后的自动GC同样需在受控配置中明确关闭，避免大仓库隐式做额外工作。
- Repo.get_config_stack默认叠加全局配置，get_config默认展开include/includeIf。生产要在构造仓库之前校验控制目录、commondir、alternates、索引、对象路径及链接，再按明确规则读取配置；不能先打开任意Git工作树后才检查。
- 本探测的ProbeRepo采用固定SHA-1配置用于隔离试验，**不是生产安全封装**。生产要校验格式扩展、必要Git设置和文件大小，不得把需要LFS/自定义过滤器的仓库静默按普通字节暂存；应明确拒绝或实现安全等价处理。
- 当前DocumentGateway可readBytes但只有文本write，SAF还没有Git对象、索引和refs的二进制事务。必须实现新鲜读取、版本前提、失败恢复和取消边界，不能把旧运行镜像当外部仓库，也不能只做内部目录后宣称SAF已完成。
- App侧仍需独立工作服务/请求代次、保存门控、真实源码管理侧栏、提交身份/消息/历史和过期结果拒绝，之后按四组计划整批回归/Lint/APK。

## 证据与未验证范围

[原始目录](../verification/git-preflight-2026-11-02/README.md)保存官方元数据、制品与许可清单、首次沙箱DNS11001失败和主机网络成功记录、API探查日志、两轮10项日志及绑定源摘要的local-git-results.json。没有修改系统代理/DNS，没有克隆远端仓库、上传源码、推送或触发Actions。

本轮不证明Android解释器、SAF、实际JNI/进程、GitHub认证/网络或大仓库性能通过。P3-A只是前置可行，生产接入未完成；P3-B GitHub同步、P3-C设置搜索与主题、P3-D更多语言分析继续保留，整体目标仍在进行。纯计划/预检不打包，新APK仍以格式化交付记录为准。
