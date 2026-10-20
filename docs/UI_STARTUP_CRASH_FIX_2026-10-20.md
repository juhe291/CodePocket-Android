# UI 版启动闪退：根因与修复（2026-10-20）

用户反馈新UI版点击图标即退出。本轮在实际仓库基线 `c2d2f24` 上定位并修复一个足以导致此现象的确定错误。没有取得手机崩溃日志，不能据此排除特定设备上的其他问题。

## 原因和复现

SVG `polyline`/`polygon` 的坐标转换错误：把每一个数字当成独立的 `L` 命令，而不是 `(x,y)` 坐标对。例如终端图标原始顶点为 `4 17 10 11 4 5`，旧资源写成 `M4 L17 L10 L11 L4 L5`，应为 `M4,17 L10,11 L4,5`。

首页 `MainActivity.createUi()` 创建 `MainToolbar`，立即构造终端按钮的 `IdeIconDrawable`；Android加载矢量时抛出异常，程序还没进入Python/Shell执行就退出：

```text
Resources$NotFoundException: Drawable com.codepocket.ide:drawable/lucide_terminal
Caused by: IllegalArgumentException: M needs to be followed by a multiple of 2 floats.
However, 1 float(s) are found. Failure occurred at position 0 of path: M4 L17 L10 L11 L4 L5
  PathParserNatives.nCreatePathDataFromString
  IdeIconDrawable.<init>(IdeIcons.kt:63)
  MainToolbar.<init>(MainToolbar.kt:25)
  MainActivity.createUi(MainActivity.kt:486)
```

使用Robolectric 4.14.1的 **NATIVE** 图形模式，在本机真实加载其native解析器并执行上述链路。修复前7项新回归全部失败，其中手机/平板、深/浅主题4项完整启动均被同一路径错误阻断。旧版APK中的编译资源也提取出相同的8段非法路径，并非仅源码中的问题。

上版测试使用默认旧图形模式，未校验这些native路径参数；之前大部分MainActivity测试又只构造对象和局部控件，没有执行完整的onCreate/异步工作区初始化。构建和旧测试通过未能捕捉这个错误，是前一轮验证覆盖不足。

## 修改文件

- `app/src/main/res/drawable/lucide_{terminal,play,maximize_2,minimize_2,download,upload}.xml`：6个图标、8段路径恢复成正确坐标对；保留原图形、尺寸、描边、颜色及许可证。
- `app/src/test/java/com/codepocket/ide/StartupNativeGraphicsTest.kt`：7项NATIVE回归。终端与运行图标单测、所有47个图标身份的加载与像素绘制，以及手机/平板深浅主题的真实Activity生命周期与异步工作区载入。没有替换首页、吞掉异常或绕开图标。
- `scripts/verify_vector_resources.py`：纯Python标准库校验Android矢量路径的命令参数数量、有限坐标和弧线参数；可独立运行。
- `tests/test_vector_resources.py`：5项资源回归，遍历全部矢量、校验8段路径与Lucide原始SVG一致，并验证检查器能够拒绝旧坏路径和不完整命令。
- README、RESUME、DEVELOPMENT_PROGRESS、本报告与原UI报告：标明旧APK缺陷、替代下载入口和准确验证边界。

应用Kotlin、原编辑器、保存/草稿/SAF、终端、Python桥、Wandbox代码均未更改；194份受保护源码/第三方/Gradle/Actions文件哈希保持。新测试自动由现有GitHub Actions的unittest discovery及Gradle测试任务执行，不改动工作流，不新增产品依赖，也没有推送或触发远端Actions。

## 执行结果

| 检查 | 结果 |
| --- | --- |
| 新增NATIVE图标/完整启动回归 | 修复前7/7失败；修复后7/7通过 |
| 最终全部App回归 | 479项，478通过、1既有跳过；原472项状态保持 |
| 终端核心146项 | 源码不变、任务UP-TO-DATE；未声称本轮重新执行 |
| 源码Python与新增资源回归 | 34/34通过，其中原runner29项保持 |
| 实际APK中的Python字节码 | 主机CPython3.13执行29/29通过 |
| 实际生成Shell启动脚本 | 主机Shell4/4通过 |
| 实际生成模板 | 主机Python7、C++7通过 |
| Lint | 0错误、11既有警告；新增测试未改变原警告类型 |
| Debug APK | 原仓库离线构建成功1m3s |
| APK资源检查 | 45个vector/135段路径逐项从最终编译资源提取，与源码一致且参数有效 |
| 完整性和签名 | ZIP CRC、v2签名、16KB ZIP对齐通过；同原证书 |
| 运行模块保持 | 全部DEX、native `.so`、Python `.imy`归档共39个条目逐字节不变 |
| APK内容变化 | ZIP内部仅上述6个drawable编译资源变化（另签名因内容变化重新生成） |
| 旧交付物 | 39份原APK哈希均保持；修复包独立名称，未覆盖旧版本 |

原生图形模式使用真实路径解析与绘制库，但仍是Robolectric主机环境，**没有安装/运行Android设备或模拟器**。它证明该非法资源导致的启动异常已可复现并被修正；不代替实机图形效果、输入法、真实PTY/Python Android整链、键鼠、卓易通或帧率验收。实机仍按此前约定后置。

## 安装包

[下载启动修复版Debug APK](../dist/CodePocket-ui-startup-fixed-2026-10-20-debug.apk)

- 包名 `com.codepocket.ide`，versionCode2/versionName0.2.0，Android7.0+、arm64-v8a。
- 22,682,264字节，SHA256 `f86ccfbd546e38024f1649eced0fa5afbe23f564fb6bb7fa4936b6ac85d451d4`。
- Debug证书SHA256 `d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`，与旧版本相同，可直接覆盖安装；无需卸载或清空数据。
- 新包与旧包大小相同，务必按文件名和哈希区分。此前UI版仅为保留证据，不应继续安装。

## 复核与证据

本地完整执行记录位于 `verification/ui-startup-crash-2026-10-20/`：修复前源码/39份APK哈希、修复前原生异常JUnit、修复后单项及最终JUnit、构建/Lint/Python/Shell/模板日志、编译矢量提取、签名、旧文件保持检查及 `results.json`。保留第一次命令行参数未正确传递导致的中文路径构建失败日志；修正命令后才得到上述7项真实失败，未把环境错误当作故障复现。

独立资源检查：

```sh
python scripts/verify_vector_resources.py
python -m unittest discover -s tests -v
./gradlew :app:testDebugUnitTest --tests com.codepocket.ide.StartupNativeGraphicsTest
```

Windows中文路径构建使用已有本地JDK17、Gradle8.9、SDK35与Python3.13，需正确传入 `-Pandroid.overridePathCheck=true`；本轮完整命令保存在证据目录的 `verify-repository.ps1`。原P2/P3与UI实机验收事项继续保留。
