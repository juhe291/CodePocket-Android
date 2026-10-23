# 诊断浮层与小窗口详情

实际MainActivity/Sora主机Native Graphics绘制，不是Android设备截图。布局/长文本场景明确使用UI夹具；真实结构诊断来自当前本地括号分析器，不代表完整Python/C++语义或编译。

- [当前工作台与真实结构错误下划线](phone-actual-structural-code.png)
- [真实括号结构诊断](phone-actual-structural-diagnostic.png)
- [290dp高、大字体仍可显示浮层](compact-large-diagnostic-hover.png)
- [220dp高的完整详情兜底](phone-tiny-large-diagnostic-details.png)
- [小窗滚动到末条错误](phone-tiny-large-diagnostic-scrolled.png)

[本批修复与真实验证边界](../UI_DIAGNOSTIC_LAYOUT_2026-10-24.md)。设备IME、键鼠、卓易通及帧率仍后置。
