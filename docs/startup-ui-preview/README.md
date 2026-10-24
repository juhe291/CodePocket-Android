# 原生工作区载入状态

实际MainActivity与真实IO/ContentResolver路径的主机Robolectric Native Graphics绘制，不是设备或Android系统启动画面截图。载入测试先短暂阻塞真实IO，释放后验证真实文件内容；失败图是无提供方的URI导致失败后的根界面，对话框恢复操作另由回归实际点击。

- [手机深色载入](phone-dark-loading.png)
- [平板浅色载入](tablet-light-loading.png)
- [手机大字体失败状态](phone-large-failed-workspace.png)

[修改、测试及边界](../UI_STARTUP_STATES_2026-10-24.md)。IME、键鼠、卓易通、系统冷启动及帧率仍未实机验收。
