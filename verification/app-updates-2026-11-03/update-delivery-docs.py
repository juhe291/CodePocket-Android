"""Write delivery facts only after the actual artifact collector has succeeded."""
import json
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
r=json.loads((OUT/'results.json').read_text());assert r['tests']['tests']==1173
digest=r['sha256'];size=r['bytes'];apk=Path(r['apk']).name
delivery=f'''# 检查更新与首次 GitHub 推送

本批从1daa97f接续，用户已授权新建公开仓库juhe291/CodePocket-Android并正常推送。原C设置搜索/主题、D更多语言保留；不创建新工作流或自动任务，不自动发布Release/APK。公开仓库已创建且dry-run通过；远端提交以Git分支为准，推送后的push-results.json保存在本地证据目录，不为把自证SHA写回提交而重复触发CI。

设置 → 帮助与许可 → 检查更新。显示实际安装版本0.2.1（3），只在手动点击时后台读取固定GitHub正式Release；无正式Release明确显示尚无可访问版本，不冒称最新版。支持新版本、当前版本较新、限流、超时/网络、格式异常、显式重试、全文选择阅读及正式发布页/适配APK跳转。关闭或销毁取消请求，设置草稿、编辑/Sora、保存与恢复、运行及PTY保持。启动/重建不自动联网，不使用项目Git令牌；无新增安装权限或静默安装。

## 本轮实际验证

- 入口缺失基线2项全失败，原断言保留；首组36项全通过（本批新增24），扩大相关122项全通过，包括原设置触摸、主题预览、头文件Tab、Git路径、网页及欢迎区。
- 集中1173项1171通过、1既有Windows头文件解压失败、1旧跳过；旧1149身份和跳过集合保持，新24全通过。完整命令真实退出1，不能称全量全绿；只有一次实际packageDebug。Lint零错误，App34/Sora1/Workspace0警告；新增4项为更新对话框SetTextI18n本地化提示，按用户要求登记后续处理，不阻塞本批。
- APK {apk}，{size}字节，SHA256 `{digest}`，版本0.2.1/3。原Debug证书、CRC、16KiB对齐、更新类/正式源/测试类排除及Manifest检查通过；相对前包仅版本字段变化，权限不变。
- 原25原生库、50运行资产按精确文件集合与字节保持；102旧APK逐项保持。未变源码Python152、包内48/语义22/Git及格式化82、Shell4/片段14、矢量5按源/生成内容摘要复用，并非本轮重新执行。终端146有效UP-TO-DATE，editor-assist NO-SOURCE。
- 两张原生图已检查：手机深色新版本、平板浅色大字体未发布状态。图中fixture版本为测试数据，不能当成已发布GitHub版本或实机证明。Android运行、设备窗口、Keystore及用户手机修复效果验收继续后置。
- 对真实公开更新API的无凭据读取实际返回403，响应明确为当前出口IP的60次额度已耗尽；不冒称线上读取成功。限流显示/重试分支已通过真实回环HTTP与原生UI检查，不增加令牌或绕过限流。原响应和限流头保留。

冻结486份输入、原始JUnit、实际依赖与完整构建日志在verification/app-updates-2026-11-03。保留最初com.sun.net.httpserver测试夹具编译失败；仅将夹具换为java.base真实ServerSocket，原断言保持。审批额度中断时未启动进程，恢复后正常重新申请并完成；没有冒用旧JUnit。

## 后续正式更新约定

正式Release用稳定语义版本（例如v0.2.2），发布包versionCode应大于3，应用ID保持com.codepocket.ide，并使用原签名。适配APK名称用CodePocket-v0.2.2-arm64-v8a-debug.apk或明确universal变体。多个同类候选不猜选，提供正式发布页；网页/下载链接仅接受当前GitHub仓库HTTPS地址。

本次只推送源码和已核对的本批证据，新APK保存在本机忽略提交的dist中；不将118MB安装包强制加入Git或假装已有公开下载。既有历史Git对象与旧已跟踪APK保持，不重写历史。原两条工作流不改触发/发布行为，正常推送可能触发现有CI，状态独立记录。
'''
(ROOT/'docs/APP_UPDATES_DELIVERY_2026-11-03.md').write_text(delivery,encoding='utf-8')
readme=ROOT/'README.md';text=readme.read_text()
start=text.index('**当前：Git 手机入口修复') if '**当前：Git 手机入口修复' in text else text.index('**历史：Git 手机入口修复')
intro=f'''# CodePocket · Android C++ / Python IDE

Kotlin原生Views与正式Sora编辑器，提供Python/C++真实本地运行、PTY终端、头文件Tab补全、保存/恢复保护、项目树/搜索/Git工作台及网页辅助栏。支持手机和平板，网页搜索默认必应、可选百度。

**当前候选：0.2.1（3），2026-11-03。** 设置 → 帮助与许可 → 检查更新；手动读取[本仓库正式发布页](https://github.com/juhe291/CodePocket-Android/releases)。本次尚未发布Release或公开APK，未发布时会明确提示。

- [本批结果与更新约定](docs/APP_UPDATES_DELIVERY_2026-11-03.md)：新增24项及相关122项全部通过；集中1173项1171通过、1既有Windows失败、1旧跳过，Lint零错误。完整命令退出1，不称全绿；Android设备验收后置。
- 本机安装包为`dist/{apk}`（{size}字节，原Debug签名），SHA256 `{digest}`。新包未加入Git；下文dist文件名用于本地历史追溯，不代表公开下载。
- 本批保留Git手机路径修复、网页辅助栏、设置草稿、头文件Tab、真实运行和PTY。后续仍为设置搜索/主题、更多语言分析，见[原P3计划](docs/P3_IMPLEMENTATION_PLAN_2026-11-02.md)。

以下为既往交付与开发记录；各阶段能力和验证范围以对应日期文档为准。

'''
text=intro+text[start:].replace('**当前：Git 手机入口修复','**历史：Git 手机入口修复',1)
text=re.sub(r'\[[^\]]+\]\((dist/[^)]+)\)',lambda m:'`'+m.group(1)+'`',text)
text=text.replace('本地已建立 Git 基线与分阶段记录，尚未配置远端或运行 GitHub Actions；没有自动 push、发布或上传 APK。','本次按用户授权新建公开仓库并正常推送；远端提交与既有CI的实际结果见本批推送记录。不新增工作流或自动发布Release，CI结果与本地主机验证分别记录。')
readme.write_text(text,encoding='utf-8')
resume=ROOT/'docs/RESUME.md';text=resume.read_text()
heading='## 最新优先级：检查更新 → GitHub推送（2026-11-03）' if '## 最新优先级：检查更新 → GitHub推送（2026-11-03）' in text else '## 最新优先级：检查更新完成验证 → GitHub推送（2026-11-03）'
begin=text.index(heading);end=text.index('## 最近完成：用户手机Git路径修复',begin)
text=text[:begin]+f'''## 最新优先级：检查更新完成验证 → GitHub推送（2026-11-03）

- [本批交付](APP_UPDATES_DELIVERY_2026-11-03.md)：从1daa97f完成手动更新入口/实际安装版本、固定juhe291/CodePocket-Android正式源、有界HTTP读取与取消、状态/全文和可信下载跳转。版本0.2.1/3，无自动联网/安装；原设置草稿、Sora保存、头文件Tab、真实运行与PTY保持。
- 定向36/扩大122全部通过，新24全通过；集中1173项1171通过/1既有Windows失败/1旧跳过，旧1149身份和跳过保持，Lint零错误。一次实际打包，完整命令退出1，不能称全绿。486份冻结输入及全部原始证据在verification/app-updates-2026-11-03。
- 候选dist/{apk}，{size}字节，SHA256 `{digest}`；原签名/CRC/16KiB/版本/Manifest/正式源/DEX检查通过，原25库/50资产和102旧包保持。未变运行专项按摘要复用，设备验收后置。
- 用户明确授权新建公开仓库juhe291/CodePocket-Android并正常推送，替代早期仅本地限制。远端创建、origin、提交SHA及CI实际状态见本批push-results.json；尚无Release，本次不自动发布APK。历史脏文件与C计划草稿保留，不混入本批。C设置/主题、D更多语言继续保留，整个原目标未完成。

'''+text[end:];resume.write_text(text,encoding='utf-8')
plan=ROOT/'docs/APP_UPDATES_AND_GITHUB_BATCH_2026-11-03.md';text=plan.read_text().replace('当前尚无本轮通过结果或新APK，不冒称推送完成。','本轮实际验证和候选已完成，见APP_UPDATES_DELIVERY_2026-11-03.md；远端实际结果见本批push-results.json，不把未发布Release说成已有线上版本。');plan.write_text(text,encoding='utf-8')
print('Delivery, current README, resume and retained plan updated from verified results')
