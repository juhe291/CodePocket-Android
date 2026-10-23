# 工作台搜索入口精简（2026-10-24）

基于a4bee38继续最新UI优先任务。核对源码后确认：两种查找原本已正确区分；顶部与左侧重复的都是项目搜索。本批移除顶部重复图标，保留左侧项目搜索（Ctrl+Shift+F）、编辑区当前文件查找（Ctrl+F），以及原菜单/快捷键。

主工具栏固定为终端、运行/停止、更多三项；宽窗口继续显示文字菜单，标题获得更大空间。移除构造器未使用的旧资源管理器/搜索回调；未更改搜索引擎、替换版本校验、保存/文件事务、权限、Python/Wandbox或单一PTY。

新增1项真实Main/Sora入口用例，实际点击左侧项目搜索与编辑区当前文件查找，核对不同窗口/输入框和同一编辑缓冲；已有终端按钮测试同时核对顶部没有项目搜索副本。旧构造器夹具适配新参数，断言保持。定向回归通过后再全量构建，不把编译成功代替点击行为验证。

可点击样稿同步移除顶部项目搜索，并再次执行232场景与13项交互检查，未发现页面脚本错误或横向越界。示例输出仍明确为设计预览，不运行代码。临时浏览器及仅监听127.0.0.1的预览服务在测试后已关闭。

## 边界与保留项

- 两项此前登记的诊断浮层测试保留失败断言，按用户要求记录后置；不使用新显式详情测试掩盖点击路径问题。
- 部分TextMate动态令牌亮色另记；本批不扩大着色/语义改动。
- 网页辅助栏、Git/插件/账号、深度语义及原P2/P3暂缓，原需求和研究文件保留。
- 实机IME、蓝牙键鼠、API24、卓易通/FPS后置；未触发远端Actions或新增Wandbox执行结论。

最终完整测试、Lint、APK及真实主机验证在verification/workbench-search-entry-2026-10-24/results.json；只有完成后登记结论。新截图与上批旧图分别保存，避免把旧入口截图称为当前版本。

## 最终实际结果

- 73项定向测试全部通过。完整主App675项：672通过/2项已登记诊断浮层失败/1旧Windows符号链接跳过；没有新增失败。
- 完整命令2分8秒，因2项保留失败返回1；packageDebug与assembleDebug实际执行成功。Lint0错误，主App17/Sora1警告，与上批相同；不能声称全量绿色或远端CI通过。
- 实际APK签名、16KB对齐、源码Python44/APK字节码39/主机Shell4/真实Python与C++17片段各7检查通过。66个旧APK哈希、10个原生库和Python app.imy保持。
- 浏览器232场景/13项交互再次通过；原生目视检查了两个查找窗口与平板竖屏深色工作台。完整UI与实机仍未宣称全部完成。

[最新主APK](../dist/CodePocket-search-entry-2026-10-24-debug.apk)：26,508,745字节；SHA256 `3c150a6147dd4b53a2b9fe7e402067132335ecf6939827dd8f520f8072cb4319`，原签名、arm64-v8a。

[当前原生预览](search-entry-preview/README.md)，[可点击样稿](ui-preview/README.md)。

修改文件：MainActivity.kt、ui/MainToolbar.kt；UiRedesignTest.kt、TerminalWorkbenchViewTest.kt、AuxiliarySurfaceIntegrationTest.kt；既有样稿workbench.js/截图、README/RESUME/本报告，以及本批复现脚本和结果。未更改Gradle依赖、Manifest、Actions或运行/存储底层。
