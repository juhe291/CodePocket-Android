# 二级窗口收尾与视觉清单审计（2026-10-24）

基于62902b2继续现有项目。按最新指令，UI视觉第一，未实现功能暂缓，非阻塞问题先登记。允许小图标、细线、柔和底色与类型色；不用荧光、光晕或大块装饰。

## 本批改动

- 工作区及草稿恢复：图标标题与当前路径/草稿数量分层，限制实际窗口宽度；原目录授权、工作区切换和草稿导出处理器保持。
- 代码字号：增加真实字号预览，调整按钮保持至少48dp；即时保存行为不变，保留同一编辑文本与撤销缓冲。
- 快捷键、使用说明、错误详情及设置许可：统一可滚动、可选择正文，长说明不再挤在普通提示框中；诊断复制继续复制完整原文。
- 共享选择列表按文件类型使用柔和图形与颜色，最近目录使用文件夹图形，不增加重复入口。
- 浅色Sora基础配色：收敛默认主题的高饱和绿/紫，并在主题挂接后应用边框/行号/基础色，避免挂接初始化覆盖。已加载TextMate动态令牌、词法装饰规则、光标、选区和实际文本保持原机制；预览中部分动态令牌仍偏亮，登记后续评估，不宣称整体着色已统一。

## 界面清单及状态

| 界面 | 现有源码入口/共享组件 | 本轮状态 |
| --- | --- | --- |
| 主工作台/四种窗口 | MainActivity、MainToolbar、ActivityBar、StatusBar | 全高导航/上层边栏/横跨底部面板；补充横屏浅色、竖屏平板深色、大字体手机绘制 |
| 文件树及目录菜单 | MainActivity、ExplorerRow、WorkbenchStyle | 前批统一；根目录收起、新建/移动/删除安全链保留 |
| 名称/移动/删除 | MainActivity、WorkbenchForms | 前批独立路径区和错误保留；不重做事务 |
| 主菜单及编辑/运行分组 | MainToolbar、WorkbenchStyle | 前批单一全局更多；窄窗口隐藏低频入口 |
| 当前文件查找/替换 | SearchDialog | 前批选项/反馈同行；保留真实替换与撤销 |
| 项目搜索/跳转行 | MainActivity、WorkbenchForms | 前批结果位置/代码分层与窗口限宽 |
| 工作区/草稿恢复 | MainActivity、WorkbenchForms | 本批图标标题、位置或数量副标题；保持授权/导出 |
| 设置分类/搜索/选择器 | SettingsDialog | 七类、宽窗口分栏、草稿取消/应用；已有主题示意保持 |
| 快捷键/帮助/许可 | MainActivity、SettingsDialog | 本批完整可滚动/可选择正文 |
| 代码字号 | MainActivity | 本批真实字号样本，按钮沿用原即时保存逻辑 |
| 片段/补全/参数参考 | MainActivity、共享编辑器浮层 | 既有统一外观和输入/光标保护保持；深度语义未接入 |
| 诊断详情 | MainActivity | 本批显式详情可复制完整原文；红线点击浮层的2项失败另记 |
| 终端/会话/配色菜单 | PanelToolbar、TerminalChrome、MainActivity | 前批动作分组/低边框控制键；单一真实PTY、原会话与配色保留 |
| 运行确认/退出保存/权限错误 | MainActivity、WorkbenchStyle | 共用对话框样式；必要安全说明与确认不删 |
| 系统目录选择器/输入法/文本菜单 | Android系统 | 保持系统机制；不声称已自定义或实机通过 |
| 网页辅助栏/Git/插件/账号/深度语义 | 隐藏结构/原需求/暂存研究 | 按最新要求暂缓，不放假功能按钮 |

## 主机验证与边界

新增8项实际Main/Sora原生测试：工作区取消授权、草稿精确导出、字号与同一编辑缓冲、完整许可可选择/关闭、显式诊断完整复制，以及三种补充窗口结构。截图来自Robolectric Native Graphics，并非设备截图；示例源码用于UI测试，未把它描述成运行结果。

首轮2项新增失败是测试未等待AlertDialog按钮异步回调，已在测试中等待主线程处理，保留关闭/剪贴板原断言。定向复测通过。后续配色断言还发现主题挂接会重置基础色，修正挂接后覆盖顺序；预览等待真实语法加载，不替换运行或分析结果。最终72项定向测试通过。首轮完整脚本因PowerShell把原生标准错误当作终止错误而提前退出；保留原日志，修正记录方式后重跑，不把中断当作完整构建。

完整测试、Lint、Debug打包、APK签名/对齐及主机Python/Shell/片段实际结果在本批results.json汇总。只有成功执行后才登记通过。

此前两项诊断红线/浮层用例仍保留失败断言，按用户视觉优先要求后置；不以显式详情通过代替该点击路径。未新增设备IME、蓝牙键鼠、Harmony卓易通、API24、帧率或远端Wandbox/Actions的通过结论。

## 最终实际结果

- 主App674项：671通过、2项此前登记的诊断浮层失败、1项旧Windows符号链接跳过；相对上一批没有新增失败。新增8项及最终72项定向回归通过。
- 最终完整命令2分7秒，返回1（两项保留断言失败）；packageDebug/assembleDebug确实执行成功。Lint0错误；主App17警告、Sora1警告，主App比上一批多1个SetTextI18n，登记后续资源化，不当成旧警告。
- 源码Python44、实际APK字节码39、主机Shell启动4、真实Python/C++17片段各7、原签名及16KB对齐检查通过。Windows主机片段不代表Android运行或Wandbox远端通过。
- 65个旧APK哈希保持，10个原生库及Python app.imy相对上一包完全相同；本批不改变原生PTY/运行归档。
- 主机实际绘制目视检查：手机字号、草稿、工作区、显式详情、手机横屏浅色、平板竖屏深色、大字体手机。完整许可的可选择/滚动/关闭由原生测试检查；没有将全部屏幕目视或实机测试称为通过。

APK：`dist/CodePocket-audit-2026-10-24-debug.apk`，26,714,492字节，SHA256 `f6d5e91a24620c83623e0acf0382b120ea3fbb7bbc9cab12c6f91af92ba2442d`。

[原生界面预览](auxiliary-surfaces-preview/README.md)。完整UI改版仍进行中；动态令牌亮色、诊断浮层2项失败及未实现功能保持后续清单。

## 修改文件

- `app/src/main/java/com/codepocket/ide/MainActivity.kt`：工作区/草稿标题、字号预览、帮助/显式详情。
- `app/src/main/java/com/codepocket/ide/SettingsDialog.kt`：许可滚动/选择正文。
- `app/src/main/java/com/codepocket/ide/ui/WorkbenchForms.kt`：共享标题和正文。
- `app/src/main/java/com/codepocket/ide/ui/IdeIcons.kt`、`WorkbenchStyle.kt`：选择条目类型图标与柔和色。
- `editor-sora/src/main/java/com/codepocket/editorlab/SoraSyntaxController.java`：挂接后应用基础颜色，保留动态令牌机制。
- `app/src/test/java/com/codepocket/ide/AuxiliarySurfaceIntegrationTest.kt`：8项实际主界面回归与截图。
- README、RESUME、本报告和预览、`verification/workbench-audit-2026-10-24/`：需求优先级、实际结果与复现脚本。
