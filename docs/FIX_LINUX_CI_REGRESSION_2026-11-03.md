# Linux CI 回归修复

从48ca1cc接续，只修已有构建准备和测试夹具；设置搜索/主题的六项失败基线单独保留，未加入本次推送。

真实CI [41588320086](https://github.com/juhe291/CodePocket-Android/actions/runs/41588320086)已通过Python168、SDK35/NDK及C++固定依赖准备，App1173项中10项失败，后续Lint/APK步骤未执行。原HTML报告ZIP、完整堆栈和本地修改前75项基线保存在verification/linux-ci-regression-2026-11-03，不把失败CI说成通过。

本次三组修复：

1. C++准备脚本写入原生ELF后保留755执行权限。Linux夹具使用这棵真实准备目录，原运行时canExecute保护保持；不更换固定依赖或放松安全校验。
2. 真实Python用例测试复用已有跨平台解释器选择，尊重codepocket.hostPython；Windows继续使用现有离线Python，Linux使用python3，不再硬编码python.exe。
3. 四组Main/Sora/终端测试等候真实工作区、TextMate及Sora布局就绪。编辑器禁用时原isLayoutReady可能提前返回；现在同时检查workspaceBusy、enabled、语法加载及真实尺寸，五秒未完成仍失败。保留全部场景断言，窗口重建后也等待真实编辑器，不伪造终端或重开会话。

本地75项复测74通过、1个既有Windows大小写不敏感头文件解压失败，无跳过；和修改前75项身份/结果一致。完整源码Python168通过。离线准备21份运行输入逐项字节相同，本地36c76a0b…APK保持，没有重新打包，也未宣称设备或Linux修复已经通过。

下一步按已授权范围正常追加推送，核对原工作流的实际Linux回归、Lint和产物结果。远端记录另存本批push-results.json；没有改工作流、触发、权限、签名或Release行为。原C/D后续任务继续保留，设备验收后置。
