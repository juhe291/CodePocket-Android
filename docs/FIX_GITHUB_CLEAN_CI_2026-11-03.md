# 首次 GitHub CI：干净环境测试夹具修复

公开仓库juhe291/CodePocket-Android已正常接收1948720，默认分支master。首次既有Build Android APK工作流41583733713在Python测试阶段失败：共116项（两个模块未完整导入），1失败、2导入错误；尚未执行Android构建，不能说远端APK或CI通过。原始日志在verification/github-clean-ci-fix-2026-11-03/first-remote-ci.log。

## 两处实际修复

- HTTPS回环夹具的CACHE.mkdir补parents=True。干净检出没有.tools，导入fixture时原代码无法创建git-remote-probe子目录。仅补父目录创建，保留缓存范围断言、真实TLS校验和所有Git断言，不上传夹具生成的临时私钥。
- filemode_false测试显式在自有测试仓库设置core.filemode=false，并读取核对。Dulwich根据宿主文件系统探测filemode，Linux原本为true，测试此前依赖Windows探测结果。原100755断言保留，无新跳过；生产Git仍尊重真实仓库和提供方的模式能力。

## 实际验证与边界

同一隔离干净目录中，原夹具导入实际退出1（FileNotFoundError），修复后退出0并创建所需缓存；源码和两份日志均保存。完整Python157项在主机重新运行，全通过、零跳过，旧157身份精确保持；包含真实回环TLS/Git和矢量5。汇总脚本首轮缺仓库模块路径导致身份断言失败的日志保留，修正运行路径后才执行完整套件，没有使用旧结果。

本次只改测试夹具/测试设置与记录，生产输入、依赖、签名、工作流内容/触发保持，与1948720逐项核对。沿用已验证0.2.1/3的本机APK，SHA256 4715b84bb9454c5136f823ea07cf2e09d34e3cfc4c21b24a35e688502e44bd91，不为夹具再打Android包。上批1173项结果是1948720的实际集中结果，不把本次Python157当成新的全App回归或Linux已通过。

按本次推送授权，将这两处CI修复正常追加到同一master，不强推、不创建或手动调度工作流，不发布Release。后续既有CI实际状态和远端SHA保存在本地github-clean-ci-fix-2026-11-03/push-results.json；无论CI结果如何都单独报告，Android设备验收继续后置。C设置/主题、D更多语言保留后续，不因本次源码已上传就标记整个原目标完成。
