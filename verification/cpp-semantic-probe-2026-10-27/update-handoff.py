from pathlib import Path
import json

phase = Path(__file__).resolve().parent
root = phase.parents[1]
result = json.loads((phase / 'results.json').read_text())
assert result['real_lsp_cases'] == 15 and result['original_apk_unchanged'] and not result['production_integrated']
block = '''## 正在推进：P2-C C++语义前置验证（2026-10-27）

- [前置报告](P2_CPP_SEMANTICS_PROBE_2026-10-27.md)：固定Termux clang/libllvm 21.1.8-3；官方GPG/索引/包摘要及真实AArch64/API24/16KiB/9文件依赖闭包通过静态核验。工具缓存验证独立库名/ORIGIN，未改旧JNI。原生约209.6MiB、压缩探测ZIP约66.9MiB；该ZIP不是APK。
- 官方同版本Windows clangd配实际Android头文件，经真实LSP的15项通过，原始响应/失败日志保留。发现并验证必须等待解析器、启用未保存头文件、项目上下文代次变化时重启进程；只didClose可能保留旧头文件。实际TextEdit中间尾部与$0还需客户端正确处理。
- 已收集12份许可文本和匹配libiconv1.19完整对应源码；NDK r30完整notices、生产许可入口、对应源码随包交付及重链接说明仍待完成。未将候选统一标为MIT，也未声明发布条件满足。
- **当前生产代码仍为c5672c7/P2-B，C++语义未接入主App，Android执行未验证。** 未新建App、未构建新APK或重跑全量/Lint，旧APK摘要已核对保持。所有二进制/头文件缓存位于.tools/cpp-semantic-probe，不是第二份项目。
- 下一步继续本报告中的四组主App实际接入：原生/许可/受控进程、LSP协议与编辑范围、有界新鲜项目上下文、现有Main/Sora事务；整批最后集中回归/Lint/构建。原P2-D/P3与设备后置约定保持，整体目标继续。

'''
for relative in ('docs/RESUME.md', 'docs/DEVELOPMENT_PROGRESS.md'):
    file = root / relative
    text = file.read_text(encoding='utf-8')
    assert '## 正在推进：P2-C' not in text
    assert '## 当前：P2-B' in text
    file.write_text(text.replace('## 当前：P2-B', block + '## 当前：P2-B', 1), encoding='utf-8')
plan = root / 'docs/P2_IMPLEMENTATION_PLAN_2026-10-26.md'
text = plan.read_text(encoding='utf-8')
text += '\n2026-10-27：P2-C[前置验证报告](P2_CPP_SEMANTICS_PROBE_2026-10-27.md)。已核验真实arm64依赖闭包及15项同版本主机LSP；源码/许可材料与上下文重启策略已形成证据。C++语义还未进入主App，Android执行、实际APK及许可交付仍未完成；继续四组接入，P2-D/P3原范围保持。\n'
plan.write_text(text, encoding='utf-8')
inventory = root / 'docs/THIRD_PARTY_INVENTORY.md'
text = inventory.read_text(encoding='utf-8'); head, rest = text.split('\n\n', 1)
text = head + '\n\n## 2026-10-27 C++候选核验（尚未接入）\n\n[前置报告](P2_CPP_SEMANTICS_PROBE_2026-10-27.md)保存真实候选GPG/摘要/ELF依赖与12份通知。LLVM/Clang、libc++、libffi、libxml2、zlib、Zstd库与libiconv分别核对，libiconv1.19完整对应源码已取得。原生只是.tools缓存中的试验闭包，当前生产APK仍为P2-B；NDK r30完整notices、重链接说明、生产许可入口和对应源码交付未完成，不据此宣称发布许可清单已闭合。\n\n' + rest
inventory.write_text(text, encoding='utf-8')
readme = root / 'README.md'
text = readme.read_text(encoding='utf-8'); head, rest = text.split('\n\n', 1)
text = head + '\n\n> 2026-10-27开发中：[C++语义前置验证](docs/P2_CPP_SEMANTICS_PROBE_2026-10-27.md)已取得真实arm64静态证据与15项同版本主机LSP结果；仍需接入主App、补齐许可交付并验证实际APK。当前安装包继续使用下方P2-B，不把主机探测称作Android运行通过。\n\n' + rest
readme.write_text(text, encoding='utf-8')
print('Recorded C++ preflight and explicit remaining production work; latest APK remains P2-B')
