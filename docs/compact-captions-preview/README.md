# 短窗口的原生布局对照

本页为真实 MainActivity / Sora 的 Robolectric Native Graphics 主机绘制。窗口由主机明确约束；API34 Insets 使用一致的测试 Shadow 提供。没有安装或显示真实安卓输入法，也不表示设备终端运行通过。

| 情况 | 改前 | 改后 |
| --- | --- | --- |
| 手机240dp高、双倍字体、建模输入遮挡 | [路径占位](before-phone-large-short-input.png) | [腾出代码空间](phone-large-short-input.png) |
| 平板240dp高、浅色双倍字体、建模输入遮挡 | [路径占位](before-tablet-light-large-short-input.png) | [腾出代码空间](tablet-light-large-short-input.png) |

- [平板恢复800dp高度](tablet-light-large-restored.png) · [浮动Insets不压缩窗口](tablet-light-large-floating-insets.png)
- [359dp边界](medium-height-359.png) · [360dp边界](medium-height-360.png)
- [200dp完整错误详情](diagnostic-200-full-details.png) · [220dp真实诊断浮层](diagnostic-220-hover.png)

平板被动高度过渡的主机焦点问题已登记；短窗口输入前明确请求编辑器焦点，此后输入/保存/恢复保焦有断言，不能据此声称无需点选的过渡自动保焦通过。缓冲、磁盘保存、路径及会话选择保持有独立断言。截图里终端的不可用提示来自主机无法启动 Android Shell，不是伪造程序输出。

[实际结果与待办](../UI_COMPACT_CAPTIONS_2026-10-26.md) · [可点击设计样稿](../ui-preview/README.md)。
