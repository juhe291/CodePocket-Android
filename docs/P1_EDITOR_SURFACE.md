# P1-C 生产弹窗与Sora共享光标定位首批

2026-10-18，基线 `3dbc4afc3fdb9b24006c7e357b5f31fdf449d25b`。接续现有编辑/导航/文档交接，不重建IDE；默认仍使用原生CodeEditor，实机统一后置。

## 改动与目的

原CompletionPopup与SignaturePopup分别直接依赖CodeEditor/TextView布局、选区、正文组合区和窗口坐标。这使生产弹窗不能直接接受Sora提供的位置，裁剪/坐标代码也重复。本批把只读组件边界抽成EditorSurface：提供文件身份、版本、UTF-16光标偏移、屏幕像素光标行和实际可用区域，不传正文或Android View。

新增editor-surface本地Java库，两模块共同依赖；不包含编辑内核、Android API、Kotlin元数据或第三方依赖。原生DocumentEditorPort继承该接口，NativeEditorPort调用NativeEditorSurface读取真实TextView布局，忽略不可用/替换中/组合/选区/失焦/未布局/不可见的状态。捕获只读长度/选区/布局，不复制整份代码，不修改滚动、焦点、输入、系统历史或文字。

生产两个弹窗现在只接通用View父节点和EditorSurface，不再读取原生布局或文本；重定位时复查请求的文件、revision和UTF-16偏移，拒绝过期锚点。保留原候选/内置参数内容、触屏与键盘规则、动画和API29前窗口坐标换算。现有CompletionGeometry外部API委托共享放置规则，原测试继续运行。

共享规则把root-relative可见矩形换到屏幕空间，与窗口/文本区相交；文本内边距和Sora行号栏不作为可用锚点区域。弹窗在光标行上/下选择可用空间，宽高约束到区域，不足则拒绝；拒绝非有限坐标、非法预算和溢出平移。采用一致像素取整和Long中间运算。真实窗口/输入法坐标行为仍需实机验收，不以纯数学测试作兼容性结论。

## Sora适配的实际范围

editor-lab新增SoraEditorSurface，真实调用固定0.23.7的Layout.getCharLayoutOffset；该API返回“字符行底部、字符左边”，结合measureTextRegionOffset、getOffsetX/Y和getRowHeight计算屏幕光标行。没有用逻辑行号×字体大小猜测折行位置，也未调用会改变滚动的光标锚点更新方法。

已直接检查本地Maven精确sources.jar，SHA256 `0429f8853930a0ada2936bf6f8740cfaa22358009fd56f0f49111dff9709e7d8`；Layout.java/CodeEditor.java原文在证据pinned-source目录。版本仍为0.23.7，没有升级库或整个工程Kotlin。

实验新增“光标定位”按钮，在Sora临时草稿显示明确标注“非补全”的只读定位探针；它通过同一接口和放置规则工作。滚动/布局重新定位；编辑/选区变化、换临时草稿、主题变化、失焦/暂停/销毁关闭，文档/版本/偏移变化拒绝旧位置。探针没有候选或编译执行输出，不能当作IntelliSense、语言分析或运行功能。

**Sora只接入定位接口**；完整文本/选区事务、整文档/撤销历史、装饰/语言包、现有候选/片段/参数及项目/SAF会话仍待适配。默认APK未引入Sora，实验仍不接收项目/外部URI、不读写文件、不运行代码。其临时草稿、真实Content编辑、各自历史、主题与动画控制保留。

## 文件

| 文件 | 改动 |
| --- | --- |
| editor-surface/build.gradle.kts、EditorSurface.java、PopupGeometry.java（新增） | 本地Java契约、只读不可变锚点、裁剪与放置规则 |
| settings.gradle.kts、app/editor-lab的build.gradle.kts | 包含共享库与本地project依赖，不改插件/外部库版本 |
| editor/NativeEditorSurface.kt（新增）、NativeEditorPort.kt、EditorDocuments.kt | 实际原生锚点与生产Port集成 |
| CompletionPopup.kt、SignaturePopup.kt、CompletionModel.kt、MainActivity.kt | 通用组件接口、过期复查与原API委托 |
| editor-lab/SoraEditorSurface.java、SurfaceProbe.java（新增）、EditorLabActivity.java、surface_strings.xml | 固定Sora布局适配与只读探针/清理 |
| EditorSurfaceTest.kt、SoraSurfaceTest.java（新增） | 18项默认主机与5项Sora适配回归 |
| README、DEVELOPMENT_PROGRESS、RESUME、本报告 | 构建/边界与接续点 |

## 实际验证

默认 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline`：**BUILD SUCCESSFUL in 1m 17s**，64 actionable tasks: 22 executed, 42 up-to-date。

- Kotlin **264项：263通过、0失败、1 Windows符号链接用例跳过**；新增18全部通过，原246项及状态逐项保留。
- 新用例覆盖身份/版本/UTF-16偏移、屏幕/root变换、嵌套区域、键盘边界上翻、空间不足、部分裁剪、NaN/无穷/极值、像素取整、窄窗、非法预算、文本内边距/行号栏、右边界、负屏幕坐标、Long溢出保护和旧CompletionGeometry委托。1项确定性采样测试另检查1000个场景的矩形包含与光标行间距，不把场景数算成JUnit用例数。
- Python源码 **13/13**；取真实APK runner.pyc在匹配Python3.13主机 **13/13**；Kotlin实际生成模板Python **7/7**、主机g++ C++17 **7/7**。不是Android本地C++或本轮Wandbox请求。
- 默认Lint **0错误、11既有警告**，类别计数与上批一致；既有Android弃用API编译警告保留。

实验 `:editor-lab:testDebugUnitTest :editor-lab:lintDebug :editor-lab:assembleDebug -Pcodepocket.editorLab=true --offline`：**BUILD SUCCESSFUL in 32s**，49 actionable tasks: 24 executed, 25 up-to-date。

- **19/19**，原14项真实Content/Indexer/UndoManager测试逐项保留；新增5项执行Sora适配器实际使用的fromLayout数学函数，部分同时操作真实Content/Indexer，覆盖UTF-16/CRLF、实际编辑后版本、滚动只扣一次、视觉行坐标及无效测量。
- 主机测试没有实例化或执行Android CodeEditor View，也没有执行真实getCharLayoutOffset渲染；该调用仅经精确源码与真实编译验证。手填布局数值只用于坐标变换测试，不能写成Android/Sora布局已实测。
- 实验Lint **0错误、6既有警告**，与前实验相同；无Kotlin元数据不兼容日志。
- 分别读取实际debugRuntimeClasspath：默认为Kotlin1.9.25、无Sora、新增本地Java库；实验仍Sora0.23.7/Kotlin2.1.21、新增同一Java库。没有屏蔽元数据检查或强降stdlib。

两份APK：ZIP CRC、v2 Debug签名、16KB ZIP对齐、API24/target34、独立包名、所需Dex类定义和复制后SHA256通过。默认arm64-v8a及嵌套Python归档检查通过，无Sora/实验类；实验无Python/native .so与申请权限。上一各自APK类定义全部保留；旧文档/导航/事务/原生/实验APK与历史材料包六项SHA256保持。

CodeEditor输入事件、EditorActions/Features、事务/导航模型、文件/SAF/草稿/运行/Python、旧测试、Manifest、root Gradle插件版本、两份Actions和三份需求原文保持输入哈希。仅增加本地共享库依赖与包含配置，CI原任务会自动编译该库；没有远端运行/push/发布或新增权限/存储迁移。

## 产物与接续

[默认IDE APK](../dist/CodePocket-editor-surface-debug.apk)：**22,106,015字节**，SHA256 `0a8c53bc85b83c586a92b640b824d21b19ded88eea0a6b9613671c657b7f3fb9`。

[Sora定位实验APK](../dist/CodePocket-Sora-surface-lab-debug.apk)：**1,650,260字节**，SHA256 `8944ece94e994acb25ea44ab5b536c1be9bea885b6da83fc133c99f653fa9d21`。

证据 `verification/editor-surface-2026-10-18/`：results.json、verify-native.ps1/verify-lab.ps1、两份构建/测试/Lint、APK签名/对齐/字节码、runtime-dependencies.log、精确API源码、修改前备份/输入哈希/补丁。历史实验材料包保留原样，不称其包含新增定位代码；新源码在当前仓库的editor-surface和editor-lab。完整上游源码/重链接交付核验仍待，未进行发布。

下一批继续Sora文本/选区事务、文档更换与历史接口适配，再接语言包及现有候选/片段/参数/SAF会话；原P1完整动画/手势/设置帮助许可/真实Python流式、P2语义与本地/多文件C++、P3 Git/主题需求保留，P0问题优先。

实机继续统一后置；没有Android/模拟器、截图、手机/平板/鸿蒙卓易通、真实InputConnection/IME遮挡/多窗口/RTL/滚动/触屏键鼠与FPS验证。数学模型通过与APK编译不证明坐标在设备上正确或达到60FPS；本轮无新Wandbox或远端Actions。
