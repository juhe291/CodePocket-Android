# 设置层次与运行说明一致性（2026-10-24）

在54fc322的现有界面上增量调整。继续采用灰蓝工作台、标题图标底座、细分隔线和柔和文件类型色，避免发光、荧光、渐变或额外重复入口。本批没有接入暂缓的新功能。

## 本批修改

- 终端设置用两行只读信息展示既有Python本地交互、C++17云端预填输入；分别使用文件类型图标、标题和简短说明。第三方提示完整说明代码与输入会发送至Wandbox，原发送确认保持。
- 移除当前bottomOnly工作台中不生效的“优先左右分屏”开关。旧AppSettings.panelPreferSide字段、JSON版本/读写和后台布局能力保持；不是删除旧偏好，也不是新增左右分屏。
- 分类说明改为“终端显示与代码运行”；设置搜索、草稿、取消/应用和返回编辑器继续沿用原处理器。Python未恢复预填输入；没有新增第二个终端或改变真实运行逻辑。
- 浏览器样稿同步分组信息，示例仍明确标为设计预览，不执行程序。

## 测试与构建

- 主App完整696项：695通过、0失败、1既有Windows符号链接跳过。前批694个用例身份及跳过集合保留，新2项覆盖旧JSON偏好读入/修改无关设置后的保留与Sora内容身份、320dp/1.7倍字体下说明完整换行、无省略、滚动后完整可见。
- 初次完整695项694通过；补充窄屏可达性后首次696项出现1个新增断言失败。主机fullScroll默认动画在检查时尚未结束；只在夹具关闭平滑滚动后定向通过，保留完整可见断言和初次日志/结果。未放宽断言或改动生产滚动行为。
- 最终完整Gradle命令2m 5s成功；Lint0错误，主App16/Sora1既有警告。最终APK为首轮实际packageDebug后的缓存复核；相同哈希已重新比对。
- Python源码44、实际APK字节码39、主机Shell启动4、真实Python/C++17片段各7通过；原签名、ZIP CRC和16KB对齐通过。75个旧及中间APK保持原哈希，10个原生库与Python app.imy逐字节不变。
- 浏览器31场景×4窗口×2主题=248项场景/边界、24项交互、20项配色检查通过。首次样稿观察器将含装饰SVG空白的标题当成不同字符串，等待超时；修正trim后全部重测，保留browser-matrix-first.log。没有程序运行结果由样稿代替。

## 视觉检查与产物

[最新APK](../dist/CodePocket-settings-consistency-2026-10-24-debug.apk)：26,472,670字节，SHA256 `3ee6045cbe4a9b0f81aa30f2fa66e92842860126be59569975bd5fbdee6d7c26`，arm64-v8a、原调试签名。

[原生设置预览](settings-consistency-preview/README.md) · [可点击样稿](ui-preview/README.md) · [机器结果](../verification/workbench-settings-consistency-2026-10-24/results.json) · [UI范围](UI_SCOPE_AUDIT_2026-10-24.md)。

本次实际目视了原生手机分类、平板浅色终端设置、320dp大字体终端说明，以及浏览器平板深色工作台/浅色终端设置；其余截图和自动检查单独归档，不声称所有界面已逐张人工验收。原生截图来自Robolectric Native Graphics，不是设备截图；预览专用浏览器/本机服务已关闭。

设备输入法/实体键鼠/卓易通/API24/冷启动第一帧/帧率及Wandbox、远端Actions仍未验收。旧单次helper导入失败完整重测未复现，原因未确认；本批没有改动Python运行模块，不标记为已解决。网页/Git/插件/账号/深度语义与原P2/P3需求继续保留暂缓，整体项目不据此称全部完成。

生产文件：SettingsDialog.kt、strings.xml；回归MainFileSettingsIntegrationTest.kt、SettingsSurfaceVisualTest.kt；样稿JS/CSS及截图；README/RESUME、当前范围清单、本报告、原生预览、本批验证脚本与JSON。
