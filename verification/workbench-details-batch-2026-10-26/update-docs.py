from pathlib import Path
import json

root=Path.cwd()
phase=root/'verification/workbench-details-batch-2026-10-26'
r=json.loads((phase/'results.json').read_text(encoding='utf-8'))
tests=r['junit']; passed=tests['tests']-tests['skipped']; shots=r['native_previews']
apk='CodePocket-details-batch-2026-10-26-debug.apk'
report='UI_DETAILS_BATCH_2026-10-26.md'
summary=f'主App{tests["tests"]}项中{passed}通过、0失败、1项旧跳过；Lint0错误，主17/Sora1警告，其中1条新增未使用字符串警告已登记。'
intro='帮助、许可、按键说明、诊断及终端错误记录统一原生阅读浮层；640dp最大阅读宽度、上下文标题、尺寸跟随、全文选择/复制和关闭返回，设置未应用草稿保持。'
limits='实机IME/组合输入、蓝牙键鼠、API24、卓易通、第一帧/FPS、Wandbox远端及GitHub Actions均未验收，按约定后置。旧被动缩窗焦点、关键词状态、未确认单次helper导入问题继续登记；原P2/P3及未实现功能保留暂缓。'
details=f'''# CodePocket 只读详情合并批次 · 2026-10-26

{intro} 本批接手现有仓库，未重建项目。基线HEAD为e1300e4，包含上一批f81c466；历史APK摘要已在修改前重新核对一致，历史测试数字未当成本轮结果。

## 集中修改

| 相关范围 | 实际修改与保护 |
| --- | --- |
| 共享阅读组件 | ReadOnlyDetails：复用工作台图标、标题/范围及灰蓝色层次，宽度最多640dp、随宿主可用宽度收窄，正文可滚动、原生关闭/复制按钮；结束后解除监听 |
| 帮助和快捷键 | MainActivity改用共享阅读层；原完整说明及生产Sora文档保持 |
| 设置按键说明和许可 | SettingsDialog统一阅读入口；全部许可全文保持，说明关闭/Back返回同一设置实例；未应用草稿不落盘 |
| 诊断与终端错误 | 保留来源、等宽完整正文、原复制回调和终端FAILED状态；不新建替代会话，不改变运行或文件事务 |

## 本轮验证

- 接手时已有6项测试，原始基线5失败/1通过；基线文件和日志保留，未删断言或新增跳过。
- 定向回归{r['targeted']['tests']}项，最终0失败/0跳过；含实际Main/Sora、设置草稿关闭/Back、许可末尾滚动、完整复制、终端失败状态、窗口缩放及既有极矮诊断。
- {summary} 旧{r['retained_test_cases']}项身份与跳过集合全部保留，新增{r['new_test_cases']}项。
- 新增的UnusedResources指向app/src/main/res/values/strings.xml:96的shell_failure_close：共享阅读层统一“关闭”后旧资源未再引用，资源仍保留，未屏蔽规则。按用户“新发现非阻塞先登记”保留此记录，不为这一警告单独重打包；主App其余16条和Sora1条为既有警告。
- 整批最终统一运行全量回归、主App/Sora Lint和Debug构建，packageDebug实际执行。基线和定向阶段没有调用assembleDebug；文档更新未再构建。
- 本轮重新执行Python源码44、APK字节码39、主机Shell4、真实Python片段7/C++17片段7；签名、ZIP CRC、元数据、权限与16KB对齐通过。
- {r['old_apks_preserved']}个旧APK摘要保持；10个原生库及assets/chaquopy/app.imy与上一已交付APK逐字节一致。原Debug签名保持。
- [原生预览]（details-batch-preview/README.md）{shots}图来自主机Robolectric实际原生Views，实际查看的图与限制见该索引。本轮未另制浏览器样稿，不复用上批浏览器数字作为本轮验证。

## 安装包

[{apk}](../dist/{apk})

- 字节：{r['bytes']}
- SHA256：`{r['sha256']}`
- 签名SHA256：`{r['signer_sha256']}`
- 包名com.codepocket.ide，版本0.2.0/code2，min24/target34，arm64-v8a。

## 证据及边界

原始基线、各轮定向日志/JUnit、最终全量JUnit、Lint、APK检查及机器摘要在verification/workbench-details-batch-2026-10-26/。首轮沙箱Gradle daemon连接失败没有进入测试，后续使用相同离线工具在获准环境运行；紧凑标题一次Kotlin属性名冲突编译失败已修正，原日志保留。

定向检查中保留了极矮诊断正文被挤掉、Back按键未关闭、缩窗宽度不符等失败证据。生产阅读层补紧凑标题与Back/Esc关闭、宿主布局监听。缩窗进一步记录到host在560×700手动布局后被ViewRoot还原1280×800；夹具改为约束实际宿主，原528px期望、正文身份、滚动与关闭后监听解除断言均保留。宽度通过后又发现可选择正文焦点使滚动回顶，生产组件补布局到pre-draw结束的位置保护，新增精确120px保留检查，另加320px高度、文本选区和完整按钮断言。原生按钮异步关闭用例补idle及isShowing=false前置检查，再缩窗验证原宽度不变。没有通过删断言或新增跳过消除失败。

{limits} 主机绘制和测试不代表Android设备的实际输入法、进程或系统选择菜单已通过，不声明整个项目/UI全部完成。未推送、未发布、未创建自动任务。
'''
details=details.replace('[原生预览]（details-batch-preview/README.md）','[原生预览](details-batch-preview/README.md)')
(root/'docs'/report).write_text(details,encoding='utf-8')
resume=root/'docs/RESUME.md'
text=resume.read_text(encoding='utf-8')
marker='## 当前：只读详情合并批次（2026-10-26）'
if marker not in text:
    at=text.index('## 当前：主工作台与设置中心合并批次')
    text=text[:at]+f'''{marker}

- [本批报告]({report})：{intro}
- {summary} 旧{r['retained_test_cases']}身份/跳过集合保留；整批集中回归/Lint/Debug及APK检查通过，运行模块保持。
- dist/{apk}，{r['bytes']}字节，SHA256 {r['sha256']}。
- [原生预览](details-batch-preview/README.md){shots}图；主机实际原生绘制与设备行为区分。本轮不引用上批浏览器样稿数字。
- {limits}

'''+text[at:].replace('## 当前：主工作台与设置中心合并批次','## 历史：主工作台与设置中心合并批次',1)
    resume.write_text(text,encoding='utf-8')
readme=root/'README.md';text=readme.read_text(encoding='utf-8')
start=text.index('> 当前优先级');end=text.index('## 历史主机整合验收')
text=text[:start]+f'''> 当前优先级：UI视觉与工作台层次。允许适度装饰，未实现新功能暂缓；保留原保存、生产Sora和真实运行。见[本批记录](docs/{report})和[范围核对](docs/UI_SCOPE_AUDIT_2026-10-24.md)。按[批次节奏](docs/DEVELOPMENT_BATCH_POLICY.md)积累相关修改再统一构建。

**2026-10-26：只读详情合并整理。** {intro}

- [最新Debug APK](dist/{apk})（{r['bytes']/1048576:.2f}MiB，原签名、arm64-v8a）。
- [结果与限制](docs/{report}) · [{shots}张原生预览](docs/details-batch-preview/README.md)：{summary} Debug实际打包、签名及运行模块主机检查通过。
- [可点击样稿](docs/ui-preview/README.md)为既有设计示例；本批以实际原生Views图和断言验证，不将旧样稿数字当成本轮结果。
- {limits} 历史见[接续记录](docs/RESUME.md)，不声明整个项目完成。

'''+text[end:];readme.write_text(text,encoding='utf-8')
policy=root/'docs/DEVELOPMENT_BATCH_POLICY.md';text=policy.read_text(encoding='utf-8')
text=text.replace('[主工作台与设置合并批次APK](../dist/CodePocket-settings-chrome-batch-2026-10-26-debug.apk)',f'[只读详情合并批次APK](../dist/{apk})')
policy.write_text(text,encoding='utf-8')
scope=root/'docs/UI_SCOPE_AUDIT_2026-10-24.md';text=scope.read_text(encoding='utf-8')
text=text.replace('[主工作台与设置合并批次](UI_SETTINGS_CHROME_BATCH_2026-10-26.md)',f'[只读详情合并批次]({report})')
text=text.replace('WorkbenchForms只读滚动正文、原说明/许可证/诊断完整复制','ReadOnlyDetails共享上下文标题/640dp宽度/窗口缩放、完整滚动正文与原复制回调')
text=text.replace('AuxiliarySurfaceIntegrationTest；工作区/草稿','ReadOnlyDetailsIntegrationTest、AuxiliarySurfaceIntegrationTest及MainSurfaceIntegrationTest；工作区/草稿')
text=text.replace('平板按需只读说明阅读跨度偏大继续登记，下一批与帮助/许可/详情共同处理。','平板只读说明阅读跨度已在本批统一为640dp上限，并覆盖缩窗、全文与设置草稿返回；设备系统文本选择菜单仍后置。')
note='- 只读详情批次新增1条UnusedResources：strings.xml:96的shell_failure_close已无引用；共享层仍显示相同“关闭”，旧资源保留，无运行阻塞。按约定先登记，不屏蔽规则，不为此单独重打包。当前主App17/Sora1条警告、零错误。'
if note not in text:text=text.replace('## 登记与验收边界','## 登记与验收边界\n\n'+note,1)
text=text.replace('ViewConstructor提示已在本批补标准Context构造消除：Lint主16/Sora1既有警告','ViewConstructor提示已在上一设置批次补标准Context构造消除：当时Lint主16/Sora1既有警告')
scope.write_text(text,encoding='utf-8')
progress=root/'docs/DEVELOPMENT_PROGRESS.md';text=progress.read_text(encoding='utf-8')
if '## 当前：UI视觉与只读详情批次' not in text:
    text=text.replace('## 当前：I4单一主APK主机整合验收',f'''## 当前：UI视觉与只读详情批次（2026-10-26）

当前优先级以[RESUME.md](RESUME.md)及用户最新指示为准：UI视觉第一，尚未实现的新功能暂缓，原P2/P3保留；按3–5项相关修改合批验证。下方I4后直接P2的表述为历史接续点。

- [本批报告]({report})：{intro}
- {summary} [本批APK](../dist/{apk})及签名、运行模块主机检查通过，原生绘制与设备验收分开。
- {limits}

## 历史：I4单一主APK主机整合验收''',1)
    progress.write_text(text,encoding='utf-8')
native=root/'docs/details-batch-preview'
images=sorted(native.glob('*.png'))
index=f'# 只读详情批次原生预览\n\n主机Robolectric原生绘制，来自实际MainActivity/生产Sora、设置、许可资产与终端错误入口。诊断和启动失败长文本为明确标记的布局夹具，未执行设备程序。\n\n已逐张查看本批{len(images)}张原生图，含手机双倍字体、平板深色按键说明、浅色完整许可末尾、诊断及矮窗终端记录、缩窗和极矮诊断。主机画图不代表实机输入法、键鼠、系统选择菜单或帧率通过。\n\n'
for image in images:index+=f'## {image.stem}\n\n![{image.stem}]({image.name})\n\n'
(native/'README.md').write_text(index.rstrip()+'\n',encoding='utf-8')
print('Updated report, handoff, README, policy, scope and native preview index')
