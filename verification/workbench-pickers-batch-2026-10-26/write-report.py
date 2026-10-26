from pathlib import Path
import json
root=Path.cwd();phase=root/'verification/workbench-pickers-batch-2026-10-26';r=json.loads((phase/'results.json').read_text(encoding='utf-8'));apk=r['apk'];report='UI_PICKERS_BATCH_2026-10-26.md';passed=r['junit']['tests']-r['junit']['skipped']
(root/'docs/pickers-batch-preview/README.md').write_text("""# 工作区、草稿与移动窗口原生预览

真实MainActivity/Sora、AlertDialog和原文件事务的Robolectric Native Graphics主机绘制，不是Android设备截图。

- [平板当前工作区](tablet-workspace-current.png) · [手机浅色双倍字体与最近目录](phone-light-large-workspaces.png)
- [平板同名草稿/不同目录](tablet-light-drafts.png) · [手机浅色双倍字体长草稿列表](phone-light-large-drafts.png)
- [手机双倍字体深层移动目标](phone-light-large-move-deep-target.png) · [平板无可选子目录](tablet-move-empty.png)

[本批修改和实际测试](../UI_PICKERS_BATCH_2026-10-26.md) · [四类窗口可点击设计样稿](../ui-preview/README.md)。实际系统文件选择器、IME和键鼠验证继续后置。
""",encoding='utf-8')
(root/'docs'/report).write_text(f"""# 工作区、草稿与移动选择器合并批次（2026-10-26）

在3e41338基础上按用户新节奏集中完成3项相关UI调整；继续视觉优先、未实现新功能暂缓、实机后置的约定。本批不改存储、权限、草稿格式、运行模块或终端底层。

## 三项界面改动

1. **工作区选择**：名称与说明分层，当前存储项使用柔和选中底色及勾选标记；选择系统目录、内部项目、重载使用明确图标，最近目录单独分隔。内部项目卸载提示放在说明层，当前范围标题缩短。增加清晰的取消入口；原目录说明、Android授权、请求工作区和未保存保护继续执行。最近目录的展示元数据继续容忍缺少URI，未提高旧数据读取要求。
2. **恢复草稿**：文件名、类型图标与父目录分两级显示，同名文件可按目录区分；长路径保留完整辅助说明，不把源码内容放进列表。取消不移除草稿；选择项继续导出原entries快照，不替换当前编辑器或原文件。
3. **移动窗口**：使用统一标题图标、来源位置卡片，目标路径随字体/换行增长，完整路径可被辅助功能读取。目录列表改为自然高度并设上限，空目录不占固定240dp；整块正文可滚动，返回上一级和目录使用明确图标。原禁止移动到自身/子目录、保存前置、路径重基和文件身份保护保持。

新增共享WorkbenchChoiceAdapter只负责两级行、图标、当前项和完整说明，按原顺序提供展示。WorkbenchStyle保留这种适配器的元数据，其他旧列表继续原兼容样式。目录仍使用原可点击menuRow，避免改变旧事务入口；未重写项目或引入新编辑器。

## 实际测试和构建

- 新6项基线失败：4项为旧列表缺少两级适配器的结构检查，2项为深层目标高度及空目录固定高度。53项定向全部通过后，将新测试中的适配器类名检查换成可见取消文字/48dp高度，保留所有选择、路径、磁盘与导出断言，并增加草稿完整路径断言。基线源码与XML单独保留，没有靠放宽行为断言取得通过。
- 最终主App {r['junit']['tests']} 项：{passed}通过、0失败、1既有Windows符号链接跳过；旧{r['baseline_test_cases_retained']}个用例身份和跳过集合保持。
- 真实Main/Sora新用例覆盖：当前内部存储标记与取消；手机320dp/2倍字体最近目录、权限说明取消；同名不同目录草稿的精确导出文本和Android ACTION_CREATE_DOCUMENT intent；长列表取消保留10个草稿；脏缓冲深层实际移动、磁盘文本/文件ID/缓冲保持；禁止源目录及其子目录、空列表自然高度和取消。
- 完整Gradle测试、app/Sora Lint和assembleDebug成功，用时3分14秒，packageDebug实际执行。本批只调用1次APK构建；基线/定向只运行测试。Lint0错误，主App16/Sora1既有警告，无新增。
- Python源码44、APK字节码39、主机Shell4、真实Python/C++17片段各7，签名、ZIP CRC和16KB对齐全部重新检查通过。{r['old_apks_preserved']}个旧APK原哈希、10个原生库、Python app.imy保持。
- 可点击样稿新增3个长名称/长路径场景，40场景×4窗口×2主题=320次场景/边界检查、72项交互和20项配色检查通过，无脚本错误；174张设计图同步。首轮长列表用常规手机窗口，后将滚动检查限制为640px并对齐64dp行高；仍发现整窗滚动，修正为正文独立滚动、取消按钮固定，并增加位置保持断言后完整重测通过。失败日志保留。
- 首次写入命令在自动审批时超时，未执行；按工具允许重试一次成功。此事件不是编译失败，没有多构建安装包。

## 视觉和边界

[6张原生预览](pickers-batch-preview/README.md)。目视手机浅色双倍字体工作区、平板同名草稿、深层移动目标/空目录，以及浏览器手机长草稿列表；其余为自动几何/交互检查与截图归档，不声称全部逐张人工验收。沿用灰蓝表面、细分隔、柔和类型色和本地Lucide，没有增加荧光或发光效果。

主机的系统Intent检查不代表实际Android提供方授权/导出成功；文件移动为真实主机工作区事务。实际IME/组合输入、实体键鼠、卓易通、API24、首帧/FPS/高刷、Wandbox与远端Actions继续后置。前批被动缩窗焦点、关键词状态与单次导入未确认问题仍登记；原P2/P3及未实现功能保留暂缓。本批不声明整个项目和设备验收完成。

## 交付和文件

[Debug APK](../{apk})：{r['bytes']:,}字节，SHA256 `{r['sha256']}`，原调试签名、arm64-v8a。

生产文件：MainActivity.kt、ui/WorkbenchChoiceAdapter.kt、ui/WorkbenchStyle.kt、ui/WorkbenchForms.kt。新测试WorkspacePickerIntegrationTest.kt；样稿JS/CSS/图、README/RESUME/范围清单及本批验证脚本/结果同步。

[机器结果](../verification/workbench-pickers-batch-2026-10-26/results.json) · [UI范围与待办](UI_SCOPE_AUDIT_2026-10-24.md) · [批次节奏](DEVELOPMENT_BATCH_POLICY.md)。
""",encoding='utf-8')
f=root/'README.md';s=f.read_text(encoding='utf-8');end=s.index('## 历史主机整合验收');f.write_text(f"""# CodePocket · Android C++ / Python IDE

> 当前优先级：UI视觉与工作台层次。允许适度装饰，尚未实现的新功能暂缓；保留原有保存、编辑和真实运行。见[本批记录](docs/{report})与[完整范围核对](docs/UI_SCOPE_AUDIT_2026-10-24.md)。按[批次节奏](docs/DEVELOPMENT_BATCH_POLICY.md)积累相关修改再统一构建。

**2026-10-26：一批整理工作区、恢复草稿和移动窗口。** 名称/说明分层，明确当前存储和同名草稿所在目录；长目标路径、大字体、空目录与取消操作使用一致布局。原权限、导出快照和真实文件事务保持。

- [最新Debug APK]({apk})（{r['bytes']/1024/1024:.2f}MiB，原签名、arm64-v8a）。
- [实际结果与限制](docs/{report}) · [原生预览](docs/pickers-batch-preview/README.md)：主App{passed}通过/0失败/1旧跳过，Lint0错误，Debug实际打包及APK检查通过。
- [可点击样稿](docs/ui-preview/README.md)：40场景、320次场景检查、72项交互和20项配色检查通过，均为设计示例。
- 前批焦点、关键词状态等问题继续登记；实机IME/键鼠/卓易通/FPS及远端CI后置。历史见[接续记录](docs/RESUME.md)，不声明整个项目完成。

"""+s[end:],encoding='utf-8')
f=root/'docs/RESUME.md';s=f.read_text(encoding='utf-8');pos=s.index('## 当前：');s=s[:pos]+f"""## 当前：工作区、草稿与移动选择器批次（2026-10-26）

- [本批报告]({report})：合并3项相关UI改动再构建。共享两级选择行、当前存储/最近目录、同名草稿按路径区分/完整说明、取消；移动目标增长、自然高度目录区和整块正文滚动。原文件/权限/导出事务不改。
- 基线6失败、定向53通过；之后把类名检查改成可见取消文字/高度并保留功能断言，补草稿完整路径；最终733项中732通过/0失败/1旧跳过。旧727身份和跳过集合保持，Lint0错误、主16/Sora1旧警告。
- 完整构建3分14秒成功、实际打包，仅1次APK构建。{apk}，{r['bytes']:,}字节，SHA256 {r['sha256']}。Python44/APK39/主机Shell4/真实片段14/签名/16KB通过；83旧包、10原生库和app.imy保持。
- [6张原生预览](pickers-batch-preview/README.md)；样稿320场景/72交互/20配色通过，174图。长列表样稿改为正文滚动/固定取消后完整重测通过；失败记录保留。
- UI优先、未实现功能暂缓、设备后置保持；焦点/关键词状态/未确认导入问题继续登记，原P2/P3保留，整体目标继续。

"""+s[pos:].replace('## 当前：','## 历史：',1);f.write_text(s,encoding='utf-8')
f=root/'docs/UI_SCOPE_AUDIT_2026-10-24.md';s=f.read_text(encoding='utf-8').replace('[上下文与搜索合并批次](UI_CONTEXT_BATCH_2026-10-26.md)',f'[选择器合并批次]({report})');s=s.replace('实际移动回归保留','WorkspacePickerIntegrationTest另覆盖长目标可增长、空列表自然高度和脏缓冲真实深层移动');s=s.replace('系统文本选择菜单/SAF选择器/输入法继续由Android负责','工作区/草稿两级行与类型图标、完整路径、当前项/取消另由WorkspacePickerIntegrationTest验证；系统文本选择菜单/SAF选择器/输入法继续由Android负责');f.write_text(s,encoding='utf-8')
f=root/'docs/DEVELOPMENT_BATCH_POLICY.md';s=f.read_text(encoding='utf-8').replace('[上下文合并批次APK](../dist/CodePocket-context-batch-2026-10-26-debug.apk)',f'[选择器合并批次APK](../{apk})');f.write_text(s,encoding='utf-8')
f=root/'docs/ui-preview/README.md';s=f.read_text(encoding='utf-8').replace('37种界面场景','40种界面场景').replace('37场景×4窗口×2主题=296次','40场景×4窗口×2主题=320次').replace('加62项交互','加72项交互').replace('126张截图','174张截图').replace('../../dist/CodePocket-context-batch-2026-10-26-debug.apk','../../'+apk);s+='\n2026-10-26同步工作区当前项/最近目录、同名草稿的文件/目录两级行、深层移动与空目录布局；新增3种长名称/路径场景，正文滚动时取消保持位置。[原生预览](../pickers-batch-preview/README.md) · [真实测试与限制](../UI_PICKERS_BATCH_2026-10-26.md)。\n';f.write_text(s,encoding='utf-8')
print('Latest delivery, current scope and history synchronized.')
