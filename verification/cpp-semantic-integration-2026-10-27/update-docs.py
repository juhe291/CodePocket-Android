"""Update handoff pages only from the final verified artifact record."""
import json
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[2]
PHASE = Path(__file__).resolve().parent
result = json.loads((PHASE / 'results.json').read_text())
assert result['junit'] == dict(tests=821, failures=0, errors=0, skipped=1)
assert not result['android_execution'] and result['corresponding_source_bundled']
size = result['bytes'] / 1024 / 1024
delta = result['apk_size_delta_bytes'] / 1024 / 1024
summary = f'''## 当前：P2-C C++语义已完成代码/主机/APK验证（2026-10-27）

- [接入报告](P2_CPP_SEMANTICS_2026-10-27.md)：固定clangd与实际Android头文件、独立私有分析服务和只关闭自身子进程的JNI句柄、真实LSP编辑/重载参数、新鲜项目/恢复草稿快照及Main/Sora接入；普通输入URI稳定，预处理前缀变化重开文档，其他文件代次变化重启分析进程。保存/恢复、Python、Wandbox和PTY保持。
- 定向91通过；最终主821项：820通过/0失败/1旧跳过，旧794身份及跳过集合保持，新增27项。真实主机clangd15场景通过；终端{result['terminal']['tests']}项通过。Lint0错误，主20/Sora1警告；新增2项旧提示字符串UnusedResources登记，不为此继续UI打磨。
- `{result['apk']}`，{result['bytes']:,}字节（{size:.2f}MiB），SHA256 `{result['sha256']}`。相对P2-B增加{delta:.2f}MiB。实际APK九文件C++闭包约209.6MiB、3,486头文件约33.68MiB、完整libiconv对应源码和17份通知/说明均已检查；旧10原生库/20个Chaquopy资产及89旧APK摘要保持。
- 本批Python源码66、实际APK原运行39/语义22、主机Shell4/片段14、原签名/16KiB通过。[4张原生图](cpp-semantic-preview/README.md)已查看。第一次全流程在NDK主机识别失败且未打包；随后实际APK检查分别发现gzip资产改名和旧库被重新剥离，修复后集中重检。两份中间包未交付，只保存一份最终dist包，失败结果和断言保留。
- 用户明确授权后只补齐既有Actions的一条依赖准备步骤，未新增任务/触发器、未推送或运行远端CI。Android实际安装/JNI/clangd启动、性能、API24/卓易通与输入法/键鼠仍待设备验收；无需现在连接设备。不把主机LSP或静态ELF检查称作设备通过。
- 下一工作包P2-D：本地C++构建/执行可行性与体积许可核验，然后多文件运行、测试用例/批量运行、格式化；原P3保留。UI零碎润色继续暂停，整体目标未完成。

'''
for relative in ('docs/RESUME.md', 'docs/DEVELOPMENT_PROGRESS.md'):
    file = ROOT / relative
    text = file.read_text(encoding='utf-8')
    text = text.replace('## 正在推进：P2-C C++语义前置验证（2026-10-27）', '## 历史：P2-C C++语义前置验证（2026-10-27）', 1)
    text = text.replace('**当前生产代码仍为c5672c7/P2-B，C++语义未接入主App，Android执行未验证。**', '**此前置阶段的生产代码为c5672c7/P2-B，C++语义未接入，Android执行未验证。**', 1)
    text = text.replace('## 当前：P2-B Python项目上下文已完成', '## 历史：P2-B Python项目上下文已完成', 1)
    offset = text.index('## 历史：P2-C C++语义前置验证')
    text = text[:offset] + summary + text[offset:]
    file.write_text(text, encoding='utf-8')

file = ROOT / 'docs/P2_CPP_SEMANTICS_2026-10-27.md'
text = file.read_text().replace('主 App C++ 语义接入（进行中）', '主 App C++ 语义接入（代码/主机/APK已验证，设备后置）')
text = text.replace('首轮定向 91 项中 88 通过、3 失败。正在检查手机大字体参数窗、Unicode 主编辑器回放及一个前缀夹具；断言保留、没有新增跳过。最终结果尚未形成。',
    '首轮定向 91 项中 88 通过、3 失败：长行夹具只设置光标未显现真实插入点，两个窗口正确拒绝屏幕外锚点；前缀夹具有九个真实后端候选却只改了第一项。修正操作/夹具后原断言保留，91项全部通过，没有新增跳过。')
text = text.replace('完整回归、Lint、实际 APK、签名/对齐/原运行内容、手机/平板图像仍待本批收尾。**当前最新已验证 APK 仍是 P2-B**，不能将本批源码状态称为已交付。',
    '完整回归、Lint、实际 APK 与签名/对齐/原运行内容检查已完成，四张原生图已查看。具体数字及包摘要见下方最终结果。Android设备执行仍未验收。')
text += f'''
## 最终结果

- 全量821项：820通过、0失败、1既有Windows符号链接跳过；原794项身份及跳过集合保持。新增27项，定向91通过；终端模块{result['terminal']['tests']}项通过。
- Lint零错误，App20/Sora1警告。新增2项UnusedResources来自被新语义加载/空结果文案替代的旧提示字符串；按非阻塞先登记约定保留，不屏蔽规则。
- 最终 APK：[下载](../{result['apk']})，{result['bytes']:,}字节（{size:.2f}MiB），相对P2-B增加{result['apk_size_delta_bytes']:,}字节（{delta:.2f}MiB）。SHA256 `{result['sha256']}`，原Debug证书保持。
- 实际APK的9份C++ELF逐一匹配锁文件摘要，闭包/ABI/API声明/16KiB段对齐、JNI桥、头文件ZIP、完整对应源码原始归档及17份许可/说明均已核对；`libc++_shared`原本无RUNPATH，其他候选现有路径为ORIGIN，不能误要求全部都有RUNPATH。受控进程仅以安装原生目录设置库搜索路径。
- Python源码66、实际APK原运行39/语义22、主机Shell4和生成片段14通过。旧10份原生库、20份Chaquopy资源及89个历史APK摘要保持。原运行/保存代码没有被重写。
- [4张实际原生图](cpp-semantic-preview/README.md)已查看。手机双倍字体标题/辅助说明会省略；实际参数横向滚动并定位活动参数，选择/关闭及编辑缓冲保持。不是实机截图。
- 原始结果位于 `verification/cpp-semantic-integration-2026-10-27/results.json`；失败、定向/全量JUnit、实际APK检查和来源材料均保留。最终构建及缓存复用状态以本批full-build.log为准。

本批没有验证Android上的实际JNI/clangd执行，也没有推送、发布或触发远端CI。该边界不阻止按约定继续后置设备验收，但不能写成“设备可用已确认”。后续P2-D/P3与整体目标仍未完成。
'''
file.write_text(text, encoding='utf-8')

file = ROOT / 'README.md'; text = file.read_text()
start = text.index('> 2026-10-27开发中：'); end = text.index('**2026-10-26：Python跨文件', start)
text = text[:start] + f'''> 当前按[P2计划](docs/P2_IMPLEMENTATION_PLAN_2026-10-26.md)推进核心功能，零碎UI润色暂停。Python项目上下文与C++语义接入已完成代码/主机/APK验证；Android执行验收仍后置，整个计划尚未完成。

**2026-10-27：同一主 App 接入 C++ clangd 成员补全与真实参数。**

- [最新Debug APK]({result['apk']})（{size:.2f}MiB，原签名，arm64-v8a）。新增工具链体积约{delta:.2f}MiB；安装后原生库/头文件还占额外空间。
- [结果与限制](docs/P2_CPP_SEMANTICS_2026-10-27.md) · [4张原生图](docs/cpp-semantic-preview/README.md)：821项中820通过、1旧跳过，定向91通过，Lint零错误；实际包内C++依赖、头文件、源码、通知及原运行内容检查通过。
- 未将Windows clangd/主机Main-Sora/JNI静态检查称作Android设备已运行。下一步本地C++可行性、多文件运行、批量用例和格式化，原P3保留。

**历史：2026-10-26 Python跨文件''' + text[end + len('**2026-10-26：Python跨文件'):]
file.write_text(text, encoding='utf-8')

file = ROOT / 'docs/P2_IMPLEMENTATION_PLAN_2026-10-26.md'; text = file.read_text()
text = text.replace('源码已开始修改，仍有定向失败待修复；通过后整批统一回归、Lint、构建一个 Debug APK。当前最新已验证包继续沿用 P2-B，不预先记为完成。',
    '已完成四组代码/主机/APK验证：821项中820通过、1旧跳过，定向91通过，Lint零错误；实际包内依赖/许可/对应源码及原运行内容已核验。设备执行仍后置，下一工作包P2-D与后续原需求继续，不将本批当作全部计划完成。')
file.write_text(text, encoding='utf-8')

file = ROOT / 'docs/UI_SCOPE_AUDIT_2026-10-24.md'; text = file.read_text()
text = text.replace('当前最终验证及APK见[Python项目上下文批次](P2_PYTHON_PROJECT_CONTEXT_2026-10-26.md)。', '当前最终验证及APK见[C++语义接入批次](P2_CPP_SEMANTICS_2026-10-27.md)。')
file.write_text(text, encoding='utf-8')
file = ROOT / 'docs/DEVELOPMENT_BATCH_POLICY.md'; text = file.read_text().replace('[Python项目上下文批次APK](../dist/CodePocket-python-project-context-2026-10-26-debug.apk)', f'[C++语义接入批次APK](../{result["apk"]})')
file.write_text(text, encoding='utf-8')

file = ROOT / 'docs/THIRD_PARTY_INVENTORY.md'; text = file.read_text()
text = text.replace('## 2026-10-27 C++候选核验（尚未接入）', '## 历史：2026-10-27 C++候选核验（前置阶段）', 1)
offset = text.index('## 历史：2026-10-27')
text = text[:offset] + '''## 2026-10-27 C++语义实际接入增补

[本批报告](P2_CPP_SEMANTICS_2026-10-27.md)及实际APK检查确认固定九文件原生闭包、Android头文件、17份许可/说明、四份NDK r30通知，以及原始libiconv1.19完整对应源码归档和构建/修补材料已随包交付。设置→帮助与许可→C++语义分析许可与源码可逐项查看。独立共享库名称/NEEDED/SONAME/RUNPATH修改有锁文件与重链接说明，未统一归入项目许可、未静态链接LGPL库。

NDK通知取得方式为官方HTTPS ZIP范围与成员CRC核验，并非完整NDK归档SHA256校验。项目原有CPython/Sora等未决发布通知事项继续保留；本批不宣称整个项目发布法律条件已全部满足，也不替所有者选择项目许可证。

''' + text[offset:]
file.write_text(text, encoding='utf-8')

preview = ROOT / 'docs/cpp-semantic-preview'; preview.mkdir(exist_ok=True)
for file in (PHASE / 'previews').glob('*.png'): shutil.copy2(file, preview / file.name)
(preview / 'README.md').write_text('''# C++语义接入原生检查图

这四张图来自实际MainActivity、生产Sora与原生浮层的Robolectric Native Graphics；Android IPC回放同版本真实clangd记录，实际生产LSP连接另经真实主机clangd验证。均已目视查看，不是设备/JNI实际执行截图。

- [平板成员与中间词替换](tablet-real-member.png)：C++/clangd来源、真实函数与接受/保存/撤销断言。
- [手机浅色双倍字体参数](phone-light-large-signature.png)：两个重载、活动第二参数、横向滚动/关闭；标题和辅助说明省略保留完整无障碍说明。
- [平板浅色标准库](tablet-light-vector.png)：实际Android libc++ vector两个重载，emoji/CRLF与光标进入参数。
- [平板许可阅读器](tablet-light-cpp-license.png)：完整可选择源码/重链接说明，关闭返回许可列表和设置草稿。

未为本批重做界面装饰。实际输入法、实体键鼠、缩窗行为与设备性能继续按原约定后置。
''', encoding='utf-8')
print('Verified result pages, handoff and four reviewed native images updated; no APK rebuilt')
