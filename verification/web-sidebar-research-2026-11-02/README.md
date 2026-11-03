# 网页侧边栏 GitHub 现成方案研究

依据用户“先在GitHub找有没有现成的”，只做源码/许可/制品核对，没有修改生产依赖或构建APK。原需求在docs/UI_WORKBENCH_REQUIREMENTS_2026-10-23.md第7、11–14节；MainActivity的auxiliarySlot仍是隐藏空容器，没有WebView，不能将预留结构算成功能。

github-source-index.json记录实际仓库元数据、默认分支、推送时间、许可证，以及读过文件的URL和Git blob SHA。AgentWeb和AndroidX WebKit的许可证为Apache-2.0，AdvancedWebView为MIT，Markwon为Apache-2.0。读取的是原仓库，不根据第三方教程或星标数量作兼容结论；没有执行远端源码/构建脚本。

AgentWeb最新5.1.7分支声明SDK36/AGP8.13.2与AppCompat主题要求，getWebParentLayout可取得根容器，生命周期onPause调用pauseTimers。本项目原生Activity/平台Material主题、SDK35/AGP8.7.3，不能盲目加入最新封装。AdvancedWebView虽然轻量，但实际最近推送2021-04-02；Markwon实际是原生Markdown渲染器，不用于冒充网页浏览。

官方WebKit发布页面核对1.18.0后，inspect_webkit.py尝试从官方Google Maven读取，webkit-inspection-first.log和webkit-inspection-host.log保留DNS11001失败。随后使用仓库既有Google Maven镜像成功取得固定AAR/POM到.tools/web-sidebar-probe，没有变更网络配置。webkit-aar-inspection.json记录镜像来源、文件摘要和真实内容：AAR295710字节，minSdk24/minCompileSdk33/minAGP7.2.0，271个class，无.so/.dll/.exe，含WebViewCompat和WebViewAssetLoader。

这些元数据符合项目现有24/35/8.7.3范围，但没有编译/运行此依赖，不能说完整兼容或Android网页测试通过。首选系统WebView+现成WebKit兼容库，AgentWeb兼容版本/部分实现和AdvancedWebView保留候选；左右停靠、宽度/导航状态和原文件/终端保护由现有工作台接入。详见docs/P3_WEB_AUXILIARY_BATCH_2026-11-02.md。

本次同时纠正“只剩3块”的遗漏，将原网页辅助栏列为P3-E，排在当前P3-B之后、P3-C/D之前。库文档、函数参数、补全文档、Markdown与插件未来项边界保留。新APK没有构建；最新交付仍为本地Git候选26ac0bec…，未包含此功能。
