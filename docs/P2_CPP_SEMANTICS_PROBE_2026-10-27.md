# P2-C C++语义前置验证

2026-10-27，接续 `c5672c7` 的 P2-B。复用同一主项目，本阶段没有改生产代码、创建实验App、安装设备或构建新APK。最新已验证APK仍为 P2-B，原测试794项/1既有跳过与Lint结果属于上一批，不写成本轮重测。

## 当前结论

已取得真实候选、完整运行依赖的静态证据和同版本主机LSP结果，可以继续做主App接入。尚不能把候选称作“Android运行已通过”，也没有完成P2-C或整个计划。

- 固定候选来自Termux官方仓库的 `clang` / `libllvm` **21.1.8-3**。索引与收到的InRelease的SHA256一致，GPG签名已用官方公钥验证，指纹 `CC72CF8BA7DBFA0182877D045A897D96E57CF20C`。每个已下载包再按签名索引检查大小及SHA256；构建脚本/索引/签名/实际ELF报告保存在 `verification/cpp-semantic-probe-2026-10-27/`。
- 真实 `clangd` 需要 `libclang-cpp`、`libLLVM`、`libc++_shared`、`libffi`、`zlib`、`zstd`、`libxml2`、`libiconv`，共9个文件（含可执行）。实际依赖闭包已解析；未把包管理器声明的所有工具一并塞入，例如未采用不在ELF闭包中的ncurses、liblzma及命令行程序。
- 9个实际ELF均为64位AArch64，Android标记不高于API24，加载段满足16KiB对齐，无文本重定位。原始clangd解释器是 `/system/bin/linker64`。这些是静态事实，不能替代Android动态加载/执行。
- 原始对象含Termux固定RUNPATH，并与主App现有 `libc++_shared.so` 同名。`stage-native.py` 只在工具缓存复制，给整个依赖闭包分配独立、等长、以 `.so` 结尾的文件名，调整动态依赖/SONAME及 `$ORIGIN`；保持文件大小、加载段、API标记与原始ABI。再解析实际修改后的ELF，验证依赖闭包和无旧JNI文件名冲突。**原APK和10个旧JNI库未修改。**
- 该候选原生闭包为219,813,944字节（约209.6MiB）；工具缓存中的压缩探测ZIP为70,112,897字节（约66.9MiB）。此ZIP不是APK，不能把它的大小称为最终APK增量。实际APK体积、提取占用和设备内存仍需在接入后检查。

包的版本和构建来源见[Termux LLVM构建脚本](https://github.com/termux/termux-packages/blob/master/packages/libllvm/build.sh)、[clang子包清单](https://github.com/termux/termux-packages/blob/master/packages/libllvm/clang.subpackage.sh)；本轮精确摘要以保存的签名索引与实际文件报告为准，不随远端master自动升级。

## 真实主机LSP验证

使用[官方clangd 21.1.8 Windows便携包](https://github.com/clangd/clangd/releases/tag/21.1.8)，发布资产SHA256 `aeb5a68414c4d10b0867a479c9f21593043cc6d122e39de61b3801c89ebd57fc`，没有全局安装。其实际版本输出是LLVM提交 `2078da43e25a4623cab2d0d60decddf709aaea28`；使用从候选包提取的Android C/C++头文件、Clang内建头及 `--target=aarch64-linux-android24` / C++17。

`probe-lsp.py` 实际启动该Windows进程，经UTF-8 Content-Length标准输入输出通信；初始化明确UTF-16位置。15项最终通过，原始响应保存为 `host-semantic-fixtures.json`，不是手写成功结果：

| 场景 | 核验内容 |
| --- | --- |
| `. / -> / ::` | 实际类成员、指针成员、命名空间函数 |
| 标准库与Unicode | Android libc++ `vector::push_back`，中文/emoji/CRLF的UTF-16坐标 |
| 中间词、前缀与作用域 | 实际TextEdit、变量前缀；不能出现其他函数局部变量或未知类型成员 |
| 真实签名/重载 | 活动第二参数、两个可行双参数形式、排除已经不可行的单参数重载 |
| 未保存头文件 | 内存头文件优于磁盘，更新后显示新成员，原文件未被分析写入 |
| 关闭头文件后的上下文 | 更换上下文代次并重启进程后恢复当前磁盘定义，旧成员不能残留 |

测试使用合成代码。原文件保持、未知成员为空、禁用项目配置不传递测试插件路径均有断言。该验证没有执行用户C++程序，更不是Android/卓易通验收。

### 本轮发现的接入约束

1. 默认 `--completion-parse=auto` 会在解析器未就绪时返回文本补全，首次标准库用例实际失败。改为 `--completion-parse=always` 后标准库结果通过；生产中必须等待解析或明确失败回退，不能把内部文本回退标成语义。仅等待CLI响应不能证明使用过解析器。
2. 未保存头文件需要 `--use-dirty-headers`。该项保留失败日志后修正，断言未删。
3. 只做 `didClose` 可能保留旧头文件预编译内容；本轮实际观察到应恢复 `old_disk` 时仍返回 `save`。按其他源文件/草稿快照代次重启有限的分析进程后通过。接入不能只看当前正文版本；主文件普通输入可用didChange，项目上下文变化则更换代次/进程。
4. clangd对 `ruxx` 中间位置的TextEdit只替换光标前的 `ru`。客户端须在严格标识符边界内处理光标后的尾部，避免产生 `run()xx`。实际返回还含 `push_back($0)`，不能把 `$0` 字面插入正文。
5. 重载夹具原先在输入第二参数后仍期待单参数形式，属于夹具错误；改为两个可行双参数形式并新增原单参数形式被排除的独立断言，原重载数量断言保留。其他所有失败记录保存。

上述旗标说明取自实际固定版本 `--help-hidden`；[系统头文件说明](https://clangd.llvm.org/guides/system-headers)说明目标与include参数的重要性。计划禁用项目配置和query-driver，只生成可信编译参数，不为分析调用项目工具或上传源码。

## 许可与对应源码

已从真实包、匹配源码与上游固定版本取得12份通知文本及构建来源。不能把整个候选统一称为MIT或NCSA：LLVM代码/例外、NCSA、MIT、zlib、Zstd库的BSD选项、libiconv的LGPL都要分别保留。命令行工具未采用，不因工具包使用GPL就把库的双许可选择混为一谈；Zstd库的具体BSD原文来自[固定v1.5.7许可](https://github.com/facebook/zstd/blob/v1.5.7/LICENSE)。

libiconv 1.19完整对应源码5,921,103字节已下载并核对源构建脚本的SHA256 `88dd96a8c0464eca144fc791ae60cd31cd8ee78321e67397e25fc095c4a19aa6`；COPYING.LIB、COPYING和README保留，目录清单显示该Termux包没有额外源码补丁。后续APK接入仍必须随附对应源码/重链接说明、标明二进制路径改动，并在许可阅读器可达。**完整NDK r30 notices核验、生产许可入口及对应源码交付尚未完成；本前置报告不声明已具备发布条件。** 原有项目第三方清单中的未决项继续保留。

## 下一批主App实际修改范围

按原计划继续四组相关工作，完成后集中回归/Lint/构建一个APK：

1. 原生依赖、头文件与许可/对应源码材料；同一主APK内的不可变可执行、独立受控分析进程及只终止自己子进程的取消/看门狗。先补足上述许可交付，再打包。保持min24、target34和原Debug密钥。
2. 有界LSP通道与初始化/文档版本/UTF-16/TextEdit/实际签名/片段标记处理；正确处理迟到、崩溃、超时及明确词法回退。对照本轮真实响应做协议测试。
3. 内部与SAF的有界C++源码/头文件新鲜快照和未保存正文；项目上下文代次变化重启，普通主文件输入保留稳定URI以免每次重建全项目。原文件与保存冲突基线不被分析修改。
4. 接回同一Main/Sora候选/参数事务、按键与触屏、撤销/草稿/切文件保护；保留原Python、Wandbox和PTY。定向通过后统一完整检查并验实际APK，不在小修改时逐次打包。

Android 10起针对目标API29+限制从可写App目录直接execve，见[官方行为说明](https://developer.android.com/about/versions/10/behavior-changes-10)。拟验证从APK的原生库位置执行，不把从files/cache直接chmod执行当作适配成功；安装提取与进程行为必须继续核验。物理设备仍按用户要求后置，不能拿本次Windows结果冒充。

P2-D的本地C++可行性、多文件运行、批量测试、格式化，以及P3原需求没有删除。当前只推进P2-C，整体目标保持未完成。
