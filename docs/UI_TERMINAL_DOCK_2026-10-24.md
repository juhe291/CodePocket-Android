# 终端工具区自适应与裁切修复（2026-10-24）

基于798706e继续UI视觉优先。在实际MainActivity中发现底部工具区固定宽度没有适应工作区；本批只调整已有四个动作的呈现，继续使用单一真实PTY及原输入/会话逻辑。

## 问题证据与改动

- 修复前3项定向测试中2项失败：280dp手机扣除导航后工具区232px，回到底部按钮从225px开始且宽48px，超出右边界；1000dp平板/2倍字体中键盘文字高度超过48px固定行。原日志、JUnit及baseline-results.json保留，修复前图片未单独归档，不能用最终截图冒充修复前图。
- 新TerminalControlsDock复用原键盘、控制键、复制和回到底部回调。工具区实际宽度不足320dp或字体比例大于1.3时采用图标，空间充足时保留文字；不减少48dp触控目标，不新增重复操作。极窄窗口使用原生横向滚动保留四个入口。
- 键盘和控制键采用不同本地Lucide图形；控制键选中底色、提示和无障碍描述保留。布局参数复用，四动作仍为原处理器，没有改TerminalKeyboard、PTY/终端解析、ANSI配色、Python/Wandbox或文件事务。
- 样稿同步工具区并增加交互检查。首次实际点击发现旧示例引用了缺失的controlkeys行，产生2次脚本错误和1项行为失败；补齐明确标记的设计示例行后通过，初始browser-first-results.json与日志保留。浏览器不执行代码；示例复制只复制设计文字。

## 验证

- 新4项实际Main/Sora回归：窄窗口四入口边界、大字体完整可读、实际窗口由411变280dp后会话/文件内容/文件身份/控制键偏好保持，以及220dp极窄窗口滚动后末按钮完整可见。
- 首次完整693项中691通过/1新窗口夹具失败/1旧跳过。夹具只改Configuration而显示实际仍411dp；改为同时变更主显示设备后保留280dp严格断言，并验证真实Activity重建，而非放宽宽度或删除保留断言。4项定向通过后再完整回归。
- 最终主App694项：693通过/0失败/1既有Windows符号链接跳过；原690个用例身份及跳过集合保持。最终完整命令1分48秒成功，Debug为首次完整日志中已实际打包产物的缓存复核。Lint0错误、主16/Sora1既有警告，无新增。
- 源码Python44、实际APK字节码39、主机Shell4、真实Python/C++17片段各7、原签名/16KB对齐通过。74个旧及中间APK保持，10个原生库和Python app.imy逐字节一致。
- 样稿31×4×2=248次场景检查、21项交互及20项既有语法配色检查通过；正常/窄/大字体/选中/极窄原生工具区截图已目视，完整主机绘制不代表设备输入或FPS。

[最新主APK](../dist/CodePocket-terminal-dock-2026-10-24-debug.apk)：26,472,670字节，SHA256 `c872f0c1e21501191040b7e06cad9cc014ca5977cf007e77a4bafed46eb40d1d`，原签名、arm64-v8a。

[原生预览](terminal-dock-preview/README.md) · [当前UI范围](UI_SCOPE_AUDIT_2026-10-24.md) · [机器结果](../verification/workbench-terminal-dock-2026-10-24/results.json)。本机预览服务器和专用浏览器已关闭。

设备IME/蓝牙键鼠/卓易通/API24/帧率、Android系统冷启动与Wandbox/Actions仍未验收。主机保持的是会话模型/运行对象和文件缓冲，不据此声称设备进程已验证存活。旧分屏设置文案及先前单次导入失败仍登记；未实现功能及P2/P3继续暂缓。

生产文件：ui/TerminalControlsDock.kt、ShellTerminalPane.java（底部工具区替换）、strings.xml；回归TerminalDockIntegrationTest.kt；样稿JS/CSS与图、README/RESUME、本记录及本批验证脚本/结果。
