# 工作区载入与恢复状态的视觉统一（2026-10-24）

基于30ba543继续UI优先范围。检查初始化路径时发现，MainActivity初次工作区IO期间及首次载入失败的根界面仍使用临时TextView，只有原始像素内边距和平台默认排版；首轮系统栏也要等完整工作台才调整。本批将这些既有状态纳入视觉体系，不新增执行或项目功能。

## 实现

- WorkspacePlaceholder复用本地Lucide图标、柔和图标底座、49dp品牌区及细分隔线。状态正文居中，手机/平板与大字体可滚动，实际载入完成即由原回调进入真实工作台，不伪造进度百分比或编辑/运行结果。
- 失败页只显示真实状态与“载入失败不会删除原文件或恢复草稿”。继续调用原有选择目录/内部项目对话框及处理器，未更换目录授权、IO、文件事务或草稿存储。
- 窗口背景、系统栏与平台控件强调色向现有灰蓝色板对齐；系统栏在初次载入页前应用，完整工作台复用同一入口。状态图形不参与焦点或无障碍朗读，状态文字使用礼貌更新；没有新动画或重复操作按钮。
- 可点击样稿新增“打开工作区”“工作区载入失败”两种既有状态的设计示例；内置示例恢复入口只导航样稿，仍明确不执行程序。

## 实际验证

- 新3项真正MainActivity回归：阻塞真实WorkspaceIo后检查手机深色/平板浅色载入状态，释放后进入真实内部项目并核对文件内容及系统栏；不存在的ContentResolver提供方导致真实载入失败后，核对事先落盘的恢复草稿仍在，再经原按钮进入真实内部项目。最后一项在API28、360×640dp、1.8倍字体主机环境执行，不能当作设备SAF认证。
- 主App全量688项：687通过、0失败、1旧Windows符号链接跳过，旧685个用例身份及跳过集合保留。首次新状态页因ScrollView.LayoutParams引用编译失败，已改为FrameLayout.LayoutParams，原日志保留；随后2分34秒完整构建实际打包成功。补强“失败前事先落盘”断言后，最终1分48秒完整回归成功，APK缓存复核。
- Lint0错误，主App16/Sora1警告；主App相对上一批减少1个SetTextI18n，未宣称所有警告清零。
- Python源码44、实际APK内字节码39、主机Shell启动4、真实Python/C++17片段各7、原签名与16KB对齐通过。71个旧/中间APK保持原哈希，10个原生库与Python app.imy逐字节保持。
- 浏览器样稿31场景×4窗口×2主题=248次场景/边界检查，15项交互及20项既有语法配色检查通过，无页面脚本错误。保存24张示例图（含8张载入状态），另有3张原生状态绘制。目视检查了这3张原生图和手机深色浏览器载入图，不把自动覆盖声称为逐张目视。
- 核对锁定的Sora0.23.7实际字节码：isEditable检查editable、layoutBusy及isFormatting；现有WorkbenchEditor就绪封装在可编辑时确实等待布局，并非无条件true。本批没有改动生产编辑器/高亮/光标或深度语义服务。
- 检查build-apk.yml：原有Debug构建及APK上传路径保持，未触发远端Actions。本机临时服务及专用浏览器已关闭。

## 交付与边界

[最新主APK](../dist/CodePocket-startup-ui-2026-10-24-debug.apk)：26,467,525字节，SHA256 `312e55c0f7e8b26bb4b689d5b735bf317838b11e885c7ed0245adc528e7491d2`，原签名、com.codepocket.ide、v0.2.0/code2、min24/target34、arm64-v8a。

[原生预览](startup-ui-preview/README.md) · [可点击样稿](ui-preview/README.md) · [机器结果](../verification/workbench-startup-ui-2026-10-24/results.json)。

原生图来自Robolectric Native Graphics，不是设备截图；未验证Android系统冷启动画面/真正第一帧时序、IME、蓝牙键鼠、卓易通、API24、帧率或Wandbox远端。先前一次APK导入失败仍未确认根因；旧“优先左右分屏”文案仍列后续处理。本批未扩大为底层修复，网页/Git/插件/账号/深度语义及原P2/P3继续保留暂缓，整体UI尚不能据本批结果称全部完成。

## 文件

生产：MainActivity.kt、ui/WorkspacePlaceholder.kt、res/values/styles.xml与strings.xml。回归：WorkspaceStartupIntegrationTest.kt。样稿：workbench.js/CSS、截图/README。文档、脚本、机器结果和原生图见本批目录。
