# 网页辅助栏与现成组件接入计划

用户再次询问网页侧边栏后，按原 [工作台需求第7节](UI_WORKBENCH_REQUIREMENTS_2026-10-23.md)和第11–14节复核：当前Main只有隐藏的auxiliarySlot，没有生产WebView。此前“剩3个工作包”漏列了这一原需求，不能当作已完成或删除。按本次补充先查GitHub现成实现，再接入现有项目；不重新创建App。

本工作包记为P3-E，执行顺序调整为当前P3-B GitHub同步 → P3-E网页辅助栏 → P3-C设置/主题 → P3-D更多语言。原P3-A结果保留，后续不以完成部分浏览功能代替原第7节的完整要求。

## GitHub核对与选型

| 方案 | 已核对的适用性 | 当前决定 |
| --- | --- | --- |
| [AndroidX WebKit](https://github.com/androidx/androidx/tree/androidx-main/webkit) | 官方原生WebView兼容库，Apache-2.0。1.18.0已发布；实际缓存AAR的minSdk24、minCompileSdk33、minAGP7.2.0符合本项目24/35/8.7.3，295710字节、无原生库、依赖不含Kotlin。只是静态制品检查，尚未集成构建/运行 | 优先采用系统WebView和这一现成兼容库；共享工作台容器承载面板 |
| [AgentWeb](https://github.com/Justson/AgentWeb) | Apache-2.0，原生WebView封装；getWebParentLayout可取得面板根容器，具备生命周期/错误页等实现。2026-09-14仍有推送。最新5.1.7分支采用SDK36/AGP8.13.2，README要求AppCompat主题，onPause会pauseTimers影响同App全部WebView | 保留为参考/兼容版本候选；未经兼容验证不直接升级现有工具或替换主题 |
| [Android-AdvancedWebView](https://github.com/delight-im/Android-AdvancedWebView) | MIT，Java类直接继承WebView，支持原生Activity和监听器；仓库最近推送为2021-04-02 | 轻量备用，旧默认行为和新系统适配需审阅，未直接引入 |
| [Markwon](https://github.com/noties/Markwon) | Apache-2.0，实际Markdown解析到原生TextView/Spannable，支持标题、代码、列表等；浏览器和Markdown预览是不同用途 | 作为辅助栏Markdown预览候选，后续固定版本和依赖/许可后再接入 |

这些组件提供浏览或文档渲染核心，CodePocket的左右停靠、宽度策略、文件树切换和编辑保护仍由现有工作台承接。不能把一个全屏浏览器样例算作已完成IDE网页侧栏。

GitHub检索/源码读取优先使用已连接GitHub工具；短暂传输失败后使用公开GitHub/Android官方页面补核，再成功取得库源码和许可证。部分标签列表端点不在连接器读取范围，未绕过其访问控制或调用写接口。没有克隆第二份App、触发JitPack构建、修改生产依赖或生成新APK。

## 五组实际修改

1. 固定现成组件。沿用Kotlin原生Views和正式Sora，固定WebKit制品/摘要/许可并核对实际依赖；使用现成浏览核心。Markdown候选单独验证，不在没有完整许可和依赖证据时复制源码或声明已采用。
2. 真正的网页面板。地址栏、打开网址、返回/前进、刷新/停止、加载/错误状态、网页滚动、明确的外部浏览器入口；独立面板默认关闭。实现WebView生命周期、渲染进程故障和系统返回；不加入项目文件访问或原生JS桥，网页不获取Git令牌/项目保存权限。
3. 左右移动与响应式布局。默认右侧，菜单可移左/移右；重挂载同一WebView，移动不关闭或重新加载网页。左侧与文件树/搜索/Git切换，保留原查询/滚动/选中状态。平板可双边栏并拖动宽度/恢复默认值；空间不足优先收起辅助栏，保障编辑器最小宽度，手机按需临时面板/专注视图，活动栏固定。
4. 原辅助视图需求。网页、真实C++/Python库文档、函数与参数说明、Markdown预览、补全文档接同一宿主，使用实际文件/当前正文或已有引擎结果；不能放示例内容冒充功能。保存辅助视图类型、左右位置、显示/宽度和必要页面导航状态。进程重建的浏览状态按WebView实际支持范围如实处理，不宣称任意网页DOM均可完整恢复。未来插件自定义面板及安装/更新/API仍按原规格明确的未来项保留，不放虚假商店入口。
5. 保护与整批验收。当前文件/保存版本、草稿、独立stdin、设置未应用草稿、终端会话/PTY、真实Python/C++运行和头文件Tab不因网页操作重置。定向检查左右重挂载/被动缩窗/返回/重建/权限边界、手机平板/深浅色/大字体及文档真实来源；收敛后统一全量、Lint、APK与实际包检查。主机ShadowWebView/原生外壳绘制不能称Android Chromium网页运行或实机验收。

## 已取得证据与限制

[原始记录](../verification/web-sidebar-research-2026-11-02/README.md)保存GitHub源索引、真实AAR/POM静态检查和下载失败日志。Google Maven官方地址的沙箱及主机读取均DNS11001失败，随后沿用仓库.tools/mirror.init.gradle既有Google制品镜像取得固定版本，未改系统DNS/代理或仓库依赖。镜像文件摘要用于本次静态检查，不把它写成已通过原生运行或完整构建。

WebKit AAR SHA256：`f15a0bd7d51f4bb8dffdd3a2cf5829f9a7de9ad4ba2682fa85d619032050581f`。WebKit官方[发布记录](https://developer.android.com/jetpack/androidx/releases/webkit)与[源构建文件](https://github.com/androidx/androidx/blob/androidx-main/webkit/webkit/build.gradle)另行核对。

本包未实现、未构建新APK。当前可安装候选仍为P3-A本地Git包26ac0bec…，不含HTTPS同步或网页辅助栏；设备仍后置，不推送/发布/创建自动任务。
