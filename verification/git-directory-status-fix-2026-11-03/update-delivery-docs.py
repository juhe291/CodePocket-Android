"""Update public handoff only from completed runtime and artifact evidence."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
r=json.loads((OUT/'results.json').read_text());digest=r['sha256'];size=r['bytes'];apk=r['apk']
doc=ROOT/'docs/FIX_GIT_DIRECTORY_STATUS_2026-11-03.md';text=doc.read_text();text=text[:text.index('## 尚待完成的本批工作')]+f'''## 本批实际验证

- 原21项基线20通过/1失败，修复后21全通过；完整Python159项全通过、零跳过，旧157身份保持，新增2项。包括原Linux暴露的真实TLS/Git目录与文件双向转换、快进和未跟踪文件保护。
- 集中1173项1171通过/1既有Windows头文件解压失败/1旧跳过，旧1173身份/跳过集合保持，无新增Kotlin失败。Lint零错误，App34/Sora1/Workspace0警告；源自前批的4项SetTextI18n继续登记。完整命令实际退出1，只一次packageDebug，不能称全绿。
- 实际新APK内Python48/语义22/Git与格式化84全通过。159是源码测试，84是加载实际包内模块及依赖的执行；两者都只是主机证据。
- 原Debug签名、CRC、16KiB、版本0.2.1/3与Manifest核对通过。严格比对固定4715b84b…包，仅git_workspace.pyc代码改变；其余6个pyc只允许4字节mtime头改变，magic/flags/源长度/完整代码负载保持，逐项摘要已记录；build.json对应app.imy的实际SHA1。外层其他归档条目（签名记录除外）字节相同，包含依赖、25原生库、DEX/资源/Manifest/许可。旧包被新检查器真实拒绝，旧CPP检查模式保持。
- 103旧APK逐项保持；Shell4/片段14按本次实际JUnit生成字节复用前批执行，矢量5在源码159中重跑；终端146有效UP-TO-DATE。原生图按字节完全一致的DEX/资源/Manifest复用更新批次已看图，没有新UI绘制或设备验收。

候选`{apk}`，{size}字节，SHA256 `{digest}`。旧更新包4715b84b…保留。487份冻结输入、全部JUnit/失败及实际包检查在verification/git-directory-status-fix-2026-11-03；先前两个远端CI失败分别保留，不能改成已通过。

初次包检查因6个未变模块的pyc时间戳头不同而拒绝，原检查器/日志保留。仅调整验证脚本以精确限制4字节mtime，原编译输入不变，验证脚本前后摘要独立记录；复用同一个已生成APK，没有重打包或把代码负载变动放行。

接下来按本次授权正常追加推送同一master，最终远端SHA与CI实际结果在本地本批push-results.json。不强推，不发布Release或新建工作流/自动任务；无首个Release时更新功能明确未发布，当前主机出口匿名API限流也已记录。原C设置/主题、D更多语言保留，整个原目标仍活动；Android设备验收继续后置。
''';doc.write_text(text,encoding='utf-8')
resume=ROOT/'docs/RESUME.md';text=resume.read_text();begin=text.index('## 最新优先级：检查更新完成验证');end=text.index('## 最近完成：用户手机Git路径修复',begin)
text=text[:begin]+f'''## 最新优先级：检查更新与GitHub首次推送、POSIX Git修复（2026-11-03）

- 检查更新已随1948720推送到公开仓库https://github.com/juhe291/CodePocket-Android ，master/远端SHA核对。设置 → 帮助与许可 → 检查更新，当前0.2.1/3，手动访问固定正式Release；未发布/限流有真实提示，关闭取消且保留草稿，无自动联网/安装。原头文件Tab、Sora、保存、真实运行/PTY和网页保持。
- 首次CI暴露缓存父目录和filemode=false夹具问题，9933915仅修夹具后推送；真实Linux再次157项1失败，暴露Dulwich1.2.20索引子路径的ENOTDIR生产问题。[最新修复](FIX_GIT_DIRECTORY_STATUS_2026-11-03.md)已完成：21项20通过/1失败→21通过，源码159全通过（旧157保留、新2），实际包内Python48/语义22/Git与格式化84通过。两个真实CI失败日志和旧断言保留，无新跳过。
- 本批集中1173项1171通过/1既有Windows失败/1旧跳过，旧1173身份保持，Lint零错误（34/1/0警告）。完整命令退出1，仅一次实际打包；前更新包是另一批已验证产物，不能冒称一次打包涵盖两批。487份冻结输入和最终证据在verification/git-directory-status-fix-2026-11-03。
- 最新候选{apk}，{size}字节，SHA256 `{digest}`；原签名/CRC/16KiB/版本/Manifest通过。相对4715b84b…只允许git_workspace.pyc及对应元数据改变，其他包条目/25库/DEX/资源相同，103旧包保持；设备后置。Shell4/片段14按生成字节复用，终端146有效UP-TO-DATE。
- 最新生产修复完成本地验证，按本轮授权正常提交/推送；最终远端SHA/既有CI以本地本批push-results.json为准，不能把未结束或失败的CI写成通过。不发布Release/新建工作流或自动任务。先跟踪本次真实CI与必要bug，再接原C设置/主题、D更多语言；C计划草稿及历史脏文件保留，整个目标未标完成。

'''+text[end:];resume.write_text(text,encoding='utf-8')
readme=ROOT/'README.md';text=readme.read_text().replace('dist/CodePocket-updates-0.2.1-2026-11-03-debug.apk',apk).replace('118139841字节',str(size)+'字节').replace('4715b84bb9454c5136f823ea07cf2e09d34e3cfc4c21b24a35e688502e44bd91',digest)
text=text.replace('- 首次远端CI暴露两处旧测试夹具的干净环境差异，已[修复并重跑主机Python157项](docs/FIX_GITHUB_CLEAN_CI_2026-11-03.md)。生产和本机APK保持；[远端CI状态](https://github.com/juhe291/CodePocket-Android/actions)独立核对。', '- 首次CI的两处旧夹具差异已修；之后暴露的[Git目录转换POSIX状态错误](docs/FIX_GIT_DIRECTORY_STATUS_2026-11-03.md)已纳入本候选。源码Python159、实际包内Python48/语义22/Git与格式化84通过；[远端CI状态](https://github.com/juhe291/CodePocket-Android/actions)独立核对，设备验收后置。')
readme.write_text(text,encoding='utf-8')
print('Latest handoff written from the verified Git status package')
