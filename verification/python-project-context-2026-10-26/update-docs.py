from pathlib import Path
import json
import shutil

root = Path(__file__).resolve().parents[2]
phase = Path(__file__).resolve().parent
result = json.loads((phase / 'results.json').read_text())
assert result['junit']['tests'] == 794 and not result['device_execution']

block = '''## 当前：P2-B Python项目上下文已完成代码/主机/APK验证（2026-10-26）

- [本批报告](P2_PYTHON_PROJECT_CONTEXT_2026-10-26.md)：当前正文与多个有效恢复草稿组成独立有界源码快照；内部目录/SAF真实提供方重读，不分析旧镜像、不写原文件或保存基线；传递导入/真实参数，迟到上下文拒绝、撤权回退。三版快照、提供方读取时限/有限排队及16MiB/256文件Parso缓存上限。
- 定向132通过；完整794项中793通过、0失败、1旧跳过，旧772身份/跳过集合保持，新增22项。Lint0错误，主18/Sora1警告不变；2分35秒完整构建成功。首次完整流程在Lint中断且未打包，最终流程只实际生成一次新APK。
- `dist/CodePocket-python-project-context-2026-10-26-debug.apk`，32,750,879字节，SHA256 `836042a759ba9d80ac85a0e5647da1e2e45f6bc550fa5b0c76da2f62d5fb3c5c`。Python源码66、APK原运行39/语义22、主机Shell4/片段14、原签名/16KB通过；88旧APK、10原生库和20个Chaquopy资源保持。
- [3张原生图](python-project-context-preview/README.md)已查看；Main/Sora回放真实后端并逐项校验生产快照，Android服务/JNI实际执行及设备性能/兼容性仍未验收。单个不响应取消的提供方读取可能占用一个共享线程，后续有界失败，不阻塞保存/UI；提供方不具备跨文件原子快照，结果发布前重读校验。
- 下一工作包P2-C：固定版本C++语义后端、Android可执行/头文件/编译参数兼容性核验后接同一编辑链路；原本地C++可行性、多文件运行、批量用例、格式化及P3继续保留并按计划推进。整个目标仍未完成；不推送、发布或新增自动任务，设备后置。

'''
for relative in ('docs/RESUME.md', 'docs/DEVELOPMENT_PROGRESS.md'):
    file = root / relative
    text = file.read_text(encoding='utf-8')
    marker = '## 当前：P2-A'
    assert marker in text and '## 当前：P2-B' not in text
    text = text.replace(marker, block + '## 历史：P2-A', 1)
    file.write_text(text, encoding='utf-8')

readme = root / 'README.md'
text = readme.read_text(encoding='utf-8')
head, rest = text.split('\n\n', 1)
new = '''> 当前按[P2计划](docs/P2_IMPLEMENTATION_PLAN_2026-10-26.md)推进核心能力，零碎UI润色暂停。P2-B项目上下文已完成代码/主机/APK验证；下一步C++语义，设备运行验收仍后置。整个计划尚未完成。

**2026-10-26：Python跨文件未保存上下文与SAF新鲜快照。**

- [最新Debug APK](dist/CodePocket-python-project-context-2026-10-26-debug.apk)（31.23MiB，原签名，arm64-v8a）。
- [结果与限制](docs/P2_PYTHON_PROJECT_CONTEXT_2026-10-26.md) · [3张原生图](docs/python-project-context-preview/README.md)：主793通过/1旧跳过，定向132通过，Lint0错误；Python源码66、实际APK语义22与运行专项通过。
- 多个有效恢复草稿、传递导入/真实参数及SAF当前模块参与补全；原文件/镜像/保存基线不被分析改写，变化的上下文拒绝旧结果；缓存和读取有界。
- Android分析服务/JNI实际运行、设备资源/IME/键鼠/API24/卓易通及远端CI未验收；[交接](docs/RESUME.md)。原保存、Sora、真实运行和PTY保留。

## 历史：P2-A首批接入

'''
readme.write_text(head + '\n\n' + new + rest, encoding='utf-8')

plan = root / 'docs/P2_IMPLEMENTATION_PLAN_2026-10-26.md'
text = plan.read_text(encoding='utf-8').replace('P2-A Python真实补全（现在执行）', 'P2-A Python真实补全（代码/主机/APK完成）')
text += '\nP2-B已完成代码/主机/APK验证，详见[项目上下文报告](P2_PYTHON_PROJECT_CONTEXT_2026-10-26.md)。主794项：793通过/1旧跳过，定向132通过，Lint0错误；实际APK语义22通过。当前未保存正文、其他有效恢复草稿与实时SAF源文件进入不可变有界分析快照；原保存/运行保持。下一工作包P2-C，设备验收及后续原需求仍未完成。\n'
plan.write_text(text, encoding='utf-8')

policy = root / 'docs/DEVELOPMENT_BATCH_POLICY.md'
text = policy.read_text(encoding='utf-8').replace('本计划已授权的P2-A', '本计划已授权并要求持续推进的P2各工作包')
text = text.replace('[Python语义接入批次APK](../dist/CodePocket-python-semantics-2026-10-26-debug.apk)', '[Python项目上下文批次APK](../dist/CodePocket-python-project-context-2026-10-26-debug.apk)')
policy.write_text(text, encoding='utf-8')
audit = root / 'docs/UI_SCOPE_AUDIT_2026-10-24.md'
text = audit.read_text(encoding='utf-8').replace('本批最终验证及APK见[Python真实补全接入批次](P2_PYTHON_SEMANTICS_2026-10-26.md)', '当前最终验证及APK见[Python项目上下文批次](P2_PYTHON_PROJECT_CONTEXT_2026-10-26.md)')
audit.write_text(text, encoding='utf-8')

gallery = root / 'docs/python-project-context-preview'
gallery.mkdir(exist_ok=True)
for file in (phase / 'previews').glob('*.png'):
    shutil.copy2(file, gallery / file.name)
(gallery / 'README.md').write_text('''# Python项目上下文原生预览

2026-10-26，三图均已查看。真实Main/Sora与原生Views，用主机Jedi实际输出替代Android IPC；测试逐项校验生产快照正文相同。不是Android设备服务执行证据；布局沿用既有组件，本批不继续零碎UI重做。

![平板多个未保存模块的真实成员](tablet-unsaved-project.png)

![手机跨文件真实参数，第二参数高亮](phone-project-signature.png)

![平板SAF实时读取模块的真实成员](tablet-saf-live-module.png)

候选、真实签名、活动参数和关闭入口可见。设备IME/键鼠/无障碍/性能继续后置；完整结果见[报告](../P2_PYTHON_PROJECT_CONTEXT_2026-10-26.md)。
''', encoding='utf-8')
print('Updated current handoff, plan, README, policy, scope audit and three-image gallery')
