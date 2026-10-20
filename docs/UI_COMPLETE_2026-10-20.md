# CodePocket 完整 UI 改版与交互修复（2026-10-20）

> 后续缺陷记录：本页旧APK的6个矢量图标包含非法路径，已复现启动闪退。请使用[启动修复版及验证报告](UI_STARTUP_CRASH_FIX_2026-10-20.md)。本页保留原交付记录；此前472项旧图形测试不能作为原生图标解析/完整启动通过的证据。

本批在基线 `4e4fd34b` 上完成计划内的原生界面代码、主机回归与 Debug APK 交付。**实机输入、实际像素效果和帧率没有在本轮验证。** 原 P2/P3 与深度语义补全继续保留。

- [Debug APK](../dist/CodePocket-ui-complete-2026-10-20-debug.apk)：22,682,264 字节（21.63 MiB），Android 7.0+ / arm64-v8a，Debug，包名 `com.codepocket.ide`。
- SHA-256：`f07346856373dea64e9dad2f1cfa899753ebb0b1c4c284b5ac8dcbb194af7873`。
- Debug证书与动画版一致：`d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`。
- [完整可点击设计预览](ui-preview/index.html)、[预览边界与浏览器截图](ui-preview/README.md)、[全部界面清单](UI_SURFACE_INVENTORY_2026-10-20.md)。预览是明确标注的示例，未代替真实 App 运行。

## 实际修改

| 分组 | 文件与行为 |
| --- | --- |
| 工作台 | MainActivity、MainToolbar、EditorToolbar相关承载、ActivityBar、EditorTab、DesignTokens、IdeControls、IdeIcons：统一灰蓝/浅色、类型图标、路径、终端快捷入口；编辑图标与纯符号栏分开 |
| 二级界面 | 新 WorkbenchMenu / WorkbenchStyle，DialogMotion；所有 App 内菜单、列表、输入/确认/错误/帮助窗口接入，保留原命令身份及操作前校验；搜索、补全和参数窗口一起调整 |
| 文件树 | ExplorerRow、新 ExplorerUiState、MainActivity：根目录真正控制子项；整行展开/收起，独立选中项、正确新建目标、深层父目录展开、工作区状态及滚动；分层移动选择器；失败表单保留输入 |
| 设置 | SettingsDialog、AppSettings：全屏分类中心、840×480dp起分栏、搜索、草稿、取消/应用、失败重试、旋转恢复；已关闭设置不因主题重建再次打开；JSON v8兼容旧配置 |
| 终端 | ShellTerminalPane、新 TerminalAppearance、ShellLaunchPlan：五种配色、菜单入口、控制键折叠、按需键盘、ANSI提示符；不新建第二终端，不按输出反复重建绘制器；OSC 133/Android运行环境保护保留 |
| 光标 | CodeEditor、EditorCaretMotion：普通输入/退格约60ms，短距离导航约80ms；后续操作接管动画；组合输入、拖动/滚动/大变更即时定位；只改变绘制，不改插入点 |
| 资源与许可 | 本地 Lucide 0.468.0 SVG/VectorDrawable、原始许可证及SHA清单；类型图标使用低饱和颜色，无荧光或发光效果 |
| 回归与说明 | 更新受新契约影响的原断言，新增24项；verify_shell_startup执行Java实际生成的彩色脚本；维护README、接续与原UI计划 |

文件、保存、恢复草稿、执行器、Python服务与runner、桥接及成熟Termux源保持；Workspace只更新“已授权目录”的显示说明，未改变文件操作语义。Gradle、Manifest和GitHub Actions工作流未修改；无push、发布或远端Actions运行。

## Bug原因与修复

1. 根目录原先只设置新建位置，渲染总是遍历其子项：现改为与子目录相同的展开状态控制。
2. 树选中项和打开文件混用，新建位置可停留在旧目录：分别管理，恢复旧配置时按实际选中项校正。
3. 新建深层文件只展开直接父目录：补齐全部祖先目录。
4. 浮动键盘也被当成底部遮挡：结合IME实际底部Insets判断，避免浮动工具条触发收起终端；OS不报告的浮动遮挡仍待设备验证。
5. 每次文字变化重置光标过渡：普通短编辑保留之前的绘制位置，逻辑文本和选区仍立即更新。
6. 原设置堆叠默认控件，取消/重建状态不完整：新分类中心使用独立草稿和应用完成回调；取消不写偏好。
7. 设置主题应用后可能把已关闭窗口写入恢复状态：只检查正在显示的设置中心。
8. 终端主题刷新可能反复构造配色或重建字体渲染器：缓存色板，同主题保留程序OSC覆盖，只在字号改变时调整字体。

## 本轮执行结果

| 检查 | 最终结果 |
| --- | --- |
| Gradle | `BUILD SUCCESSFUL in 1m 29s`；执行测试、Lint和assembleDebug，不是只读旧APK |
| App测试 | 472项：471通过、1项既有跳过，0失败/错误；原448对应保留，仅打字光标用例按新要求改名，新增24项 |
| 终端核心 | 146项成功结果复用；核心源码未改，本次Gradle为UP-TO-DATE，不称重新执行 |
| Python | 源码29/29；从最终APK提取的runner字节码29/29，实际主机CPython执行；均非Android Chaquopy整链实测 |
| 生成脚本/模板 | 实际生成Shell启动脚本4/4；Python模板7/7、主机g++ C++17模板编译执行7/7 |
| Lint | 0错误、11既有警告：SetTextI18n×8，OldTargetApi/ChromeOsAbiSupport/DataExtractionRules各1；未新增baseline或全局抑制 |
| 浏览器设计预览 | 96个设备/主题/场景打开与横向边界检查；另外验证设置取消、标签还原、根目录折叠和浅色终端配色；已人工查看主页面/设置浏览器截图 |
| APK | CRC、v2签名、包名/API/ABI、16KB ZIP对齐及ELF LOAD对齐、必要类真实定义、无测试库、Lucide/终端许可检查通过 |
| 旧产物/核心 | 38份旧APK最终哈希全部保持；原生库与Python运行归档逐字节保持，保护文件哈希核对通过 |

逐文件修改清单及源文件SHA见 `verification/ui-complete-2026-10-19/changed-files.json`。完整日志、每轮失败与修正、JUnit/Lint、生成脚本/模板及APK检查在 `verification/ui-complete-2026-10-19/`，最终汇总为 `results.json`，可重跑 `verify-repository.ps1` 与 `audit_final.py`。

### 构建中间问题

保留了初次共享菜单上下文引用的编译失败；首轮448项中7项失败；定向97项修正后通过；随后实际窗口测试夹具未收窄decor的单项失败也已纠正。没有删掉失败测试以获得成功。

产物审计还发现新旧文件名仅大小写不同，Windows将旧包覆盖。已从 `verification/ui-redesign-2026-10-17/CodePocket-UI-redesign-debug.apk` 按原SHA恢复，所有38份旧APK重验通过。本次改用独立日期文件名，验证脚本增加大小写不敏感的基线冲突检查。恢复证据为 `artifact-collision-recovery.json`。

## 仍需实机验收

- 手机/平板真实像素、触控命中、虚拟键盘与中文组合、蓝牙键鼠、旋转/分屏及大字体。
- Android mksh/PTY/app_process/FD传递/Chaquopy整链，特别是彩色提示符的行编辑宽度、实时输出、多轮input/EOF/停止/超时。
- 鸿蒙卓易通，以及60FPS/高刷新率帧时间。
- 不把主机模板编译说成手机C++本地编译；C++仍由Wandbox云端处理，本轮未新增外网端到端调用。

系统SAF选择器、输入法及系统文本工具条按系统机制保留。本批不增加深语义后端、Sora默认迁移、独立系统工具链或本地C++，原后续需求继续保留。

来源：[Lucide固定版本许可](https://github.com/lucide-icons/lucide/blob/0.468.0/LICENSE)；彩色提示符的零宽分隔遵循[mksh上游说明](https://raw.githubusercontent.com/MirBSD/mksh/master/mksh.1)，但Android设备效果仍需实测。
