# P2-A Python真实补全接入 · 2026-10-26

先完成[实施计划书](P2_IMPLEMENTATION_PLAN_2026-10-26.md)，本地计划提交9765aa3，再执行本批实际代码、测试和集中构建。Python真实成员/标准库/已保存内部模块补全与函数参数接入同一主App；当前未保存正文参与，来源明确，失败保留词法/内置参考。 零碎UI润色暂停，沿用原原生工作台、Sora、保存/恢复、运行和PTY。

## 四组实际修改

| 工作包 | 实现与边界 |
| --- | --- |
| 静态后端 | 复用封存semantics.py及15项用例，固定Jedi0.20.1/Parso0.8.7，串行静态Script分析；补保存模块刷新、并发、UTF-16/CRLF签名及无变化编辑回归，不执行项目顶层代码 |
| 独立进程/依赖 | 非导出PythonAnalysisService运行于:analysis，原执行:python和PTY保持；有界最新请求、连接归属、取消/关闭、看门狗。离线hash固定wheel，Jedi/Parso提取到文件系统，四份许可和新设置通知入口 |
| 生产候选/参数 | Main原文档/版本/组合/光标门控继续生效，后台语义结果升级原窗口；先显示词法/内置参考，用户已操作的候选或形式不被迟到结果替换。真实种类、签名/活动参数及准确替换范围复用原Sora事务 |
| 兼容与验证 | 旧文件结果丢弃，失败/空结果区分，精确片段触发优先、占位符/撤销/保存保留；新增真实Jedi响应产物供Main/Sora测试，另用Messenger验证连接故障与关闭；不以回放冒称Android服务运行 |

## 证据分层

- **真实后端执行**：本轮恢复后的15项基线通过；最终Python源码63项通过，其中语义19项，原执行/诊断/图标等44项保持。
- **主工作台接入**：主App772项：771通过、0失败、1旧跳过；旧751项身份/跳过集合保留，新增21项。定向79项全部通过。Lint0错误，主18/Sora1警告。 新21项包含协议7、实际Main/Sora8、服务/绑定6。Main测试使用真实Jedi生成且校验后端hash的11组响应，仅替换Android传输；有接受→实际保存→撤销、类/模块、同名print参数、切文件旧结果、键盘选择冻结、失败/空结果及片段链路。
- **实际APK内容**：强制用APK提取的semantics.pyc及Jedi/Parso字节码执行19项语义测试，0失败/错误；核对实际extract_packages、typeshed/grammar313、四份许可、DEX中的客户端/服务与非导出的:analysis声明。不是从主机site-packages替代打包依赖。
- **原运行链**：本轮实际APK运行/诊断字节码39、主机Shell4、Python/C++17片段各7项通过。10个原生库、原runner/diagnostics字节码主体与上一包一致，权限保持。
- **集中构建**：完整Gradle2分36秒成功，packageDebug实际执行；本批assembleDebug调用1次。基线/定向仅测试，纯计划和收尾文档没有构建。相关editor-assist:test为NO-SOURCE，模型由主App原/新增用例覆盖，不报额外共享测试数量。
- **原生图**：[3张预览](python-semantic-preview/README.md)已逐张查看，来自实际Main/Sora视图和真实后端响应；本轮不创建浏览器视觉样稿，不引用旧浏览器检查数字。
- **设备边界**：其他未保存标签、SAF项目新鲜快照留P2-B；C++仍为原词法/内置参考与Wandbox运行。Android分析服务/JNI实际执行、冷启动时延/资源、IME/键鼠/无障碍、API24、卓易通及远端CI仍未验收，实机继续后置。 不声称Android设备已实际返回Jedi结果，也不声称整个P2/P3完成。

## 安装包

[CodePocket-python-semantics-2026-10-26-debug.apk](../dist/CodePocket-python-semantics-2026-10-26-debug.apk)

- 大小：32431159字节，30.93MiB；较上一包增加5749966字节（5.48MiB），包含分析库、源码/字节码及类型桩。
- SHA256：`42866696f87dbd42651d910ebac1865f954c40f65fbf428618e339c8127a21d5`
- 原签名SHA256：`d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`
- 包名com.codepocket.ide，版本0.2.0/code2，min24/target34，arm64-v8a；签名/CRC/16KB通过，87个旧APK摘要保持。

## 限制与新登记

P2-A只把当前未保存文件和已保存内部项目模块送入静态分析；其他标签尚未保存的内容不参与。SAF私有镜像可能过期，本批禁用其项目导入，候选/参数来源标明当前文件/标准库，不读取提供方写回路径。Jedi并非完整Python解释与全部第三方类型推断，无法推断时可无结果；未获得语义结果则保留明确词法/资料参考，不制造成员。

语义正文上限为45000个UTF-16单位。另登记源码观察：超过上限时的显式成员请求可能仍显示“正在分析”提示，实际不会提交语义请求；这是非阻塞反馈文案问题，留P2-B统一修正，不为其改写本批已验证APK。

独立服务配置冷启动30秒、常规分析8秒看门狗；客户端绑定5秒、响应35秒等待上限均在后台，编辑和现有词法窗口先行。进程启动/真实中断及设备耗时未实测。:analysis与:python同App UID，进程隔离用于资源与故障边界，不宣称权限沙箱。

新增ClickableViewAccessibility提示位于CompletionPopup.kt触摸观察器：监听返回false，由原ListView继续处理点击；无规则屏蔽，设备无障碍仍需验收。按用户非阻塞先登记约定保留，不为此另打包。上一批UnusedResources及旧焦点/关键词状态/未确认helper导入问题继续登记。

原始日志保留两次新增测试编译形式错误及首轮75项中28失败：主要为Robolectric空Binder/ComponentName回调触发非空参数检查，另两项夹具未先创建文件；生产回调补安全失败处理，夹具使用Workspace.create后write，断言保留。随后75项、78项及最终79项通过；补片段精确触发及no-op过滤后完整772项通过。没有删除旧测试、删断言或新增跳过来获得通过。

## 接续

本批代码、主机和APK验证完成。下一批P2-B补多个未保存文件及SAF新鲜快照，再按计划推进C++真实语义和本地编译评估；不回到连续UI边角润色。原P2/P3保留。仅本地提交，未推送、未发布、未创建自动任务。

官方依据：[Jedi Script与线程约束](https://jedi.readthedocs.io/en/latest/docs/api.html)、[Chaquopy包提取/数据与字节码](https://chaquo.com/chaquopy/doc/current/android.html#extractpackages)。精确实施和失败/通过记录以verification/python-semantic-integration-2026-10-26/为准。
