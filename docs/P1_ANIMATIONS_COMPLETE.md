# P1-E 动画开发完成与平板适配（2026-10-19）

本批承接b804563。用户要求“继续直到动画完成”，并追加“别忘了适配平板”。**原计划所列动画已完成代码接线和主机验收，实际设备上的视觉、输入法与帧率验收按既定约定后置。** 这不代表整体UI重设计、P2/P3或生产Sora迁移完成。

## 已完成的动画

| 区域 | 实际行为 | 输入与资源约束 |
| --- | --- | --- |
| 编辑器光标 | 80ms短距离移动；停顿600ms后进入1000ms柔和闪烁周期；移动与闪烁分别持久化开关；关闭闪烁或总动画后常亮 | 继续使用原生EditText、Layout与输入连接，透明原生cursor drawable仅替换像素；原生cursorVisible、插入/选择控制器保持。文本/逻辑选区立即变更；打字、组合输入、滚动、窗口尺寸变化、远距离跳转立即到位。动画只画光标，脏区包含上次绘制区域，避免拖影；不改变文档或IME坐标。 |
| 文件树 | 原生ChangeBounds/Fade展开、收起与行间过渡，独立箭头旋转；已有行保留身份和焦点 | 代码/终端不参与树重排；超过120项时直接更新，避免大量动画占用输入线程。右键、长按、安全操作保持。 |
| 标签 | 140ms活动指示条移动；新标签淡入；重复dirty刷新不打断同一次指示条过渡 | 标签文字和关闭按钮始终在真实位置；不平移代码，原视图、焦点和触摸区域保留。 |
| 侧栏/抽屉 | 宽屏侧栏滑入，收起时真实侧栏立即隐藏，内存视觉快照短时退出；手机和窄屏平板使用左侧抽屉窗口过渡 | 快照位于无输入/无无障碍节点的ViewOverlay中，不产生可点击的旧文件树或第二编辑器；窗口变化或新动作可立即取消。 |
| 运行面板 | 展开160ms轻微入场；收起、最大化/恢复及按钮调整尺寸使用180ms显示过渡；拖动直接跟手 | 不对真实终端按帧改尺寸。面板一次到最终布局，只动画原显示快照的位置/透明度，不缩放文字；终端取得输入焦点、IME出现、拖动或窗口变化时结束过渡。原Shell/前台Python桥、运行/停止/EOF与迟到消息隔离保持。 |
| 菜单与浮层 | 新建/重命名/移动/删除/搜索/设置/会话等原生对话框统一140ms进入、90ms退出；抽屉200/140ms左侧过渡；原生上下文菜单保留主题和命令回调 | 先安装Dialog decor再选择动画样式，防止主题覆盖总开关；关闭应用/系统动画时样式为0。保留原确认、安全校验与取消/保存回调。 |
| 补全/参数提示 | 首次90ms淡入、显式关闭80ms淡出；已有窗口刷新和同文档350ms内快速重开不重复淡入；失效文档/IME/焦点变化立即关闭 | 呈现前先结算显示光标，保证窗口与真实插入点一致；候选仍基础词法、签名仍内置参考，语义后端没有被动画替代。 |
| 深浅主题 | 手动切换及跟随系统配置重建时进行限额内存快照交叉淡化 | 沿用已存在的Activity重建/会话恢复流程，不按帧重建界面或重新解析源码；只保留Bitmap和尺寸，不保留旧Activity/View；大小不一致或捕获失败立即显示新主题。 |
| 动态策略 | 总开关、系统Animator/Transition/Window三个动画缩放、省电与前后台统一生效；系统开关通过监听更新 | 无帧内设置轮询；失焦、后台、移除、重建、IME及连续操作取消回调和视觉层。终端动画开关独立于字体设置，切换策略不重建Termux renderer、不重置缩放或PTY尺寸。 |

主题/旋转配置检查点另外保留目录展开与可见树滚动位置（最多512项、合计32K字符，限制系统状态包体积），避免主题交叉淡化后文件树突然折回根目录；跨进程/工作区完整导航持久化仍属其余UI工作。

设置入口：**设置 → 外观 → 动画总开关 / 平滑移动代码光标 / 柔和闪烁光标**。设置取消不修改当前配置；保存后写入JSON v7，旧v6配置缺失的新字段采用兼容默认值，原设置和项目数据保持。

## 平板与可变窗口

- 根据扣除系统栏后的实际根窗口尺寸决定首次启动：宽≥600dp且高≥480dp默认展开运行面板，初始底部分数约35%；手机及更短窗口默认收起。极短窗口仍受终端/编辑器最小高度保护，不能保证物理高度恰好35%。
- 固定活动栏/文件侧栏从840dp开始，且内容高度≥360dp；600–839dp保留编辑器空间，使用抽屉。仍保留既有短横屏/输入法紧凑终端策略。
- 启动选择只应用一次；旋转、分屏变化、主题重建、切换工作区及恢复前台保留当次面板开关、最大化和尺寸选择。显式运行仍展开面板，原后台消息不重开用户已关闭面板的保护不变。
- 拖动文件侧栏和终端先取消过渡，直接采用真实手势；动画不等待手指、不追赶排队的尺寸。
- 每张显示快照上限200万ARGB像素，约7.63MiB；这是每张快照的上限，不是整个App内存用量。3840×2160窗口降采样捕获，保留真实布局尺寸用于覆盖；退出、重入、窗口不匹配或销毁清理，不写入文件、不上传。

## 修改文件

- `MainActivity.kt`：动画策略/生命周期、面板与侧栏接线、主题Bitmap交接、平板启动及Bundle恢复、补全定位结算、统一对话框和菜单。
- `animation/EditorCaretMotion.kt`、`CodeEditor.kt`、`drawable/editor_cursor_transparent.xml`：仅光标显示层，保留NativeEditorPort和原生编辑/历史/IME路径。
- `animation/MotionEnvironment.kt`、`LayoutMotion.kt`、`SurfaceMotion.kt`、`DialogMotion.kt`、`Animations.kt`：系统观察、原生树过渡、有界快照、窗口样式和统一取消。
- `ui/EditorTabs.kt`、`EditorTab.kt`、`ExplorerRow.kt`、`ResponsiveLayout.kt`、`WorkspaceLayoutPolicy.kt`、`IdeCommandMenu.kt`：指示条、目录行、840dp策略和原生命令菜单。
- `AppSettings.kt`、`SettingsDialog.kt`、`SearchDialog.kt`、`CompletionPopup.kt`、`SignaturePopup.kt`、`motion_styles.xml`、`motion_ids.xml`和4个anim资源：开关、原生窗口和弹窗过渡。
- `terminal/shell/ShellTerminalPane.java`：只新增不改字体的setAnimationsEnabled接口，调用原成熟光标闪烁控制；没有改Termux/JNI或Python桥。
- 新增 `EditorCaretMotionTest`、`MotionEnvironmentTest`、`FullWorkbenchMotionTest`、`TabletAnimationLayoutTest`；扩展 `PopupMotionTest`、`TerminalPanelFocusTest`、`TerminalWorkbenchViewTest`；`ResponsiveLayoutTest`和`InteractionTest`的旧650dp固定栏断言按840dp计划调整。
- README、RESUME、开发进度和UI计划记录本批事实；原完整需求、动画/语义需求基线和原P2/P3保留。

## 实际构建与测试

| 检查 | 最终结果 |
| --- | --- |
| 原仓库Gradle测试/Lint/assembleDebug | **成功，1m 9s**；Java17、Gradle8.9、SDK35、Chaquopy/Python3.13，未升级依赖 |
| App JUnit | **448项：447通过、1既有Windows符号链接跳过，0失败/错误** |
| 既有用例保持 | 旧399项均有对应相同状态；其中1项中屏用例按新规则改名，其余名称保持，几处宽度断言相应修正；没有删除旧输入、保存或终端用例 |
| 本批新增49项 | 光标14、系统/设置5、工作台/快照/菜单/主题/配置保持18、平板5、弹窗重开4、实际Termux renderer身份2、首次输出刷新不吞平板启动决策1 |
| 终端核心 | 146项通过结果保持，源未改，Gradle本批复用UP-TO-DATE，不称为重新执行146项 |
| Python源码/最终APK runner.pyc | 各29项通过，各含16项真实主机管道子进程测试 |
| Java生成的真实Shell脚本 | 4项主机执行通过 |
| 生成模板 | Python7、C++17 7项真实主机运行通过 |
| Lint | **0错误、11既有警告** |
| 产物 | Debug v2签名与上版证书一致、ZIP CRC、ZIP/ELF16KB对齐；桥类和新动画类/资源存在，无测试库；原生库、Python归档与终端许可资产逐字节保持 |
| 旧数据与构建路径 | 37份既有dist APK哈希保持；Workspace/SAF/DraftStore、RunSession/隔离运行服务/runner、桥/键盘启动等受保护源码、Gradle、Manifest、GitHub Actions及原需求基线保持 |

真实测试促成的修正包括：原生Dialog安装decor时会覆盖提前设置的动画样式；重复标签刷新会提前结算指示条；初次窗口布局会提前取消主题过渡；光标脏区须包含上一次绘制区域；补全窗口与动画光标必须在实际插入点会合。面板方案改为只动画快照，从结构上避免逐帧PTY尺寸更新；首次输出渲染不再提前消耗平板启动决策，只有真实尺寸或明确操作决定首次面板状态。

测试过程也区分了主机条件：Robolectric旧字形测量可能让同一行多个offset的横坐标相同，因此移动曲线使用真实跨行Layout，并另测同源锚点/UTF16；曲线以真实平台Animator的可控fraction验证，不能用主机帧时钟推断设备FPS。原生Dialog按钮回调经Handler调度，测试等待实际回调；尺寸计数先稳定主机Window初次遍历。中间结果和这些初始失败保留，未算成已复现的设备故障。

## APK与复查

[CodePocket-animations-debug.apk](../dist/CodePocket-animations-debug.apk)：**22,678,738字节，约21.63MiB**，Android7+ / arm64，package com.codepocket.ide，version0.2.0，使用既有Debug证书。

SHA256：`0bc57db2be96e4c7c4766d7c6b0dcd870a8b0d4fc7fd57293c81a4565265ff0e`

证据目录：`verification/animations-complete-2026-10-19/`，含before.json、最终results.json、基线/最终JUnit、Lint、构建/签名/实际APK检查、真实Shell与模板结果及复查脚本；first-existing-junit、new-tests-first/second-junit及round-01…05保留过程结果。原库验证命令见verify-repository.ps1，不是临时工程构建。没有push、发布或运行远端Actions。

## 实机验收与其余项目

- **未连接设备或模拟器。** 尚无手机/平板/卓易通上的像素截图、实际光标/IME/蓝牙键鼠/分屏过渡、横竖屏全链、持续输出并发以及60/90/120Hz帧时间/输入延迟证据。Android PTY/app_process/FD/Chaquopy整链没有本轮新执行，不把主机测试当作实机通过。按用户约定统一后置。
- 动画在IME、终端输入焦点、直接拖动、系统关闭/减少动画、省电、超长树或资源不足时降级为即时状态，这是输入优先策略；终端字符网格继续使用成熟Termux原生显示，不自绘一个替代终端。
- 本批完成的是原计划动画功能；界面图标丰富度、设置分类/搜索等其余UI设计以及跨进程文件树展开/滚动状态仍按原计划推进；它们不被本批完成状态覆盖。P2真实语义补全、P3及本地C++路线保持，当前仍Chaquopy本地Python和真实Wandbox云端C++。
