# CodePocket 可点击工作台设计预览

打开 [index.html](index.html)，可选择手机竖屏/横屏、平板竖屏/横屏和深浅主题。37种界面场景覆盖工作台、菜单、表单、设置、补全及终端状态。使用本地CSS/JS/SVG资源，可以离线查看。

**所有文件和输出均为设计示例，不执行代码，不是APK或Android设备截图。** 原生App继续使用真实Python/Wandbox/单一PTY；实际诊断交互见[诊断原生预览](../diagnostic-ui-preview/README.md)与[标题原生预览](../heading-polish-preview/README.md)与[载入原生预览](../startup-ui-preview/README.md)与[命令原生预览](../command-ui-preview/README.md)与[终端工具区预览](../terminal-dock-preview/README.md)与[设置原生预览](../settings-consistency-preview/README.md)与[标签状态原生预览](../tab-stability-preview/README.md)与[导航原生预览](../tab-navigation-preview/README.md)与[最新路径/底栏原生预览](../path-status-preview/README.md)，代码配色见[原生配色预览](../token-theme-preview/README.md)与[窗口预览](../search-entry-preview/README.md)和[最新APK](../../dist/CodePocket-context-batch-2026-10-26-debug.apk)。

2026-10-24在原样稿基础上同步全高Activity Bar、上方文件树/编辑器、横跨工作区的底部终端和中性状态栏；移除顶部重复项目搜索与编辑区重复更多、合并文件夹操作。增加目录示例层级、设置分类图标及标题底座、细分隔线、简洁路径区与柔和选中短线；不引入荧光/光晕/夸张渐变。未实现功能仍暂缓，样稿不作为功能交付。

本轮Playwright真实浏览器：37场景×4窗口×2主题=296次场景/横向边界检查，加62项交互与20项实际配色检查全部通过，未发现页面脚本错误。新增载入/失败状态及示例恢复导航，编辑菜单九项命令分组及320px快捷键排版及终端四入口/控制键展开收起检查；另核对360px宽度、减少动画、目录折叠、设置取消/应用及终端入口。检查的是浏览器样稿，不代表AndroidIME/键鼠/卓易通/FPS通过。

| 窗口 | 深色工作台 | 浅色工作台 | 深色设置 | 浅色设置 |
| --- | --- | --- | --- | --- |
| 平板横屏 | [查看](workbench-screenshots/tablet-dark-workbench.png) | [查看](workbench-screenshots/tablet-light-workbench.png) | [查看](workbench-screenshots/tablet-dark-settings.png) | [查看](workbench-screenshots/tablet-light-settings.png) |
| 平板竖屏 | [查看](workbench-screenshots/tabletPortrait-dark-workbench.png) | [查看](workbench-screenshots/tabletPortrait-light-workbench.png) | [查看](workbench-screenshots/tabletPortrait-dark-settings.png) | [查看](workbench-screenshots/tabletPortrait-light-settings.png) |
| 手机横屏 | [查看](workbench-screenshots/phoneLandscape-dark-workbench.png) | [查看](workbench-screenshots/phoneLandscape-light-workbench.png) | [查看](workbench-screenshots/phoneLandscape-dark-settings.png) | [查看](workbench-screenshots/phoneLandscape-light-settings.png) |
| 手机竖屏 | [查看](workbench-screenshots/phone-dark-workbench.png) | [查看](workbench-screenshots/phone-light-workbench.png) | [查看](workbench-screenshots/phone-dark-settings.png) | [查看](workbench-screenshots/phone-light-settings.png) |

本批另核对两种既有运行方式、代码和输入第三方提示及无效分屏入口不展示；[终端设置示例](workbench-screenshots/tablet-run-modes.png)为设计预览，126张截图已更新。

新增“未保存标签”示例，圆点固定占位且与示例底栏一致；[手机深色](workbench-screenshots/phone-dark-dirty-tabs.png) · [平板浅色](workbench-screenshots/tablet-light-dirty-tabs.png)。检查包含切换前后名称/几何不变、标记显示及状态还原。

本批增加明确选择文件时的标签定位与长名称省略：[手机多标签](workbench-screenshots/phone-overflow-tabs.png) · [平板长名称](workbench-screenshots/tablet-overflow-long-name.png)。这些仍是设计示例；原生输入/保存与窗口行为见本批报告。

新增“保存失败状态”及大字体行高示例：[手机浅色大字体设计](workbench-screenshots/phone-large-status-design.png)。完整路径提示、失败原因说明、状态还原、缩窗当前标签和圆点顺序均有浏览器检查；本页不执行文件保存或程序。

前版96次浏览器检查及旧截图保留为历史，不用它们替代本轮结果。Lucide0.468.0固定资源与许可证保持，见[third-party/lucide](../../third-party/lucide/UPSTREAM.json)。

2026-10-26新增“紧凑编辑区”：按实际样稿高度隐藏路径/底栏，示例窗口240px；打开嵌套命令保持高度，切回常规场景恢复路径而不更换代码元素。[平板深色](workbench-screenshots/tablet-dark-compact.png) · [手机浅色](workbench-screenshots/phone-light-compact.png)。不模拟真实键盘，也不表示Android输入法验收通过。[原生测量与待办](../UI_COMPACT_CAPTIONS_2026-10-26.md)。

2026-10-26同步文件树“未保存”圆点与完整说明：沿用既有未保存标签/保存失败场景，位置固定，失败保留，清除时名称和菜单不移动。[平板深色示例](workbench-screenshots/tablet-dark-dirty-tabs.png) · [原生证据与限制](../UI_EXPLORER_STATE_2026-10-26.md)。

2026-10-26同步分类标题、固定返回和独立滚动；主菜单返回工作台分类，终端会话返回终端分类，尺寸返回布局与输入，直接桌面菜单不加重复返回。新增全局终端/尺寸场景：[手机短窗口示例](workbench-screenshots/phone-menu-return-context.png) · [终端会话示例](workbench-screenshots/tablet-terminal-session-context.png) · [原生证据](../UI_MENU_CONTEXT_2026-10-26.md)。

2026-10-26按合并批次同步文件路径标题、资源管理器标题/分组、会话/配色当前项及返回、项目搜索范围/自适应布局和无匹配场景；浏览器手机搜索按实际内容区换行。[原生预览](../context-batch-preview/README.md) · [真实测试与限制](../UI_CONTEXT_BATCH_2026-10-26.md)。
