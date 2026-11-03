# 固定C++源码下载的网络故障修复

cfbcd40已推送且远端SHA核对，CI41615583860两次尝试均在mirrors.kernel.org下载固定libiconv源码时失败，分别为Network is unreachable及Connection reset by peer。两次都未进入Kotlin回归，原日志保留，不能称CI通过或上述10项已在Linux验证。

本次只改构建准备：该锁定源码优先使用https://ftp.gnu.org/gnu/libiconv/libiconv-1.19.tar.gz，原镜像作为明确的第二来源；仅遇到连接/超时等传输错误才尝试下一地址，原SHA256不匹配立即失败，不因切源接受错误文件。离线缓存、--fetch授权及原版本/摘要/许可/源码打包保持，不改工作流或App功能。

官方地址已实际下载5921103字节，SHA256仍为88dd96a8c0464eca144fc791ae60cd31cd8ee78321e67397e25fc095c4a19aa6，与原锁定源码完全一致。新增四项合同基线真实失败，修复后13项下载检查通过；完整源码172项通过，旧168身份保持，无跳过。离线重新准备21份JNI/资产及元数据字节一致，本地原签名36c76a0b…APK未重建。

证据目录verification/cpp-source-download-fix-2026-11-03。随后正常追加推送，仅以新CI实际结果判断；C设置搜索/主题的本地内容和六项缺失基线不混入推送，设备验收后置，原C/D完整目标保留。
