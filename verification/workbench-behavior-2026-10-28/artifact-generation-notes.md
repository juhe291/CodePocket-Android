# 包检查代次

首次集中构建后 APK SHA256 为 47923b5fbd7c1f90c18c8c00beb6d87e8b37fe2e92c99fd29bc1245fe3325139，签名/对齐/Python48/语义22/C++检查已保存 first-build-artifact。初次语义检查的临时目录权限错误保留单独日志。

第二次集中命令先运行测试，尚未 package/assemble 时曾再次启动 verify-package.ps1；那次仍检查上述首次 APK，不能作为第二次最终包证据。待第二次命令结束且实际 APK 生成后，必须重跑 verify-package.ps1，并用三个 JSON 的 APK SHA、原签名、源快照及 ZIP 比较核对最终包，再运行 collect-results.py。未把第一次 APK 复制到 dist 当作最终包。

最终第三次构建结束后重跑 verify-package.ps1，Python48/语义22/C++检查JSON均绑定9bb61d2451b5ce80ca3b0ccd0b49d3f43c143e4e268b9bc683fa26ab3c44d499，原签名与元数据/对齐通过。collect-results.py还明确排除第二次中间包673d5b586bc13e93550a7ede2acbd8237345484d4c036d29b92c02e022a20870后才交付最终文件。
