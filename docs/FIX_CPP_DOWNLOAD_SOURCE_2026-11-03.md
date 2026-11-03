# C++固定依赖下载源修复

从4678f2c接续；现有CI41586681306已通过Linux Python159、SDK准备与SDK35/NDK安装，随后在packages-cf.termux.dev下载clang_21.1.8-3_aarch64.deb时实际HTTP403。日志保留，不把远端未构建写成成功。当前本地有效包仍为Git目录转换修复候选36c76a0b…。

## 本批三项修改

1. 准备脚本新增显式--package-repository，只接受原CDN与Termux官方主源两个HTTPS地址。默认继续使用锁文件地址，原离线缓存/--fetch选择、大小/摘要检查与源码/许可保持；不修改锁定版本或SHA256，不自动切任意镜像。
2. 原工作流的同一C++准备步骤显式选择https://packages.termux.dev/apt/termux-main。后续脚本/任务/权限/触发/产物行为不变，没有新建工作流、自动任务或发布Release。
3. 增加9项构建下载合同检查：默认源、明确主源、拒绝未知/HTTP地址、缓存/旧缓存离线使用、坏缓存拒绝、未授权联网禁止、下载摘要验证后才能成为有效缓存。原断言保留，新增分支基线9项中3个测试方法失败（含3个非法地址子例，共5处失败），修复后9全通过。

## 实际验证

- Termux[官方镜像文档](https://github.com/termux/termux-packages/wiki/Mirrors#primary-host)列出packages.termux.dev为主源，packages-cf为CDN；实际主源HEAD返回200。随后从主源完整下载锁定的12个依赖，86027684字节，逐项大小与原SHA256均匹配，没有使用新版本替代。
- 完整主机Python168项全通过、零跳过，旧159身份保持、新9项全通过。真实Linux结果仍须以随后同一工作流为准，不能把主机结果冒称远端已通过。
- 不开--fetch，用同一脚本和主源参数重新准备，21份JNI/资产/prepared.json逐项字节相同。当前APK SHA256仍为36c76a0b0a38909ee4d8c8b06267ab2af229d1a8b6607ec4506f125571d4e8d1；没有改变生产运行模块或签名，也没有重打本地APK。
- 首次新测试在沙箱默认Temp出现权限/清理错误，原日志/源码保留；测试缓存改为验证过范围的本仓库.tools目录后才取得干净功能基线及最终结果，无删断言或新跳过。下载二进制只留忽略提交的.tools/cpp-download-source-probe，不上传Git。

证据在verification/cpp-download-sources-2026-11-03：primary-downloads.json、prepared-before.json/prepared-results.json、完整测试/失败日志。正常追加推送后远端SHA和既有CI实际结果另存本地push-results.json，不为记录自证SHA再提交触发工作流。

远端清洁环境不含本机签名私钥；其现有CI Debug制品不能冒称本机原签名覆盖更新包，正式Release仍需原签名且不能上传密钥。本批不配置远端签名秘密或自动发布。Android设备验收继续后置；C设置/主题与D更多语言保留，整个原目标仍活动。
