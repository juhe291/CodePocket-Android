# 交给 DeepSeek 的未完成工作

用户最新指令：先修复 Bug、构建 App，其他工作交给 DeepSeek。本目录只在本地保留，未混入 Bug 安装包或本次推送。实际项目仍是原 CodePocket-Android，不是另建项目。

恢复入口：docs/RESUME.md、docs/DEVELOPMENT_BATCH_POLICY.md、docs/P3_SETTINGS_THEMES_BATCH_2026-11-03.md、docs/P3_IMPLEMENTATION_PLAN_2026-11-02.md。以当前源码和本批原始结果为准。

P3-C 设置搜索/主题：六项缺失基线原为六失败；只实现搜索后为四通过、两项自定义主题失败，日志/JUnit留在verification/settings-themes-2026-11-03。多词、全半角、英文别名及搜索后真实设置操作/草稿恢复已取得这四项验证。自定义主题Java解析模型与AppSettings扩展刚开始，尚未编译验证，也没有接实际Sora、预览和编辑弹窗；不能称主题完成。P3-D更多语言未实现，原需求保留。

files目录保存五份部分工作，路径相对原仓库；manifest.json记录全部SHA256。settings-themes.patch保存两份已有生产文件的修改，另外三份原未跟踪文件已原样移到files下。原六项断言全文保留；因用户取消本次功能范围，其基线测试随工作移交，没有加入Bug批次。Bug批次会单独核对原1173项完整身份，不能用这六项未实现功能的基线掩盖Bug回归。

接手时审查补丁并合并到最新代码，勿直接覆盖后续Bug修复。只有整页应用成功才保存设置；保留原Views、Sora、保存/恢复、Python/C++真实运行、PTY及头文件Tab。其他功能继续按原计划，避免再做零碎视觉润色。
