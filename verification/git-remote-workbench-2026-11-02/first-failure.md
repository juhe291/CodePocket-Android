# 首轮真实结果

2026-11-03运行targeted.ps1，129项中128通过、1失败，无跳过。first-junit和first-results.json保留实际结果及原120项身份；first-sources.json记录当时输入摘要。不是整批回归、APK或设备结果。

失败位于actualTlsClonePublishesItsOwnInternalProjectAndStandardGitReadsIt的标准Git fsck检查：Windows Git在打开Robolectric生成的超过260字符目录前退出128，原始错误为Filename too long。真实克隆、工作树内容和默认项目保留断言此前已通过，其余8项新工作台用例也通过。

修正仅将已发布仓库逐字节复制到本仓库.tools/git-remote-probe内更短的独立UUID路径，新增完整文件集合/字节相等断言，然后执行原git fsck --full及退出码0断言。没有删除Git检查、跳过用例、缩减断言或修改生产文件内容。后续结果另存，不覆写首轮JUnit或日志。
