# 格式化批次（候选包完成，Android验收后置）

基线50c1288，2026-10-30建批、11-02收尾。四组范围见../../docs/P2_FORMATTING_BATCH_2026-10-30.md，最终交付见../../docs/P2_FORMATTING_DELIVERY_2026-10-30.md。results.json、final-junit和final-source-hashes为最终检查点：995项993通过/1既有Windows失败/1旧跳过，新增27全通过；Lint零错误。原P3与整体目标保留。

最终dist/CodePocket-formatting-2026-10-30-debug.apk为121359886字节，SHA256为59e13ca581f0806ba25df937772228e3802c5ee1896aebb091cf80e174756668。原签名/CRC/16KiB、实际包内Python48/语义22/格式化20和C++闭包通过。原25原生库、45份未变运行资产及98旧APK保持；3份Chaquopy资产只允许明确的新增内容。三张原生图已查看，非设备截图。

- entry-baseline：实际Main缺入口，2执行/2失败；原断言保留。entry-first：23通过，含原C++生产引擎和设置/菜单。
- python-format-first/current：原19真实后端检查通过。selection-baseline：20中1失败，暴露value=中选区末端多含新增空格；selection-fixed：20通过。Python全源码python-source-all.log：95通过（原75+20）。
- cpp-format-probe.json：固定真实主机clangd，五组实际g++前后执行等价和三种样式，8项通过；不是Android运行。
- transactions-first：测试夹具一处Kotlin语法错误，未执行测试；不能用旧XML计数。transactions-fixed：17中1真实选区失败；transactions-final：35中33通过/2新视觉夹具的触摸前断言失败。补明确宿主测量和真实DOWN/UP后transactions-complete的37项全部通过。原失败日志/JUnit及源摘要保持。
- build-asset-delta前三份日志保留：初版误把48份总体运行资产当成20份Chaquopy资产，又漏计旧空__pycache__/目录。按实际归档修正后，build-asset-delta-verified.log的4项通过；反例使用具体原因断言，拒绝原生、runner字节码和额外构建字段变化。该检查只读取生成缓存和旧APK，没有构建/验证新APK。
- before-apks.json：98份既有APK摘要；收尾再次全部核对保持，原签名保留。Android实际执行仍未验证。

- full-first：新视觉测试编译失败，没有执行主App测试；--continue实际打包一次，full-first-status记录该APK。full-second：995中991通过/3失败/1旧跳过，两处旧结构10断言改为11并保留其他迁移断言。full-third：995中993通过/1既有失败/1旧跳过。full-build为第四次，只调整新主机夹具工具路径后重验，仍为993/1/1；后三次packageDebug均UP-TO-DATE，同一APK没有重打包。CodeFormattingTest-before-portability原文件与第三次摘要一致，七个测试正文未变。
- 最终源码Python95、主机Shell4/片段14本批实际重跑通过。Shell沙箱DLL初始化失败日志保留，主机权限重跑通过；未修改系统环境。
- formatting-apk-first-error保存检查器首次重载pycodestyle找不到搜索路径的错误。只补临时字节码目录后，实际APK20项通过；修正前检查器逐字节保留，生产代码和APK未变，不重跑构建。签名/包内Python/语义/C++检查此前已通过，JSON全部绑定相同APK摘要。
- collect-results.py严格校验旧用例身份、原生/资产/旧APK、最终源摘要、Lint、实际包检查和Manifest，仅在全部门槛满足后复制一份dist候选。它没有将既有失败改写为通过，完整命令退出1。

实际实现：code_formatting.py、CodeFormatting/CodeFormattingService、CppLspEngine格式化模式与独立8MB帧上限、CppRuntime独立子进程、Main正式编辑事务/保存策略、原生菜单和设置。原语义2MB/45000字符限制不放宽，格式化不读原工作区路径/配置、不写原文件，迟到结果绑定key/版本/正文/选区；失败的保存仍走原版本冲突与草稿保护。
