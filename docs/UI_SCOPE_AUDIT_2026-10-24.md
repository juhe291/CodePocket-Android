# UI 范围核对与剩余事项（更新至2026-10-26）

范围按用户最新指令确定：UI视觉优先，允许适度装饰；保留已有项目和真实运行；未实现功能先不接入，新发现的非阻塞问题登记。下表区分源码实现、主机证据和仍未验证的设备行为，不能把当前主机通过等同于整个项目完成。

本批最终验证及APK见[只读详情合并批次](UI_DETAILS_BATCH_2026-10-26.md)。历史阶段的测试数字只表示当时版本；当前完整用例清单以本批test-case-manifest.json为准。

| 范围 | 当前源码与实际入口 | 证据及边界 |
| --- | --- | --- |
| 现有原生工程 | MainActivity、Kotlin原生Views、生产editor-sora；未重建项目 | Gradle测试/Lint/Debug及实际APK检查；不是完整Sora重迁移 |
| 四类窗口 | ActivityBar全高，topWorkspace放边栏/编辑器，底部面板横跨其下、状态栏在导航右侧 | WorkbenchStructureIntegrationTest、WorkbenchWindowIntegrationTest；按实际窗口尺寸，含600×480及840边界 |
| 手机/平板布局 | 窄/矮窗口文件抽屉，足够空间可调侧栏；平板新启动35%终端、手机收起 | 前述实际Main/Sora测试及配置恢复用例；实际IME/自由窗口过渡仍待设备 |
| 入口精简 | 顶部终端/运行/单一全局更多，手机实际项目名/工作区入口，导航去除重复运行；项目搜索在导航、当前文件查找在编辑区；编辑区无重复更多 | AuxiliarySurfaceIntegrationTest；面板局部更多管理终端，不是第二个全局菜单 |
| 视觉体系与图标 | DesignTokens、IdeControls、IdeIcons；本地Lucide及许可证；灰蓝层次、柔和文件类型色、中性底栏 | 原生及浏览器预览分别标识；没有发光/荧光/夸张渐变。并非全部界面逐张人眼验收 |
| 触控与文本 | 常用动作48dp组件，长文件名省略但保留完整路径说明，大字体行增长与菜单滚动 | WorkbenchViewsTest、WorkbenchWindowIntegrationTest、MainSurfaceIntegrationTest的实际抽样；不能据此声称所有系统控件都已测量 |
| 文件树 | ExplorerRow/ExplorerUiState；根与子目录切换、末级目录折叠、工作区展开/滚动、树选中与编辑文件分离；当前未保存缓冲的固定圆点/完整说明 | UiRedesignTest、WorkbenchWindowIntegrationTest、MainFileSettingsIntegrationTest、ExplorerModifiedStateIntegrationTest；收起不关闭文档，真实输入/保存/冲突重试同步状态，保留行/滚动/48dp菜单；不扫描非当前恢复草稿 |
| 文件表单/选择器 | WorkbenchForms与原新建/重命名/移动/删除；锚点文件菜单含路径标题、身份图标、危险分组与旧行隔离，表单仍保留路径区/错误/确认及旧事务校验 | ContextBatchIntegrationTest、FormSurfaceIntegrationTest、MainFileSettingsIntegrationTest；真实脏文件重命名、目录内新建、删除取消及旧回调隔离，WorkspacePickerIntegrationTest另覆盖长目标可增长、空列表自然高度和脏缓冲真实深层移动 |
| 保存与恢复 | EditorSessions、DraftStore、SAF原链路；冲突/失败保留缓冲与草稿 | MainFileSettingsIntegrationTest、MainSafRecoveryIntegrationTest、WorkspaceStartupIntegrationTest；Windows符号链接用例仍有1旧跳过 |
| 标签与编辑操作 | 类型图标、固定占位未保存圆点、单独关闭；首次Sora键入刷新标记，编辑工具按空间显示 | WorkbenchViewsTest、EditorTabStabilityIntegrationTest、EditorTabNavigationIntegrationTest、SoraWorkbenchIntegrationTest；输入/保存保持坐标和手动滚动，明确导航/缩窗后当前项可见；长标签保留类型图标及关闭目标 |
| 分类设置 | 七类、搜索、宽窗口分栏、草稿取消/应用、失败保留与旋转恢复 | SettingsSurfaceVisualTest、MainFileSettingsIntegrationTest；已隐藏bottomOnly工作台中的无效分屏开关；旧panelPreferSide JSON偏好保留，实际Main/Sora往返回归及窄屏大字体说明可达性通过 |
| 菜单层级 | 固定分类标题与可滚动正文，按进入路径提供48dp返回/方向左键返回，Esc关闭；主分类与命令图标按稳定身份指定；撤销、查找定位、字号、辅助功能按组分隔；快捷键窄屏/大字体换到名称下方 | MainSurfaceIntegrationTest和MenuHierarchyIntegrationTest实际弹窗、返回/旧回调隔离、改名后图标/禁用/派发身份、大字体实际跳转；终端分类/尺寸/配色返回保留会话模型；会话/配色选择器含标题与当前项，真实Nord设置和已有会话切换保持缓冲；其余旧列表选择器保留兼容映射 |
| 查找/替换与定位 | SearchDialog及原literal替换；跳转行、项目搜索的范围标题、自适应输入、可滚正文、空结果不留空列表及现有过时来源保护 | ContextBatchIntegrationTest、MainSurfaceIntegrationTest、SoraWorkbenchIntegrationTest、EditorSearch相关回归；280dp/2倍字体真实未保存搜索并定位，没有改为新的搜索引擎 |
| 语法与括号/角色色 | 当前TextMate语法与词法角色/嵌套括号装饰；深浅主题与真实令牌配色 | SoraWorkbenchIntegrationTest验证真实span颜色、选区/撤销/主题往返；这些颜色不代表深度语义分析 |
| 补全/片段/参数 | 基础词法候选的类型/名称/说明分层、窄屏优先名称；内置片段分语言/触发词/说明，单/多形式参数参考与布局后横向定位；原Tab/方向键/Esc、组合输入/异步布局与旧结果保护 | AssistSurfaceIntegrationTest、CompletionInteractionTest、MainSurfaceIntegrationTest；真实提供器/模板、方向键/Tab、插入/导航/撤销和参考形式/滚动分别回归；Jedi/clangd深语义未接入，代码折叠等额外能力不作为本轮新交付 |
| 错误反馈 | 基础结构诊断与运行定位映射、红线、点击只读浮层；变化时关闭旧诊断 | MainSurfaceIntegrationTest、SoraWorkbenchIntegrationTest；不是完整LSP/所有实时编译错误检查 |
| 真实终端与运行 | 单一真实PTY及其会话管理；Python本地桥、C++既有Wandbox完成后返回/预填stdin；第三方提示保持 | 当前运行模块/原生库逐字节保留，Python源码/APK字节码及主机Shell/片段实际检查；本轮不能证明Android Chaquopy/mksh或Wandbox远端执行已实机通过 |
| 面板操作与配色 | 现有拖高/最大化/恢复/收起、会话关闭、键盘/控制键、复制/清空；TerminalAppearance五种配色与成熟ANSI解析 | TerminalWorkbenchViewTest、ShellUiTest、ShellTerminalTouchTest及TerminalDockIntegrationTest；四动作窄屏/大字体图标化、极窄滚动保持48dp；主机模型保持不代表设备进程存活验收，实际触屏/键鼠交互后置 |
| 动画/光标 | 原可取消动画、MotionEnvironment、SoraCaretMotion；菜单/侧栏/面板/标签、60/80ms视觉光标、独立闪烁开关 | FullWorkbenchMotionTest、MainCaretMotionIntegrationTest等主机回归；60FPS/高刷没有设备测量结论 |
| 载入与失败状态 | WorkspacePlaceholder、主题窗口背景/系统栏、原IO和恢复按钮处理器 | WorkspaceStartupIntegrationTest实际IO阻塞/释放、失败前落盘草稿及恢复；系统冷启动真正第一帧仍未验证 |
| 帮助/许可/详情 | ReadOnlyDetails共享上下文标题/640dp宽度/窗口缩放、完整滚动正文与原复制回调 | ReadOnlyDetailsIntegrationTest、AuxiliarySurfaceIntegrationTest及MainSurfaceIntegrationTest；工作区/草稿两级行与类型图标、完整路径、当前项/取消另由WorkspacePickerIntegrationTest验证；系统文本选择菜单/SAF选择器/输入法继续由Android负责 |
| 状态信息 | 中性底栏、优先真实保存失败，窄/大字体优先主状态和行列；完整信息保留说明，宽窗口正常字体增加缩进和编码 | WorkbenchPathStatusIntegrationTest实际外部冲突、草稿/缓冲保持、成功重试及特殊文件名新编辑回归；状态栏点击设置尚未接入，继续暂缓 |
| 路径栏 | 最小24dp并随文字增高、中间省略、完整原路径说明及提示；相同路径避免重复文本设置 | 前述实际Main/Sora手机/平板2倍字体及正常高度回归；没有新增路径导航菜单；<360dp高隐藏被动路径、恢复后显示，浮动Insets不直接隐藏 |
| CI与产物 | build-apk.yml保留原Debug构建、校验及上传；当前本地主APK、示例预览与机器结果归档 | 当前未派发远端Actions，不能声称远端CI通过 |
| 明确后置范围 | WebView/网页左右移动/辅助栏内容、Git/插件/账号、Jedi/clangd等深语义及原P2/P3 | 原需求与研究文件保留；auxiliarySlot隐藏，不放假网页/商店或伪造语义候选 |

## 登记与验收边界

- 只读详情批次新增1条UnusedResources：strings.xml:96的shell_failure_close已无引用；共享层仍显示相同“关闭”，旧资源保留，无运行阻塞。按约定先登记，不屏蔽规则，不为此单独重打包。当前主App17/Sora1条警告、零错误。

- 编辑辅助批次记录的ViewConstructor提示已在上一设置批次补标准Context构造消除：当时Lint主16/Sora1既有警告，无规则屏蔽。SettingsChromeIntegrationTest另验证设置分组、选项草稿/取消/应用和旋转搜索保留。平板只读说明阅读跨度已在本批统一为640dp上限，并覆盖缩窗、全文与设置草稿返回；设备系统文本选择菜单仍后置。

- 多标签基本定位已补充5项实际Main/Sora证据：明确导航/关闭当前项/区域变窄后定位，输入和保存保持手动滚动；窄屏长标签完整显示图标、截断名称及关闭按钮。路径/状态栏已有5项实际Main/Sora证据；短窗口/IME路径占位已补充4项Main/Sora主机证据及359/360边界，不据此声称全部输入环境已验收。平板高度过渡后编辑器焦点丢失的主机观察已另记，设备及旧版情况未确定；220dp有空间诊断/200dp无空间详情分别回归。
- 原InteractionState次级说明仍按文案关键词判断，特殊文件名可能命中；可见摘要已保护旧成功不覆盖新未保存，结构化状态留后续评估。
- 旧“优先左右分屏”展示不一致已处理：隐藏无效入口，保留旧JSON字段/布局能力；新增实际设置应用保留回归。左右停靠功能仍未开放，没有删除偏好或改变当前底部结构。
- 先前一次实际APK helper导入用例返回1，原断言未记录底层异常；完整重测与独立40次未复现，原因仍未确认。见workbench-diagnostic-ui-2026-10-24/import-recheck-summary.json。本批不把它标为已解决。
- 实机输入法/组合输入、蓝牙键鼠、Android系统第一帧、卓易通、API24、帧率/高刷新率和Wandbox远端仍未验收，按用户约定后置。
- 本清单是现状核对，不是全量完成声明。后续按当前源码及实际验收补齐证据，暂缓项继续保留。
