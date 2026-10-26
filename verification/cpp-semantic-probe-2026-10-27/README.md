# C++语义前置验证证据

2026-10-27。本目录是同一CodePocket项目的工具与验证记录，不是第二个App。生产仍为c5672c7/P2-B，本阶段没有新APK或设备执行结果。详细结论、限制与后续接入范围见[报告](../../docs/P2_CPP_SEMANTICS_PROBE_2026-10-27.md)。

- `package-candidates.json` / `InRelease` / `signature-verification.log`：固定候选及实际GPG验签。官方公钥在upstream，私有密钥环仅在.tools。主机默认DNS失败和绝对路径GPG导入错误保留在日志；更早的xz路径404见会话工具输出，不冒称本目录有该原始日志。最终使用gpgv直接读取公钥验证；临时解析映射只用于命令，未改系统DNS/代理。
- `inspect-index.py`：检查收到的索引与InRelease摘要、记录实际签名指纹；运行需要.tools/cpp-semantic-probe下保留的Packages.gz/InRelease。
- `download-packages.ps1`：按固定索引摘要下载/续传。可选ResolveAddress只用于当次命令；重试时重新查询官方域名，不能长期假设本机当时解析的IP仍有效。所有原始包在.tools缓存，没有安装。
- `inspect-elf.py` / `elf-inspection.json`：从真实包提取候选ELF和Android头文件，实际解析ELF并用仓库NDK llvm-readelf交叉检查；跳过符号链接，许可的链接目标另由官方文本包补齐。
- `stage-native.py` / `native-bundle-inspection.json`：在工具缓存生成独立名字/ORIGIN的9文件闭包；原始输入保留，实际修改后重新核验加载段和依赖，不改主App旧JNI库。压缩ZIP不是APK，未执行其中二进制。
- `host-version.txt` / `probe-lsp.py` / `host-semantic-fixtures.json` / `host-lsp.log`：官方同版本Windows进程+候选Android头文件的15项真实LSP结果。含解析等待、未保存/变动/关闭头文件的行为；所有输入均为合成代码，原文件保持。失败日志保留，包括文本回退与关闭头文件后的旧预编译结果。
- `collect-licenses.py` / `license-inspection.json`：12份通知原文、实际包来源、匹配GNU libiconv1.19完整源码的摘要。对应源码在工具缓存；生产许可入口/源码随包交付/重链接说明和完整NDK r30 notices仍待完成。
- `verify-probe.py` / `results.json`：重新核查实际源/修改后二进制摘要、15个真实用例、旧APK与生产源码未变；明确Android执行/主App接入/整体完成均为false。文本哈希规范化LF，二进制哈希不改字节。

已有缓存时，在仓库根执行：

```powershell
& .tools/python313-full/tools/python.exe verification/cpp-semantic-probe-2026-10-27/inspect-index.py
& .tools/python313-full/tools/python.exe verification/cpp-semantic-probe-2026-10-27/probe-lsp.py
& .tools/python313-full/tools/python.exe verification/cpp-semantic-probe-2026-10-27/verify-probe.py
```

工具、原始包、头文件、主机可执行和对应源码均位于被忽略的.tools/cpp-semantic-probe；未把数百MB二进制或外部源码展开副本提交为项目源码。没有推送、发布或创建自动任务。完整回归/Lint/APK在主App实际接入批次结束后集中执行，不能用本目录代替。
