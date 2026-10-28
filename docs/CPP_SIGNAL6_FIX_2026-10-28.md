# 本地 C++ signal 6 排查与修复（进行中）

用户补充“编译错误（130ms）”和退出码“signal 6”。SIGABRT 表示工具异常中止，不能直接当作源码语法错误。没有取得设备完整 stderr/logcat，不声称唯一根因已确认；实机仍按约定后置，不要求连接设备。

源码存在一个 Android 兼容性缺陷：execution_process.c 在 execve 前设置 RLIMIT_AS，编译器为 1,536 MiB、程序为 256 MiB，限制的是虚拟地址空间。Android 常用 Scudo 会先预留较大的虚拟区；[LLVM 上游问题与维护者解释](https://github.com/llvm/llvm-project/issues/118735)记录了小 RLIMIT_AS 导致 arm64 Android 启动时 SIGABRT 的同类现象。[Scudo 设计](https://www.llvm.org/docs/ScudoHardenedAllocator.html)与[AOSP 实现](https://android.googlesource.com/platform/external/scudo/+/b21607a6ead0c1243dc64657e1c5c3a4cfbf03d7/standalone/primary64.h)说明预留区与实际提交页面不同。设备症状与此相符，仍不是该设备日志确认。

修复去除不兼容的虚拟地址上限，保留 CPU、墙钟时间、文件/输出限额及父进程死亡、进程组所有权。原生状态轮询每 100ms 按实际页大小采样自有进程组 RSS，明确超额后只终止该组并用状态 512 标记，不把普通 SIGKILL 当内存超限。系统限制 /proc 枚举时尝试读取已知自有 PID；RSS 采样不是内核硬内存隔离，无法读取指标时不伪造 OOM。

原分类也有问题：工具信号退出与普通源码失败同标编译错误，无 stderr 时只剩执行失败。新增基线复现 1 项失败，退出信号和关闭自身进程的断言保持。工具信号退出现作为服务故障，CPU 超时单列，正常非零编译诊断仍为编译错误；正文保留阶段、signal 和 SIGABRT。

用例批次工作保留：数据/原子保存/比较/历史/队列 9 项通过，实际生产 C 运行器的主机有限输入/EOF/分离输出/原入口/拒绝输入 5 项通过；随后定向编译的三处 Kotlin 空格语法错误已修正，原失败保留。该批尚无 UI/完整控制器，不称已交付批量运行。其尚未开放入口的基础代码保留在本工作树并纳入本轮编译/回归，新增 fixedInput 参数默认关闭；没有新增面向用户的用例入口。

修复后定向 46 项全部通过；完整 911 项中 909 通过/1 失败/1 旧跳过。旧 896 项身份与结果保持，新增 15 项通过，未删断言或新增跳过。唯一失败仍是 CppRuntimePreparationTest 在 Windows 大小写不敏感文件系统不能完整解压固定头文件；整批命令真实返回 1，BUILD FAILED，不能称全绿。Lint 零错误（App 21/Sora 1 警告）；终端 146 为有效 Gradle UP-TO-DATE 结果，editor-assist NO-SOURCE。

本批只集中构建一次，实际 Android arm64/API24 原生编译及 Debug 打包完成。产物为[signal 6 修复候选 APK](../dist/CodePocket-cpp-signal6-fix-2026-10-28-debug.apk)，115,421,743 字节，SHA256 `7e8959a94c8a5cff06a76abdf217c9ce95e4e5dbf1ca08566de38d7b2ca48503`。原签名、CRC、16KiB、Manifest/权限及实际 C++ 闭包检查通过，95 旧 APK 摘要保持。23 原生映像/48 运行资产逐字节保持，只有执行桥和安装运行器更新；新运行器同时包含未开放入口的有限输入基础。

针对实际打包的执行桥反汇编，spawn 里的三次 setrlimit 参数为 0/1/4（CPU/文件/core），不再有 9（地址空间）；该映像与 APK 中的字节一致，DEX 含明确的 memoryExceeded 事件解码。生产 RSS 解析 C 代码在主机运行，验证自有/其他进程组、巨大虚拟区但较小 RSS、4K/16K 页、缺失数据及溢出饱和；这些夹具不是 Android /proc、信号终止或 Scudo 实际启动证据。

本轮实际包内 Python 48 项重新运行通过。Python 源码 75、包内语义 22、Shell 4/片段 14 复用前批：Python 源码、48 运行资产以及相关 Shell/片段实现保持，未冒称本轮重跑；依据见 runtime-validation-reuse.json。主机解析器的升级权限请求曾等待自动审批超时，之后在普通受限环境运行成功，并非不安全操作被拒后声称通过。

[手机错误展示图](../verification/cpp-signal6-fix-2026-10-28/previews/phone-compiler-sigabrt.png)已查看，是实际 Main/Sora 对信号分类结果的协议展示测试，不是在 Windows 触发 Android 编译器崩溃。Android 设备根因及修复效果仍未确认，不要求连接设备。证据见 verification/cpp-signal6-fix-2026-10-28/results.json。后续继续原用例完整控制器/UI、格式化及 P3；不推送、不发布、不新增自动任务。
