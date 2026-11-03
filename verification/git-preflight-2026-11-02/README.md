# Git 前置验证（非生产接入）

基线fbb9d60。范围与下一步见../../docs/P3_IMPLEMENTATION_PLAN_2026-11-02.md，实际报告见../../docs/P3_GIT_PREFLIGHT_2026-11-02.md。

- metadata-first.log：沙箱DNS11001失败；metadata-host.log为同一官方固定版本元数据读取成功。
- dulwich-1.2.20-metadata.json、urllib3-2.8.0-metadata.json、candidate-inputs.json：官方制品、版本、大小、摘要和依赖；两份原始wheel只在.tools/git-probe/wheels，未打包。
- licenses/：wheel原始许可字节，逐项SHA在candidate-inputs.json中；不是整个项目的发布通知闭合声明。
- api-inspection.log：旧Repo.do_commit不存在的原始错误；api-current.log为实际get_worktree/WorkTree API签名。
- local-git-first.log：10通过。将锁错误断言加强为确切FileLocked后，local-git-final.log仍10通过；local-git-results.json绑定最终探测源SHA。
- 探测期间禁用外部子进程，只在Dulwich操作之外用标准Git交叉验证实际对象、索引、pack与fsck。临时仓库位于.tools/git-probe/local-*，仅作主机夹具，没有第二份产品项目。

Python纯实现候选可行，但生产服务、SAF二进制事务、仓库边界和原生源码管理入口尚未实现；Android/GitHub实际执行未验证。没有修改生产依赖，没有构建APK、推送或上传仓库。其他P3工作保持完整范围。
