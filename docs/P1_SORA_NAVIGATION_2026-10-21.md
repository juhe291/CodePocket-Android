# P1-C Sora 导航适配首批（2026-10-21）

基线 `6b66a8f981e5989dbea677caba80ac69c34994a3`。依据现有计划继续成熟编辑器适配，不重新创建项目；实机按用户要求统一后置。本批没有替换日常 IDE 的原生编辑器，也没有恢复 Python 预填输入。

## 完成的代码

- 把既有生产导航队列的“最新请求优先、回调只消费一次、取消与关闭”规则提取到本地 Java 库 `ViewportRequests`，原 Kotlin `EditorViewportQueue` 兼容入口保留并委托它。原生选区、搜索、错误定位、文档与视口契约保持，未改文件保存或运行功能。
- SoraBuffer 增加真实 Content/Cursor/Indexer 驱动的选区校验与严格行号定位；拒绝越界、半个 emoji、CRLF 中间位置、旧版本、另一份同名草稿及组合输入。纯导航不修改正文或历史，可定位已载入的超长草稿。
- Sora 视口快照只保存实际缓冲身份、版本、选区、交互戳与滚动坐标；捕获不调用 Content.toString。内容、选区、触摸、滚轮、按键、窗口失焦、暂停、文档更换或销毁会阻止过期恢复。重复或被拒绝的布局回调不能稍后重放。
- SoraEditorAdapter 实际接线到布局后的 pre-draw、真实 scroller 和 ensurePositionVisible；恢复坐标按组件范围裁剪，不从逻辑行号估计像素。Sora重排的scrollBy(0,0)也会产生USER_DRAG事件，因此真实输入在派发入口识别，不把重排当用户拖动；异步折行期间延迟到实际布局就绪再恢复，2秒未就绪则清理，不按帧忙等。移除待执行 observer，注销订阅，避免视图脱离后遗留回调。
- 核对固定 Sora 0.23.7 源码发现 setSelectionRegion 会主动 requestFocus：未授权后台选区不调用它；显式跳转允许聚焦；视口恢复不改焦点、不替换 InputConnection。历史操作也要求编辑器已聚焦。
- 独立实验版加入“跳转行”和“自动换行”。无效/过期跳转保留输入与错误说明；换行重排使用上述视口保护，换行选择加入现有旋转/组件切换的临时草稿交接。该草稿仍不持久保存，不接项目或 SAF。
- 新增很薄的 SoraInputEditor 派发观察：触摸/滚轮/按键先让旧导航失效，然后原样调用成熟组件的 super；不消费事件，不代替手势、无障碍或 IME。

本批仍不是完整生产 EditorPort 或默认 Sora 迁移。没有新增语言服务、语义候选、文件访问或执行引擎，原 P2/P3 全部保留。

## 修改文件

| 文件 | 内容 |
| --- | --- |
| editor-surface/.../ViewportRequests.java（新增） | 两编辑器共用的可取消布局队列 |
| app/.../editor/EditorNavigation.kt | 保留原接口，委托共享队列 |
| app/.../EditorNavigationTest.kt | 增加无效后请求、重入和重复回调的2项回归 |
| editor-lab/.../SoraBuffer.java | 真实选区/行号及轻量视口快照 |
| editor-lab/.../SoraEditorAdapter.java | Sora 组件导航接线、输入与生命周期清理 |
| editor-lab/.../SoraInputEditor.java（新增） | 透明输入派发观察 |
| editor-lab/.../EditorLabActivity.java | 实验导航入口、换行与草稿交接 |
| editor-lab/.../SoraNavigationTest.java（新增） | 22项真实 Sora 核心/共享队列回归 |
| README、editor-lab/README、DEVELOPMENT_PROGRESS、RESUME、本报告 | 当前范围、交付与接续 |

`app/...` 主源码位于 `app/src/main/java/com/codepocket/ide/`，测试位于 `app/src/test/java/com/codepocket/ide/`；实验源码/测试分别位于 `editor-lab/src/main/java/com/codepocket/editorlab/` 与 `editor-lab/src/test/java/com/codepocket/editorlab/`。

## 实际验证

| 项目 | 结果 |
| --- | --- |
| 定向测试首轮 | 原生导航20/20、Sora新增22/22，无失败 |
| App全量 | **536项：535通过、1原有Windows符号链接跳过**；原534项名称/状态逐项保持，新增2项通过 |
| Sora实验全量 | **82/82通过**；原60项名称/状态保持，新增22项通过 |
| Lint | 主应用0错误/11原有警告；实验0错误/6原有警告，无新增 |
| Debug构建 | 两个APK成功；首轮全量1m34s，最终构建29s；主应用最终轮为未变化缓存，最终实验重新执行 |
| Python与资源 | 源码/资源44、实际APK内Python字节码39通过；实际主机Shell启动4、生成Python模板7/C++17模板7通过 |
| 终端核心 | 146项UP-TO-DATE缓存结果，本批未重跑 |
| APK检查 | 两份CRC、v2同Debug证书、16KiB ZIP对齐、实际DEX/包名/API通过；主包仅arm64-v8a，实验无Python/本地库或申请权限 |
| 资源和保护 | 46个实际编译vector/137条path校验；856份非目标已跟踪文件、44个旧APK、28份Python/native归档保持；实验许可/语法资产字节保持 |

所有测试首轮无失败。Lint中途发现新被动 onTouchListener 触发3项 ClickableViewAccessibility 警告；改为透明 dispatch 观察后重跑测试/Lint/构建，恢复原6项警告。没有屏蔽该规则或添加 performClick 来重复触发成熟组件的点击。中间报告保留于 round-02-lab-lint.xml 等文件。

首次全量与最终构建覆盖范围相同；最终阶段未修改主应用代码，主应用测试UP-TO-DATE，不将缓存称为重新执行。两个 Actions 文件和 Gradle/Manifest/依赖版本均保持，没有 push、发布或远端 Actions 记录。

复现：在实际仓库用 PowerShell 执行 `verification/editor-sora-navigation-2026-10-21/verify-repository.ps1 -Repository <仓库绝对路径>`；使用现有 .tools 的 JDK17、SDK35、Gradle8.9、Python3.13 离线环境，并启用 `-Pcodepocket.editorLab=true`。该开关只包含独立实验模块，默认IDE的依赖与APK不包含Sora。原始构建、JUnit、Lint、完整基线及实际安装包审计见同目录的 `results.json`、`audit_artifact.py` 与日志。

## 产物

- [主IDE Debug APK](../dist/CodePocket-editor-sora-navigation-2026-10-21-debug.apk)：23,022,221字节（约21.96MiB），SHA256 `d0234b8be2e2006cc3fe68042ba84aa6af274a53351510b8ff28e9fc52a4fdda`。
- [Sora导航独立实验 APK](../dist/CodePocket-Sora-navigation-2026-10-21-lab-debug.apk)：4,896,994字节（约4.67MiB），SHA256 `a4f435b4633a7c5af2de6a260c613d7c3e2aaad9f42d33be494eeaf7b55c1c07`。包名 `com.codepocket.ide.editorlab`，仅临时编辑实验，不是完整IDE。

两者Android7.0+、target34/compile35；签名证书SHA256 `d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`。主包保留0.2.0/versionCode2，同旧签名可覆盖安装；实验为0.1-experiment/versionCode1。

## 接续与未验证

下一批继续把现有候选、片段、参数提示契约适配到成熟组件，再接项目/SAF文档会话与完整生产Port，取得设备输入与历史证据后才评估默认迁移。真实clangd/Jedi语义、本地/多文件C++、批量用例、格式化及P3仍未完成；本轮不把导航适配写成这些能力已完成。

本轮验证的是主机真实Content/Cursor/Indexer/UndoManager、共享队列和实际APK编译内容。**没有执行Android Sora View/InputConnection、真实视觉布局/折行/旋转、IME/蓝牙键鼠、手机/平板/卓易通或FPS验收**；焦点与事件API接线经过固定源码核对、编译和Lint，尚非设备行为证明。实验跨旋转保存正文/选区/换行选择，进程死亡不保存，滚动位置跨旋转的完整会话恢复仍待项目会话适配。本轮无新Wandbox调用。
