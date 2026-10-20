# CodePocket 完整可点击设计预览

> 本轮终端原生布局以[原生控件预览](../terminal-preview/README.md)为准。本目录仍保留原完整交互式设计样稿，终端示例没有同步成最终原生实现。两者均不表示设备运行通过。

打开 [index.html](index.html)，可选择手机/平板、深浅主题，并从“界面场景”查看工作台、菜单、表单、选择器、设置、补全和终端状态。主页面文件树、标签、终端入口、设置搜索与配色可以点击。

**所有文件和输出均为设计示例，不执行代码，不是 APK 或 Android 实机截图。** 原生 App 继续连接真实 Python/Wandbox/PTY 后端。此页面用于检查设计语言，不能替代 Android 输入法、键鼠或帧率验收。

浏览器已执行 24 场景 × 2 设备布局 × 2 主题 = 96 次打开与横向边界检查，并另外验证设置取消、文件标签切换、根目录折叠和浅色终端配色。没有把这些检查称为 Android 设备通过。

| 预览 | 截图 |
| --- | --- |
| 平板深色工作台 | [浏览器截图](screenshots/tablet-dark.png) |
| 手机浅色工作台 | [浏览器截图](screenshots/phone-light.png) |
| 平板分类设置 | [浏览器截图](screenshots/tablet-dark-settings.png) |
| 手机设置分类首页 | [浏览器截图](screenshots/phone-light-settings.png) |

Lucide 0.468.0 图标使用仓库内固定 SVG 资源，App 使用本地 VectorDrawable。来源、哈希及完整许可证见 [third-party/lucide](../../third-party/lucide/UPSTREAM.json)。
