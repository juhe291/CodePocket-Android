from pathlib import Path
import json
root=Path.cwd();phase=root/'verification/workbench-context-batch-2026-10-26';r=json.loads((phase/'results.json').read_text(encoding='utf-8'))
apk=r['apk'];report='UI_CONTEXT_BATCH_2026-10-26.md';passed=r['junit']['tests']-r['junit']['skipped'];digest=r['sha256']
(root/'docs/context-batch-preview/README.md').write_text("""# 文件、项目、终端选择与项目搜索原生预览

真实MainActivity/Sora、WorkbenchMenu和AlertDialog的Robolectric Native Graphics主机绘制，不是Android设备截图。

- [平板文件操作](tablet-file-context.png) · [手机浅色双倍字体长路径](phone-light-large-file-context.png)
- [平板项目菜单](tablet-project-context.png)
- [平板浅色双倍字体会话选择](tablet-light-large-session-selector.png) · [终端配色与父级返回](tablet-light-large-palette-selector.png)
- [280dp手机浅色双倍字体实际未保存内容搜索](phone-light-large-project-search.png) · [平板实际搜索无匹配](tablet-project-search-empty.png)

[改动与真实测试](../UI_CONTEXT_BATCH_2026-10-26.md) · [四类窗口可点击设计样稿](../ui-preview/README.md)。样稿与主机View绘制、实机验收分别记录。
""",encoding='utf-8')
(root/'docs'/report).write_text(f"""# 文件、项目、终端与搜索上下文批次（2026-10-26）

按用户新的构建节奏，在5210b0f基础上集中完成4项相关展示/交互修改，中途定向回归和视觉检查，批次收尾统一构建。保留Kotlin Views、生产Sora、原文件保护、真实搜索/运行和单一PTY；本批不增加暂缓功能。

## 四项修改

1. 文件/文件夹操作改用现有锚点菜单，路径作为固定标题，图标按稳定命令身份指定，删除单独危险分组；长路径省略但完整内容保留辅助说明。点击/长按/鼠标右键使用同一入口。原新建/重命名/移动/删除和保存事务继续执行。
2. 资源管理器菜单增加固定标题，定位/折叠图标明确，新建/工作区/导入导出/文件维护按组分隔；图标映射复用IdeCommandMenu，原命令身份和执行检查保留。
3. 终端会话与配色选择器显示上下文标题和当前选中项；配色可返回实际父分类。真实选择另一个已有会话和应用Nord的主机回归保留registry条目、当前文档缓冲；没有重建终端运行底层。
4. 项目搜索增加项目范围标题，按内容宽度和字体缩放切换横排/竖排，输入最少48dp且可随字体增长；正文可滚动。等待和空结果不占固定空白结果面板。有结果继续用两级文件/代码行显示及原worker、取消/迟到保护和未保存缓冲定位。

共享菜单增加显示周期令牌与窗口仍显示的检查，关闭后的旧行不能重新派发危险操作。搜索控件仅在横竖排策略变化时更新LayoutParams，避免每次测量重复请求布局。内部项目的搜索标题只显示简短范围名，已有权限/备份说明保留在原入口。

## 实际测试

- 新6项基线全部失败，分别暴露旧居中文件列表、缺少菜单标题、窄屏控件挤压及空结果留白。定向测试72项通过；之后依据目视结果缩短搜索范围标题，补强实际Nord应用/会话切换断言，最终全量再覆盖。
- 最终主App {r['junit']['tests']} 项：{passed}通过、0失败、1既有Windows符号链接跳过。原{r['baseline_test_cases_retained']}个用例身份及跳过集合保留；新6项不替代旧文件/SAF/保存/补全回归。
- 真实Main/Sora用例包括：脏缓冲重命名前保存、磁盘内容及file ID保持；长中文名删除取消和旧行回调隔离；选中目录内真实创建；已有终端模型选择与实际设置应用；280dp/2倍字体未保存内容搜索并实际定位；无匹配不显示空列表。
- 完整Gradle测试、app/Sora Lint、assembleDebug成功，packageDebug实际执行。本批只调用1次APK构建；原生基线/定向阶段只运行测试。Lint0错误，主App16/Sora1既有警告，没有新增。
- Python源码44、APK字节码39、主机Shell4、真实Python/C++17片段各7，以及签名、ZIP CRC、16KB对齐通过。本批重新执行这些专项，未把历史结果冒充重测。{r['old_apks_preserved']}个旧APK原哈希、10个原生库、Python app.imy保持。
- 样稿37场景×4窗口×2主题=296场景/边界检查、62项交互与20项配色检查通过，无脚本错误；126张设计截图归档。首轮脚本试图选择未列入场景选择器的分类而超时，改为实际菜单层级点击；随后1项手机搜索换行检查失败，将CSS容器查询移至内容区后完整重测通过。失败日志保留。

## 视觉检查与验收边界

[7张原生预览](context-batch-preview/README.md)。目视文件菜单/项目菜单、终端配色及窄屏双倍字体搜索；浏览器目视平板深色与手机浅色搜索。其余场景为自动边界/交互检查和截图归档，不能声称全部逐张人眼验收。固定标题、灰蓝层次、克制线性图标继续沿用现有视觉体系。

主机没有可用Android PTY进程；这里验证的是实际App的菜单派发、设置和会话模型，不是设备Shell执行。实际IME/组合输入、实体键鼠、卓易通、API24、首帧/FPS/高刷、Wandbox和远端Actions按约定后置。前批被动缩窗焦点、关键词状态及单次导入未确认问题继续登记；原P2/P3和新功能暂缓。本批不声明整体UI及完整项目验收完成。

## 交付和涉及文件

[Debug APK](../{apk})：{r['bytes']:,}字节，SHA256 `{digest}`，原调试签名、arm64-v8a。

生产：MainActivity.kt、ui/WorkbenchMenu.kt、ui/IdeCommandMenu.kt、ui/WorkbenchForms.kt。测试：新增ContextBatchIntegrationTest.kt。设计样稿：workbench.js/CSS和截图；README、RESUME、范围清单及本批验证脚本/结果同步。

[机器结果](../verification/workbench-context-batch-2026-10-26/results.json) · [UI范围/待办](UI_SCOPE_AUDIT_2026-10-24.md) · [批次节奏](DEVELOPMENT_BATCH_POLICY.md)。
""",encoding='utf-8')
f=root/'README.md';s=f.read_text(encoding='utf-8');end=s.index('## 历史主机整合验收')
prefix=f"""# CodePocket · Android C++ / Python IDE

> 当前优先级：UI视觉与工作台层次。允许适度装饰，尚未实现的新功能暂缓；保留原有保存、编辑和真实运行。见[本批记录](docs/{report})与[完整范围核对](docs/UI_SCOPE_AUDIT_2026-10-24.md)。按[批次节奏](docs/DEVELOPMENT_BATCH_POLICY.md)积累相关修改再统一构建。

**2026-10-26：一批整理文件操作、资源管理器、终端选择器和项目搜索。** 统一锚点菜单和上下文标题，保留安全确认；终端配色可返回父分类，搜索适配窄屏/大字体，空结果减少留白。

- [最新Debug APK]({apk})（{r['bytes']/1024/1024:.2f}MiB，原签名、arm64-v8a）。
- [实际结果与限制](docs/{report}) · [原生预览](docs/context-batch-preview/README.md)：主App{passed}通过/0失败/1旧跳过，Lint0错误，Debug实际打包和APK检查通过。
- [可点击样稿](docs/ui-preview/README.md)：37场景、296次场景检查、62项交互和20项配色检查通过，均为设计示例。
- 前批焦点、关键词状态等问题继续登记；实机IME/键鼠/卓易通/FPS及远端CI后置。历史见[接续记录](docs/RESUME.md)，不声明整个项目完成。

"""
f.write_text(prefix+s[end:],encoding='utf-8')
f=root/'docs/RESUME.md';s=f.read_text(encoding='utf-8').replace('本次仅更新计划，以下已验证代码、测试数字和APK不变。','纯文档更新不打包；当前批次的实际代码/产物见下文。')
pos=s.index('## 当前：');s=s[:pos]+f"""## 当前：文件、项目、终端与搜索上下文批次（2026-10-26）

- [本批报告]({report})：合并4项相关UI改动，再统一构建；原文件事务/真实worker/运行底层不改。路径文件菜单、资源管理器标题/图标/分组、终端会话/配色标题与返回、窄屏大字体搜索和空结果布局。
- 新6基线失败；72定向通过；最终主727项中726通过/0失败/1旧跳过，旧721身份与跳过集合保留。Lint0错误、主16/Sora1旧警告；完整构建成功，packageDebug实际执行，本批只构建1次APK。
- {apk}，{r['bytes']:,}字节，SHA256 {digest}。Python44/APK39/主机Shell4/真实片段14/签名/16KB通过；82旧包、10原生库和app.imy保持。
- [7张原生预览](context-batch-preview/README.md)；样稿296场景/62交互/20配色通过，126图。浏览器首轮分类选择脚本修正，1项手机搜索CSS边界修正后完整重测通过；日志保留。
- 用户要求UI优先、未实现新功能暂缓、实机后置继续有效。前批焦点/关键词状态/未确认导入问题继续登记，原P2/P3保留，整体目标继续。

"""+s[pos:].replace('## 当前：','## 历史：',1);f.write_text(s,encoding='utf-8')
f=root/'docs/UI_SCOPE_AUDIT_2026-10-24.md';s=f.read_text(encoding='utf-8').replace('[菜单上下文记录](UI_MENU_CONTEXT_2026-10-26.md)',f'[上下文与搜索合并批次]({report})')
s=s.replace('WorkbenchForms与原新建/重命名/移动/删除；路径区、错误保留、删除确认、实际事务仍受旧校验','WorkbenchForms与原新建/重命名/移动/删除；锚点文件菜单含路径标题、身份图标、危险分组与旧行隔离，表单仍保留路径区/错误/确认及旧事务校验')
s=s.replace('FormSurfaceIntegrationTest、MainFileSettingsIntegrationTest；本批无快捷键行保持原层级，实际移动回归保留','ContextBatchIntegrationTest、FormSurfaceIntegrationTest、MainFileSettingsIntegrationTest；真实脏文件重命名、目录内新建、删除取消及旧回调隔离，实际移动回归保留')
s=s.replace('终端分类/尺寸返回保留会话模型和面板状态；旧列表型选择器保留兼容标题图标映射','终端分类/尺寸/配色返回保留会话模型；会话/配色选择器含标题与当前项，真实Nord设置和已有会话切换保持缓冲；其余旧列表选择器保留兼容映射')
s=s.replace('跳转行、项目搜索的现有输入/结果/过时来源保护','跳转行、项目搜索的范围标题、自适应输入、可滚正文、空结果不留空列表及现有过时来源保护')
s=s.replace('MainSurfaceIntegrationTest、SoraWorkbenchIntegrationTest、EditorSearch相关回归；没有改为新的搜索引擎','ContextBatchIntegrationTest、MainSurfaceIntegrationTest、SoraWorkbenchIntegrationTest、EditorSearch相关回归；280dp/2倍字体真实未保存搜索并定位，没有改为新的搜索引擎')
f.write_text(s,encoding='utf-8')
f=root/'docs/DEVELOPMENT_BATCH_POLICY.md';s=f.read_text(encoding='utf-8').replace('[菜单批次APK](../dist/CodePocket-menu-context-2026-10-26-debug.apk)，直到下一开发批次完成。',f'[上下文合并批次APK](../{apk})；完成下一开发批次后再更新。');f.write_text(s,encoding='utf-8')
f=root/'docs/ui-preview/README.md';s=f.read_text(encoding='utf-8').replace('36种界面场景','37种界面场景').replace('36场景×4窗口×2主题=288次','37场景×4窗口×2主题=296次').replace('加54项交互','加62项交互').replace('78张截图','126张截图').replace('../../dist/CodePocket-menu-context-2026-10-26-debug.apk','../../'+apk)
s+='\n2026-10-26按合并批次同步文件路径标题、资源管理器标题/分组、会话/配色当前项及返回、项目搜索范围/自适应布局和无匹配场景；浏览器手机搜索按实际内容区换行。[原生预览](../context-batch-preview/README.md) · [真实测试与限制](../UI_CONTEXT_BATCH_2026-10-26.md)。\n';f.write_text(s,encoding='utf-8')
print('Reports and latest artifact links synchronized.')
