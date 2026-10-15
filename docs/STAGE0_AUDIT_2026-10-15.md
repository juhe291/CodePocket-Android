# CodePocket 阶段 0：审查与构建基线

审查日期：2026-10-15，Asia/Shanghai。结论：**当前源码能够真实构建 Debug APK，但尚未满足新需求中的 P0 日常使用条件。** 本阶段未修改应用源码、测试源码、Gradle、Manifest 或 Actions，未进入 P0。

本报告以当前实际代码为准；历史 README 中的“已实现”不作为实机验收证据。需求原文保存在 [DEVELOPMENT_REQUIREMENTS.md](DEVELOPMENT_REQUIREMENTS.md)，与用户提供文件的 SHA256 相同：`55f5e64f6fbae4057ebe4fc30fa5d2501025230ec641976e885879e7d06657b8`。

## 1. 目录、技术栈与现有设计

实际工作目录：`C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android`。原 OneDrive 桌面路径已失效。当前是源码目录，`git rev-parse --is-inside-work-tree` 返回“not a git repository”；没有可核验的提交历史或远端 Actions 记录。

```text
CodePocket-Android/
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/com/codepocket/ide/
      MainActivity.kt       原生 Activity、界面、文件菜单、保存、快捷键
      CodeEditor.kt         EditText 的光标通知扩展
      EditorActions.kt      自动缩进、选区缩进、括号及引号补齐
      EditorFeatures.kt     正则词法高亮、候选词和括号匹配
      Workspace.kt          私有目录中的文件操作与边界保护
      ExecutionEngines.kt   Chaquopy 桥接、Wandbox HTTP 与结果解析
      RunSession.kt         进程内单执行队列与 Activity 状态回调
    src/main/python/runner.py
    src/main/res/           两套原生主题、自绘矢量图标
    src/test/              3 个 Kotlin/JUnit 测试类
  tests/test_runner.py      13 项主机 Python 回归
  scripts/                 本地构建、Wandbox 探测、APK 内容检查
  gradle/wrapper/          Gradle 8.9 Wrapper 与分发校验值
  .github/workflows/build-apk.yml
  docs/                    需求、审查、历史验证和设备清单
  verification/            原始备份、历史日志、本次基线证据
  dist/                    本地 APK，忽略提交
  .tools/                  便携工具与依赖缓存，忽略提交
```

| 项目 | 当前实际配置 |
| --- | --- |
| 界面 | 原生 Kotlin + Android View / LinearLayout / EditText；没有 Compose、Monaco、Sora 或 WebView 编辑器 |
| 构建 | JDK 17、Gradle 8.9、AGP 8.7.3、Kotlin 1.9.25 |
| Android | compileSdk 35、Build Tools 35.0.0、targetSdk 34、minSdk 24 |
| 架构 | APK 仅 arm64-v8a；不能据此宣称支持 x86_64 模拟器 |
| Python | Chaquopy 17.1.0，配置 Python 3.13；主机构建/测试 Python 3.13.9，缓存 target 制品为 3.13.16-0，设备内精确运行版本未测 |
| C++ | Wandbox 公共 HTTPS API，动态发现 GCC，`-std=c++17`，不需要 API Key；没有本地 C++ 工具链 |
| 数据 | 私有 `files/workspace` + SharedPreferences `codepocket_v2`；没有真实 SAF 目录工作区或数据库 |
| 权限 | Manifest 仅 INTERNET；没有全面存储或额外蓝牙权限 |
| 测试 | JUnit 4.13.2、测试用 org.json 20240303、Python unittest；没有 androidTest 或 UI 自动化测试 |

标准 `debugRuntimeClasspath` 报告包含 Kotlin stdlib 1.9.25、annotations 13.0。Chaquopy 还通过插件专用配置打包运行库，这份 classpath 报告不是完整原生依赖清单。

## 2. 功能矩阵

“已有”表示存在实际实现；有对应主机测试时单独注明。Android 界面、原生加载和系统提供者行为均未经过本次实机验收。

| 能力 | 状态与实际边界 | 优先级 |
| --- | --- | --- |
| 多文件与多标签 | 已有；同一 Activity 中切换记录单个插入点；选区、滚动位置、重建后的非活动标签光标不完整 | P0 |
| 深色/浅色 | 已有，偏好记忆；没有跟随系统、独立编辑器主题、设置页 | P1 |
| 高亮/补全 | 已有关键词、部分类型/函数、字符串、数字、注释；词法规则，不是完整语言解析；超过 45000 字符停用高亮与匹配 | P1/P2 |
| 编辑基础 | 行号、自动缩进、Tab/反缩进、成对括号、匹配已有，相关纯函数通过 JVM 测试；没有缩进引导线、可配置折行/Tab 宽度 | P1 |
| 查找与编辑命令 | 没有应用级查找替换、跳行、项目搜索、格式化；撤销重做依赖原生 EditText，未提供明确工具栏及跨标签策略 | P1/P2 |
| 键盘/鼠标 | 有所要求快捷键的分发、原生编辑鼠标行为、文件树长按/右键菜单；蓝牙实机、中文组合输入、系统拦截情况未验 | P0/P1 |
| 触屏/IME | 有常用按钮；没有主动关闭输入法按钮；固定高度区域与键盘启发式检测存在适配缺口 | P0 |
| 自适应 | 按窗口配置宽度 600/720 dp 分段，并非仅看方向；面板尺寸固定，无拖动、最大化、显式终端隐藏/恢复 | P0/P1 |
| 内部文件管理 | 嵌套 CRUD、目录移动、同名拒绝、删除确认、路径/符号链接保护已有；内部文件纯逻辑有 JVM 测试 | 保留并扩展 |
| 系统项目目录 | 缺失；仅 ACTION_OPEN_DOCUMENT 导入副本和 ACTION_CREATE_DOCUMENT 导出，不会保存回导入来源 | P0 |
| 授权/工作区 | 没有 OPEN_DOCUMENT_TREE、持久授权、最近项目、撤权恢复、外部文档稳定身份 | P0 |
| 保存/恢复 | 700 ms 自动保存和离开时保存；内部文件临时写入同步后替换；失败阻止切换；主线程 I/O、持久草稿恢复不足 | P0 |
| 独立标准输入 | 缺失；全局一个 stdin，语言/文件切换共享，没有按文件清空/记忆 | P0 |
| Python 执行 | 真实本地 Chaquopy 桥接；预填输入、标准库、工作区模块、异常定位；主机源码/打包字节码测试通过，Android 内执行未验 | 保留，P0 改进中止 |
| C++ 执行 | 真实云端单文件；STL/stdin/编译错误/退出码本次接口实测通过；没有本地头文件上传、多 cpp 链接、离线编译 | P0/P2 |
| 加载/重复保护 | 已有进程内单队列，Activity 重建复用；没有停止操作或可强制中止的 Python 进程隔离 | P0 |
| 错误分类/跳转 | 编译、运行、超时等分类及首个诊断跳转已有；HTTP 服务失败误归网络错误；不是完整“问题”列表 | P0/P1 |
| 程序终端 | 目前是 TextView 一次性显示；无 ANSI 网格、流式输出、运行中 stdin、清空/历史管理；不提供 Shell | P1，受引擎能力限制 |
| 隐私提示 | C++ 首次运行有真实第三方上传确认，记住同意；缺设置中的撤回/说明入口 | P1 |
| 设置/语言 | 没有设置中心、英文资源、帮助和许可证页，主题按钮之外尚无相关用户设置 | P1/P3 |
| 算法测试用例 | 没有按文件测试组、预期输出比较或批量运行 | P2 |
| Git/GitHub | 没有应用内集成；开发目录自身也未初始化 Git | 开发基线/P3 |
| 鸿蒙卓易通 | 无设备/兼容实测证据，不能承诺已适配；安装、原生 Python、SAF、IME、窗口均待测 | 各阶段验收 |

## 3. 关键缺陷、原因与复现方法

以下 UI 操作是交给设备验收的复现步骤，**不是已经执行的设备测试**。来源行号以本次未修改的源码为准。

| 编号 | 问题与影响 | 代码原因 | 复现 / 当前证据 |
| --- | --- | --- | --- |
| B01 / P0 | 输入法不能从工具栏主动收起；stdin 获焦时编辑区域可能被挤压 | MainActivity:261、268–275、313、358：输出固定 225 dp、候选栏 37 dp、stdin 57 dp；仅在键盘被估算为弹出且 editor.hasFocus 时隐藏整个输出区；没有 IME 显隐控制 | 手机竖屏/横屏分别点代码和 stdin，键盘弹出后检查编辑光标可见性与恢复；Gboard、系统 IME、卓易通都待复现。缺少关闭入口由源码确认，具体挤压尺寸未测 |
| B02 / P0 | C++/Python、不同文件 stdin 串用，可能得到错误计算结果 | MainActivity:75、128、169、350、789：一个 input 控件、单一偏好键 stdin；switchFile 不切换输入数据 | main.cpp 填 A，切 main.py 再填 B，返回 main.cpp；从代码路径可确认仍是 B，设备操作待验 |
| B03 / P0 | 导入后“保存”只改内部副本，不能作为用户真实目录项目 | Workspace:9 固定 files/workspace；MainActivity:732–775 仅单文件导入/导出，没有目录 URI 或来源写回映射 | 在系统目录创建 a.py→导入→编辑→保存→用系统文件工具查看原 a.py；预期现版原文件不变。这是现版设计边界，不能当作已完成新需求 |
| B04 / P0 | 崩溃/保存失败后缺少独立持久草稿；频繁保存可能阻塞输入 | MainActivity:59、146–170、606–616；Workspace:129–135：主线程 Handler 调用文件写入和 fd.sync；草稿只在保存仍失败时放入 Bundle | 写入失败→编辑→恢复进程，检查草稿；慢存储/大文本测输入延迟。失败替换保留原文已有 JVM 测试；Activity 恢复、ANR/掉帧没有设备数据，不能称必现 |
| B05 / P0 | 旋转、切主题、关闭标签后光标/选区/滚动/焦点恢复不完整 | MainActivity:58、113、168、557、590–598：内存 cursors 只在 switchFile 记录；只持久化活动 selectionStart；没有选区终点、scrollX/Y、焦点记录；rename/move 未迁移 cursors | A/B 各滚到不同位置并选择多字符，旋转或切主题后逐个返回；再重命名目录检查插入点；恢复字段不足由源码确认，设备场景待验 |
| B06 / P0 | Python 阻塞调用不能按期限强制结束；运行队列和文件操作可能长时间被占用 | runner.py:86–106 只设 sys.settrace；RunSession:11、25 无取消句柄；MainActivity:620、806–807 无停止入口 | **主机真实复现**：临时将限时设为 40 ms，运行 sleep(0.25)，实际约 253 ms 才返回 timeout。独立子进程有 3 秒兜底，不制造无限阻塞。无限 native 调用的设备表现未测 |
| B07 / P0 | Wandbox HTTP 4xx/5xx 服务失败显示“网络错误” | ExecutionEngines:62、95–98 抛 IOException；RunSession:34–37 把 C++ IOException 全归 NETWORK | 让可注入 HTTP 层返回 429/503，期待 SERVICE，现代码会走 NETWORK；本次静态控制流确认，没有对公共服务故意制造错误 |
| B08 / P1/P2 | “C++ 慢”尚无分段测量，不能直接认定服务或手机为根因 | ExecutionEngines:56–87：只在进程内缓存 compiler，冷启动需 GET list 再 POST compile；仅记录整体 elapsedMs，无连接/排队/编译阶段指标 | 本次接口脚本三项共约 17.1 秒，包含列表请求和三次编译，不能作为 App 单次耗时或本地/云端对照；下一步再分冷/热、网络与服务测量 |

额外边界：高亮是有限正则词法规则；输出错误区整体点击跳转首个诊断；运行后编辑可能使行号偏移。目录遍历显示深度最多约 19 层。所有非 .py 文本按 C++ 模式处理，没有独立入口文件配置。

## 4. 本次真实构建与测试

证据统一放在 [verification/stage0-2026-10-15](../verification/stage0-2026-10-15/)，与此前日志分开保存。未清空工具缓存，但使用 `--rerun-tasks` 强制重新执行编译、测试、Lint 和打包，没有把 UP-TO-DATE 当作重新测试。

| 检查 | 结果与覆盖范围 |
| --- | --- |
| Python 源码回归 | **13/13 通过**；输入输出、math、异常行号、工作区模块刷新、全局环境恢复、SystemExit、字节码超时、输出限额、Unicode/EOF |
| Kotlin/JVM | **16 项：15 通过、1 跳过、0 失败/错误**。编辑规则 5、Wandbox 解析 4、Workspace 7；Windows 符号链接用例按测试明确跳过，Linux CI 尚未运行 |
| Android Lint | **0 错误、12 警告**：SetTextI18n 9、OldTargetApi 1、ChromeOsAbiSupport 1、DataExtractionRules 1；没有添加 Lint baseline 或禁用检查 |
| Debug 构建 | **BUILD SUCCESSFUL in 53s；62 actionable tasks: 62 executed** |
| APK 签名与包信息 | apksigner 验证通过，v2 Debug 签名；包名 com.codepocket.ide，0.2.0/code 2，min 24/target 34，arm64-v8a |
| APK 归档与 Python 资源 | APK CRC 和 6 个 .imy 内部归档 CRC 通过；包含 DEX、libpython3.13.so 和 runner.pyc |
| APK 实际字节码回归 | 从本次 APK 读取 runner.pyc，在匹配的主机 Python 3.13 上 **13/13 通过**；不是 Android 执行 |
| ZIP 对齐 | zipalign -c -P 16 4 通过；不代表已经在 16 KB 页面设备中运行 |
| Wandbox 实际 API | **3/3 通过**，本次 GCC 13.2.0：STL/stdin 输出 8；语法错误 status 1 带 prog.cc 行号；真实程序退出 7。只发送脚本预置的微型程序，没有上传项目文件 |
| Python native 超时探测 | 40 ms 设定、约 253 ms 返回，确认尽力 watchdog 的范围；该脚本是缺陷复现，不是 Android 中止实现 |
| Android 设备 | adb devices -l 为空；未安装、未启动 App，未验证 Chaquopy 在设备内加载 |
| GitHub Actions | 仅完成代码审查和本地对应任务；没有远端仓库/运行证据，不能称 Actions 已远端通过 |

构建还提示若干预编译 .so 未被 strip，以及 Android API 弃用提示；它们没有阻断构建。暂不在审查阶段升级 targetSdk、替换 UI 框架或改变 ABI。

本次 APK：[CodePocket-stage0-2026-10-15-debug.apk](../dist/CodePocket-stage0-2026-10-15-debug.apk)，**21,609,140 字节，约 20.61 MiB**。

SHA256：`d85e032b975a9f779fe79b34be39fa680e0b19bf8ed85ea7326b24178f93a824`。

旧 `dist/CodePocket-v0.2-debug.apk` 保留为历史产物，大小与哈希不同，不将旧包当作本次构建结果。新旧 APK 对比中，条目解压内容仅 app.imy 与关联 build.json 不同，其余条目内容一致；构建并未承诺逐字节可复现。当前应用源码/配置哈希前后不变，见 source-before.json、source-after.json、source-comparison.json。

### 复现命令

在项目根目录，使用已有便携工具并设置 JDK 17/SDK 环境后，本次完整 Gradle 参数保存在 invocation.json。主要命令如下：

```powershell
& ./.tools/python313-full/tools/python.exe -m unittest discover -s tests -v
# 与 build-local.ps1 相同的本机工具/目录配置，额外强制 rerun：
& ./.tools/gradle-8.9/bin/gradle.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --rerun-tasks --stacktrace --no-daemon --console=plain --max-workers=4 -Pandroid.overridePathCheck=true "-Duser.home=$PWD/.tools/userhome" -I .tools/mirror.init.gradle '-Dhttp.nonProxyHosts=*.aliyun.com' "-Pcodepocket.buildPython=$PWD/.tools/python313-full/tools/python.exe" -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890
& ./.tools/python313-full/tools/python.exe scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk --output verification/stage0-2026-10-15/apk-inspection.json
& ./.tools/android-sdk/build-tools/35.0.0/apksigner.bat verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
& ./.tools/android-sdk/build-tools/35.0.0/zipalign.exe -c -v -P 16 4 app/build/outputs/apk/debug/app-debug.apk
& ./.tools/android-sdk/build-tools/35.0.0/aapt.exe dump badging app/build/outputs/apk/debug/app-debug.apk
& ./.tools/android-sdk/platform-tools/adb.exe devices -l
& ./.tools/python313-full/tools/python.exe -B verification/stage0-2026-10-15/probe_native_timeout.py
& ./.tools/python313-full/tools/python.exe -B scripts/check_wandbox.py --proxy http://127.0.0.1:7890
```

本机 JDK/SDK/Gradle 用户目录的精确设置见现有 scripts/build-local.ps1；镜像只在被忽略的 .tools/mirror.init.gradle 中，正式 settings.gradle.kts 仍使用 Google/Maven Central。上述网络检查结果取决于运行时网络，不是永久可用性保证。

### Actions 审查

工作流当前包括：push main/master、PR、手动触发；JDK 17/Python 3.13/SDK 35；Python 与 Kotlin 测试、Lint、Debug 构建；APK CRC、签名、对齐、打包字节码检查；APK 上传 `CodePocket-v0.2-debug-apk`，报告以 always 上传。APK 缺失会失败。静态未发现确定阻断该流程的配置错误；Ubuntu runner、首次下载、Linux 符号链接用例和 Artifact 下载还需真实远端运行确认。未替用户 push 或触发发布。

## 5. 许可证与开发留痕

已检查本地配置、Wrapper 头部、缓存 POM 和 APK 内容，初步清单见 [THIRD_PARTY_INVENTORY.md](THIRD_PARTY_INVENTORY.md)。Chaquopy 的 POM 声明 MIT，Kotlin/annotations 声明 Apache 2.0，JUnit 声明 EPL 1.0，测试用 JSON 声明 Public Domain。**Chaquopy 元数据的 MIT 不能替代 CPython 和其原生依赖的各自许可证。**

当前缺项目 LICENSE、集中第三方声明及 App 内许可证页面；CPython、OpenSSL/SQLite 等打包组件的完整逐项通知尚未核验。当前源码目录无 Git 历史；本次用前后哈希、需求原文副本、测试 XML、日志和独立 APK 建立可比较基线，这些不能替代原创过程的提交记录。P0 开始前建议建立本地版本控制基线，后续按小批次保留测试和提交；项目许可证由项目所有者确定，第三方原有声明须保留。

## 6. 建议修改顺序与文件范围（尚未执行）

按 P0 → P1 → P2 → P3，每个批次独立编译、测试、记录回滚点；保留现有 UI、Workspace 内部安全能力、Chaquopy 与 Wandbox 路径，不整体重建项目。

| 批次 | 具体工作与主要文件 | 验证要求 |
| --- | --- | --- |
| P0-1：IME 与编辑状态 | 修改 MainActivity.kt、CodeEditor.kt、Manifest；按需要新增 EditorSession.kt / ImeLayoutController.kt。显式收起键盘、区分代码/stdin 焦点、处理实际可用高度；保存每个标签的选区/滚动/焦点，预留稳定文档 ID | 补会话状态 JVM 测试；手机竖横屏、分屏、旋转、切主题和中文 composing 设备验证；确认收起键盘后空间恢复 |
| P0-2：真实工作区与保存 | 在 Workspace.kt 之上增加存储接口，保留现有内部实现；新增 SafWorkspace.kt / WorkspaceRepository.kt / DraftStore.kt 等最小必要模块；MainActivity.kt 接目录选择、持久授权与文件树。后台串行保存、版本核对、持久草稿、撤权/冲突/失败恢复；不把 URI 当普通 File | 扩展 WorkspaceTest 并增加存储/恢复测试；真实 SAF 提供者的目录 CRUD、重命名/移动后的标签、同名冲突、授权撤回与重新授权、保存失败不丢内容；旧内部项目可继续访问/导出 |
| P0-3：文件独立 stdin | 修改 MainActivity.kt 会话切换；新增按 workspaceId + stableDocumentId 保存的 StdinStore.kt，处理重命名/移动身份映射、清空和恢复；禁止仅用语言名或易变文件名作永久键 | 两个 cpp、两个 py、两个工作区交叉切换；重建、关闭重开、改名移动后输入保持独立；补 StdinStoreTest |
| P0-4：中止和网络恢复 | 修改 RunSession.kt、ExecutionEngines.kt、runner.py 与运行按钮；可取消的请求/会话 ID，丢弃过期回调，HTTP 状态单独分类。先验证独立 Python 执行进程/服务的可行实现，再承诺强制中止；可能新增 PythonExecutionService.kt 并更新 Manifest | 正常运行/编译失败/离线/429/503/超时/取消后重跑；字节码死循环和 native sleep；后台/旋转不重复、不用旧结果覆盖新会话；P0 不能仅用 Future.cancel(true) 宣称终止原生 Python |
| P1：编辑与界面体验 | MainActivity.kt、EditorFeatures.kt、EditorActions.kt、styles.xml；增 strings.xml、真实 Settings、搜索替换与程序终端模块；按窗口宽度和高度调整可调面板，完善 Dark+/Light+、图标、符号栏和键鼠 | 每项设置真实生效；词法边界测试；Android/平板/卓易通交互验证；Wandbox 保留预填输入，流式/运行时输入只按实际引擎能力开放，不伪装 Shell |
| P2：生产力 | 新增独立本地 C++ 原型与性能记录；评估 ABI、原生可执行限制、工具链体积和许可，验证后才接正式后端；再做多文件构建、用例管理、格式化、增强补全 | 同一设备/题目下冷暖启动、速度、离线、安装体积对照；真实编译结果；用例输出差异和边界测试。不可行时保留云端回退并如实说明 |
| P3：后续增强 | 后续 Git/GitHub 模块、设置搜索、主题扩展和语言分析服务；保持模块边界 | 授权后才进行远端写入；差异、冲突、离线和数据安全测试 |

SAF 提供者不一定支持原子替换或原生 move，实施时必须按实际能力处理，不直接复制内部 POSIX rename 的承诺；失败要保留草稿和可恢复状态。云端取消等待也不能宣称已终止第三方服务器上的编译。

P0 必须覆盖 Android 手机/平板及鸿蒙卓易通的安装、CPython 原生库加载、离线 Python、SAF 目录、输入法、蓝牙键鼠和窗口状态；当前缺设备，此项验收仍待安排，不能用主机测试替代。

## 7. 本阶段落地文件及交接边界

- 更新 README.md、docs/RESUME.md，指向本次实际基线，并明确当前 P0 缺口与等待确认状态。
- 新增本审查报告、需求原文副本、第三方初步清单。
- 新增 verification/stage0-2026-10-15 中的日志、JUnit/Lint 快照、依赖与许可证元数据、APK 检查、哈希对照和有限 native 超时复现脚本。
- 新增本次本地基线 APK；旧 APK 与历史 VERIFICATION.md 保留。
- 应用源码、既有测试、Gradle/Manifest/Actions、原有脚本未改；本次 30 项哈希清单（含 2 个原有 pyc 缓存）前后 0 变化。

**阶段 0 已完成；等待用户确认后进入 P0。**
