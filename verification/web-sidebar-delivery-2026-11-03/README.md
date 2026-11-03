# 网页批次首轮集中记录（未交付）

本轮真实执行1138项：1136通过、1既有Windows头文件解压失败、1旧跳过；旧1109身份/跳过集合保持，新29通过。Lint零错误，App30/Sora1/Workspace0警告；245任务28执行，6分14秒，真实退出1，App确实打包一次。

额外的recordWebDependencies使用旧resolvedArtifacts时遇到Android项目多制品类型歧义，该任务失败，不宣称整批通过。原脚本和完整日志保留。该失败与App生产编译不同，实际APK另外冻结在.tools/web-sidebar-before-device-fix，摘要在相邻git-private-path-fix的before-fix.json；未复制为dist交付包，未冒称包含手机Git崩溃修复。

用户随后报告GitCredentialVault与内部项目菜单的设备崩溃，优先修复并在verification/git-private-path-fix-2026-11-03重新冻结输入、全量/Lint/集中构建；最终交付以该目录为准。新依赖记录采用modern artifactView仅选择外部组件，实际WebKit/Markwon/CommonMark摘要和Kotlin编译1.9.25/运行2.1.21已取得，不改变生产依赖图。
