from pathlib import Path
import json
root=Path.cwd();phase=root/'verification/python-semantic-integration-2026-10-26'
r=json.loads((phase/'results.json').read_text(encoding='utf-8'))
report='P2_PYTHON_SEMANTICS_2026-10-26.md';apk=Path(r['apk']).name
counts=f'主App772项：771通过、0失败、1旧跳过；旧751项身份/跳过集合保留，新增21项。定向79项全部通过。Lint0错误，主18/Sora1警告。'
scope='Python真实成员/标准库/已保存内部模块补全与函数参数接入同一主App；当前未保存正文参与，来源明确，失败保留词法/内置参考。'
limits='其他未保存标签、SAF项目新鲜快照留P2-B；C++仍为原词法/内置参考与Wandbox运行。Android分析服务/JNI实际执行、冷启动时延/资源、IME/键鼠/无障碍、API24、卓易通及远端CI仍未验收，实机继续后置。'
warning='新增ClickableViewAccessibility提示位于CompletionPopup.kt触摸观察器：监听返回false，由原ListView继续处理点击；无规则屏蔽，设备无障碍仍需验收。按用户非阻塞先登记约定保留，不为此另打包。上一批UnusedResources及旧焦点/关键词状态/未确认helper导入问题继续登记。'
text=f'''# P2-A Python真实补全接入 · 2026-10-26

先完成[实施计划书](P2_IMPLEMENTATION_PLAN_2026-10-26.md)，本地计划提交9765aa3，再执行本批实际代码、测试和集中构建。{scope} 零碎UI润色暂停，沿用原原生工作台、Sora、保存/恢复、运行和PTY。

## 四组实际修改

| 工作包 | 实现与边界 |
| --- | --- |
| 静态后端 | 复用封存semantics.py及15项用例，固定Jedi0.20.1/Parso0.8.7，串行静态Script分析；补保存模块刷新、并发、UTF-16/CRLF签名及无变化编辑回归，不执行项目顶层代码 |
| 独立进程/依赖 | 非导出PythonAnalysisService运行于:analysis，原执行:python和PTY保持；有界最新请求、连接归属、取消/关闭、看门狗。离线hash固定wheel，Jedi/Parso提取到文件系统，四份许可和新设置通知入口 |
| 生产候选/参数 | Main原文档/版本/组合/光标门控继续生效，后台语义结果升级原窗口；先显示词法/内置参考，用户已操作的候选或形式不被迟到结果替换。真实种类、签名/活动参数及准确替换范围复用原Sora事务 |
| 兼容与验证 | 旧文件结果丢弃，失败/空结果区分，精确片段触发优先、占位符/撤销/保存保留；新增真实Jedi响应产物供Main/Sora测试，另用Messenger验证连接故障与关闭；不以回放冒称Android服务运行 |

## 证据分层

- **真实后端执行**：本轮恢复后的15项基线通过；最终Python源码63项通过，其中语义19项，原执行/诊断/图标等44项保持。
- **主工作台接入**：{counts} 新21项包含协议7、实际Main/Sora8、服务/绑定6。Main测试使用真实Jedi生成且校验后端hash的11组响应，仅替换Android传输；有接受→实际保存→撤销、类/模块、同名print参数、切文件旧结果、键盘选择冻结、失败/空结果及片段链路。
- **实际APK内容**：强制用APK提取的semantics.pyc及Jedi/Parso字节码执行19项语义测试，0失败/错误；核对实际extract_packages、typeshed/grammar313、四份许可、DEX中的客户端/服务与非导出的:analysis声明。不是从主机site-packages替代打包依赖。
- **原运行链**：本轮实际APK运行/诊断字节码39、主机Shell4、Python/C++17片段各7项通过。10个原生库、原runner/diagnostics字节码主体与上一包一致，权限保持。
- **集中构建**：完整Gradle2分36秒成功，packageDebug实际执行；本批assembleDebug调用1次。基线/定向仅测试，纯计划和收尾文档没有构建。相关editor-assist:test为NO-SOURCE，模型由主App原/新增用例覆盖，不报额外共享测试数量。
- **原生图**：[3张预览](python-semantic-preview/README.md)已逐张查看，来自实际Main/Sora视图和真实后端响应；本轮不创建浏览器视觉样稿，不引用旧浏览器检查数字。
- **设备边界**：{limits} 不声称Android设备已实际返回Jedi结果，也不声称整个P2/P3完成。

## 安装包

[{apk}](../dist/{apk})

- 大小：{r['bytes']}字节，{r['bytes']/1048576:.2f}MiB；较上一包增加{r['apk_size_delta_bytes']}字节（{r['apk_size_delta_bytes']/1048576:.2f}MiB），包含分析库、源码/字节码及类型桩。
- SHA256：`{r['sha256']}`
- 原签名SHA256：`{r['signer_sha256']}`
- 包名com.codepocket.ide，版本0.2.0/code2，min24/target34，arm64-v8a；签名/CRC/16KB通过，{r['old_apks_preserved']}个旧APK摘要保持。

## 限制与新登记

P2-A只把当前未保存文件和已保存内部项目模块送入静态分析；其他标签尚未保存的内容不参与。SAF私有镜像可能过期，本批禁用其项目导入，候选/参数来源标明当前文件/标准库，不读取提供方写回路径。Jedi并非完整Python解释与全部第三方类型推断，无法推断时可无结果；未获得语义结果则保留明确词法/资料参考，不制造成员。

语义正文上限为45000个UTF-16单位。另登记源码观察：超过上限时的显式成员请求可能仍显示“正在分析”提示，实际不会提交语义请求；这是非阻塞反馈文案问题，留P2-B统一修正，不为其改写本批已验证APK。

独立服务配置冷启动30秒、常规分析8秒看门狗；客户端绑定5秒、响应35秒等待上限均在后台，编辑和现有词法窗口先行。进程启动/真实中断及设备耗时未实测。:analysis与:python同App UID，进程隔离用于资源与故障边界，不宣称权限沙箱。

{warning}

原始日志保留两次新增测试编译形式错误及首轮75项中28失败：主要为Robolectric空Binder/ComponentName回调触发非空参数检查，另两项夹具未先创建文件；生产回调补安全失败处理，夹具使用Workspace.create后write，断言保留。随后75项、78项及最终79项通过；补片段精确触发及no-op过滤后完整772项通过。没有删除旧测试、删断言或新增跳过来获得通过。

## 接续

本批代码、主机和APK验证完成。下一批P2-B补多个未保存文件及SAF新鲜快照，再按计划推进C++真实语义和本地编译评估；不回到连续UI边角润色。原P2/P3保留。仅本地提交，未推送、未发布、未创建自动任务。

官方依据：[Jedi Script与线程约束](https://jedi.readthedocs.io/en/latest/docs/api.html)、[Chaquopy包提取/数据与字节码](https://chaquo.com/chaquopy/doc/current/android.html#extractpackages)。精确实施和失败/通过记录以verification/python-semantic-integration-2026-10-26/为准。
'''
(root/'docs'/report).write_text(text,encoding='utf-8')
resume=root/'docs/RESUME.md';text=resume.read_text(encoding='utf-8')
start=text.index('## 正在执行：P2-A');end=text.index('## 最近完成：只读详情',start)
text=text[:start]+f'''## 当前：P2-A Python真实补全代码/主机/APK验证完成（2026-10-26）

- [计划书](P2_IMPLEMENTATION_PLAN_2026-10-26.md)已先提交9765aa3；[本批报告]({report})：{scope}
- {counts} Python源码63、APK原运行39/真实语义19、主机Shell4/片段14、原签名及16KB通过；本批集中构建一次。
- dist/{apk}，{r['bytes']}字节，SHA256 {r['sha256']}；旧87包与10原生库保持，原runner/diagnostics主体不变。
- [3张原生图](python-semantic-preview/README.md)已查看。Main/Sora测试回放真实后端结果、APK执行实际依赖字节码，与Android服务实机运行分开记录。
- {limits}
- {warning}
- 下一批P2-B：多未保存文件上下文与SAF新鲜快照。UI零碎润色停止，已完成成果保留，不再制作第二个实验App。

'''+text[end:];resume.write_text(text,encoding='utf-8')
readme=root/'README.md';text=readme.read_text(encoding='utf-8');start=text.index('> 当前推进：');end=text.index('## 历史主机整合验收',start)
text=text[:start]+f'''> 当前按[P2计划](docs/P2_IMPLEMENTATION_PLAN_2026-10-26.md)推进核心能力，零碎UI润色暂停。Python真实补全首批已完成代码/主机/APK验证；设备运行验收仍后置，下一批补多文件未保存上下文与SAF快照。以下历史记录保留当时含义，当前事实以本批报告和RESUME为准。

**2026-10-26：Python真实补全与参数接入。** {scope}

- [最新Debug APK](dist/{apk})（{r['bytes']/1048576:.2f}MiB，原签名，arm64-v8a）。
- [结果与限制](docs/{report}) · [3张原生预览](docs/python-semantic-preview/README.md)：主771通过/1旧跳过，定向79通过，Lint0错误；Python源码63、APK原运行39/语义19及运行专项通过。
- 已验证内部项目已保存模块和当前未保存正文；其他未保存标签、SAF项目快照、C++语义留后续。新增1条非阻塞触摸监听Lint提示已登记。
- Android分析服务/JNI实际运行、IME/键鼠/API24/卓易通/性能及远端CI未验收；[交接](docs/RESUME.md)。原保存、Sora、真实Python/C++运行和PTY保持。

'''+text[end:]
text=text.replace('python -m unittest discover -s tests -v\n','python -m pip install --require-hashes -r app/requirements.txt\npython -m unittest discover -s tests -v\n',1)
readme.write_text(text,encoding='utf-8')
progress=root/'docs/DEVELOPMENT_PROGRESS.md';text=progress.read_text(encoding='utf-8')
start=text.index('## 当前：P2-A');end=text.index('## 最近完成：UI视觉',start)
text=text[:start]+f'''## 当前：P2-A Python真实补全已完成代码/主机/APK验证（2026-10-26）

[实施计划](P2_IMPLEMENTATION_PLAN_2026-10-26.md)先于代码落地；[本批报告]({report})、[最新APK](../dist/{apk})。{scope}

{counts} 源码Python63、实际APK原运行39/语义19、Shell4、片段14通过；设备分析调用未实测。下一批P2-B补多个未保存文件和SAF新鲜上下文，随后原C++语义/本地编译等P2/P3；不恢复零碎UI润色。

'''+text[end:];progress.write_text(text,encoding='utf-8')
policy=root/'docs/DEVELOPMENT_BATCH_POLICY.md';text=policy.read_text(encoding='utf-8')
text=text.replace('[只读详情合并批次APK](../dist/CodePocket-details-batch-2026-10-26-debug.apk)',f'[Python语义接入批次APK](../dist/{apk})')
text=text.replace('继续UI视觉优先、未实现新功能暂缓、实机统一后置的约定。','按当前授权的P2核心功能推进；实机统一后置，未列入当批的功能继续暂缓。')
policy.write_text(text,encoding='utf-8')
plan=root/'docs/P2_IMPLEMENTATION_PLAN_2026-10-26.md';text=plan.read_text(encoding='utf-8')
if '## 七、本轮执行结果' not in text:text+=f'\n## 七、本轮执行结果\n\nP2-A已完成代码、主机和APK验证，详见[本批报告]({report})。{counts} APK实际依赖/语义字节码19项通过，Android端服务运行仍待实机，不将本状态扩大为全部P2完成。下一工作包为P2-B。\n'
plan.write_text(text,encoding='utf-8')
audit=root/'docs/UI_SCOPE_AUDIT_2026-10-24.md';text=audit.read_text(encoding='utf-8')
text=text.replace('范围按用户最新指令确定：UI视觉优先，允许适度装饰；保留已有项目和真实运行；未实现功能先不接入，新发现的非阻塞问题登记。','范围按用户最新指令确定：零碎UI润色暂停，已授权按P2计划推进Python真实补全；保留已有项目和真实运行，其他未列入当批的功能暂缓，新发现的非阻塞问题登记。')
text=text.replace('[只读详情合并批次](UI_DETAILS_BATCH_2026-10-26.md)',f'[Python真实补全接入批次]({report})',1)
text=text.replace('Jedi/clangd深语义未接入，代码折叠等额外能力不作为本轮新交付','Python/Jedi成员与真实参数已接主App，证据见P2_PYTHON_SEMANTICS_2026-10-26.md及新21项；Android实际分析未实测，C++/clangd及其他额外能力仍后置')
text=text.replace('Git/插件/账号、Jedi/clangd等深语义及原P2/P3','Git/插件/账号、C++/clangd及其余P2/P3，Python多未保存标签/SAF项目快照')
if 'Python补全接入新增ClickableViewAccessibility' not in text:text=text.replace('## 登记与验收边界','## 登记与验收边界\n\n- Python补全接入新增ClickableViewAccessibility一条：用于冻结用户已操作的候选，触摸监听返回false保留ListView原处理；不屏蔽规则，设备无障碍待验收。当前主18/Sora1警告、零错误。',1)
audit.write_text(text,encoding='utf-8')
native=root/'docs/python-semantic-preview';index='# Python真实补全接入原生预览\n\n已逐张查看以下3图。来自主机实际Main/Sora和真实Jedi响应，主机测试替换Android传输；不是设备进程调用成功的截图。原生窗口样式沿用已有实现，仅来源和实际候选/参数内容变化。\n\n'
for path in sorted(native.glob('*.png')):index+=f'## {path.stem}\n\n![{path.stem}]({path.name})\n\n'
(native/'README.md').write_text(index.rstrip()+'\n',encoding='utf-8')
print('Updated plan execution status, batch report, handoff, progress, README, policy, scope and previews')
