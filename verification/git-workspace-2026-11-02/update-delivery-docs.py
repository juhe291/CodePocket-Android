"""Update the current handoff only after the final evidence collector succeeds."""
import json
from pathlib import Path
import shutil

ROOT=Path(__file__).resolve().parents[2];PHASE=Path(__file__).resolve().parent
result=json.loads((PHASE/'results.json').read_text())
assert result['junit']==dict(tests=1033,passed=1031,failures=1,errors=0,skipped=1)
assert result['sha256']=='26ac0bec36f7a0a89108727452d1f96a8576086e5f9ee19d889987a568961075'
path=ROOT/'docs/RESUME.md';text=path.read_text(encoding='utf-8')
start=text.index('## 正在进行：P3-A 本地Git，前置可行、尚未生产接入（2026-11-02）')
end=text.index('## 最近完成：P2 代码格式化候选',start)
section='''## 最近完成：P3-A 本地 Git 候选（2026-11-02）

- 从f1c4328按[P3四组计划](P3_IMPLEMENTATION_PLAN_2026-11-02.md)接入固定Dulwich1.2.20/urllib3 2.8.0、独立非导出:git服务、内部/真实SAF二进制快照/元数据事务、与树/搜索共享的原生源代码管理侧栏，以及真实状态/差异/暂存/取消暂存/身份提交/完整历史。非仓库可初始化，无项目给打开入口；不要求设备预装Git，没有GitHub假入口。
- [本批交付](P3_LOCAL_GIT_DELIVERY_2026-11-02.md)：最终1033项1031通过/1既有Windows头文件失败/1旧跳过，原995身份/跳过集合保持，新38全通过。定向52/85通过，Python全源码114、实际APK原Python48/语义22/Git19/格式化20通过。Lint零错误，App27/Sora1/Workspace0警告；新增3条展示提示和测试平台空值提示登记。
- 首轮集中1032项中1029通过/2失败/1跳过，暴露Sora字体缩放异步布局未完成时撤销越界，已在正式Java适配器加入布局忙碌保护。旧撤销断言保留并等待真实布局，新增用例核对忙碌时不消耗undo/redo、恢复后正常使用。首次新测试类型引用编译失败和PowerShell原生stderr处理失败均保留，未引用旧JUnit冒充本轮执行。
- 两次集中流程都实际打包且真实退出1；首轮中间包未交付，最终4分58秒。终端146有效UP-TO-DATE、editor-assist NO-SOURCE；不能称全量全绿。原签名/CRC/16KiB/Manifest/DEX/C++闭包及269项固定Git依赖增量检查通过；25原生库、45运行资产及99旧APK保持，Shell4/片段14按未变摘要复用格式化批次。
- [最新候选APK](../dist/CodePocket-local-git-2026-11-02-debug.apk)，123878915字节，SHA256 `26ac0bec36f7a0a89108727452d1f96a8576086e5f9ee19d889987a568961075`。5张[原生图](../verification/git-workspace-2026-11-02/previews/README.md)已逐张查看。工作树原有历史日志/重绘/生成缓存保留，不混入本批提交；本批提交见git log。
- 当前未保存内容先走原保存/版本保护；其他恢复草稿需明确选择。内部发布持有标准Git锁，重启只清理日志证明属于自身的锁。SAF无跨文件原子性，未知部分写入保留备份、恢复记录和导出，不静默覆盖。禁止外部钩子/全局配置/GC/签名程序，对不支持的过滤器/LFS/工作树布局明确失败。
- 当前证据在verification/git-workspace-2026-11-02/results.json、final-junit和final-source-hashes.json；前置及各轮失败记录保留。不是Android服务实际运行或设备验收；设备C++signal6、Git/IME/键鼠/API24/性能继续后置，不要求设备，不推送、发布、触发远端CI或创建自动任务。
- 下一工作包P3-B：用户显式HTTPS克隆/fetch、安全fast-forward/push和受保护凭据；P3-C设置多关键词/别名搜索及可保存的主题扩展、P3-D更多语言真实分析全部保留。整个目标继续，不能因P3-A候选完成而将目标标为完成。

'''
path.write_text(text[:start]+section+text[end:],encoding='utf-8')
old=PHASE/'checkpoint.json';archive=PHASE/'checkpoint-store-saf-bridge.json'
assert not archive.exists();shutil.copy2(old,archive)
checkpoint=dict(stage='P3-A local Git validated on host and actual APK',baseline_head='f1c4328',
    junit=result['junit'],targeted=result['layout_targeted'],production_service_integrated=True,
    source_control_sidebar_integrated=True,new_apk_built=True,source_python_tests=114,git_backend_tests=19,
    apk=result['apk'],sha256=result['sha256'],android_execution=False,full_goal_complete=False,
    remaining=result['remaining_plan'],evidence='results.json and final-source-hashes.json')
old.write_text(json.dumps(checkpoint,indent=2)+'\n',encoding='utf-8')
print('Current handoff and checkpoint updated from verified results')
