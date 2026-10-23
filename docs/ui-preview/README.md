# CodePocket 可点击工作台设计预览

打开 [index.html](index.html)，可选择手机竖屏/横屏、平板竖屏/横屏和深浅主题。29种界面场景覆盖工作台、菜单、表单、设置、补全及终端状态。使用本地CSS/JS/SVG资源，可以离线查看。

**所有文件和输出均为设计示例，不执行代码，不是APK或Android设备截图。** 原生App继续使用真实Python/Wandbox/单一PTY；实际界面见[原生窗口预览](../auxiliary-surfaces-preview/README.md)和[最新APK](../../dist/CodePocket-audit-2026-10-24-debug.apk)。

2026-10-24在原样稿基础上同步全高Activity Bar、上方文件树/编辑器、横跨工作区的底部终端和中性状态栏；移除编辑区重复更多、合并文件夹操作。增加目录示例层级、设置分类图标、简洁路径区与柔和选中短线；不引入荧光/光晕/夸张渐变。未实现功能仍暂缓，样稿不作为功能交付。

本轮Playwright真实浏览器：29场景×4窗口×2主题=232次场景/横向边界检查，加13项交互检查全部通过，未发现页面脚本错误。另核对360px宽度、减少动画、目录折叠、设置取消/应用及终端入口。检查的是浏览器样稿，不代表AndroidIME/键鼠/卓易通/FPS通过。

| 窗口 | 深色工作台 | 浅色工作台 | 深色设置 | 浅色设置 |
| --- | --- | --- | --- | --- |
| 平板横屏 | [查看](workbench-screenshots/tablet-dark-workbench.png) | [查看](workbench-screenshots/tablet-light-workbench.png) | [查看](workbench-screenshots/tablet-dark-settings.png) | [查看](workbench-screenshots/tablet-light-settings.png) |
| 平板竖屏 | [查看](workbench-screenshots/tabletPortrait-dark-workbench.png) | [查看](workbench-screenshots/tabletPortrait-light-workbench.png) | [查看](workbench-screenshots/tabletPortrait-dark-settings.png) | [查看](workbench-screenshots/tabletPortrait-light-settings.png) |
| 手机横屏 | [查看](workbench-screenshots/phoneLandscape-dark-workbench.png) | [查看](workbench-screenshots/phoneLandscape-light-workbench.png) | [查看](workbench-screenshots/phoneLandscape-dark-settings.png) | [查看](workbench-screenshots/phoneLandscape-light-settings.png) |
| 手机竖屏 | [查看](workbench-screenshots/phone-dark-workbench.png) | [查看](workbench-screenshots/phone-light-workbench.png) | [查看](workbench-screenshots/phone-dark-settings.png) | [查看](workbench-screenshots/phone-light-settings.png) |

前版96次浏览器检查及旧截图保留为历史，不用它们替代本轮结果。Lucide0.468.0固定资源与许可证保持，见[third-party/lucide](../../third-party/lucide/UPSTREAM.json)。
