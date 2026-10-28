# 设置二级菜单触摸选择修复（2026-10-28）

用户确认问题为“打开设置后，二级菜单的选择点不动”，不是花屏。从 `64b3553` 继续原主 App，优先修复该操作阻塞；原 P2 后续及 P3 保留，整体目标未完成。

## 原因与修复

共享二级菜单行与补全候选行通过 IdeControls.describe 同时设置无障碍描述和原生 tooltip。行本身应由 ListView 统一选择，原生 tooltip 却使被动行消费了触摸，列表没有收到选择回调。旧测试直接执行 performClick/performItemClick，绕过完整触摸分发，因此没有覆盖这一故障。

生产修复只改三处：describe 可分别配置 tooltip；共享二级菜单行保留完整无障碍描述但不安装 tooltip；补全候选行按同一输入职责处理。按钮和独立控件仍保留原提示。没有重做设置布局、动画、二级菜单设计、草稿/应用/取消逻辑，也没有扩展新功能。Python/C++ 运行、保存/恢复、PTY 以及已交付的头文件 Tab 补全保持。

## 实际验证

- 新增 SettingsTouchIntegrationTest 使用真实 Dialog/ListView 的 DOWN/UP 事件，动画开启，不直接调用点击函数。生产修改前 **5 项：4 失败、1 通过**：默认原生列表对照能选择，手机设置主题、平板字号、共享二级菜单和补全候选均无选择回调。失败日志与 JUnit 保留在 baseline-*。
- 修改后 **109 项定向全部通过**，包含上述五项触摸测试，以及设置草稿/应用/取消、现有工作区/恢复草稿/许可/详情、原补全和头文件 Tab 等相关集成。手机主题/平板字号均由完整触摸事件完成；回调收到正确选项，应用前不提交设置，取消不丢失原设置。
- 集中完整主 App **874 项：873 通过、0 失败、1 既有跳过**；旧 869 项身份/跳过集合保持，新增 5 项。终端 146 项有效结果保持。Lint **0 错误**，主 App 21 / Sora 1 警告，既有登记项保留。
- [两张必要原生图](settings-touch-preview/README.md)已查看：本次触摸测试的手机主题选项，以及同一当前源码原设置测试生成的平板 C++ 选项。视觉布局保持；主机原生绘制不等于设备截图。
- 本批完整构建调用 1 次，实际 APK 原 Debug 签名、ZIP CRC、arm64 元数据、权限/Manifest 和 16KiB 对齐通过。新 DEX 已改变，包含修复的三类代码；**25 个原生映像、48 个 C++/Chaquopy 资产**与前批逐字节相同，**92 个历史 APK**摘要保持。
- 本次仅 UI 输入职责改变，运行源码摘要也保持。因此 Python 源码 66、包内运行 39/语义 22、主机 Shell 4/片段 14 引用前批有效结果，**本批未重新执行这些专项**。依据见 runtime-validation-reuse.json 的源码范围、前批包摘要及每个实际新包资产摘要；没有冒称为本批重新运行。

修复包：[CodePocket-settings-touch-fix-2026-10-28-debug.apk](../dist/CodePocket-settings-touch-fix-2026-10-28-debug.apk)，**115,421,743 字节（110.07 MiB）**，相对前批变化 +0 字节。SHA256：`9ea42c7bff22cb91259614a64d09bccecc19f6e2d8ef8106068a5491eef20064`。原证书 `d58e4cd7c849ebcf3cf35fde35f1d201c2569bb9ef11f2b48a4ba80cacd10408`。

证据目录 `verification/settings-touch-fix-2026-10-28/`：baseline-junit/、targeted-junit/、final-junit/、final-terminal-junit/、full-build.log、final-source-hashes.json、test-case-manifest.json、results.json、runtime-validation-reuse.json、certificates.txt、alignment.txt。未删旧断言或新增跳过。

## 验证边界与后续

这次确实使用主机原生 Android Views 的完整触摸分发复现并修复点击失效；没有在 Android 设备重新验收。按原约定不要求现在连接设备，也不把主机测试扩大为实机通过。API24、卓易通、IME/键鼠、性能及前批本地 C++ 内核/PTY/故障执行仍保持各自未验收状态。

仅作本地提交，不推送、发布或新增自动任务。阻塞修复交付后继续原计划的多文件运行查漏、测试用例管理/批量运行、格式化，原 P3 保留，整体目标继续。
