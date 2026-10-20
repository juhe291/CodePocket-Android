# 实体键盘候选导航与代码字号快捷键（2026-10-20）

## 下载与操作

[Debug APK](../dist/CodePocket-keyboard-navigation-zoom-2026-10-20-debug.apk)，Android7.0+ / arm64-v8a、同旧 Debug 证书，可覆盖安装。大小 **23,020,899 字节（约21.95MiB）**；SHA256 `81aad63883f0b90338a1090f30ed62dfb07015483011d66b3525c453fddc2236`。

- 自动弹出候选后，**↑ / ↓ 选择，Tab 接受，Esc 关闭**。候选打开时不移动源码光标；关闭后方向键继续移动代码光标。Enter默认换行，开启“Enter接受选中候选”后可接受方向键明确选择的项目。
- **Ctrl+加号 / Ctrl+减号** 放大、缩小代码字号，每次1sp，范围10–28sp；兼容 `Ctrl+=`、`Ctrl+Shift+=`、独立Plus及数字小键盘加减。字号记入设置，行号同步缩放，终端字号保持。
- 代码编辑区获得焦点时生效；查找/设置等对话框、终端和预设输入继续使用各自按键。中文组合输入时忽略代码缩放，保留组合区；AltGr、Meta和标记为软键盘的事件不变成缩放。

## 原因与修复

旧版默认 `completionArrows=EXPLICIT`，仅Ctrl+Space等主动请求后允许上下键控制候选；自动弹出的列表把方向键交给原生编辑器，移动光标后又使候选失效。原策略单元测试确认了这个旧默认，却没有覆盖自动候选的实际方向键操作。

默认调整为 `WHEN_VISIBLE`。设置JSON升级为v9；**v8及更早的EXPLICIT统一迁移为WHEN_VISIBLE，原OFF保留**。旧版没有区分“默认EXPLICIT”和“用户手动选择EXPLICIT”的记录，因此手动选择过的旧EXPLICIT也会迁移；如需恢复，可在“设置→补全与快捷键→候选方向键”重新选择“主动触发后导航候选”。v9三种选项均原样保存，其他设置不改变。

字号快捷键在现有Activity按键入口处理，复用已有可取消的布局后光标定位。只更新字号及Tab宽度，不重建Editable/编辑器或PTY，不修改文档修订和源码，不触发文件保存；选区、原生撤销和当前终端会话保留。到达字号边界仍消费按键，避免意外写入加减字符。

## 修改文件

| 文件 | 内容 |
| --- | --- |
| `AppSettings.kt` | 自动候选方向键默认及v9迁移，保留OFF和新版本三种配置 |
| `MainActivity.kt` / 新增`EditorFontKeys.kt` | Ctrl加减与数字小键盘、焦点/组合输入门控、字号持久化和行号同步 |
| `SettingsDialog.kt` / `res/values/strings.xml` | 当前按键策略、缩放操作和字号边界提示 |
| `CompletionInteractionTest.kt` | 新增11项完整Activity原生键盘事件回归；以真实v8配置启动 |
| `CompletionTest.kt` | 更新旧默认策略断言/用例名，新增2项迁移与可配置策略测试 |
| 新增`EditorFontKeysTest.kt` | 2项主键盘/小键盘映射及AltGr/软键盘保护测试 |
| README、交接、P1计划与本报告 | 最新下载、行为和真实测试边界 |

Kotlin主源码路径为 `app/src/main/java/com/codepocket/ide/`，测试为 `app/src/test/java/com/codepocket/ide/`。前一轮控制键闪退、查找/替换、代码配色和行内错误功能保留；真实PTY、运行引擎、Python源码、Gradle及Actions工作流未修改。

## 实际验证

- 修复前新用例连同旧交互测试共18项执行，**7项失败**，包括自动候选上下键、配置Enter接受、手机/平板候选导航、字号快捷键和组合输入期间意外插入加号。保留修复前日志与JUnit。
- 最终App共 **532项：531通过、1原有跳过**；新增15项全部通过。原517项状态保持，其中一个旧默认策略测试按新需求改名并更新断言，没有删掉覆盖。
- 原生交互：Python `p` 的第二项`print`、C++ `pri` 的第二项`private`通过方向键选择并由Tab实际插入；候选边界/重复按键、Esc后源码导航、可配置Enter/EXPLICIT/OFF、手机和平板、旧v8配置升级均通过。
- 缩放：实际字号及行号字号、Ctrl+Shift+=、小键盘、重复按键10/28边界、持久化、源码/选区/修订/Editable身份、原生undo、终端会话身份和面板状态、组合输入和非编辑器焦点测试通过。
- **Lint0错误/11原有警告**，无新增。最终Gradle测试、Lint及Debug构建成功，用时 **1m13s**。
- Python源码/资源44、实际APK字节码39（runner29+diagnostics10）、主机真实Shell4、Python模板7/C++17模板7均通过。终端核心146为UP-TO-DATE缓存结果，未声称重新执行。
- APK CRC、v2签名、旧证书、16KiB ZIP对齐、实际DEX、46个编译vector/137条path检查通过。214份非目标源码/配置、42个旧APK摘要保持；28份native/Python归档包括app.imy逐字节相同。
- 证据目录：`verification/keyboard-navigation-zoom-2026-10-20/`。包含基线、修复前后JUnit/日志、`apk-inspection.json`、签名/对齐、编译图标及`results.json`。

本轮是主机原生View和按键事件验证，**没有连接真实蓝牙键盘、手机或平板**。具体输入法是否截获实体按键、蓝牙布局差异、卓易通、帧率与耗电仍待此前约定的统一实机验收。没有push、发布或触发远端Actions。
