# CodePocket 当前交接状态

## 最新：完整UI改版与交互修复（2026-10-20）

- 已接入整套原生工作台、文件树、共享菜单/弹窗、独立分类设置、五种真实终端配色和输入光标过渡；不是只修改设置或添加动画。详见[完整报告](UI_COMPLETE_2026-10-20.md)及[界面清单](UI_SURFACE_INVENTORY_2026-10-20.md)。
- App472项471通过/1旧跳过，新增24；源码/打包Python各29，Shell4，模板各7；Lint0错/11旧警告，Debug APK签名/CRC/16KB/必要类/许可证检查通过。终端核心146为缓存保持，非重跑。浏览器预览96场景及额外取消/标签/折叠/配色检查通过，非Android截图。
- [新APK](../dist/CodePocket-ui-complete-2026-10-20-debug.apk)：22,682,264字节，SHA256 `f07346856373dea64e9dad2f1cfa899753ebb0b1c4c284b5ac8dcbb194af7873`。同旧Debug证书。38旧APK最终哈希保持；一次大小写撞名覆盖已从原哈希备份恢复，并加入冲突保护，详情有记录。
- 原运行、保存/SAF/草稿、编辑器事务、桥及成熟Termux代码保护；Python固定真实终端交互，C++仍Wandbox预填云端。根目录可收起，选中与新建位置独立；浮动IME按实际遮挡判断。
- 实机统一后置：未报告Android整链/真实像素/IME/键鼠/卓易通/FPS通过。原P2/P3继续。无push、发布或远端Actions；保留.tools、旧dist、verification和设备备份。


## 最新：P1-E动画代码/主机验收完成，含平板适配（2026-10-19）

- 基线b804563；用户要求直到动画完成并强调平板。已完成光标移动/柔和闪烁、分别开关、树展开/折叠、标签指示条、侧栏/左抽屉、面板开关/最大化/恢复、菜单浮层/候选退出及手动/系统主题交叉淡化。系统三种动画缩放/省电动态监听；输入、IME、窗口、拖动及生命周期能取消。详见[P1_ANIMATIONS_COMPLETE.md](P1_ANIMATIONS_COMPLETE.md)。
- 不移动编辑器文档或改逻辑光标；输入/组合输入直接到位。真实终端一次到最终尺寸，仅动画不接收输入的显示快照；策略切换不重建Termux renderer或改字体/PTY。单张快照200万像素上限，超120项树直接刷新，避免大量动画。
- 平板启动600×480dp起默认约35%终端；固定多栏840dp起，内容高至少360dp。旋转/主题/工作区和前台恢复保留当次选择；短窗口/IME仍保护最小可用高度。
- 最终App448项：447通过/1既有跳过，新增49；旧399对应保持（1项按840dp规则改名）。终端146源未改缓存保持；Python源码/最终APK字节码各29、Shell4、模板各7，Lint0错/11旧警告；构建1m 9s。签名/CRC/16KB/桥及动画类与资源/旧37份dist检查通过，native与Python归档及终端许可逐字节保持。
- [APK](../dist/CodePocket-animations-debug.apk)：22,678,738字节，SHA256 0bc57db2be96e4c7c4766d7c6b0dcd870a8b0d4fc7fd57293c81a4565265ff0e。证据verification/animations-complete-2026-10-19/，保留所有中间失败与round结果，原仓库构建；没有push/发布/远端Actions。
- 动画代码范围已闭环，未获得设备像素/实际帧时钟/IME/蓝牙键鼠/卓易通或本轮Android运行链路证据；实机按用户约定后置。其余UI、文件树跨进程状态及原P2/P3保留；不把词法补全称为语义，不新增系统python/pip/clang或本地C++。
- 实际仓库仍在Desktop。通过获准命令读写/构建，保留.tools、旧APK、verification及设备备份，原文件安全/SAF/保存/运行链路与Gradle/Actions不变；只在ShellTerminalPane增加动画策略专用接口。


## 历史：P1-E1 工作台动画与稳定刷新（2026-10-19）

- 基线8011d7b，接续用户“继续”。按路径保留文件树/标签，增加箭头与引导线、192dp窄栏预算、选中态与触摸反馈；只淡入新项，弹窗仅初次入场。统一取消结算，窗口/后台/重建/IME/关闭动画时取消子级过渡；不按帧改编辑器/PTY尺寸。input帮助纠正预填旧文案。
- 最终App399项：398通过/1既有跳过，旧373全部保持，新增26；终端146未改缓存保持，Python源码/实际APK字节码各29、Shell4、模板各7，Lint0错/11旧警告。签名/CRC/16KB/旧36份dist/终端许可与native、Python资产逐字节保持。详见[P1_WORKBENCH_MOTION.md](P1_WORKBENCH_MOTION.md)。
- [APK](../dist/CodePocket-workbench-motion-debug.apk)：22,678,342字节，SHA256 000f5996fc21f0b1595781c76d7f2ae7ccf01fc78bd16da05690eabfd4102225。证据verification/workbench-motion-2026-10-19/，保留首轮fixture错误及round-01中间报告；没有push/发布/远端Actions。
- 本批是P1动画增量：完整光标/主题/侧栏与面板/统一浮层过渡、图标及分层设置余项和P2/P3仍保留。目录收起立即移除子项，不称为高度动画；同一View刷新保持不等于跨旋转/进程持久化。无新增设备/IME/键鼠/卓易通/实际像素截图/FPS证据，实机按用户约定后置。
- 仍在实际Desktop仓库；旧OneDrive权限快照无效，原仓库写入/构建用获准命令。保留.tools、旧APK、verification及设备备份，终端/文件/运行后端/Gradle与Actions不改。


## 历史：终端焦点/过期输入连接/会话选择（2026-10-19）

- 基线a78dd6c；用户反馈“行了，继续吧”后继续终端操作。最大化与视口恢复不再抢终端焦点，明确代码导航/关闭面板仍可回编辑器。切换已有会话移交焦点，新会话无View时聚焦面板并清旧IME，不向源码误输、不排队迟到焦点；先发布新pane再触发其焦点回调。
- 真正Termux InputConnection绑定创建代次及Session，关闭/失焦/移除/更换/新连接时失效，不把旧组合词提交到新终端。Pane额外拦截inactive/disposed文本与控制键。组件仅增47行，原输入类型/渲染/手势保持；上游LF保留，累计补丁从固定归档生成，真实git apply重放10个累计修改源码/配置通过，许可与UPSTREAM保持。
- 修复前17项中9失败，最终9场景全部通过；App373中372通过/1既有跳过，旧357全部保持、新增16。核心146缓存保持，Python源码/最终APK字节码各29、Shell4、模板各7，Lint0错/11旧警告。最终原库构建1m11s，签名/CRC/16KB/桥类/许可/旧dist通过，native库与上一版逐字节相同。
- [APK](../dist/CodePocket-terminal-focus-debug.apk)：22,678,342字节，SHA256 06057a8ce583bfca62e2f10d55254d73fec259eb71ea4503edff0d259875772c，同旧Debug证书。[报告](P1_TERMINAL_FOCUS_LIFECYCLE.md)，证据verification/terminal-focus-lifecycle-2026-10-19/；round-01/02保留中间构建。
- 上版用户可用反馈不是本版或其他设备完整验收；本轮未接设备/模拟器，无新增Android链路、IME/键鼠/卓易通/FPS证据。既有保存/SAF/Python运行与停止/超时后端、Gradle/Actions/原需求保持，原P1动画与P2/P3继续保留。无push/发布/远端Actions，不清理旧工具/产物/证据/设备备份。

## 历史：Python启动期间键盘拦截与ART环境回修（2026-10-19）

- 基线59551f1，用户确认上一APK仍不能输入，状态仍为启动阶段。本轮把键盘显示与进程写入保护拆开；真实PTY输入保护保留，明确不把主机测试当作手机已经解决。
- ShellLaunchPlan继承明确名单内的实际Android ART/I18N/TZDATA/APEX与boot路径，修正native clearenv后app_process缺少运行环境；不猜系统路径，不继承任意秘密或loader设置。TerminalKeyboard等待窗口+View焦点，显式点击才清HIDDEN状态，保留resize，失焦/移除/暂停/超时取消，返回关闭后不重开。
- 启动脚本真实发出OSC133 C后，才结合提示符和内核前台确认桥早退；避免readline重绘/程序输出/旧运行误判。Python单一真实终端、C++云端限制、保存/目录/SAF/原输入数据不变；未改成熟Termux组件。
- 最终原库构建1m22s，357项中356通过/1既有跳过，原342全保留，新增15；核心146缓存保持，真实主机Shell4、Python源码/打包字节码各29、模板各7、Lint0错/11旧警告；签名/完整性/许可/类/16KB及旧dist哈希通过。
- [本轮APK](../dist/CodePocket-terminal-running-input-debug.apk)，22,678,342字节，SHA256 ae4faaf4fdca1955cd9fb5fbb1fc559be01797907b78d0e139c27dfb66508978；同旧Debug证书。[报告](P1_TERMINAL_RUNNING_INPUT_FIX.md)，证据verification/terminal-running-input-2026-10-19/；round-01保留355项中间版本。
- 手机/平板/模拟器未连接；Android PTY/app_process/FD/Chaquopy与物理IME/键鼠/卓易通/FPS没有本轮实测。先验证print真实输出，再做input/Enter/EOF/停止/重跑。原P1/P2/P3保留，动画暂停。无push/发布/远端Actions，不删除.tools/旧dist/verification/设备备份。

## 历史：提示符覆盖、终端IME与短窗口回修（2026-10-19）

- 用户手机真实记录为系统目录提示符、Python等待Shell标记超时，平板相同。确认系统mkshrc覆盖PS1；上一批View尺寸修复未覆盖此路径。每会话私有ENV rc先加载系统配置再设置OSC133标记，并补齐清理，保留启动/超时/身份保护。
- 统一真实Shell/Python面板不变；Python无预填，C++仍真实云端预填。新增48dp键盘按钮与点击焦点延后请求、隐藏/失焦取消；按字号和窗口紧凑显示，空间不足临时全终端，不覆写用户布局偏好；会话焦点白名单补入terminal，恢复已有终端时不转回编辑器或强制IME。
- 验证副本完整通过后，4个后续文件按源哈希校验应用回原仓库；原仓库再次构建1m9s。App342中341通过/1既有跳过，旧328状态保留、新增14；核心146（验证副本实际运行、原库复用缓存）；Python源码/最终APK字节码各29，Shell实际生成脚本3，模板各7，Lint0错/11旧警告；产物/许可/旧dist检查通过。
- APK `dist/CodePocket-terminal-prompt-ime-debug.apk`，22,678,342字节，SHA256 `22ff560ab19c4f3b56e005497e68c81c5d57dd49d3791978fb135ca4bbf84109`；[报告](P1_TERMINAL_PROMPT_IME_FIX.md)，证据 `verification/terminal-prompt-ime-2026-10-19/`。与旧Debug证书一致；未做本轮设备安装/运行。
- 当前实仓仍在Desktop路径，当前任务权限快照仍指向旧OneDrive路径；本轮通过获准的命令完成原库写入和构建。Windows沙箱Java ZipFS路径异常与早期审批超时均记录，不再把环境失败写成源码失败。不要重建或迁移项目，不清理.tools/dist/旧verification/设备备份。
- Android PTY/app_process/FD/Chaquopy整链、手机和平板IME/键鼠/卓易通/FPS仍未实测。动画暂停，原P1完整动画与P2/P3继续；无push/发布/远端Actions。没有python/pip/clang命令，不把系统Shell说成完整Termux发行环境。

## 历史：Python连接等待修正、移除Python预填（2026-10-18）

- 用户反馈Python一直连接后超时；已发现Shell只在View测量后创建，而代码IME可能隐藏面板，且运行只按逻辑面板开关展开。改为ShellRuntime主动创建80x24真实PTY，后续视图仅resize；运行根据实际可见状态展开并收起代码IME。没有手机日志，不把这一代码问题称为唯一设备根因。
- Python固定终端交互，删除预填输入UI/菜单/开关；旧设置忽略且保留历史stdin数据。C++仍云端预填并明确限制。新增可见启动阶段，超时指出Shell/桥/服务/运行时哪个阶段；迟到调度回调身份隔离保持。
- App328项327通过/1旧跳过，新增语言UI2项，旧被撤销模式测试改为配置兼容性；核心146缓存保持，实际打包Python29/29，模板各7，Lint0错/11旧警告，最终Debug构建1m6s，APK完整性/签名/对齐/类/JNI/旧产物检查通过。
- APK dist/CodePocket-terminal-startup-debug.apk，SHA256 45b938879b7fa15d1ee636c044c7007924ac876a64922a8ec1a0e530a8a59e76；[报告](P1_TERMINAL_STARTUP_FIX.md)，证据verification/terminal-startup-2026-10-18。没有Android链路/IME实测，无push/发布/远端Actions。动画暂停，原P1/P2/P3保留。

## 历史：真实PTY内的Python前台运行（2026-10-18）

- 基线fe4c709；[完整报告](P1_LIVE_TERMINAL.md)。用户明确要求不要再只做入口合并：已移除任务输出终端，运行接入当前Shell的app_process前台桥，经同UID/令牌认证LocalSocket+PFD把真实PTY的0/1/2交给隔离Chaquopy进程。实际输出/输入直接走成熟Termux组件，结果模型只用于问题与定位。
- 默认交互、多轮input/EOF/停止、启动/有效执行/等待独立预算及身份序号保护；保留可选每文件预填，C++真实Wandbox仍预填/完成后返回。历史不抢滚动、回底、相对偏移与不向stdin注入的清空；IDE快捷键与PTY控制键分流。动画继续暂停。
- 最终构建57s；App326中325通过/1旧跳过，核心146通过；Python源码/实际字节码各29（各16项真实管道子进程），模板各7；Lint0错/11旧警告。旧305项状态保持，1项旧任务UI断言按新需求替换。APK CRC/签名/ZIP与ELF16KB/新桥类及JNI/稳定类/许可/旧dist检查通过。
- APK dist/CodePocket-live-terminal-debug.apk，22,678,342字节，SHA256 b01843b3bce932605053fd905496e017a32068aaba2b32538531ab8ed60acafc；证据 verification/terminal-live-2026-10-18/results.json。累计上游源码补丁已放入third-party/termux/codepocket-port.patch，原许可证和UPSTREAM.json保持。
- **未运行Android PTY/app_process/FD传递/Chaquopy整链/手机平板IME与键鼠/卓易通/FPS**。不能将主机管道通过写成设备通过，也不保证全Unix作业控制或任意脱离会话子进程清理。无python/pip/clang命令，无手机C++，无新远端Wandbox调用/Actions/push/发布。
- 不再把“接Python输入桥”列为未写代码；接续为设备统一验收、P1余项及P2/P3，原需求继续保留。实际仓库仍是Desktop路径，保留.tools/dist/verification/设备备份，不清理旧材料。

## 历史：统一终端入口与多Shell会话首批（2026-10-18）

- 基线834fb97；[报告](P1_TERMINAL_WORKFLOW.md)。底部终端/问题两页，终端内部选择多个Shell或最近运行任务；稳定ID/8会话上限/新建/重命名/单独结束/初始cwd，Registry不持有Activity/View，关闭IDE释放所有Shell及准备线程池。新启动不用已关闭Executor，字体初始化修复保持。
- 显式运行选择任务，迟到任务结果不抢Shell/问题页或重开面板。每文件预填输入保持；真实stdout/stderr及退出信息同页展示，原错误安全跳转保留；菜单停止/完成竞态保护，Shell键盘/复制/错误记录与许可入口保持。Shell嵌入隐藏重复标题栏。
- 这是入口/会话统一，非所有后端已变成PTY：Shell用成熟Termux，任务仍原只读ANSI视图+真实Python/Wandbox批量结果；Python实时多轮输入/成熟任务终端渲染/命令环境未接。本地C++没有实现，不将任务结果伪装为可输入Shell。
- 最终构建1m9s，App306中305通过/1旧跳过，旧287状态保持，新增19（含实际生产面板方法/Views的Robolectric4项、Registry4、Catalog7、结果4）；Lint0错/11旧警告，Python源码/真实字节码各13/13，模板各7/7，核心145复用通过结果。完整Activity启动/测量/渲染/PTY未运行。
- 新APK dist/CodePocket-terminal-workflow-debug.apk，22,678,342字节，SHA256 7b3c0419ec05901b4502cde75a16b76a4d511d493b7bd879c9559861368e95ed。证据verification/terminal-workflow-2026-10-18/results.json；CRC/签名/ZIP对齐/关键类/未打包测试库/旧dist哈希通过，上游Termux/JNI/许可与原保存/SAF/草稿/CodeEditor/执行器/RunSession/Gradle/Actions/需求及旧测试源码未改。
- 下一批先真实Python流式/输入桥、终端滚动/退出与启动布局，再恢复动画。旧P1 UI/Sora与P2/P3继续；实机统一后置，无手机连接/Android Shell/IME/键鼠/卓易通/FPS通过声明。只有进程内会话保持，不承诺杀进程后恢复原Shell、滚动视口或子进程树退出。无新Wandbox/远端Actions/push/发布。

## 历史：Shell启动闪退复现与修复（2026-10-18）

- 用户暂停动画，要求先查明点击Shell闪退，手机无法连接。基线edf3cf4；[报告](P1_SHELL_CRASH_FIX.md)。在SDK34/Robolectric4.14.1实际执行View初始化，旧代码2项正向启动用例均在TerminalView.setTypeface:504 / ShellTerminalPane.changed:125空指针；原顺序独立异常断言也通过。原因是先字体后字号，绘制器未创建，且主线程创建段在原异常保护之外。
- 修正先字号后字体、完整视图/主线程启动保护、失败会话身份隔离和单次FAILED通知；本机最后错误限64KiB，菜单可查看/复制，只记录新版本今后捕获的故障。没有旧日志恢复承诺，不是native信号崩溃捕获器。
- 最终构建57s，App287中286通过/1旧跳过，新增9；原278状态全保留。Lint0错/11旧警告，Python源码/实际字节码各13/13，模板各7/7；核心145复用通过结果。签名/CRC/16KB ZIP对齐、稳定DEX类/未打包测试库、JNI/许可不变和旧dist哈希已检查。测试依赖仅testImplementation，默认生产依赖不变。
- 新APK dist/CodePocket-shell-crash-fixed-debug.apk，22,678,342字节，SHA256 96cd81703e515190afb16aed4e03ad1e72368edf5581c846cde3c985e055c8a3。完整失败/成功证据verification/shell-crash-2026-10-18/results.json。主机证明该Java故障并已修复；没有手机日志，Android PTY/IME等仍未实测，不声称已排除其他设备故障。
- 动画保持暂停；下一步遵照用户纠正，将旧输出与Shell整合为一个底部真实终端并保留Python/Wandbox真实功能。当前尚未完成统一界面，不再追加第二套终端或模拟运行；本地C++和P1/P2/P3仍保留。MainActivity/文件/SAF/草稿/编辑器/执行器/原JNI未改，无push/远端Actions或设备连接。

## 历史：P1 原生 Shell 终端首批（2026-10-18）

- 用户已确认“第二种”：嵌入成熟终端并连接真实Shell，撤销脚本化终端演示作为实现路线；[本批报告](P1_NATIVE_TERMINAL.md)。Termux v0.118.3/5b657c6固定源码、原生视图+协议核心+真实PTY JNI，默认App新增面板“Shell终端”标签；点击连接/system/bin/sh，不需要另装Termux。
- 单进程会话/弱UI观察、终端焦点快捷键直通、IME面板保护、输出字号/动画联动、复制粘贴/控制键/结束会话；原结果面板和Python/Wandbox执行器保持。上游5项适配及许可/归档/哈希、NDK r27c、CI构建/核心回归已接入。
- 最终构建1m 9s，App278中277通过/1旧跳过，原270保持；核心145/145，新增8启动/目录边界；Lint0错/11旧警告，Python源码/字节码各13/13，模板各7/7。APK归档/签名/ZIP对齐/类/16KB ELF/JNI/许可检查通过，旧dist哈希保持。原菜单测试失败已通过恢复紧凑菜单修复，未放宽测试。
- APK `dist/CodePocket-native-terminal-debug.apk`，22,678,342字节，SHA256 `def9ba82b438445276bda980a79dbe616c424fb833e150d117854f6b3224ab98`；证据verification/native-terminal-2026-10-18/results.json及各失败轮日志。**没有Android PTY/Shell实际运行或IME/键鼠/卓易通/FPS验证**；实机仍统一后置。
- Shell仅基础Android命令环境；尚无python/pip/本地clang，IDE Python仍Chaquopy/预填输入、C++仍Wandbox。NDK用于主机JNI构建，不是手机C++编译器；Python交互桥与本地C++原型仍待做。此前图标/设置/文件树样稿与P1动画/Sora/P2/P3保留，整体UI未完成。
- 下一批按真实终端路线继续，不再反复解释或制作脚本结果冒充运行。维护原保存/SAF/草稿保护，P0缺陷优先。无新Wandbox/远端Actions/push/发布；不要求先连设备。

## 历史：P1-C Sora C++ / Python离线语法适配（2026-10-18）

- 基线 `3a5b53c`；[报告](P1_SORA_LANGUAGES.md)。只给可选实验增加固定language-textmate0.23.7与desugar_jdk_libs2.1.5；默认生产源码/Gradle/Manifest/依赖、Actions与三份需求哈希保持。Sora核心仍0.23.7，实验stdlib2.1.21，默认1.9.25/无Sora，未整体升级工程。
- TextMateBundle读取固定VS Code1.95.3的原始Python/C++/macro/platform语法和配置，加载两套本地主题，后台预热代表语句；临时文件resolver在finally移除/关闭。SoraSyntaxController只在UI线程挂载成熟异步分析器，语法选择不换正文/历史，旋转保留选择；组合时延后，暂停/销毁取消回调，迟到加载结果关闭，失败稳定退回纯文本。超4.5万字符明确纯文本，原大草稿不截断。
- 真实语法scope/跨行/Unicode/颜色/配置回车测试新增19，Sora60/60，旧41项名称/状态保留。JVM仅转发Android Log至stderr，未默认返回所有Android API、未mock View或分词器；该桥不入APK。C++ SQL/GLSL/汇编/正则等部分外部语法/规则仍缺失，真实警告保留；非语义补全，自动词法候选关闭，等后续接现有候选/片段/参数。
- 最终实验离线构建29s、51任务18执行/33未变化；默认强制重编译1m26s、64/64任务执行，原270中269通过/1 Windows符号链接跳过。Python源码/真实APK字节码各13/13、模板Python/主机C++17各7/7。Lint默认0错误/11既有警告，实验0错误/6既有警告；两份CRC/v2签名/16KB ZIP对齐/关键Dex定义通过。
- API核对发现TextMate调用Duration，实验开启脱糖。实际Dex包含j$.time.Duration，IGrammar的Duration参数与调用表改写到j$.time；D8的TimeConversions兼容桥仍保留平台java.time引用，不能笼统断言无java.time。没有执行API24设备，不能据构建声称Android7兼容验收完成。
- 默认完整重编译少一个已核对synthetic且无悬空引用的IdeCommandMenu$EntriesMappings，当前javap直接调用IdeCommand.getEntries；其他稳定类保留。不是功能删除或体积优化。实验保留旧类；新.gitattributes保持TextMate资产原始字节/上游许可原有空白，不改Git配置；20项assets解压后与源码逐字节相同，日志测试桥未入包。旧10项APK/材料包哈希保持。
- [默认APK](../dist/CodePocket-editor-sora-languages-debug.apk)：21,879,652字节，SHA256 `b80071918c11b2eb6706a8f4415f5ef5b1b1e61f80a3468e80a54384677ecb28`。[Sora语法实验APK](../dist/CodePocket-Sora-languages-lab-debug.apk)：4,890,437字节，SHA256 `223ce8377a129553bf464b6e59b3cb10b1e8d1248f167d527c7c67b9ed95541a`。历史bundle未重新打包；匹配TextMate源码/制品/元数据在.tools，完整对应源码/重链接交付仍待正式分发前核验。
- 证据 `verification/editor-sora-languages-2026-10-18/`：results.json、所有跟踪输入哈希、源码备份、首次编译API误用/Log桩失败/两项夹具预期失败及最终日志、JUnit/Lint、首个成功APK、API原文、语法/许可来源哈希、依赖图、javap/脱糖专项及签名/对齐/字节码审计。保留全部旧测试，未删失败用例；许可入口/清单及README/进度已维护，本地提交无push。
- **下一批**：完整生产Port/导航、候选/片段/参数与SAF会话联动、默认迁移评估；不重复阶段0或已做首批。P1完整动画/手势/设置/真实Python流式以及P2/P3仍保留，P0缺陷优先。
- **实机统一后置**：无Android/模拟器、View渲染/InputConnection/组合时序/焦点/键鼠/折行/定位/手机平板/卓易通/FPS验收；无新Wandbox/远端Actions。主机语法颜色验证不等于设备实际着色或60FPS。
- 保留.tools、dist、verification和设备备份。真实仓库 `C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android`；配置OneDrive cwd不存在，显式workdir和正常权限审批，不改Git配置。

## 历史：P1-C Sora编辑事务、文档更换与历史适配首批（2026-10-18）

- 基线 `c5f5bf1`；[本批报告](P1_SORA_TRANSACTIONS.md)。原生产EditorTransactions API与文档/版本/组合保护保留，范围/长度/UTF-16/CRLF/新选区规则委托本地Java TextEdits；两组件复用，无外部依赖或Kotlin升级。
- SoraBuffer使用真实Content/Cursor及Content.getDocumentVersion，快照仅在显式命令捕获，拒绝旧实例/版本/选区/组合/关闭后请求；无变化不替换，选区变更不新增历史，替换使用原Content批次。SoraEditorAdapter已接实验插入、撤销/重做、同步草稿更换及共享锚点。实际历史/选区通知仍走CodeEditor，ContentChange监听不再另加版本计数。
- 更换草稿创建新Content/UndoManager，关闭旧实例；组合期间重置也拒绝，选区恢复避开emoji/CRLF。原临时文本原样保留，包含超限/已有不配对UTF-16；显式编辑命令仍限制20万字符并拒绝不合法替换。不把临时原型说成项目保存或进程死亡恢复。
- 默认构建 **48s**，64任务19执行/45未变化；Kotlin270中269通过/1原Windows符号链接跳过，新增6，原264项及状态保留。实验构建 **24s**，49任务20执行/29未变化；41/41，新增22，原19及状态保留。实际运行Sora Content/Cursor/UndoManager，不是View模拟。
- Python源码/真实APK字节码各13/13、实际模板Python/主机C++17各7/7；Lint默认0错误/11既有警告，实验0错误/6既有警告。两份CRC/v2签名/16KB ZIP对齐/关键Dex定义通过，默认APK保留上轮所有类；实验仅两个生成lambda改编号，无稳定类删除。旧8项APK/材料包SHA256保持。
- [默认APK](../dist/CodePocket-editor-sora-transactions-debug.apk)：22,106,015字节，SHA256 `cf5064df34b52cd1f841c714448c598f186142f0796f48291b2b61af009199a2`。[Sora事务实验APK](../dist/CodePocket-Sora-transactions-lab-debug.apk)：1,668,943字节，SHA256 `73e17c28ed44dd920fb511128b5fb5a5d4f6f8783e27abe58a04a2046a3e9aee`。默认仍Kotlin1.9.25/无Sora，实验仍0.23.7/Kotlin2.1.21；原Gradle/Actions/Manifest/文件/SAF/草稿/运行/输入事件/旧测试/三份需求哈希保持。
- 证据 `verification/editor-sora-transactions-2026-10-18/`：results.json、修改前备份/所有已跟踪输入哈希、两份验证脚本/日志、JUnit/Lint、API原文、runtime graph、签名/对齐/字节码报告、补丁及提交记录；本轮测试与构建首轮通过，未删改失败测试。README/实验README/进度/本报告已维护，仅本地阶段提交，无push/发布。
- **接续**：固定Sora语言包/语法装饰，随后完整生产Port/导航/候选/片段/参数/SAF项目会话与默认迁移评估。当前事务只接实验临时草稿，不是默认迁移完成；异步项目文档交接仍应接既有源快照门控。P1余项与P2/P3保留，P0缺陷优先。
- **实机统一后置**：无Android/模拟器、真实CodeEditor View/InputConnection、系统历史通知、IME/焦点/触屏键鼠/手机平板/卓易通/定位/FPS验收；core中的组合布尔门控是主机规则测试，不是实际输入法时序。无新Wandbox或远端Actions。完整上游对应源码/重链接交付核验仍待，没有发布。
- 保留.tools、dist、verification与设备备份；实际仓库 `C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android`，配置OneDrive cwd不存在，显式workdir并按需正常审批，不改Git配置。

## 历史：P1-C 共享弹窗定位与Sora坐标适配（2026-10-18）

- 基线 `3dbc4af`；[本批报告](P1_EDITOR_SURFACE.md)。新editor-surface本地Java库提供无View/无正文的EditorSurface锚点、裁剪与放置规则。NativeEditorPort实际实现，生产CompletionPopup/SignaturePopup改接View父节点+接口，复查文档/版本/UTF-16偏移；原CompletionGeometry API保留为委托。输入/焦点/编辑和词法候选算法未迁移。
- Sora实验通过固定0.23.7的Layout.getCharLayoutOffset真实API提供锚点，结合文本区偏移、滚动、行高与窗口裁剪；原始sources.jar SHA256与API源码已核对/保留。新增“光标定位”非补全探针，选择/编辑/换草稿/失焦/暂停关闭，滚动与布局重新定位；不是模拟IntelliSense或运行结果。
- 默认IDE构建 **1m 17s**，64 actionable tasks: 22 executed, 42 up-to-date；Kotlin264中263通过/1 Windows符号链接跳过，新增18通过，原246项及状态保留；Python源码/实际APK字节码各13/13，实际模板Python/主机C++17各7/7，Lint0错误/11既有警告。
- 实验构建 **32s**，49 actionable tasks: 24 executed, 25 up-to-date；19/19，原14个真实Content/Indexer/UndoManager用例保留，5项新测试运行真实Content及实际适配数学函数；未运行Sora View。实验Lint0错误/6既有警告，无元数据不兼容日志。
- [默认APK](../dist/CodePocket-editor-surface-debug.apk)：22,106,015字节，SHA256 `0a8c53bc85b83c586a92b640b824d21b19ded88eea0a6b9613671c657b7f3fb9`。[Sora定位实验APK](../dist/CodePocket-Sora-surface-lab-debug.apk)：1,650,260字节，SHA256 `8944ece94e994acb25ea44ab5b536c1be9bea885b6da83fc133c99f653fa9d21`。两份CRC/v2签名/16KB ZIP对齐/关键Dex定义通过；上一各自APK类定义全部保留，旧六项APK/材料包SHA256保持。历史材料包不是新增定位源码的打包版本，新源码在当前仓库。
- runtime graph实测：App仍Kotlin1.9.25，无Sora，仅新增本地Java surface；实验仍Sora0.23.7/Kotlin2.1.21，新增同一Java库。没有升级整个Kotlin工程、压低库stdlib或跳过元数据检查。两模块Gradle仅增本地project依赖，settings包含该库；原Actions命令仍自动构建它，未远端执行。
- 证据 `verification/editor-surface-2026-10-18/results.json`、两份验证脚本/日志、JUnit/Lint、runtime-dependencies.log、精确API源码/哈希与APK报告。保存/SAF/草稿/运行/Python服务、CodeEditor输入事件、原编辑/事务/导航规则、旧测试、Manifest/root Gradle版本/Actions/原需求哈希保持；无新权限或存储迁移。
- **接续**：Sora文本/选区事务、文档更换与历史接口，再接语言包和现有候选/片段/参数/SAF会话。当前仅Sora定位适配，默认仍原生；完整动画/手势/帮助许可/真实流式以及P2/P3保留，P0缺陷优先。
- **实机统一后置**：未运行Android/模拟器、Sora实际布局/InputConnection、窗口坐标/IME遮挡/焦点/RTL/滚动、手机/平板/卓易通或FPS验收；无新Wandbox/远端Actions。新数学用例与1000个采样场景不能证明设备定位或60FPS。完整上游源码/重链接交付核验沿用历史待办，没有发布。
- 保留.tools、dist、旧verification与设备备份，仅本地阶段提交，无push。真实仓库 `C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android`，配置OneDrive cwd不存在，显式workdir并按需正常审批，不改Git配置。

## 历史：P1-C 文档交接保护首批（2026-10-18）

- 基线 `fa5cd60`；[本批报告](P1_EDITOR_DOCUMENTS.md)。原生适配器实现DocumentEditorPort的交接快照/显式替换；EditorDocumentTransitions在文件/工作区读取完成时校验原缓冲/编辑版本/组合状态，请求一次性消费。默认仍为原生编辑器，未迁移Sora。
- 修复switchFile/reloadActive/startWorkspace后台读文件期间继续输入会被旧结果覆盖的风险；双阶段“保留草稿后切工作区”同样门控。源码改变保留缓冲、排队草稿与自动保存；stdin/选区/滚动/焦点取交接前最新状态，同一工作区重载复用最新会话。活动标签仅在替代文件实际载入后关闭，原生拒绝替换时还原路径与标签列表，最后一个标签保留并说明。
- 原生替换先取消旧导航与组合结束通知，暂时不提供快照，结束后重新观察新Editable并清除装饰缓存。quietEdit使用try/finally恢复，加载成功才增加编辑版本。已确认删除发生后才输入的新代码会排队为恢复草稿；不声称能撤销磁盘删除或保证草稿写入失败时的数据持久性。
- 最终构建 **1m 10s**，62 actionable tasks: 18 executed, 44 up-to-date；Kotlin246中245通过/1 Windows符号链接跳过，新增20通过，原226项及状态保留。Python源码/实际APK字节码各13/13，实际模板Python/主机C++17各7/7，Lint0错误/11既有警告。
- [最新APK](../dist/CodePocket-editor-documents-debug.apk)，22,106,581字节，SHA256 `7d38f8d3c32c9802879670e787390a793065b870789f2093703379d213a46fe4`；CRC、v2签名、16KB ZIP对齐、ABI/关键Dex定义通过，旧具名类保留。增加可用性lambda导致buildEditor匿名监听类重编号，审计核对该方法除构造参数变更外源码相同。旧五项APK/材料包哈希保持。
- 证据 `verification/editor-documents-2026-10-18/results.json`；首轮1m11s/244项及APK保留在round-01，补同工作区会话/失败后自动保存，再补原生拒绝替换时路径与标签回滚后最终重跑；中间20项成功版本保留在round-02。无失败测试被移除/跳过；更新README、进度与本报告，本地阶段提交，无push/发布。
- CodeEditor输入事件、Workspace/SAF/DraftStore/EditorSessions格式、运行/Python服务、旧规则/旧测试、Gradle/Manifest/Actions及三份需求输入哈希保持。保留.tools、旧dist/verification和设备备份。
- **下一批**：Sora生产组件与语言包适配、候选/片段/参数/SAF会话联动及其余文档生命周期。当前仅文档交接首批，不等于完整EditorPort/Sora迁移。P1其余手势/动画/帮助许可/真实Python流式及P2/P3继续保留，P0问题优先。
- **实机统一后置**；没有Android/模拟器运行、原生UndoManager/IME/文档替换/焦点/布局/FPS/手机平板卓易通验收，无新Wandbox或远端Actions。主机真实文件读取+生产门控回归不等于Android输入法时序验收；删除后回退读取失败等恢复分支仍需专项联合检查。
- 实际仓库 `C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android`，配置OneDrive cwd不存在；显式指定真实workdir，正常审批沙箱升级，不改Git配置。

## 历史：P1-C 生产选区、视口与错误导航（2026-10-17）

- 基线 `241e0d1`；[本批报告](P1_EDITOR_NAVIGATION.md)。现有原生适配器实现 `NavigatingEditorPort`，查找/项目搜索、跳行、片段字段、会话与面板恢复、IME显露光标及错误定位已实际接入。默认未迁移Sora。
- 修复迟到滚动覆盖后续触摸/键鼠/选区、布局前恢复被截断、跨文件/工作区旧请求、保存选区拆分emoji/CRLF，以及超范围错误行落在末尾的问题。轻量视口戳不复制正文；一次性pre-draw队列复核文档/版本/选区/组合/交互/可见性；暂停/关闭撤销待执行回调。
- RunSession只增加入口文件稳定key/实际运行源码摘要并附回结果，Python/Wandbox运行算法与服务IPC保留。入口定位核对身份/内容；导入模块明确提示仅按行定位，尚无其运行版本证明。只定位首个识别出的错误，未增加语义诊断列表。
- 最终构建 **48s**，62 actionable tasks: 18 executed, 44 up-to-date；Kotlin226中225通过/1 Windows符号链接跳过，新增30全部通过，原196项及状态保留。Python源码/真实APK字节码各13/13，实际模板Python/主机C++17各7/7，Lint0错误/11既有警告。
- [最新APK](../dist/CodePocket-editor-navigation-debug.apk)，22,103,110字节，SHA256 `32141df155167f58c2c7837c56ecb4c7a2f325510d42c3813c4633d050baca9f`；CRC、v2签名、16KB ZIP对齐、ABI和关键Dex类定义检查通过；旧具名类定义保留，D8生成回调类数量/编号变化单独记录。旧默认/实验APK和材料包哈希保持。
- 证据 `verification/editor-navigation-2026-10-17/results.json`。保留首次测试编译失败（跨模块可空属性smart cast）、两项Windows夹具失败（renameTo无法覆盖）、中间成功及无障碍补修前日志/APK。测试夹具改用仓库现有原子替换方式；触摸监听改至原生dispatch并补performClick委托，消除新增Lint警告，没有合成点击；没有移除或跳过失败测试。
- Gradle/Manifest/Actions、Python源码/服务、文件/SAF/草稿/配置与原需求保持输入哈希。无新依赖/权限/存储迁移/push/发布；`.tools`、dist、旧证据与设备备份不清理。更新README、进度与接续，本地阶段提交。
- **下一批**：完整文档/会话生命周期与组件适配，随后语言包与候选/片段/参数/SAF联动、默认迁移评估；P1其余手势/动画/设置帮助许可/真实Python流式，P2语义与本地/多文件C++、P3继续保留。不要把首批导航等同于完整Port或迁移完成。
- **实机统一后置**；未运行Android/模拟器、pre-draw与滚动/焦点/IME/键鼠/系统历史、手机/平板/卓易通或FPS验收；没有新Wandbox/远端Actions证据。主机队列/路径/规则测试不证明设备体验。
- 实际仓库 `C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android`，配置OneDrive cwd不存在；显式指定真实workdir，按需升级沙箱，不绕过审批或改Git配置。

## 历史：P1-C 生产编辑事务与历史入口首批（2026-10-17）

- 基于 `f366ed1` 增量开发；[本批报告](P1_EDITOR_TRANSACTIONS.md)。默认原生编辑器接入统一显式事务，覆盖候选/片段、缩进、符号插入、查找替换及键入后的自动修正；只读分析接口保留，Sora仍是独立实验。
- 修复超限缩进被拒后仍标脏、无变化操作增加版本的问题；事务核对文档/版本/内容/选区/组合状态、20万字符与UTF-16/CRLF边界。查找替换提交原始快照，全部替换允许仅光标移动。原查找的长度预检保留，并非本轮才实现。
- 系统撤销/重做统一保护入口；独立stdin保留自己的历史。增加原生历史来源标识，历史恢复不再被重新自动补括号/缩进。新CRLF边界门控同时配套调整多行缩进，避免拒绝正常Windows换行文件。
- 最终构建 **1m 10s**，62 actionable tasks: 18 executed, 44 up-to-date；Kotlin196中195通过/1 Windows符号链接跳过，新增23全部通过，原173项和状态保留。Python源码/真实APK字节码各13/13，实际生成模板Python/主机C++17各7/7，Lint0错误/11既有警告。
- [最新APK](../dist/CodePocket-editor-transactions-debug.apk)，22,101,433字节，SHA256 `6c84eda6c3bc92966af33b732ec10750050d74fe8304444fdb291465bab48689`；CRC、v2签名、16KB ZIP对齐、ABI及新类定义检查通过，上一默认APK的全部类定义保留。证据`verification/editor-transactions-2026-10-17/results.json`，首轮/最终日志和旧APK保留。
- 原文件/SAF/草稿/运行模块、Gradle/Manifest/Actions、配置和原需求未改；不增加依赖或权限，无push/发布。更新README、进度与接续说明，本地阶段提交；`.tools`/设备备份不清理。
- **仍需接续**：生产Port的独立选区/视口/诊断及完整会话、组件适配；当前不能称完整Port/默认Sora迁移完成。然后继续编辑手势/完整动画、设置与真实Python流式会话，P2/P3不丢失。
- **实机统一后置**；本轮没有Android运行时、原生UndoManager、IME/键鼠/截图/FPS/卓易通、新Wandbox或远端Actions验证。纯事务和历史规则测试不等于真实系统历史/输入法联合验收。
- 实际仓库`C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android`；配置OneDrive cwd不存在。操作指定真实workdir，按需沙箱升级，不改Git配置。

## 历史：Sora隔离原型已做主机验证（2026-10-17）

- 基线`ffe8249`；[报告](P1_EDITOR_LAB.md)。同一Gradle工程的可选`editor-lab`模块，仅`-Pcodepocket.editorLab=true`启用；独立包名/临时草稿，原生/Sora基础编辑、主题和光标动画比较。默认IDE代码/编辑器/文件/SAF/运行与依赖保留。
- 实验固定Sora0.23.7；真实Content/Indexer/UndoManager14/14，Gradle 27s，Lint0错误/6警告。0.24.4直接接入出现Kotlin2.2/1.9元数据失败，Java原型下旧Lint也有不兼容日志，已弃选；没有屏蔽元数据检查或升级全项目。
- 默认IDE强制构建1m 56s、62/62任务执行；173项Kotlin中172通过/1 Windows符号链接跳过、Python源码/实际APK字节码各13/13、模板各7/7，Lint0错误/11警告。两个APK的CRC/v2签名/16KB ZIP对齐/关键类定义、依赖/源码保护已核对。
- [默认APK](../dist/CodePocket-editor-native-debug.apk)：21,861,894字节，SHA256 `7408f728e5ada1a0b07208d314ea15b31e73cfa06b506e6d8f33cebf7c0ec4a2`；[实验APK](../dist/CodePocket-Sora-editor-lab-debug.apk)：1,650,202字节，SHA256 `15ad39d0969a186b8f67582bbbc9cb798e86b93480604cf78afb1a27e4e44e01`。另有[实验源码与许可材料包](../dist/CodePocket-Sora-editor-lab-bundle.zip)；证据`verification/editor-lab-2026-10-17/results.json`，原失败/中间日志保留。
- App的83项原输入哈希未改；默认runtime为Kotlin1.9.25/无Sora，实验为Sora0.23.7/Kotlin2.1.21。原Actions未改，新增手动实验workflow通过YAML解析，没有远端执行/push/发布。原需求与工具/设备备份继续保留。
- 默认APK强制重编译后Dex分片重排，少一个已验证无引用的synthetic枚举辅助类；其他类定义保留。Python bytecode payload相同，仅头部8/9/10及构建元数据不同；不是删除功能或体积优化。
- **下一批继续完整生产EditorPort事务/历史/视口/诊断契约，再接语言适配与现有候选/片段/参数/SAF会话。** 隔离原型已做，但尚未接生产Port/语言包/LSP或默认迁移；再继续其他P1手势/动画/设置/真实Python流式会话，P0缺陷优先，P2/P3保留。
- 实机统一后置，不请求设备；本轮无Android运行时/截图/IME/蓝牙键鼠/FPS/新Wandbox证据。实验只有临时草稿，未运行用户项目。源码材料含Maven sources.jar，完整上游仓库归档下载未取得，正式许可/对应源码/重链接交付仍需核验。
- 实际仓库`C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android`；配置的OneDrive cwd不存在，操作指定实际workdir并按需沙箱升级，保留.tools/dist/verification，不改Git配置。


## 历史：P1-C 编辑分析首批已验证（2026-10-17）

- 用户继续开发，基于 `62c3750` 接续；[本批报告](P1_EDITOR_ANALYSIS.md)。保留原生 CodeEditor，已实际接入只读快照/装饰 EditorPort、后台高亮/括号扫描、缓存和迟到结果保护，补组合区结束监听，修复已测试复现的长字符串栈溢出。
- 新增20项Kotlin全部通过；完整173项中172通过/1 Windows符号链接跳过。构建1m 8s，62任务19执行/43未变化；Python源码/实际APK字节码各13/13、生成模板各7/7，Lint0错误/11警告；CRC/ABI/v2签名/16KB ZIP对齐及关键Dex类定义通过。
- [最新 APK](../dist/CodePocket-editor-analysis-debug.apk)，22,033,627字节，SHA256 `3ee15564b1bd745d786cde8af8318f5b04ba80c602204504029a3d0f7bb1001c`。证据 `verification/editor-analysis-2026-10-17/results.json`；首次失败日志保留，前后源码及保护输入哈希已记录。上批APK和证据保留。
- 文件/SAF/草稿/独立stdin/运行控制、候选/片段/参数核心、Gradle/Manifest/Actions及三份需求未变；无新依赖/权限/迁移/push/发布。颜色最多45000字符/6000span，超限正文降级；仍为词法扫描。
- **P1-C整体未完成**：目前Port只有读快照/装饰；完整事务/历史/视口/诊断契约及固定Sora制品/隔离页面待接续，不称已迁移或已验证组件兼容。然后继续手势/完整动画、设置与真实Python流式后端，P2/P3原需求保留，P0缺陷优先。
- 实机统一后置，不再请求设备。本轮没有Android运行时/IME回调/原生历史/截图/FPS/新Wandbox网络/远端Actions证据。不要把纯状态机/工作线程测试当作设备输入体验验收。
- 实际仓库 `C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android`；配置OneDrive cwd不存在。显式指定实际workdir，需要沙箱升级时正常使用，不修改Git配置；保留.tools/dist/verification及设备备份。


## 历史：UI 与操作逻辑接续已验证（2026-10-17）

用户因 Claude 额度中断返回，要求继续；网络中断后已恢复。本批在 `a6eaec1` 和原未提交修改上完成接续，未重建项目。实机统一后置仍有效，不请求手机。

- [本批报告](UI_RESUME_2026-10-17.md)：短全局/上下文菜单、文件与编辑入口、键盘上方符号栏、真实面板操作、返回保存保护、侧栏复用/拖动/消息状态修复、后台项目搜索与结果定位、结束后受限SGR输出。无“即将实现”占位动作。
- 接手真实基线133项中2失败/1跳过；最终153项中152通过/1跳过，新增20项。构建1m 9s，62任务中24执行/38未变化；Python源码/真实APK字节码各13/13、模板各7/7，Lint0错误/11警告；APK签名/CRC/ABI/16KB对齐通过。
- [最新APK](../dist/CodePocket-UI-resume-debug.apk)，22,033,037字节，SHA256 `65cea4b23f29d0664a0046f67e2354090bd379ccd878a16171e2d4233588765a`。证据 `verification/ui-resume-2026-10-17/results.json`；原未提交补丁、ANSI原型、设计、失败日志和逐项哈希已备份。
- CodeEditor/编辑规则、保存/SAF/草稿、Python/Wandbox运行、候选/片段/参数核心、Gradle/Manifest/Actions与三份需求保留。没有新依赖/权限/存储迁移，没有远端Actions/push/发布。
- 手机/平板/鸿蒙卓易通、IME/键鼠/原生撤销、焦点/拖动/返回与帧率未设备验收。当前是预填输入、结束后输出；SGR不是完整VT或流式运行。
- 下一步继续P1：编辑器Port/Sora隔离评估、高亮/手势/完整动画、流式后端专项；P0问题优先。P2语义/本地多文件C++与P3仍待做。不要把旧P1-C报告的名称当成原路线全部完成。
- 实际仓库仍是 `C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android`；OneDrive配置路径不存在。保留.tools/dist/verification及设备备份。该路径在Codex当前沙箱下读Git工作树也需升级权限，不能误判为裸仓库或损坏后修改Git配置。

## 历史：Claude P1-B / P1-C 接续前状态

`92c399f` 开发响应式布局与状态栏；`a5825a3` 开发动画/搜索模块；`a6eaec1` 更新P1-C报告。原报告保留；当前测试结果以本批日志为准。P1-B记录见 [P1B_RESPONSIVE.md](P1B_RESPONSIVE.md)，P1-C记录见 [P1C_ANIMATIONS_SEARCH.md](P1C_ANIMATIONS_SEARCH.md)。

## 历史：P1-A 外壳主机验证完成（2026-10-17 早前）

用户在新完整需求审查后回复“继续”，已确认并执行 P1-A；不要再停在待确认状态，也不要重做阶段 0。全部实机测试按用户要求统一后置，不请求设备。

- 基线 `18bf61f`；本批 [P1A_SHELL.md](P1A_SHELL.md)：单排主栏、命令菜单、原创图标/令牌、收纳常驻说明/预设输入，保留原编辑器与运行/文件实现。命令在展示和执行时检查焦点/组合区/忙碌状态；Ctrl+O 仍导入副本。
- 最终强制构建 1m 26s / 62 任务；Kotlin114中113通过/1跳过（15新增通过），Python源码/实际APK字节码各13/13，真实生成模板各7/7，Lint0错误/11警告；签名/CRC/ABI/16KB对齐/8个UI类打包检查通过。
- [最新 APK](../dist/CodePocket-P1A-shell-debug.apk)，21,794,722 字节，SHA256 `44773506fbf7937d176e8eae3e4bb23a6fedd84c81fc59fbf6f46d7c2ac23ea9`；证据 `verification/p1a-shell-2026-10-17/results.json`。失败首轮与中间日志保留。
- 原61项保护输入只改MainActivity/strings；CodeEditor、文件/SAF/草稿、runner、旧测试、Gradle/Manifest/Actions及三份需求原文保留。没有新依赖、设置schema或存储迁移。
- 本批无设备/模拟器/截图/帧率/卓易通/新Wandbox请求；当前UI、原生撤销、菜单回焦、IME/键鼠与stdin/SAF联合行为尚未实机验证。不能把模型预算检查写成设备适配通过。
- 下一批 P1-B：净可用尺寸/横屏平板、Activity Bar、可调Explorer、底部优先运行面板与简洁状态；P0问题优先穿插。P1整体、Sora、完整动画、项目搜索、真实流式终端、P2/P3仍未完成；无push/发布。
- 实际仓库仍为 `C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android`；配置cwd的OneDrive路径不存在，所有命令指定真实workdir。该路径写入/构建需工具沙箱升级。保留verification、dist和.tools，勿清理旧设备备份。

## 2026-10-17 审查记录（历史）

新需求按要求先审查，未修改业务代码；客观结论见 [审查](UI_AUDIT_2026-10-17.md)，原业务基线 `b935079`、[审查 APK](../dist/CodePocket-UI-audit-baseline-debug.apk) 保留。原99项Kotlin中98通过/1跳过，源码与字节码各13项、模板各7项通过。在线Wandbox仅第一例成功，第二例TLS EOF、第三例未执行；不是全通过。当前已获“继续”确认并完成P1-A主机阶段。

## P1 第五批内置参数参考记录（2026-10-17，历史）

前一批提交 `b688871`。本批在现有编辑器上增量开发，详见 [参数参考报告](P1_SIGNATURE_HELP.md) 和 [进度表](DEVELOPMENT_PROGRESS.md)，不重建项目；实机统一后置。

- Python 7 项、C++17 5 项带来源的内置签名资料；光标括号内参数位置、参考切换、只读弹窗、Ctrl+Shift+Space/触屏入口、独立自动设置。没有语义类型/重载解析；复杂语法保守降级。
- 初次构建颜色名与 View 属性遮蔽报错，已修正；最终 `BUILD SUCCESSFUL in 1m 3s`。Kotlin 99 项中 98 通过、1 跳过（新增 16 项通过）；Python 源码/APK 字节码各 13/13，既有真实生成模板 Python/C++17 各 7/7；Lint 0 错误、13 警告。
- [本批 APK](../dist/CodePocket-P1-signatures-debug.apk)，21,835,664 字节，SHA256 `c3fd49ba556eb76e259d8c71f53333b76f9b0a77623ffbff747a69a1529db638`；签名/CRC/ABI/对齐和关键类打包通过，证据 `verification/p1-signatures-2026-10-17/results.json`。
- 原需求、Gradle、Manifest、CodeEditor、文件/SAF/保存、runner、原片段/词法候选及 Actions 保持原哈希。配置 version 3，20 个字段；Actions 原 Kotlin 测试步骤包含新测试，未远端运行或 push。
- 本批无设备/ADB、在线 Wandbox、帧率或卓易通测试；popup/中文组合/键鼠/保存和原生撤销需最终联合验收。主机模板编译不代表 Android 本地 C++ 或语义服务已实现。
- 下一批继续 P1 状态栏与编程快捷键栏，其后继续高亮、文件树调宽、项目搜索、完整动画和流式程序终端；P2/P3 未开始。发现 P0 缺陷优先回修。


## P1 第四批内置片段记录（2026-10-17，历史）

继续按 P1 → P2 → P3 分批推进，实机统一后置，不重建项目。前一批提交 `57eb82b`；本批详见 [P1_SNIPPETS.md](P1_SNIPPETS.md) 和 [进度表](DEVELOPMENT_PROGRESS.md)。

- C++ / Python 各 6 个内置片段、Tab/Shift+Tab 字段导航、Esc 结束、触屏菜单；实际编辑追踪 UTF-16，守住文档/版本、输入法组合与 stdin 焦点边界。普通候选优先，片段明确不是语义补全。
- 最终构建 55s，Kotlin 83 项中 82 通过、1 跳过（新增片段 12 项通过）；实际 Kotlin 展开模板通过真实 Python 执行器 7 个、主机 C++17 编译执行 7 个；源码/APK 字节码各 13 项；Lint 0 错误、13 警告。
- [本批 APK](../dist/CodePocket-P1-snippets-debug.apk)，21,817,451 字节，SHA256 `c285ced69595ba4f3537bcecadb20e04f7df3a805433da84f655ebe9e839ed1f`；签名/CRC/ABI/对齐和关键类打包通过。证据 `verification/p1-snippets-2026-10-17/results.json`。
- GitHub Actions 增加相同主机模板回归，Debug 下载步骤保留；没有远端 Actions 或 push。主机 g++ 没有打包进 App，C++ 仍走 Wandbox，本批没有在线请求。
- 未进行设备/ADB、原生撤销、中文 IME、popup/键鼠联合或 60 FPS 验收；不能据模型/主机测试写成设备适配通过。原生 CodeEditor、Gradle、Manifest、runner 与文件保存实现未修改。
- 下一批继续可靠来源的内置函数调用参数提示，其后推进其它 P1 余项。完整动画、项目搜索、流式终端、P2 真实语义/本地与多文件 C++、P3 仍待做；保持先修 P0 缺陷。


## P1 第三批运行面板记录（2026-10-17，历史）

继续开发、统一后置实机；不重建项目或重做阶段 0。前一批补全提交 `f7a57e8` 保留；本批见 [P1_RUN_PANEL.md](P1_RUN_PANEL.md) 和 [进度表](DEVELOPMENT_PROGRESS.md)。

- 新增实测宽度停靠的面板、原生分隔条拖动/菜单尺寸、最大化/恢复/收起、底部与侧边独立偏好、IME 和隐藏视口保护；输出标签可横向滚动，紧凑布局保留清空当前 stdin 菜单。
- 最终构建 1m 3s；Kotlin 71 项中 70 通过、1 跳过（新增面板 10 项）；Python 源码/实际 APK 字节码各 13/13；Lint 0 错误、13 警告；签名/归档/ABI/对齐及新类打包通过。
- [本批 APK](../dist/CodePocket-P1-panel-debug.apk)，21,801,591 字节；SHA256 `0e995cdd58ef57ef66d6f23bc9299ccbe3f29eacc7e9d69c4c6e6851d144cba8`。证据在 `verification/p1-panel-2026-10-17`，含脚本、各轮日志、报告及源文件哈希。
- 本批没有 ADB/实机或远端 Actions。面板手感、IME/焦点/选区恢复、无障碍、60 FPS 和卓易通仍待统一联合验收；不能据主机测试写成适配通过。
- 下一批继续内置片段/占位符，再推进其它 P1 余项。当前仍为预填 stdin、结束后输出、词法补全；真实流式终端、完整动画和 P2 语义未实现。无 push/发布。


## P1 第二批记录（2026-10-17，历史）

按用户要求，继续分批开发，所有功能完成后统一实机。第一批已保存本地提交 `8cff187`；第二批详见 [P1_COMPLETION.md](P1_COMPLETION.md)，总体 [进度表](DEVELOPMENT_PROGRESS.md) 已更新。

- 新增光标锚定原生词法候选列表、触屏/键鼠导航设置、后台请求过期保护；修复词中补全重复后缀及接受错误候选，软键盘和 stdin 保留原生输入路径。
- 最终构建 53s，Kotlin 61 项中 60 通过、1 跳过；Python 源码与实际 APK 字节码各 13/13；Lint 0 错误、13 警告；签名、CRC、ABI、对齐和新类打包检查通过。
- [新 APK](../dist/CodePocket-P1-completion-debug.apk)，21,786,370 字节，SHA256 `00ffbf0a5918e3e3960cfae4b2e9b46fa02f98a6880e2855058824d41c798024`；验证证据 `verification/p1-completion-2026-10-17`。旧 APK 与既有设备备份保留。
- 本批没有实机/ADB 或远端 Actions；补全是词法，成员语义明确待 P2。片段、持续调用签名、完整动画和可调面板等仍未完成；不要将 P1 整体标为完成。
- 下一批继续可拖动/最大化面板和片段。代码输入、独立 stdin、文件/SAF 安全优先，不重做阶段 0、不重建项目；无 push 或发布。


## P1 第一批记录（2026-10-16，历史）

用户最新指示：“等所有的都弄完了再实机吧，继续”。**实机统一后置，不再作为阶段推进前置条件**；以下历史记录中的“先实机/进入 P1 前确认”不再是当前阻塞点。原需求和优先级不变，进度见 [DEVELOPMENT_PROGRESS.md](DEVELOPMENT_PROGRESS.md)。

- 已完成 P1 设置、独立语法主题/字号/行号/换行、2/4/8 缩进与输入开关、当前文件查找替换/跳转行、基础工作区淡入。详见 [本批报告](P1_SETTINGS_SEARCH.md)。
- 最后构建 54 秒；Kotlin 53 项中 52 通过、1 跳过；Python 源码/打包字节码各 13 项通过；Lint 0 错误、14 警告，APK 签名/CRC/ABI/对齐通过。
- 新产物：[CodePocket-P1-settings-search-debug.apk](../dist/CodePocket-P1-settings-search-debug.apk)，21,765,550 字节；SHA256 29dff4a9771cc325d68a056a49e61b0f61e8de18a684e2f73e1f4e9386a7dc6c。证据在 verification/p1-settings-search。
- 本批没有连接设备、安装 APK 或测帧率。上轮 P0 单台手机记录仍保留；完整兼容和输入体验不能据主机结果宣称通过。
- 下一批继续 P1 光标锚定补全列表、键鼠配置和可调面板；其后继续终端/动画/高亮，再进 P2 真实语义、多文件及本地 C++ 原型。不要重做已完成的阶段 0/P0，不重新创建项目。


## P0 回修时点记录（2026-10-16，历史）

P0 后续回修和主机验证已完成，本轮按用户选择先做主机测试；**最新 APK 未安装到手机，P0 实机验收未完成，P1 未开始**。详见 [本轮验证报告](P0_FOLLOWUP_2026-10-16.md)。下方初轮与增补记录保留其当时结论。

- 新 APK：[CodePocket-P0-resume-2026-10-16-debug.apk](../dist/CodePocket-P0-resume-2026-10-16-debug.apk)，21,738,146 字节；SHA256：04f9be7a951a0ecf370e96e190810a9a8a2339b531c7219d32fde7b2779af83b。
- 完整构建 54 秒；Kotlin 41 项中 40 通过、1 跳过；Python 源码/打包字节码各 13/13；Lint 0 错误、14 警告；签名/CRC/ABI/对齐通过。证据在 verification/p0-resume-2026-10-16。
- 修复同内容旧 checkpoint 引发的假冲突及 Bundle 重复标脏；导航、工作区与稳定文件 ID 在操作完成后一起排队保存，减少强制结束返回旧活动文件的问题；SharedPreferences 异步落盘仍存在极短窗口。
- 上轮手机为 25060RK16C / Android 16，已覆盖安装和冷启动、真实本地 Python 输出、2 个 py 的独立 stdin、数字输入自动保存。**旧 checkpoint 恢复测试重启回 main.cpp，失败；其后的旋转、停止、traceback 未执行，不能算通过。**
- 本轮初始 ADB 无设备。下次接入先复测活动标签落盘、强制结束/恢复后继续保存，再做横屏、Python 停止/错误定位、真实 SAF；只操作独立测试项目，现有数据备份在 .tools/device-backups。
- 原需求、Gradle、Manifest、Actions、runner.py 未改；P1 动画/候选 UI、P2 语义服务计划保留。本地已单独提交增补计划 a667a13；本轮 P0 代码另行留存，不 push/发布。

## 初轮 P0 记录（2026-10-15 主机验证时点）

2026-10-15：本轮 P0 实现、主机测试和 Debug 打包已完成；**设备验收未完成，P1 尚未开始**。下一步优先按 [P0_DEVICE_TESTS.md](P0_DEVICE_TESTS.md) 验证手机、平板和鸿蒙卓易通；进入 P1 前等待用户确认。

- 当前目录：C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android。原 OneDrive 桌面路径失效。
- 本轮 APK：[CodePocket-P0-debug.apk](../dist/CodePocket-P0-debug.apk)，21,737,438 字节，20.73 MiB，API 24+ / arm64-v8a。
- SHA256：0fcec1617a9bfff419239530b8a77d941b77e5c5e6636b8d9fcb957486d3af46。
- 最后完整构建 build-06-snapshot-final.log：BUILD SUCCESSFUL in 51s；62 任务，18 执行、44 未变化；Kotlin 35 项中 34 通过、1 Windows 符号链接用例跳过；Lint 0 错误、14 警告。
- Python 源码与实际 APK 字节码各 13/13 通过；签名/CRC/ABI/对齐通过；实际 Manifest 含私有 :python 服务；本次 Wandbox 预置三程序探测通过。
- ADB 无设备，没有 Android App 启动、IME、SAF 提供方或独立进程中止的设备运行证据。主机测试不能替代这些验收。
- Git 已建立阶段 0 基线 3094bfa，之后单独记录 P0；无远端、无 push/发布，GitHub Actions 未远端运行。

实现及限制详见 [P0_VERIFICATION.md](P0_VERIFICATION.md)。需求原文和历史 [阶段 0 审查](STAGE0_AUDIT_2026-10-15.md) 保留。

关键实现：稳定文件会话及独立 stdin、收键盘/小窗口布局、SAF 真实目录与最近项目、后台保存及磁盘草稿、外部冲突/部分写入恢复、完整 Python 运行资源快照、独立 Python 进程及中止、C++ 请求取消与错误分类/迟到结果隔离。

重要边界：外部编辑保存写回提供方；Python 在私有完整快照中执行，其运行产物不自动回写。SAF 不保证原子替换/所有移动能力，存储提供方没有统一强制超时。Python 6 秒字节码/10 秒父级总限时；C++ 45 秒父级等待；当前仍是预填 stdin 和结束后输出，不是实时终端。

本机构建：

```powershell
.\scripts\build-local.ps1 -ProxyHost 127.0.0.1 -UseLocalMirror
```

脚本使用 .tools 内便携 JDK/SDK、完整 Python 3.13.9 和依赖缓存；不能换回缺少 venv 的最小 Python。临时镜像仅在 .tools，正式依赖源不变。日志和报告快照位于 verification/p0，旧 APK 和阶段 0 产物保留。

## 2026-10-15 需求增补（仅文档）

- 已将动画系统、补全列表/按键/片段界面纳入 P1，将 C++ / Python 深度语义补全纳入 P2；详见 [增补计划](P1_P2_ANIMATION_COMPLETION_PLAN.md)。原有各阶段需求保留，与只读原始需求共同生效。
- 要求动画总开关及系统减少动画优先、平滑光标与面板过渡、候选随光标并避让 IME；性能以 60 FPS 为目标，输入、选区与文件安全优先。
- Sora、clangd、Jedi 等尚未选型、集成或验证；当前 EditText + 词法候选的能力没有变化。P2 必须用真实语言分析提供成员/参数/类型信息，失败时明确词法降级。
- 本次未改源码、Gradle 或 Actions，未新增 APK/重新构建；上文构建结果属于前一轮 P0。手机、平板、卓易通的动画/语义/键盘联合验收均待实施，不得据此标为通过。
- 接续点不变：先完成 P0 设备验收及回修，进入 P1 前等待用户确认；不重做阶段 0，不在本次增补中启动编辑器迁移。原始需求末尾的阶段 0 启动语是历史指令，不改变当前接续点。