# P3-B 集中交付证据

full-build.ps1在离线依赖环境统一执行主App全量、相关模块Lint和Debug构建。实际7分36秒，1109项1107通过/1既有Windows头文件解压失败/1旧跳过，退出1；只实际packageDebug一次。没有为汇总或文档再次打包。

build-inputs.json在构建前冻结454份输入；before-apks.json冻结100个历史APK。archive-results.py检查新鲜主AppJUnit、旧1033身份和跳过集合，归档完整结果；Sora Lint和终端146分别有明确UP-TO-DATE依据，editor-assist为NO-SOURCE。Lint仍为App27/Sora1/Workspace0警告、零错误。

verify-package.ps1/各专项日志针对实际新APK；Python48、语义22、Git/HTTPS/远端/格式化82项通过。新增scripts/verify_git_remote_apk.py严格核对旧P3-A归档，只允许两个新模块/app元数据，原依赖归档、25原生库和46份运行资产保持；CPP检查器新增独立严格参数，原模式边界不放宽。Manifest仅增加非导出:git_remote服务，权限保持，DEX确认包含新生产类且未混入HostRemoteGit。

collect-delivery.py再次检查冻结输入、所有旧APK、原证书、运行资源、实际生成的Shell/片段内容和最终图摘要后，才复制一个dist候选。collector-note.md保留首次汇总读取旧片段路径的错误；修复汇总不改App或打包结果。

results.json、test-case-manifest.json、final-junit和各实际包检查为最终依据。候选SHA256 c63e8df1d0f6b89df2f972fe2a58117c43a8fd7b0da982eb30c1e68571492c1c。未连接Android设备、未验证设备Keystore或真实GitHub账号授权；没有推送/发布/远端CI/自动任务。整体目标继续E→C→D。
