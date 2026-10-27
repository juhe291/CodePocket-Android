# C++ 头文件 Tab 补全：代码、主机与 APK 验证完成

2026-10-27：按用户“别忘了头文件的 tab 补全”，从本地 C++ 提交 `64592b9` 继续同一主 App。Android 服务/IME/键鼠实际执行仍待设备验收，整个 P2/P3 目标尚未完成。

## 本批四组修改

1. 固定 clangd 21.1.8 的真实头文件编辑接入系统 `#include <...>` 与项目 `#include "..."`。头文件采用独立的上下文、文件名、范围及匹配闭合符校验；普通成员/变量仍保留原标识符限制。注释、字符串、跨行拼接及其他普通代码不接受文件候选；不能插入新指令或越过头文件修改后文。
2. 路径支持项目目录、系统 sys/、中间后缀和已闭合头文件。Tab 接受文件后仅保留一个匹配闭合符；目录候选留下开放路径，自动请求子项。实际 clangd 将部分目录标为文件 kind 17，按其真实末尾 `/` 识别目录，使用既有文件/目录图标及来源栏。
3. 沿用 Main/Sora 的自动/手动候选、实体键盘/手机工具栏 Tab、选择及撤销事务。文件/修订/选择/大小门禁保持，旧回调不会改写新文档；无候选或禁用 Tab 接受时，在当前光标处执行原缩进。保存后撤销不会覆盖已保存版本。
4. 实际后端、主编辑器操作、必要手机/平板原生绘制与集中回归/Lint/APK 核验。没有增加设置页、运行入口或零碎外观打磨。

## 结果与证据

- `host-header-fixtures.json` 为固定真实主机 clangd 与同一 Android 头文件的十场景：系统/项目、目录及目录继续、嵌套/中间替换、已闭合文件、sys/、中文 emoji/CRLF。保留最初五场景与旧语义十五场景，未替换旧失败证据。
- 修改前新增 6 项：**5 失败/1 通过**；协议及兼容性先取得 29 项通过，最后 **90 项定向全部通过**。生产 CppLspEngine 实际与固定主机 clangd 通信，Main/Sora 则回放这些真实回复并校验生产项目副本；不会将回放称为 Android clangd 执行。
- 全量主 App **869 项：868 通过、0 失败、1 既有跳过**；旧 855 项身份/跳过集合保持，新增 14 项。终端 146 项通过（输入未变时 Gradle 复用有效结果）。editor-assist 独立 test 任务为 NO-SOURCE，其补全契约由 App 测试实际覆盖，不虚报独立测试数量。
- Lint **0 错误**，主 App 21 / Sora 1 警告，沿用前批登记项。
- Python 源码 66、实际 APK 原运行 39/语义 22、主机 Shell 4/真实片段 14，以及原签名/CRC/权限/arm64/16KiB 检查通过。新 APK 的 25 个原生文件及 48 个 C++/Chaquopy 运行资产和前批包逐字节相同；固定工具 12 文件、执行 JNI/运行器 2 映像及支持 4 文件仍通过实际包核验。91 个历史 APK 摘要保持。
- [3 张必要原生预览](cpp-header-preview/README.md)已查看：360dp 浅色大字体项目头文件、平板深色嵌套头文件与目录候选。仅是主机原生 Views 绘制，不是设备截图。
- 本批集中构建调用 1 次，最终包 [CodePocket-header-tab-2026-10-27-debug.apk](../dist/CodePocket-header-tab-2026-10-27-debug.apk)，**115,421,743 字节（110.07 MiB）**，相对前批变化 +0 字节。SHA256：`47ea9f1d1f59d056f05fcc6be0564d2f536cecff41b5a53d4081dc1c73e8a0bd`。沿用 Debug 证书 `d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`。

证据目录 `verification/cpp-header-completion-2026-10-27/`：results.json、final-junit/、final-terminal-junit/、test-case-manifest.json、final-source-hashes.json、verified-source-hashes.json、full-build.log、cpp-apk-inspection.json。源码摘要在完成构建/包检查后重新逐项匹配。

## 保留的失败与边界

第一次基线启动被会话中断，确认无存活进程后重新执行，原日志保留。首轮 90 项交互有 1 项新增测试预期错误：原 Tab 在光标处插入缩进，该测试误期待在行首。按原 EditorActions 及实际键盘行为修正为精确文本和光标断言，没有修改原缩进行为、删掉测试或新增跳过。失败 JUnit/日志保留在 interaction-first-*。之后补查数字分隔符/字符字面量上下文，最终同一 90 项通过。

收尾另发现新旧两个真实后端测试默认工具名写死 Windows `.exe`；仅测试路径改为匹配平台后补跑相关 3 项全部通过，生产源码和实际 APK 摘要保持。final-source-hashes 保留原完整构建输入，verified-source-hashes 仅结合这两个补测文件的新摘要，严格拒绝其他输入变化。Linux 执行和远端 CI 未运行，不据 Windows 补测宣称 Linux 已通过；未修改工作流。

Android 分析服务/JNI 实际启动、IME/键鼠、API24、卓易通与性能仍按约定后置，不要求现在连接设备。前批本地 C++ 内核/PTY/故障执行也未因本批通过而自动验收。下一步继续 P2-D 剩余多文件运行、用例管理/批量测试与格式化；原 P3 保留，整体目标继续。不推送、发布或新增自动任务。
