"""Describe only passed touch-fix checks, keeping reused runtime evidence explicit."""
import hashlib
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];PHASE=Path(__file__).resolve().parent
r=json.loads((PHASE/'results.json').read_text())
for name,digest in json.loads((PHASE/'final-source-hashes.json').read_text()).items():assert hashlib.sha256((ROOT/name).read_bytes()).hexdigest()==digest,name
report=f'''# 设置二级菜单触摸选择修复（2026-10-28）

用户确认问题为“打开设置后，二级菜单的选择点不动”，不是花屏。从 `64b3553` 继续原主 App，优先修复该操作阻塞；原 P2 后续及 P3 保留，整体目标未完成。

## 原因与修复

共享二级菜单行与补全候选行通过 IdeControls.describe 同时设置无障碍描述和原生 tooltip。行本身应由 ListView 统一选择，原生 tooltip 却使被动行消费了触摸，列表没有收到选择回调。旧测试直接执行 performClick/performItemClick，绕过完整触摸分发，因此没有覆盖这一故障。

生产修复只改三处：describe 可分别配置 tooltip；共享二级菜单行保留完整无障碍描述但不安装 tooltip；补全候选行按同一输入职责处理。按钮和独立控件仍保留原提示。没有重做设置布局、动画、二级菜单设计、草稿/应用/取消逻辑，也没有扩展新功能。Python/C++ 运行、保存/恢复、PTY 以及已交付的头文件 Tab 补全保持。

## 实际验证

- 新增 SettingsTouchIntegrationTest 使用真实 Dialog/ListView 的 DOWN/UP 事件，动画开启，不直接调用点击函数。生产修改前 **5 项：4 失败、1 通过**：默认原生列表对照能选择，手机设置主题、平板字号、共享二级菜单和补全候选均无选择回调。失败日志与 JUnit 保留在 baseline-*。
- 修改后 **109 项定向全部通过**，包含上述五项触摸测试，以及设置草稿/应用/取消、现有工作区/恢复草稿/许可/详情、原补全和头文件 Tab 等相关集成。手机主题/平板字号均由完整触摸事件完成；回调收到正确选项，应用前不提交设置，取消不丢失原设置。
- 集中完整主 App **874 项：873 通过、0 失败、1 既有跳过**；旧 869 项身份/跳过集合保持，新增 5 项。终端 146 项有效结果保持。Lint **0 错误**，主 App {r['lint']['app'].get('Warning',0)} / Sora {r['lint']['editor-sora'].get('Warning',0)} 警告，既有登记项保留。
- [两张必要原生图](settings-touch-preview/README.md)已查看：本次触摸测试的手机主题选项，以及同一当前源码原设置测试生成的平板 C++ 选项。视觉布局保持；主机原生绘制不等于设备截图。
- 本批完整构建调用 1 次，实际 APK 原 Debug 签名、ZIP CRC、arm64 元数据、权限/Manifest 和 16KiB 对齐通过。新 DEX 已改变，包含修复的三类代码；**25 个原生映像、48 个 C++/Chaquopy 资产**与前批逐字节相同，**92 个历史 APK**摘要保持。
- 本次仅 UI 输入职责改变，运行源码摘要也保持。因此 Python 源码 66、包内运行 39/语义 22、主机 Shell 4/片段 14 引用前批有效结果，**本批未重新执行这些专项**。依据见 runtime-validation-reuse.json 的源码范围、前批包摘要及每个实际新包资产摘要；没有冒称为本批重新运行。

修复包：[CodePocket-settings-touch-fix-2026-10-28-debug.apk](../{r['apk']})，**{r['bytes']:,} 字节（{r['bytes']/1048576:.2f} MiB）**，相对前批变化 {r['size_delta_bytes']:+,} 字节。SHA256：`{r['sha256']}`。原证书 `d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`。

证据目录 `verification/settings-touch-fix-2026-10-28/`：baseline-junit/、targeted-junit/、final-junit/、final-terminal-junit/、full-build.log、final-source-hashes.json、test-case-manifest.json、results.json、runtime-validation-reuse.json、certificates.txt、alignment.txt。未删旧断言或新增跳过。

## 验证边界与后续

这次确实使用主机原生 Android Views 的完整触摸分发复现并修复点击失效；没有在 Android 设备重新验收。按原约定不要求现在连接设备，也不把主机测试扩大为实机通过。API24、卓易通、IME/键鼠、性能及前批本地 C++ 内核/PTY/故障执行仍保持各自未验收状态。

仅作本地提交，不推送、发布或新增自动任务。阻塞修复交付后继续原计划的多文件运行查漏、测试用例管理/批量运行、格式化，原 P3 保留，整体目标继续。
'''
(ROOT/'docs/FIX_SETTINGS_TOUCH_2026-10-28.md').write_text(report,encoding='utf-8')
file=ROOT/'docs/RESUME.md';text=file.read_text(encoding='utf-8');at=text.index('## 最近完成：C++ 头文件')
section=f'''## 最近完成：设置二级菜单触摸修复（2026-10-28）

- 用户澄清为“设置打开后，二级菜单选项点不动”，不是花屏。[本批报告](FIX_SETTINGS_TOUCH_2026-10-28.md)：选项行的原生 tooltip 抢走 ListView 触摸；三处 UI 输入职责修复，保留无障碍描述，原按钮提示、设置草稿/应用/取消及头文件 Tab 均保持。未作新一轮外观打磨。
- 完整触摸基线 5 项中 4 失败/1 通过；修复后定向 109 全通过，最终主 874 项中 873 通过/1 旧跳过，旧 869 身份/跳过集合保持。终端 146、Lint 0 错误（主 {r['lint']['app'].get('Warning',0)}/Sora {r['lint']['editor-sora'].get('Warning',0)} 警告），整批构建 1 次，原签名/CRC/16KiB/Manifest 通过。
- `{r['apk']}`，{r['bytes']:,} 字节，SHA256 `{r['sha256']}`。25 原生映像/48 运行资产及运行源码摘要保持，Python66/包内39/语义22/Shell4/片段14复用前批证据，没有重称本批执行。92 旧 APK 保持；[两张原生图](settings-touch-preview/README.md)已查看。
- 主机完整触摸分发验证与设备验收分开；未要求连接设备，Android 设备尚未重验。继续 P2-D 剩余多文件运行、用例管理/批量测试和格式化，原 P3 保留，整体目标未完成。

'''
file.write_text(text[:at]+section+text[at:],encoding='utf-8')
file=ROOT/'README.md';text=file.read_text(encoding='utf-8');at=text.index('**2026-10-27：C++ 头文件')
section=f'''**2026-10-28：修复设置二级菜单触摸无响应。**

- [最新修复 APK]({r['apk']})（{r['bytes']/1048576:.2f} MiB，原签名，arm64-v8a）。移除被动选项行抢走触摸的提示行为，原说明、设置草稿及补全保留。
- [结果与限制](docs/FIX_SETTINGS_TOUCH_2026-10-28.md)：完整触摸基线 4 失败/1 通过，修复后定向 109 全通过；完整 874 项中 873 通过/1 旧跳过，Lint 零错误。设备验收仍后置。

'''
text=text[:at]+section+text[at:];text=text.replace('**2026-10-27：C++ 头文件 Tab 补全接入。**','**历史：2026-10-27 C++ 头文件 Tab 补全接入。**')
file.write_text(text,encoding='utf-8')
file=ROOT/'docs/DEVELOPMENT_BATCH_POLICY.md';text=file.read_text(encoding='utf-8')
text=text.replace('[头文件 Tab 补全批次 APK](../dist/CodePocket-header-tab-2026-10-27-debug.apk)','[设置触摸修复 APK](../dist/CodePocket-settings-touch-fix-2026-10-28-debug.apk)');file.write_text(text,encoding='utf-8')
file=ROOT/'docs/P2_IMPLEMENTATION_PLAN_2026-10-26.md';text=file.read_text(encoding='utf-8')
text+='\n2026-10-28：优先处理用户报告的[设置二级菜单无法触摸选择](FIX_SETTINGS_TOUCH_2026-10-28.md)，已完成三处输入职责修复及代码/主机/APK验证。此项属于阻塞操作修复，不恢复零碎 UI 打磨；P2 后续与 P3 保留，整体目标继续。\n'
file.write_text(text,encoding='utf-8')
folder=ROOT/'docs/settings-touch-preview';folder.mkdir(exist_ok=True)
(folder/'README.md').write_text('''# 设置触摸修复原生预览

两张主机原生 Views 实际绘制图已查看。手机主题图来自新增完整触摸测试；平板选项图来自本次定向重新执行的原 LocalCppSettingsIntegrationTest。不是设备截图。此修复不重新设计布局。

![手机主题选择](../../verification/settings-touch-fix-2026-10-28/previews/phone-theme-before-touch.png)

![平板选项](../../verification/settings-touch-fix-2026-10-28/previews/tablet-backend-current.png)
''',encoding='utf-8')
print('Documented verified touch fix and explicit reuse of runtime evidence; overall goal remains active.')
