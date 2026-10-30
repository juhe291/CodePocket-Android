# 工作台行为批次（候选已验证）

起点：63657ca；前批实际完整 946 项中 944 通过、1 个既有 Windows 失败、1 旧跳过，previous-full-identities.json 仅是前批记录。

原修改前实际基线 baseline 为 8 项中 7 失败、1 通过，第一次错误调用受保护回调的编译失败单独保留。随后抽屉窗口动画定向 24 通过、搜索生命周期定向 16 通过。欢迎/真实保存/关闭/重开、空提供方项目与搜索全流程最终定向 workspace-complete-targeted 为 144 全通过。

本批实现搜索与文件树共享手机抽屉/平板侧栏、固定活动栏、查询与缩窗恢复；无项目和最后标签关闭后的欢迎区、新建/系统打开真实入口、关闭项目和重开会话。Core 新构造参数由 Main 显式禁止播种，保留旧公开构造器用于兼容/已有项目夹具；不删除已有样例或用户文件。

中间编译/测试失败均保留，断言更正和实际修复见 welcome-oracle-notes.md：旧自动样例回退要求按本批新需求更新，保留原测试身份及输入连接/文件安全；不新增跳过。新 NoDoc 搜索失败暴露了真实空路径会话崩溃，已加生产 guard。32 个既有项目测试夹具清单单独保存。

第一次统一全量/App+Sora+workspace-core Lint/Debug APK 完成：967 项中 958 通过、8 失败、1 旧跳过，日志/源码快照/JUnit 已存 full-first-*，本机真实退出 1。除既有头文件失败外，其余 7 条为 6 条旧局部项目夹具初始化缺漏和 1 条菜单长度回归；已补夹具、修复生产菜单，保留所有断言，定向收尾中。随后需要第二次集中验证与最终包，不提前宣布全绿。已实际 package/assemble 后进行新包检查，沿用原 Debug 签名；不把前批候选改名。97 旧 APK 已在 before-apks.json 留摘要，collect-results.py 会逐一核验。

六张本批原生主机预览：phone-dark-welcome、tablet-light-welcome、compact-large-welcome、phone-explorer、phone-search、tablet-light-search-results。手机抽屉图由真实 Main 与真实 Dialog 原生绘制按实际 WindowManager 坐标合成，未伪造运行结果；平板正常已有项目图中的 Shell 失败来自主机无法启动 Android Shell。无项目首次启动不自动展开终端。设备/IME/Android执行验收后置，不要求连接设备，不推送、发布或创建自动任务。

运行器/JNI/Python/主机夹具 23 个输入摘要与 63657ca 一致；源码 Python 75、Shell 4、片段 14 以哈希依据复用前批验证，不声称本批重跑。新 APK 内 Python 与语义字节码、C++闭包/头文件/许可仍实际检查。APK 25 个原生库、48 个运行资产另与最近候选逐字节比较。原格式化/P3 继续，整体目标没有完成。

最终：真实逆向快照边界1失败已修复，35定向通过；第三次完整968项966通过/1既有失败/1旧跳过，Lint0错误。最终三个包检查绑定9bb61d2451b5ce80ca3b0ccd0b49d3f43c143e4e268b9bc683fa26ab3c44d499；results.json已在源、清单、原签名、运行资产和97旧APK核对后生成唯一dist候选。中间代次保留历史含义。完整报告见../../docs/WORKBENCH_BEHAVIOR_DELIVERY_2026-10-30.md。
