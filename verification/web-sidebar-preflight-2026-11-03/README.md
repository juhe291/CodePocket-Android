# 网页辅助栏组件与接入阶段

从本地d33faa3继续。实际可安装候选仍为HTTPS Git包c63e8df1…；本目录没有网页新APK或设备验收结果。原目标仍含本包、P3-C设置/主题、P3-D更多语言。

## 已执行记录

- baseline.log/baseline-junit/baseline-results.json：实际Main5项4失败/1通过，入口/WebView/搜索/编辑保护缺失基线；默认关闭通过。baseline-sources.json对应当时输入，尚未修改生产。后续没有删断言或新增跳过。
- markwon-download.log：官方Maven Central DNS11001失败。markwon-mirror.log和commonmark-download.log沿用仓库现有阿里云制品镜像取得固定Markwon4.6.2和实际POM要求的CommonMark0.13.0，没有改系统DNS/代理。
- resolve.init.gradle是本次隔离配置，未向App依赖图添加库；resolve.log实际29秒成功解析15份候选制品，resolved-candidates.json保存路径/版本/摘要。随后才在App加入两个固定直接依赖。实际App的Kotlin对齐沿用原规则，不以隔离图内1.7.10当作App运行版本。
- components.log/component-junit/component-results.json：9项通过，实际WebKit类/Markwon原生Spanned/CommonMark AST、必应/百度URL及双侧栏宽度规则；component-sources.json为该阶段冻结输入，不是后来Main接入的结果。
- integration-first.log：Main接入首轮编译失败，WebSettingsCompat.setAlgorithmicDarkeningAllowed误传WebView；修复前WebAuxiliaryPanel源码另存。此次没有新JUnit，没有复制旧结果。
- integration-fixed.log/integration-fixed-junit/integration-fixed-results.json：14项全部通过，原5项基线保持，真实Main入口、WebView对象、必应请求URL、手机关闭回相同脏正文、平板右侧宿主通过。integration-fixed-sources.json为当时输入；不是Android Chromium访问网络或完整批次回归。
- details-first.log/details-first-junit：27项24通过/3失败，原始JUnit、源摘要与三张图完整保存。补全测试调用了不存在的updateCompletions；窗口夹具未同步WindowManager；恢复夹具忽略原persist会尝试保存。修正为调用真实requestCompletion、同步窗口尺寸，以及通过真实外部版本冲突保持未写回草稿，原断言保留；设置保护另加强为已实际修改但未应用的草稿。
- details-fixed-daemon-failure.log：沙箱无法连接本机Gradle守护进程，未产生新JUnit。沙箱外离线details-fixed-permission.log/details-fixed-junit实际27通过，1分22秒；不是前轮14项。
- details-fixed-previews的手机图显示真实缺陷：原终端几何刷新重新显示editorRegion，使网页专注画面被挤成半宽。生产已修复可见性组合，新增实际GONE与312px断言；visibility-fixed.log/visibility-fixed-junit实际27通过，2分21秒。原失败/错误图不删除，visibility-fixed-previews手机实际全宽，已查看。
- related.ps1/related.log随后扩大到原设置/草稿/搜索/Git/头文件Tab与真实分隔条双侧调整、搜索查询视图保持，首轮174项173通过/1失败，实际分隔条负方向dp取整导致24dp步长变23dp；生产已改为正距离取整后应用方向，原344px/双侧/重建断言保持，related-fixed.log待结束归档。旧ShellUiTest无文档允许列表补入本批真实WEB命令，保持完整集合相等和禁止运行/保存等原断言；未新增跳过。

## 来源、许可和边界

直接组件：[AndroidX WebKit](https://github.com/androidx/androidx/tree/androidx-main/webkit)、[Markwon](https://github.com/noties/Markwon)、[CommonMark](https://github.com/commonmark/commonmark-java)。官方GitHub读取的Markwon Apache许可和CommonMark新版许可保留；实际0.13.0 JAR版权为2015–2016 Atlassian，和上游新版Robin Stocker文本不同，采用固定JAR原文；artifact-notices.json记录真实归档中的许可摘要。WebKit自身AAR许可原文也一并纳入App现有“帮助与许可”阅读入口。

Markwon AAR SHA256 29e4c1de9aefa3d3c7f8a7948ba24c3696adc34b29e3d7d106eb35e8fa2c85fd，minSdk16；CommonMark JAR SHA256 fd38aecef680649894ffd7b434e10081fc609e260c63e16c4323a3eaa2a9f096。原WebKit摘要f15a0bd7…保持。正式制品API由javap检查，不根据教程猜构造形式。

新增地球图标沿用Lucide0.468.0，官方icons/globe.svg blob 53c80ee35dd294b0193642f0731010e033ba0618；只作原路径的Android矢量转换，原ISC许可保留。

默认必应、可选百度，按用户本轮要求持久化。网页无项目文件访问、无原生JS桥、无Git令牌注入；普通文档由Markwon/TextView渲染，网页由系统WebView承载。SSL错误取消；子资源错误不替代主页面状态。查看/移动/关闭不保存或重置编辑正文、stdin、设置草稿或PTY所有者。

Android官方[WebView.saveState/restoreState](https://developer.android.com/reference/android/webkit/WebView#saveState(android.os.Bundle))明确不保存/恢复显示数据；主机ShadowWebView和原生窗口绘图只证明API/状态及外壳。设备Chromium、API24、真实IME和网页网络仍后置。当前主机有SDK28/34缓存，不能把SDK28的旧提供方路径等同API24实机通过。
