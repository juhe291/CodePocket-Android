"""Publish only verified batch facts; leave device acceptance and later scope open."""
import hashlib
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
result=json.loads((PHASE/'results.json').read_text())
for name,digest in json.loads((PHASE/'final-source-hashes.json').read_text()).items():
    assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name

report='''# P2-D 本地 C++ 接入：代码、主机与 APK 验证完成

2026-10-27：沿用 `9d89418` 前置验证和 `a46a88c` 底层检查点，完成同一主 App 的本地构建、执行服务、终端和故障定位接入。Android 实际编译运行仍待设备验收，整个 P2/P3 计划尚未完成。零碎 UI 润色暂停。

## 本批四组修改

1. 固定 Clang/LLD 21.1.8-3、CRT/compiler-rt/libunwind 与本地 LLVM symbolizer，复用原 LLVM/libc++ 私有闭包。安装包运行器调用生成共享程序的真实 main，正常返回执行析构；不会 execve 私有可写目录的生成程序。完整许可、固定输入与离线准备脚本随源代码保留。
2. 每次运行生成独立、新鲜的项目副本，包含当前未保存正文、有效恢复草稿及实时项目源文件。支持当前编译单元/全部项目编译单元；CPU、地址空间、输出、文件及会话预算有界。不运行项目构建脚本，旧云端模式、上传确认及失败处理保留，本地失败不会静默上传。
3. 非导出 `:cpp_run` 服务和 JNI 按请求/子进程代次管理所有权。取消、Binder 退出和迟到消息只释放自有进程。内层 PTY 接收交互和预填输入；外层独立非阻塞描述符不改原 Shell 的状态。停止先等待进程、线程、描述符释放，再归还终端。运行/F5/停止沿用原 Main/Sora 入口及保存保护，设置草稿仍仅在应用后生效。
4. 安装包运行器在 dlopen 前记录有界故障地址，经独立管道交给父进程；父进程观察自有子进程已停止，读取 maps 后恢复。按实际 ELF PT_LOAD 文件偏移换算，固定 symbolizer 本地查 DWARF，验证项目路径/行号/源码摘要后才允许跨文件跳转。未知地址显示未能定位；SIGKILL 不冒充 CPU 超时。只将 SIGXCPU 判为 CPU 限制。

设置版本升至 10，旧自动补全迁移阈值仍保留版本 9 的语义。新增默认仍为 CLOUD/CURRENT，不改变既有用户云端行为。

## 实际验证与交付

- 定向逐步取得 62、67、69 项通过；最终兼容性定向 **87 项全部通过**，包含真实 Main/Sora 保存、SAF 恢复、设置草稿、服务取消、故障消息与原设置/补全断言。
- 完整主 App **855 项：854 通过、0 失败、1 项既有跳过**。旧 821 项身份/跳过集合保持，新增 34 项；终端模块 146 项全部通过。
- Lint **0 错误**，主 App 21 / Sora 1 警告。新增 StaticFieldLeak 提示指向 RunSession 持有的 LocalCppRunClient Context；唯一生产创建路径传 Application。按约定登记，未屏蔽规则，不为非阻塞提示另起打磨批次。
- Python 源码 66、实际包内运行 39/语义 22、主机 Shell 4、真实片段 14 项通过。原 Debug 证书、CRC、arm64 ABI、16KiB 对齐、权限与私有服务声明检查通过。
- 实际 APK 核对固定工具/闭包 12 文件、当前执行 JNI/运行器 2 映像、构建支持 4 文件、依赖/头文件/许可与对应源码。前批九个 C++ 文件、原十个运行原生库及二十个 Chaquopy 资产逐字节保持，90 个历史 APK 摘要保持。
- 最终包：[CodePocket-local-cpp-2026-10-27-debug.apk](../dist/CodePocket-local-cpp-2026-10-27-debug.apk)，**115,421,743 字节（110.07 MiB）**；相对 P2-C 增加 1,890,069 字节。SHA256：`07b06aa03f393d35e8bb88ebd6f7a0c51f5ad2749aa6e98090f2c54dedd2bd26`。沿用证书 `d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`。
- [7 张必要原生预览](local-cpp-preview/README.md)已实际查看：手机/平板、深浅主题、大字体、两组选项和 320dp 上传说明。仅为主机原生 Views 绘制。

证据：`verification/cpp-local-integration-2026-10-27/results.json`、`final-junit/`、`final-terminal-junit/`、`test-case-manifest.json`、`full-build.log`、`cpp-apk-inspection.json`、`final-source-hashes.json`。最终构建完成后按摘要核对源码，再写入本记录。

## 保留的历史与失败

`a46a88c` 底层检查点取得 22 项定向、生产 cpp_runner.c Windows 分支 5 项真实执行及 Android 交叉产物证据；本次没有把这五项复述为当前 Android 故障执行结果。实际 Clang21 多文件交叉编译及真实主机 symbolizer 将 AArch64 brk 地址定位到 lib/helper.cpp 第 2 行；同样不是 Android 上产生故障。

首轮故障验证发现可空比较、实际 LLVM JSON 数组格式和结果命名参数问题，失败日志与 JUnit 保留后修复。首轮全量 855 项有 3 项失败：两项旧断言仍期待设置版本 9，一项要求明确 Wandbox 上传说明。保留测试身份/原断言含义，加入新字段检查并恢复完整说明后，87 项定向与第二次完整构建通过。第一次产生的中间包未交付，本批两次构建调用最终仅保留一份 dist 包。主机 Bash 首次受权限限制，保留日志后在获准本机环境完成余下检查；汇总脚本另修正终端结果目录及 PowerShell UTF-16/CRLF 读取，没有放宽验证条件。

固定 symbolizer 包 SHA256 `2d17e4dde485cae71e6872d19ce1d44555789208708b4e0fd15ebf6818a146d3`，提取 81,328 字节映像；同一冻结签名索引与输入/补丁后摘要记录在 symbolizer-input.json。已授权的现有 Actions 准备步骤早在 `c429a51` 补齐，本批未改工作流或运行远端 CI。

## 后续仍须完成

Android 工具实际启动、dlopen/SELinux、JNI/PTY 内核行为、故障 handler/子进程停止恢复、API24/卓易通、IME/键鼠与性能尚未验收，按约定后置，不要求现在连接设备。主机协议测试与交叉编译不能替代这些证据。

用户明确提醒的 **头文件 Tab 补全优先作为下一批**：系统和项目头文件、目录继续、中间替换、闭合符、Main/Sora Tab/撤销/过期保护与无候选缩进。真实 clangd 五场景已取得候选，尚未完成 App 接入。原多文件后续、用例管理/批量测试、格式化及全部 P3 保留，整体目标继续。
'''
(ROOT/'docs/P2_LOCAL_CPP_INTEGRATION_2026-10-27.md').write_text(report,encoding='utf-8')

file=ROOT/'docs/RESUME.md';text=file.read_text(encoding='utf-8')
start=text.index('## 正在推进：P2-D');end=text.index('## 已完成前置验证：',start)
section='''## 最近完成：P2-D 本地 C++ 代码/主机/APK 验证（2026-10-27）

- [本批报告](P2_LOCAL_CPP_INTEGRATION_2026-10-27.md)：同一 F5/运行/停止接入本地 C++ 当前文件/项目构建、新鲜草稿副本、私有执行服务、独立 PTY 及固定 LLVM 故障定位；保存、云端同意和设置草稿保持。Android 实际执行未验收。
- 完整 855 项：854 通过/0 失败/1 旧跳过，旧 821 身份及跳过集合保持；定向 87、终端 146 全通过。Lint 0 错误，主 21/Sora 1 警告，新增 Context 持有提示已登记，未屏蔽规则。原 Python 源码 66/实际 APK 运行 39/语义 22、Shell 4/片段 14 和签名/对齐均通过。
- 最终 `dist/CodePocket-local-cpp-2026-10-27-debug.apk`，115,421,743 字节，SHA256 `07b06aa03f393d35e8bb88ebd6f7a0c51f5ad2749aa6e98090f2c54dedd2bd26`；原签名，90 旧 APK、九个前批 C++ 文件、十个原运行库/二十个 Chaquopy 资产保持。本批两次集中构建，首次 3 失败已保留并修复，只交付一份最终包。
- [7 张必要原生图](local-cpp-preview/README.md)已查看；真实交叉构建/主机 DWARF 定位与 Main/Sora 协议测试不证明 Android 内核/PTY/故障运行。实机后置，不要求设备。
- **现在优先继续用户提醒的头文件 Tab 补全**。`verification/cpp-header-completion-2026-10-27/host-header-fixtures.json`已有真实 clangd 五场景候选；尚未完成 App 接入。不要重复创建项目或丢失旧失败，继续系统/项目头文件、目录、中间替换、闭合符、Tab/撤销/过期保护与无候选缩进。多文件后续、批量用例、格式化及全部 P3 保留，整体目标未完成。

'''
file.write_text(text[:start]+section+text[end:],encoding='utf-8')

file=ROOT/'docs/P2_IMPLEMENTATION_PLAN_2026-10-26.md';text=file.read_text(encoding='utf-8')
start=text.index('随后继续：底层提交`a46a88c`以后');end=text.index('用户随后明确提醒',start)
text=text[:start]+'''随后完成本批代码/主机/APK验证：855 项中 854 通过/1 旧跳过，定向 87/终端 146 全通过，Lint 零错误（主 21/Sora 1 警告），最终本地 C++ APK 已核对原签名及包内容。详见实际接入报告；Android 内核/PTY/故障执行仍后置，原多文件后续、批量用例、格式化和全部 P3 保持，整体目标未完成。

'''+text[end:]
file.write_text(text,encoding='utf-8')

file=ROOT/'docs/DEVELOPMENT_BATCH_POLICY.md';text=file.read_text(encoding='utf-8')
text=text.replace('当前最新已验证安装包继续沿用[C++语义接入批次APK](../dist/CodePocket-cpp-semantics-2026-10-27-debug.apk)；完成下一开发批次后再更新。','当前最新已验证安装包为[本地 C++ 接入批次 APK](../dist/CodePocket-local-cpp-2026-10-27-debug.apk)；代码/主机/包检查见本批报告，Android 执行验收仍后置。')
file.write_text(text,encoding='utf-8')

file=ROOT/'README.md';text=file.read_text(encoding='utf-8')
start=text.index('> 2026-10-27：');end=text.index('**2026-10-27：同一主 App',start)
text=text[:start]+'''> 2026-10-27：[本地 C++ 接入](docs/P2_LOCAL_CPP_INTEGRATION_2026-10-27.md)已完成代码/主机/APK验证，Android执行验收仍后置。下一批优先补齐头文件 Tab 补全，原后续 P2/P3 范围继续。

**2026-10-27：本地 C++ 构建/终端接入。**

- [最新 Debug APK](dist/CodePocket-local-cpp-2026-10-27-debug.apk)（110.07 MiB，原签名，arm64-v8a）。本地/云端和当前文件/项目范围沿用同一运行入口；本地失败不会静默上传。
- [结果与限制](docs/P2_LOCAL_CPP_INTEGRATION_2026-10-27.md) · [7 张原生图](docs/local-cpp-preview/README.md)：855 项中 854 通过、1 旧跳过，定向 87/终端 146 全通过，Lint 零错误。真实包内工具/支持文件、原运行内容及原签名已核对。
- 尚未在 Android 验证本地工具启动、dlopen、JNI/PTY 和故障执行；主机/交叉编译证据与设备验收分别记录。

**历史：2026-10-27 C++ 语义接入。**

'''+text[end:]
file.write_text(text,encoding='utf-8')

preview=ROOT/'docs/local-cpp-preview';preview.mkdir(exist_ok=True)
images=sorted((PHASE/'previews').glob('*.png'))
lines=['# 本地 C++ 必要设置预览','',
    '7 张主机 Robolectric 原生 Views 实际绘制图已查看。覆盖手机浅色/大字体、平板深色、后端/构建范围选项与 320dp 明确上传说明；不代表 Android 实机或运行验收。','']
for image in images:
    lines += [f'## {image.stem}','',f'![{image.stem}](../../verification/cpp-local-integration-2026-10-27/previews/{image.name})','']
(preview/'README.md').write_text('\n'.join(lines),encoding='utf-8')
print('Verified source hashes and wrote batch documentation; overall goal remains open.')
