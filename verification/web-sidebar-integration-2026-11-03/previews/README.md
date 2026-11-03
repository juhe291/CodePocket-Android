# 本批原生视觉检查

图像由 WebAuxiliaryIntegrationTest 的真实 Main、正式 Sora、工作台原生 Views 与 Markwon TextView 绘制。当前三图均已查看，后续集中全量会以冻结的相同代码重新生成并核对摘要。

- tablet-dark-left-browser.png：1100×780深色，左侧重挂载，活动栏与编辑/底部区域保持；输入框保留尚未提交查询。
- tablet-light-native-markdown.png：1100×780浅色，原文件树、实际未保存Markdown及右侧原生标题/强调/行内代码/列表。
- phone-large-bing-focus.png：360×640、1.5倍字体，辅助栏占活动栏右侧312px，关闭回原Sora正文。

WebView由主机Shadow实现，空白网页内容不等于Android Chromium联网成功。图中PTY启动不可用是主机不具有Android Shell环境；没有据此修改正式运行逻辑。设备窗口/真实网页/API24/IME验收仍后置。

首轮手机半宽缺陷图分别完整保留在web-sidebar-preflight的details-first-previews和details-fixed-previews；生产修复和新增真实宽度/可见性断言后的图在visibility-fixed-previews。

另登记既有非阻塞项：原Main把所有非.py文件的状态语言都标成C++，本轮Markdown仅复用了原编辑入口；该语言分派问题随原P3-D更多语言一起处理，不在网页批次扩展运行语言。新Web代码的两处旧WebSettings注解编译提示、已有Kotlin警告及集中Lint提示以实际报告为准。
