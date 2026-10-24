# 原生标签状态预览

实际Main/Sora操作后的Robolectric Native Graphics主机绘制。只截取标签与路径区，不是设备截图；不模拟程序输出。

- [手机 · 干净](phoneTypingAndSavingKeepTabCoordinates-clean.png)
- [手机 · 未保存](phoneTypingAndSavingKeepTabCoordinates-dirty.png)
- [手机 · 已保存](phoneTypingAndSavingKeepTabCoordinates-saved.png)
- [平板浅色大字体/长文件名 · 干净](tabletLightLargeLongNameKeepsCloseTargetAndFullPath-clean.png)
- [平板浅色大字体/长文件名 · 未保存](tabletLightLargeLongNameKeepsCloseTargetAndFullPath-dirty.png)
- [平板浅色大字体/长文件名 · 已保存](tabletLightLargeLongNameKeepsCloseTargetAndFullPath-saved.png)

[修复、像素核验与测试](../UI_TAB_STABILITY_2026-10-24.md)。未保存圆点固定占位，文件名/关闭目标坐标稳定；完整路径与辅助说明保留。实机输入和帧率后置。
