from pathlib import Path
import json,re
root=Path.cwd();phase=root/'verification/workbench-assist-batch-2026-10-26';r=json.loads((phase/'results.json').read_text(encoding='utf-8'));apk=r['apk'];report='UI_ASSIST_BATCH_2026-10-26.md';passed=r['junit']['tests']-r['junit']['skipped']
raw=(phase/'full-build.log').read_bytes();build=raw.decode('utf-16' if raw.startswith((b"\xff\xfe",b"\xfe\xff")) else 'utf-8-sig');duration=re.search(r'BUILD SUCCESSFUL in ([^\r\n]+)',build).group(1)
(root/'docs/assist-batch-preview/README.md').write_text("""# 补全、参数参考与片段菜单原生预览

实际MainActivity/Sora、词法候选、内置参考和片段事务的Robolectric Native Graphics主机绘制，不是Android设备截图，不代表完整语义分析。

- [平板候选类型和说明](tablet-completion-kinds.png) · [手机浅色双倍字体](phone-light-large-completion.png)
- [单形式参数参考](phone-large-single-signature.png) · [多形式与当前参数横向定位](phone-light-large-multiple-reference.png)
- [平板单语言片段选择](tablet-snippet-picker.png) · [手机双倍字体占位符导航](phone-light-large-snippet-navigation.png)

[本批修改与真实测试](../UI_ASSIST_BATCH_2026-10-26.md) · [四类窗口可点击设计预览](../ui-preview/README.md)。实际IME/蓝牙键鼠/帧率继续后置。
""",encoding='utf-8')
(root/'docs'/report).write_text(f"""# 编辑辅助浮层合并批次（2026-10-26）

在9d09864基础上集中完成补全、内置参数参考、代码片段3项相关UI改动。保留现有项目、生产Sora和真实运行，不引入LSP/Jedi/clangd或假语义候选；未实现功能及原P2/P3继续暂缓。

## 三项改动

1. **补全列表**：候选名称与真实CompletionKind分别显示，使用不同的克制线性图标。类型文字超过可用预算时让位给候选名，完整类型保留在说明/辅助信息中。标题明确“基础词法补全”，选择态和文档区分层；按实际字体行高与当前说明测量空间，减少固定空白，并沿用原光标/可见区域定位及非抢焦点窗口。
2. **内置参数参考**：单形式只显示关闭按钮，多形式保留前后切换；窄窗口标题保留形式计数，长签名显示横向滚动线索。修复切换长形式后仍沿用旧文本宽度的问题：显式WRAP_CONTENT加入横向容器，当前参数的横向定位延后至布局完成；关闭后清理待滚动状态，回调检查窗口/Activity。内置文档及“同名绑定未验证”说明保留，未改成重载解析。
3. **代码片段**：使用共享两级选择行，等宽触发词与说明分开；标题标明Python或C++17内置模板，触屏占位符导航使用明确图标。原快照/版本/组合输入保护、模板展开、占位符和撤销事务继续执行；没有修改模板内容或语言提供器。

生产修改为CompletionPopup.kt、SignaturePopup.kt、MainActivity.kt、ui/CompletionCandidateRow.kt和ui/WorkbenchChoiceAdapter.kt。候选高度探针每个浮层创建一次，不在每次定位中创建整张候选列表；本批未测量实际帧率。

## 测试、修正与构建

- 新6项基线均失败，分别覆盖缺失类型文字、单形式冗余按钮、滚动提示及片段层次/语言范围。保留基线XML。
- 首轮定向编译发现新颜色字段与View.foreground同名，已改名；这是本批引入并修复的编译错误，未打包APK。其后80项定向中5项失败：新用例误写了Python模板值、绕开正式请求导致待执行自动刷新收起候选、按三种而非实际两种range形式点击，以及旧用例对已主动隐藏的按钮继续测量。改为真实请求入口、当前range(10)模板和两种参考，保留原输入/撤销/文档断言；旧用例验证仅关闭按钮可见且可见按钮均至少48dp。
- 后续80/58项中保留的1项失败实际重现长形式文字已更新、子View仍226px宽的问题。明确文本尺寸并在真实布局后横向定位后，最终26项相关回归全部通过。主机测试显式测量/布局变更后的原生Popup View，不把宿主缺少系统遍历当作Android设备结论；没有去掉宽度或当前参数滚动断言。
- 最终主App {r['junit']['tests']} 项：{passed}通过、0失败、1既有Windows符号链接跳过；原{r['baseline_test_cases_retained']}个用例身份和跳过集合保持。
- 新实际Main/Sora用例覆盖：真实词法提供器类型、方向键/Tab接受和同一缓冲；双倍字体候选名/类型布局及Esc；单形式关闭与焦点；两种实际参考触屏切换、长文本扩宽及当前参数滚动；真实片段插入、占位符Tab/触屏导航、整次撤销和文档保持。
- 完整测试、app/Sora Lint和assembleDebug成功，{duration}；packageDebug实际执行。本批只调用1次APK构建，前面的编译/测试重试均未打包。Lint0错误，主App17/Sora1警告；主App新增1条ViewConstructor：代码创建的候选行缺少布局工具识别的构造形式。资源中无该View的XML引用，按用户UI优先/非阻塞问题先记录的约定登记，后续组件整理时处理；没有关闭Lint规则。
- Python源码44、实际APK字节码39、主机Shell4、真实Python/C++17片段各7、签名/ZIP CRC/16KB对齐全部重新检查通过；{r['old_apks_preserved']}个旧APK哈希、10个原生库及Python app.imy保持。

## 视觉和边界

[6张原生预览](assist-batch-preview/README.md)。目视平板候选/片段、手机双倍字体候选和单/多形式参数参考；其他为自动断言及截图归档，不声称全部逐张人工验收。

样稿增加大字体候选、多形式参考、片段导航，并将原未列入选择器的片段菜单纳入场景。44场景×4窗口×2主题=352次场景/边界检查、81项交互与20项配色检查通过，无脚本错误；222张设计图归档。新增独立assist.js便于维护，使用本地资源；候选键盘/形式切换/模板选择只是明确标注的设计示例，不执行程序，也不用它替代原生输入/插入测试。

本批未连接实机。实际IME/中文组合输入、实体蓝牙键鼠、卓易通、API24、首帧/FPS/高刷、Wandbox和远端Actions继续后置。前批被动缩窗焦点、关键词状态及单次导入未确认问题仍登记。基础词法候选、当前文件出现的名称和内置文档不代表类型推断或完整语言服务；本批不声明整个项目验收完成。

## 交付

[Debug APK](../{apk})：{r['bytes']:,}字节，SHA256 `{r['sha256']}`，原调试签名、arm64-v8a。

测试修改：新增AssistSurfaceIntegrationTest.kt；MainSurfaceIntegrationTest.kt按新的可见控制项核对尺寸，原完整说明/触控/焦点/缓冲断言保留。README、RESUME、范围清单、样稿及验证脚本/结果同步。

[机器结果](../verification/workbench-assist-batch-2026-10-26/results.json) · [UI范围和待办](UI_SCOPE_AUDIT_2026-10-24.md) · [批次节奏](DEVELOPMENT_BATCH_POLICY.md)。
""",encoding='utf-8')
f=root/'README.md';s=f.read_text(encoding='utf-8');end=s.index('## 历史主机整合验收');f.write_text(f"""# CodePocket · Android C++ / Python IDE

> 当前优先级：UI视觉与工作台层次。允许适度装饰，尚未实现的新功能暂缓；保留原有保存、编辑和真实运行。见[本批记录](docs/{report})与[完整范围核对](docs/UI_SCOPE_AUDIT_2026-10-24.md)。按[批次节奏](docs/DEVELOPMENT_BATCH_POLICY.md)积累相关修改再统一构建。

**2026-10-26：一批整理补全、内置参数参考和代码片段。** 候选名称/类型/说明分层，参数参考精简无效按钮并修复长形式横向定位，片段触发词与说明分开；保留原输入、光标和事务保护。

- [最新Debug APK]({apk})（{r['bytes']/1024/1024:.2f}MiB，原签名、arm64-v8a）。
- [实际结果与限制](docs/{report}) · [原生预览](docs/assist-batch-preview/README.md)：主App{passed}通过/0失败/1旧跳过，Lint0错误，新增1条设计器构造提示已登记；Debug实际打包和APK检查通过。
- [可点击样稿](docs/ui-preview/README.md)：44场景、352次场景检查、81项交互和20项配色检查通过，均为设计示例。
- 补全仍为基础词法/内置参考；前批焦点、关键词状态等问题继续登记，实机IME/键鼠/卓易通/FPS及远端CI后置。历史见[接续记录](docs/RESUME.md)，不声明整个项目完成。

"""+s[end:],encoding='utf-8')
f=root/'docs/RESUME.md';s=f.read_text(encoding='utf-8');pos=s.index('## 当前：');s=s[:pos]+f"""## 当前：补全、参数参考与片段浮层批次（2026-10-26）

- [本批报告]({report})：3项编辑浮层集中修改后构建。候选名称/类型自适应分层，实际字体/说明高度减少空白；单形式仅关闭，多形式保留切换/计数，显式文本尺寸与布局后当前参数滚动；片段等宽触发词/说明/语言范围，原模型与输入事务保持。
- 基线6失败。定向先修复本批引入的View.foreground字段冲突，再纠正当前模板值、实际请求入口、range两种形式和隐藏控件测量。80/58项剩余1项确认为长签名仍用旧宽度，保留断言修正后26项通过。
- 最终739项中738通过/0失败/1旧跳过，旧733身份和跳过集合保持。Lint0错误；主17/Sora1警告，新增1条候选行ViewConstructor设计器提示已登记。完整{duration}成功，实际打包，本批只有1次APK构建。
- {apk}，{r['bytes']:,}字节，SHA256 {r['sha256']}。Python44/APK39/主机Shell4/真实片段14/签名/16KB通过；84旧包、10原生库和app.imy保持。
- [6张原生预览](assist-batch-preview/README.md)；样稿44场景/352边界/81交互/20配色通过，222图。所有样稿明确设计示例；语义服务/设备验收不冒称通过，原P2/P3及未实现功能继续暂缓。

"""+s[pos:].replace('## 当前：','## 历史：',1);f.write_text(s,encoding='utf-8')
f=root/'docs/UI_SCOPE_AUDIT_2026-10-24.md';s=f.read_text(encoding='utf-8').replace('[选择器合并批次](UI_PICKERS_BATCH_2026-10-26.md)',f'[编辑辅助合并批次]({report})');s=s.replace('基础词法候选、内置片段和参数参考；Tab/方向键/Esc、组合输入/异步布局与旧结果保护','基础词法候选的类型/名称/说明分层、窄屏优先名称；内置片段分语言/触发词/说明，单/多形式参数参考与布局后横向定位；原Tab/方向键/Esc、组合输入/异步布局与旧结果保护');s=s.replace('CompletionInteractionTest、MainSurfaceIntegrationTest；Jedi/clangd深语义未接入','AssistSurfaceIntegrationTest、CompletionInteractionTest、MainSurfaceIntegrationTest；真实提供器/模板、方向键/Tab、插入/导航/撤销和参考形式/滚动分别回归；Jedi/clangd深语义未接入');f.write_text(s,encoding='utf-8')
f=root/'docs/DEVELOPMENT_BATCH_POLICY.md';s=f.read_text(encoding='utf-8').replace('[选择器合并批次APK](../dist/CodePocket-pickers-batch-2026-10-26-debug.apk)',f'[编辑辅助合并批次APK](../{apk})');f.write_text(s,encoding='utf-8')
f=root/'docs/ui-preview/README.md';s=f.read_text(encoding='utf-8').replace('40种界面场景','44种界面场景').replace('40场景×4窗口×2主题=320次','44场景×4窗口×2主题=352次').replace('加72项交互','加81项交互').replace('174张截图','222张截图').replace('../../dist/CodePocket-pickers-batch-2026-10-26-debug.apk','../../'+apk);s+='\n2026-10-26同步补全名称/类型/说明、单/多形式内置参考和单语言片段菜单；增加大字体候选、多形式参考、片段占位符导航，并补齐片段选择场景，独立assist.js保持本地可读。[原生预览](../assist-batch-preview/README.md) · [真实输入/插入/滚动验证及限制](../UI_ASSIST_BATCH_2026-10-26.md)。\n';f.write_text(s,encoding='utf-8')
print('Current assistance delivery and historical records synchronized.')
