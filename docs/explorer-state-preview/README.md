# 文件树未保存状态原生预览

真实 MainActivity / Sora 的 Robolectric Native Graphics 主机绘制；浅色和深色图片绘入了实际父容器背景。输入使用真实 InputConnection，保存冲突与重试使用实际文件。图片不是设备截图或 Android 输入法验收证据。

| 情况 | 图片 |
| --- | --- |
| 平板编辑前 | [文件树](tablet-clean-tree.png) |
| 平板键入后 | [未保存小圆点](tablet-dirty-tree.png) |
| 平板保存后 | [标记清除](tablet-saved-tree.png) |
| 手机浅色双倍字体、长路径 | [未保存状态](phone-light-large-dirty-tree.png) |
| 根目录和末级目录折叠重开、重新选中文件 | [状态保留](phone-light-large-restored-tree.png) |
| 手机保存后再开抽屉 | [已保存状态](phone-light-large-saved-tree.png) |
| 真实外部保存冲突 | [未保存标记保留](tablet-light-conflict-tree.png) |
| 解除冲突并实际重试 | [标记清除](tablet-light-recovered-tree.png) |

圆点只是现有当前文件未保存状态的展示，不是新增操作。目录选中与编辑文件分别管理，折叠目录不关闭文件。没有扫描或修改非当前文件的恢复草稿。

[本批测试与限制](../UI_EXPLORER_STATE_2026-10-26.md) · [四类窗口设计示例](../ui-preview/README.md)。
