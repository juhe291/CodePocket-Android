# 标题层次与适度装饰（2026-10-24）

基于0173199增量打磨，遵循用户“UI视觉第一、可稍加装饰”的最新范围。只调整展示，现有文件/保存/执行、单一真实PTY及AppSettings草稿模型保持。

## 当前改动

- 共享标题使用36dp柔和图标底座、16sp标题、可选11sp路径/数量和一条细分隔线。工作区、恢复草稿复用同一组件；图标不新增命令或焦点目标，辅助图形不进入无障碍朗读。
- 七类设置、分类首页与搜索结果使用共享标题；小屏仍逐页、大屏仍分栏，取消/应用、搜索、旋转草稿和滚动保持原处理器。没有添加长期说明或重复按钮。
- 可点击样稿同步标题底座和分隔层次。样稿仍明确标为示例，不能执行代码；原生绘制和浏览器截图分别归档。
- 不使用荧光、发光或渐变；没有更改编辑器、语法分析、运行引擎、PTY解析及输入底层。

## 验证

- 主App完整685项：684通过、0失败、1旧Windows符号链接跳过；685个原用例身份及跳过集合完整保留。此次是低风险展示调整，使用既有实际Main/Sora与设置草稿/导航/滚动回归，没有添加镜像样式实现的测试。
- 完整Gradle命令2分钟成功，packageDebug实际执行；Lint0错误，主App17/Sora1既有警告，无新增警告。原生设置/工作区/草稿等绘制来自本次运行。
- Playwright实际浏览器：29场景×4窗口×2主题=232次场景/边界检查；13项交互和20项既有语法颜色检查通过，未捕获页面脚本错误。16张示例截图已更新。目视检查本批原生手机编辑器设置、大字体搜索、平板浅色设置与草稿，以及平板浅色浏览器设置；没有将自动检查描述为全部屏幕逐张目视通过。
- 源码Python44、实际APK内Python字节码39、主机Shell启动4、真实Python/C++17片段各7通过；原签名与16KB对齐检查通过。这些主机执行不代表Android Chaquopy/mksh、远端Wandbox或设备输入通过。
- 70个旧及中间APK保持原哈希；10个原生库与Python app.imy相对上一主包逐字节相同。上一批导入用例一次失败仍未确认根因，本批没有修改生产Python；见上一批import-recheck-summary.json。

## 产物与边界

[最新主APK](../dist/CodePocket-heading-polish-2026-10-24-debug.apk)：26,509,321字节，SHA256 `4fedb4c0cad96a0392e410b56c12a5764b7917ba6181992b2e932eeb0efc37ec`，原签名、arm64-v8a。

[原生预览](heading-polish-preview/README.md) · [可点击样稿](ui-preview/README.md) · [机器结果](../verification/workbench-heading-polish-2026-10-24/results.json)。临时本机服务和专用浏览器已关闭。

本轮另发现：设置中的“优先左右分屏”仍读写旧panelPreferSide偏好，但MainActivity布局传入bottomOnly=true，因此当前终端布局不受它影响。这是旧设置与新工作台文案不一致，按用户要求登记，未在装饰批次中改动布局或删除偏好。后续统一终端设置时处理。

实际IME、蓝牙键鼠、卓易通、API24、帧率、初始化瞬间及远端Actions仍未验收；整体UI不能仅因本批检查通过就称全部完成。网页/Git/插件/账号/深度语义与原P2/P3继续保留暂缓。

## 文件

生产：WorkbenchForms.kt、SettingsDialog.kt；截图输出路径：SettingsSurfaceVisualTest.kt、AuxiliarySurfaceIntegrationTest.kt（断言未改）；样稿：workbench.js、workbench.css；README/RESUME、本报告、预览和verification/workbench-heading-polish-2026-10-24中的脚本/结果。
