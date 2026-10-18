# P1-C Sora C++ / Python离线语法适配

2026-10-18，基线 `3a5b53ccb99f4ba90d83cc2123e9293959d5ef29`。接续已做的事务/定位；默认IDE仍原生，实机统一后置。

## 实现范围

采用[Sora官方TextMate路线](https://project-sora.github.io/sora-editor-docs/guide/using-language)，核对实际0.23.7 sources.jar后接入，不照搬当前0.24.4文档接口。仅可选editor-lab增加language-textmate0.23.7，使用原TM4E/Joni分析器；没有新造编辑内核、修改上游库、升级全项目Kotlin或启用LSP。

VS Code1.95.3的Python/C++/C++ macro/platform语法和配置原样离线打包，两套本地TextMate主题分别给关键字、类型、函数、字符串、数字、注释等指定颜色。TextMateBundle在单一后台任务读文件/解析/预热代表语句，关闭配置流并移除临时resolver；使用实例级语法/主题注册表，不把Activity塞进全局注册表。SoraSyntaxController只在UI线程挂载成熟异步分析器；输入回调只检查长度、合并排队，状态更新不复制整篇正文。

“语法”按钮选择Python/C++17/纯文本，保留正文、选区与历史；选择随旋转保留。组合期间延后挂载，暂停/销毁取消回调，迟到加载结果关闭；加载/适配失败明确纯文本，不逐键重试失败。超4.5万字符降级，缩小后恢复所选模式，不截断原草稿。语法配置的括号/回车规则交给组件，整套CodePocket键位/补全/片段/参数联动仍待后续；TextMate自动词法候选关闭，不能把它当语义IntelliSense。

固定分析器实际调用java.time.Duration；实验开启desugar_jdk_libs2.1.5，保持minSdk24。依据[Android API脱糖说明](https://developer.android.com/studio/write/java8-support)，审计实际Dex包含j$.time.Duration、IGrammar时限参数与方法表改写。D8 TimeConversions正常保留平台Duration转换引用，不能断言所有java.time字符串消失，也不能据构建声称API24设备已验收。

## 文件与边界

| 文件 | 改动 |
| --- | --- |
| .gitattributes（新增） | textmate资产保留原始字节，避免Windows自动换行破坏哈希；仅上游许可原文保留既有空白 |
| editor-lab/build.gradle.kts | 固定TextMate和脱糖依赖，test读取与APK相同assets；默认Gradle不改 |
| SyntaxLanguage.java / TextMateBundle.java / SoraSyntaxController.java（新增） | 语言与预算、真实离线加载、UI挂载/主题/生命周期/降级 |
| EditorLabActivity.java | 语法入口、选择旋转保留、异步加载与清理、许可阅读 |
| assets/textmate/与SORA-NOTICE.txt | 原语法/配置、两套主题、来源哈希和原许可通知；20项asset已核对 |
| TextMateGrammarTest.java、test/java/android/util/Log.java（新增） | 19项真实引擎测试；仅日志转stderr，其余Android stub仍严格，桥不进APK |
| README / 实验README / 进度 / RESUME / 第三方清单 / 本报告 | 当前结果与接续 |

生产CodeEditor、编辑/导航/文档/保存/SAF/草稿/独立stdin、Python/Wandbox运行、旧测试、app/root Gradle、Manifest、Actions、原三份需求输入哈希保持。默认应用无新依赖/权限/存储迁移，实验无网络权限。没有迁移项目/运行器或默认组件。

## 真实验证与处理过的失败

实验最终离线 **29s**，51任务18执行/33未变化；**60/60**，原41项名称/状态保留，新增19。实际运行固定TM4E/Joni与APK同源语法/主题，验证C++/Python不同作用域、多行注释/三引号、C++预处理/普通raw字符串、中文emoji偏移、二进制token实际颜色、配置回车动作、空/不完整文本、密集代码、资源关闭/失败后重载及预算。不是运行结果或View mock；没有执行Android View/InputConnection。组合与真实光标/键鼠时序仍是设备项。

保留round-01编译失败（误用setUseTab，按固定API改为useTab）、round-02日志stub失败、round-03两项断言失败（staticmethod真实scope为support.type，Python通过onEnterRules而非indentationRules缩进）。仅日志桥把真实告警输出stderr，不启用returnDefaultValues；修正预期并补测真实OnEnterSupport，没有删除/跳过失败用例。round-04首个成功APK/测试保留，补许可与界面阅读入口后最终重新构建验证。

**已知语法缺口**：C++部分SQL/GLSL/汇编/正则嵌入grammar和外部规则未齐，TM4E打印缺失/跳过警告；普通C++/Python覆盖样例通过并不代表所有语法已覆盖。日志保留在JUnit，覆盖样例未出现E级正则编译回退。TextMate scope不是类型推断、成员解析、完整语法树或LSP。

默认强制离线重编译 **1m26s，64/64任务实际执行**；原270中269通过/1既有Windows符号链接跳过。Python源码/真实APK字节码各13/13、实际模板Python/主机C++17各7/7；不等于Android运行时或新Wandbox请求。Lint默认0错误/11既有警告、实验0错误/6既有警告，类别计数与上批相同。

两份APK CRC/v2签名/16KB ZIP对齐/独立包名/API24与target34/关键Dex定义/复制哈希通过；默认arm64-v8a、无Sora/实验类；实验无Python/native.so，日志桥未打包。20项asset逐字节一致，实际Gradle TextMate AAR与下载哈希相同。默认全编译清掉一个synthetic枚举辅助IdeCommandMenu$EntriesMappings：原标记已查、当前javap直接调getEntries、新Dex无悬空引用，其余稳定类保留；不当作删功能或体积优化。审计脚本初次也将正常D8平台转换桥误判为残留，保留原检查结果后改为核对引擎接口和正常桥，结果中同时列出两者。

默认runtime仍Kotlin1.9.25/无Sora；实验Sora/TextMate0.23.7、Kotlin2.1.21。旧10项APK/材料包SHA256保持；本地提交，无push/发布/远端Actions。.tools、dist、旧verification与设备备份保留。

## 产物与接续

[默认IDE APK](../dist/CodePocket-editor-sora-languages-debug.apk)：21,879,652字节，SHA256 `b80071918c11b2eb6706a8f4415f5ef5b1b1e61f80a3468e80a54384677ecb28`。

[Sora语法实验APK](../dist/CodePocket-Sora-languages-lab-debug.apk)：4,890,437字节，SHA256 `223ce8377a129553bf464b6e59b3cb10b1e8d1248f167d527c7c67b9ed95541a`。

证据verification/editor-sora-languages-2026-10-18/：results.json、两份verify脚本/日志、JUnit/Lint、失败与首个成功版本、输入哈希/备份、精确API原文、语法/许可下载哈希、依赖图、javap/脱糖、签名/对齐/字节码与源码保护审计。Maven Central本机403后使用已有Aliyun镜像取同版，最终两模块均离线构建；辅助下载/审计检查的失败记录保留。许可清单为初步核对，完整精确源码/重链接材料仍待正式发布前收齐，历史ZIP不是本批源码包。

下一批接Sora完整生产Port/导航、现有候选/片段/参数及SAF项目会话，再评估默认迁移。P1完整动画/手势/设置/真实Python流式、P2语义与本地/多文件C++、P3继续保留，P0问题优先。

实机统一后置；未执行Android/模拟器、渲染/InputConnection/实际IME/键鼠/折行/滚动/主题/手机平板/卓易通/FPS验收，无新Wandbox或远端Actions。主机语法与颜色通过不证明实际屏幕效果或60FPS。

提交检查发现上游EPL/JCodings许可原有行尾空白/末尾空行，未改写原许可；.gitattributes只对textmate资产关闭EOL转换，只对许可证目录保留原有空白，代码检查仍严格。提交时还核对Git暂存blob、工作文件、APK内资产三者字节一致，避免跨平台克隆后UPSTREAM哈希漂移。不更改用户Git配置。
