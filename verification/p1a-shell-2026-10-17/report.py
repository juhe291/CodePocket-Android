from pathlib import Path
import collections
import hashlib
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

repo = Path(r'C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android')
evidence = repo / 'verification/p1a-shell-2026-10-17'
def read(path): return (repo / path).read_text(encoding='utf-8-sig')
def base(path): return subprocess.check_output(['git', 'show', f'18bf61f:{path}'], cwd=repo).decode('utf-8-sig')
def write(path, text): (repo / path).write_text(text, encoding='utf-8', newline='\n')
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
def dump(path, data): write(path, json.dumps(data, ensure_ascii=False, indent=2) + '\n')
apk = json.loads(read('verification/p1a-shell-2026-10-17/apk-inspection.json'))
delivered = repo / 'dist/CodePocket-P1A-shell-debug.apk'
assert sha(delivered) == apk['sha256']
tests = dict(tests=0, failures=0, errors=0, skipped=0)
for file in (evidence / 'junit').glob('TEST-*.xml'):
    root = ET.parse(file).getroot()
    for key in tests: tests[key] += int(root.get(key, '0'))
tests['passed'] = tests['tests'] - tests['skipped'] - tests['failures'] - tests['errors']
shell = ET.parse(evidence / 'junit/TEST-com.codepocket.ide.ShellUiTest.xml').getroot()
assert int(shell.get('tests')) == 15 and int(shell.get('failures')) == 0
assert tests == dict(tests=114, failures=0, errors=0, skipped=1, passed=113), tests
lint = ET.parse(evidence / 'lint-results-debug.xml').getroot()
issues = list(lint)
severity = collections.Counter(i.get('severity') for i in issues)
warnings = collections.Counter(i.get('id') for i in issues if i.get('severity') == 'Warning')
assert not severity['Error'] and not severity['Fatal']
baseline = json.loads(read('verification/ui-audit-2026-10-17/protected-before.json'))
changed = {name: {'before': old, 'after': sha(repo / name)} for name, old in baseline.items() if sha(repo / name) != old}
assert set(changed) == {'app/src/main/java/com/codepocket/ide/MainActivity.kt', 'app/src/main/res/values/strings.xml'}, changed
for path in ('docs/DEVELOPMENT_REQUIREMENTS.md', 'docs/DEVELOPMENT_REQUIREMENTS_UI_2026-10-17.md', 'docs/P1_P2_ANIMATION_COMPLETION_PLAN.md'):
    old = subprocess.check_output(['git', 'show', f'18bf61f:{path}'], cwd=repo)
    current = (repo / path).read_bytes()
    assert old.replace(b'\r\n', b'\n') == current.replace(b'\r\n', b'\n'), path
with zipfile.ZipFile(delivered) as archive:
    assert archive.testzip() is None
    dex = b''.join(archive.read(n) for n in archive.namelist() if re.fullmatch(r'classes\d*\.dex', n))
    classes = ['MainToolbar', 'PanelToolbar', 'IdeCommandMenu', 'IdeCommands', 'IdeControls', 'IdeIconDrawable', 'DesignTokens', 'WorkspaceLayoutPolicy']
    for name in classes: assert f'Lcom/codepocket/ide/ui/{name};'.encode() in dex, name
signature = read('verification/p1a-shell-2026-10-17/apk-signature.txt')
assert 'Verified using v2 scheme (APK Signature Scheme v2): true' in signature
log = read('verification/p1a-shell-2026-10-17/build.log')
duration = re.search(r'BUILD SUCCESSFUL in ([^\r\n]+)', log).group(1)
assert '62 actionable tasks: 62 executed' in log
source_hashes = {str(p.relative_to(repo)).replace('\\', '/'): sha(p) for p in sorted((repo / 'app/src').rglob('*')) if p.is_file()}
dump('verification/p1a-shell-2026-10-17/source-sha256.json', source_hashes)
result = dict(scope='P1-A compact native shell', baseline='18bf61f', build_duration=duration,
    force_rerun=True, executed_gradle_tasks=62, kotlin=tests, new_shell_tests=15,
    lint=dict(errors=severity['Error'] + severity['Fatal'], warnings=severity['Warning'], warning_ids=warnings),
    python_source_tests_passed=13, apk_bytecode_tests_passed=13, generated_template_python_passed=7,
    generated_template_cpp17_passed=7, apk=apk, delivered_apk='dist/CodePocket-P1A-shell-debug.apk',
    dex_shell_classes=classes, protected_inputs_unchanged=len(baseline)-len(changed), changed_protected_inputs=changed,
    requirements_preserved=True, device_tested=False, emulator_tested=False, remote_actions_run=False,
    online_wandbox_run=False, visual_or_fps_verified=False)
dump('verification/p1a-shell-2026-10-17/results.json', result)
for p in Path(__file__).parent.glob('*.py'): shutil.copy2(p, evidence / p.name)

warning_text = '、'.join(f'{key} ×{value}' for key, value in sorted(warnings.items()))
report = f'''# P1-A 手机竖屏外壳与主机验证

2026-10-17，基于审查提交 `18bf61f`。用户在审查后回复“继续”，本批落实已评审的 P1-A；现有工程与编辑器保留。原需求、动画/智能补全增补和最新完整需求共同生效。按用户要求，所有实机验证统一后置。

## 本批行为

- 48dp 单排主栏：Explorer、弹性单行文件名、运行/停止、更多。长名省略，长按标题或标签可查看完整路径；大字体不挤压图标按钮。尺寸策略使用工具栏实际测量宽度，极窄窗口把 Explorer/运行入口收进菜单。
- 更多菜单按文件、编辑、显示、设置与帮助分组。原第二排 21 项命令全部保留，入口按命令身份分发；菜单关闭后再调原功能。运行中仍可停止；忙碌/未载入/保存/组合输入和 stdin 焦点在展示与执行时检查。
- 移除第二排功能堆积、重复路径行和 37dp 常驻说明；片段提示只在有活动占位符、无 IME 且高度足够时出现。IME 代码焦点下按设置显示编程栏，包含 Tab、反缩进、括号、符号、左右键、补全/参数/片段；不对 stdin 插入源码。
- 主栏、运行面板标题、标签关闭及文件树使用原创 20dp 线性图标；新触控目标常规宽度下 48dp。文件树标题收纳命令，长按/右键文件操作仍走原实现；输出标签和旧对话框尚未全量迁移视觉组件。
- 统一 `DesignTokens` 外壳配色。深色控件强调色保留 `#0078D4`，文字强调色改为 `#4DAAFC`：前者在 `#1F1F1F` 背景仅约 3.64:1，后者约 6.65:1。两主题主文字/辅助/强调/成功/错误文字与三种外壳背景的对比度测试均 ≥4.5:1；这不是代码语法颜色或屏幕视觉验收。
- 预设输入默认收起，点击面板输入图标或“显示→编辑当前文件的预设输入”展开。保留同一个 EditText、2 万字符限制、每文件内容与选区；收起只改变可见性和焦点。展开偏好按工作区记忆，恢复到 stdin 焦点且面板打开时重新展开；代码焦点不强制展开。收起后显式恢复可见编辑器焦点并持久化，布局变化按原视口契约恢复；不主动弹 IME。
- 面板菜单提供尺寸、清空当前 stdin、展开/收起输入、最大化/还原、关闭，窄窗隐藏的标题按钮仍可访问。无输入时短面板保留输出标签；极短且正在编辑 stdin 时可最大化面板。输出仍是预设输入、结束后展示结果。
- 撤销/重做菜单调用系统文本历史；未引入自定义历史栈。Ctrl+O 仍明确是“导入副本”，文件保存和 SAF 语义保持原路径。

## 实际构建与测试

最终强制重跑 `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --rerun-tasks --offline`：**BUILD SUCCESSFUL in {duration}**，62 任务执行。命令、便携环境、所有日志与报告在 `verification/p1a-shell-2026-10-17/`。

| 检查 | 真实结果与范围 |
| --- | --- |
| Kotlin | 114 项，113 通过、1 跳过，0 失败/错误；新增外壳 15 项全部通过；跳过为原 Windows 符号链接环境用例 |
| 新模型回归 | 0–1000dp 主栏/面板按钮宽度预算、命令无丢失且无重复、busy/loaded/saving/run/IME 组合及代码/stdin 焦点门控、折叠/恢复策略、按需片段说明和静态对比度；没有模拟设备点击 |
| Python 源码 | 13/13；真实执行 stdin/stdout、标准库、模块刷新、traceback、输出限量与超时后重跑 |
| APK 内 runner.pyc | 从本次真实 APK 提取，在主机 Python 3.13 上执行 13/13；不是 Android 进程验证 |
| 真实生成模板 | Python 7/7、主机 g++ C++17 编译运行 7/7；不代表已实现 App 本地 C++ |
| Android Lint | 0 错误、{severity['Warning']} 警告：{warning_text}；原平台废弃 API 编译提示保留 |
| APK | ZIP CRC、6 个 Python 子归档、arm64-v8a、API24/target34、v2 签名、16KB 对齐通过；8 个新 UI 类标识已在 Dex 中找到 |
| 保护输入 | 原 {len(baseline)} 项中 {len(baseline)-len(changed)} 项字节哈希未改；改变 MainActivity 和 strings；CodeEditor、文件/SAF/恢复、运行器、补全/片段/签名、Gradle/Manifest/Actions 及旧测试保留原哈希；三份需求原文保留 |
| GitHub Actions | 现有步骤自动包含新 Kotlin 测试，Debug APK 下载步骤保留；本批未运行远端 Actions 或 push |

首轮实际构建失败，原因为新菜单按钮初始化回调引用自身（`Variable menuAnchor must be initialized`）；改为控件创建后绑定回调，后续完整构建通过。过程中补了折叠输入后的持久焦点、短面板输出入口、保存状态同步与文字对比度。清理 6 项失效的旧文字/符号资源、复用面板标签；两处 ViewConstructor 提示仅针对 XML 构造器，这两个控件必须由代码传入真实回调，已在类上注明原因并精确抑制该提示，没有全局禁用检查。失败日志 `build-first-failed.log` 和中间日志均保留，不把初次失败写成通过。

产物：[CodePocket-P1A-shell-debug.apk](../dist/CodePocket-P1A-shell-debug.apk)，{apk['size_bytes']:,} 字节（{apk['size_bytes']/1048576:.2f} MiB），Debug / Android 7.0+ / arm64-v8a。SHA256：`{apk['sha256']}`。

## 修改文件

| 文件 | 职责 |
| --- | --- |
| `ui/DesignTokens.kt`、`ui/IdeIcons.kt`、`ui/IdeControls.kt` | 外壳配色/尺寸、原创图标、原生触控反馈与无障碍名称 |
| `ui/MainToolbar.kt` | 原生主栏、面板标题及实测宽度降级 |
| `ui/IdeCommands.kt`、`ui/IdeCommandMenu.kt` | 命令清单、状态门控与原生分组菜单 |
| `ui/WorkspaceLayoutPolicy.kt` | 可单测的宽度/按需提示/输入展开策略 |
| `MainActivity.kt`、`res/values/strings.xml` | 接入入口、保留原功能回调、预设输入焦点/持久化及使用说明 |
| `ShellUiTest.kt` | 新增 15 项回归 |
| README、本报告、开发进度、RESUME、UI 实施计划 | 当前结果、使用路径、已知边界及接续点 |

## 未验证与下一步

本批没有 ADB、安装、设备截图、模拟器运行或新 Wandbox 请求。SDK 缺少 emulator/system-images；按既定安排不接设备。主机模型和构建不能证明手机布局、极大字体、中文 IME、实体键鼠、菜单回焦、原生撤销、光标/弹窗位置、stdin 选区/滚动恢复、SAF 提供方、旋转/分屏、鸿蒙卓易通、60 FPS/高刷新率均已通过。代码运行仍使用真实后端，没有添加模拟结果。

P1-A 代码与主机构建完成，设备联合验收待做。P1 整体未完成；下一批 P1-B 处理横屏/平板净可用尺寸、Activity Bar、Explorer 宽度、底部优先面板与简洁状态栏。审查中目录元数据主线程、缓存回收和提供方失败窗口等 P0 项继续按影响穿插修复；Sora 隔离原型、完整动画/光标、项目搜索、流式终端，以及 P2/P3 仍未完成。
'''
write('docs/P1A_SHELL.md', report)

p = 'README.md'; text = base(p)
start = text.index('**2026-10-17 新需求审查：')
end = text.index('\n\n', start)
text = text[:start] + '**2026-10-17：P1-A 手机竖屏外壳已实现并完成主机构建。** 单排主栏、分组命令菜单、统一图标/令牌、按需编程栏与可折叠预设输入已接入，原编辑器/文件保存/运行实现保留。见 [本批结果](docs/P1A_SHELL.md)、[分批 UI 计划](docs/UI_REDESIGN_PLAN_2026-10-17.md) 和 [完整需求](docs/DEVELOPMENT_REQUIREMENTS_UI_2026-10-17.md)。' + text[end:]
start = text.index('**2026-10-17：P1 第五批'); end = text.index('\n\n', start)
text = text[:start] + f'**最新 [Debug APK](dist/CodePocket-P1A-shell-debug.apk)** {apk["size_bytes"]/1048576:.2f} MiB，Android 7.0+ / arm64-v8a。Kotlin 114 项中 113 通过、1 跳过；Python 源码/真实 APK 字节码各 13/13，生成模板 Python/C++17 各 7/7；Lint 0 错误、{severity["Warning"]} 警告，签名/CRC/ABI/对齐通过。设备验收按要求统一后置，当前 UI 与键鼠/IME/帧率尚未实机验证。' + text[end:]
text = text.replace('2. 工具栏“工作区”', '2. 更多→文件→“选择 / 切换工作区”')
start = text.index('4. 每个文件分别保存 stdin'); end = text.index('\n5.', start)
text = text[:start] + '4. 每个文件分别保存 stdin，默认收纳；面板输入图标或“更多→显示→编辑当前文件的预设输入”展开编辑，面板菜单可清空/收起。更多→显示可以收键盘、切换面板；面板图标/菜单可最大化、还原、关闭，拖动分隔条或菜单调尺寸。代码焦点和 IME 下按设置显示编程栏，stdin 焦点不会对源码插入符号。' + text[end:]
text = text.replace('或点“片段”选择', '或从“更多→编辑→内置代码片段”选择')
text = text.replace('或“参数”按钮主动请求', '或“更多→编辑→内置参数参考”主动请求')
text = text.replace('- 顶栏设置：', '- 更多→设置与帮助→设置：')
text = text.replace('| MainActivity / CodeEditor |', '| ui/MainToolbar / IdeCommandMenu / IdeCommands / WorkspaceLayoutPolicy | 实测宽度的单排主栏、原生命令入口与焦点/状态策略 |\n| ui/DesignTokens / IdeIcons / IdeControls | 外壳配色/尺寸、原创线性图标和原生触控组件 |\n| MainActivity / CodeEditor |')
text = text.replace('当前依据 2026-10-17 新请求，审查完成后先等待确认 P1-A 手机竖屏 UI 外壳；确认后继续按', '2026-10-17 审查后用户已确认继续；P1-A 手机竖屏外壳已完成代码与主机验证，下一批 P1-B 横屏/平板/状态。继续按')
write(p, text)

p = 'docs/DEVELOPMENT_PROGRESS.md'; text = base(p)
start = text.index('**当前执行点：'); end = text.index('\n\n', start)
text = text[:start] + f'**当前执行点：P1-A 手机竖屏外壳已实现、主机构建通过（2026-10-17）。** 见 [本批报告](P1A_SHELL.md)。用户在审查后回复“继续”，已接入主栏/菜单/原创图标/令牌与折叠输入；最终强制构建 {duration} / 62 任务，Kotlin 113 通过、1 跳过，新增 15 项通过，Python 源码/字节码各13/13，生成模板各7/7，Lint0错误/{severity["Warning"]}警告。下一批 P1-B；实机仍统一后置。' + text[end:]
text = text.replace('| P1 工作区/终端余项 |', '| P1-A 手机外壳 | 单排主栏、完整命令菜单、原创图标/外壳令牌、按需说明/编程栏、折叠预设输入；15 项新模型回归及 Debug APK 检查通过 | 见 P1A_SHELL.md；UI/焦点/原生历史仍待设备联合验收 |\n| P1 工作区/终端余项 |')
start = text.index('下一批改为用户确认后的 P1-A')
text = text[:start] + '下一批 P1-B 横屏/平板净可用尺寸策略、Activity Bar、Explorer 调宽、底部优先面板及简洁状态栏；再推进编辑器隔离原型、高亮、项目搜索、完整动画及真实程序终端。P1/P2/P3 余项继续保留，发现 P0 缺陷优先回修。保持分批提交，不整体重写；未授权 push 或发布。\n'
write(p, text)

p = 'docs/RESUME.md'; text = base(p)
start = text.index('## 当前：'); end = text.index('## P1 第五批', start)
current = f'''## 当前：P1-A 外壳主机验证完成（2026-10-17）

用户在新完整需求审查后回复“继续”，已确认并执行 P1-A；不要再停在待确认状态，也不要重做阶段 0。全部实机测试按用户要求统一后置，不请求设备。

- 基线 `18bf61f`；本批 [P1A_SHELL.md](P1A_SHELL.md)：单排主栏、命令菜单、原创图标/令牌、收纳常驻说明/预设输入，保留原编辑器与运行/文件实现。命令在展示和执行时检查焦点/组合区/忙碌状态；Ctrl+O 仍导入副本。
- 最终强制构建 {duration} / 62 任务；Kotlin114中113通过/1跳过（15新增通过），Python源码/实际APK字节码各13/13，真实生成模板各7/7，Lint0错误/{severity['Warning']}警告；签名/CRC/ABI/16KB对齐/8个UI类打包检查通过。
- [最新 APK](../dist/CodePocket-P1A-shell-debug.apk)，{apk['size_bytes']:,} 字节，SHA256 `{apk['sha256']}`；证据 `verification/p1a-shell-2026-10-17/results.json`。失败首轮与中间日志保留。
- 原61项保护输入只改MainActivity/strings；CodeEditor、文件/SAF/草稿、runner、旧测试、Gradle/Manifest/Actions及三份需求原文保留。没有新依赖、设置schema或存储迁移。
- 本批无设备/模拟器/截图/帧率/卓易通/新Wandbox请求；当前UI、原生撤销、菜单回焦、IME/键鼠与stdin/SAF联合行为尚未实机验证。不能把模型预算检查写成设备适配通过。
- 下一批 P1-B：净可用尺寸/横屏平板、Activity Bar、可调Explorer、底部优先运行面板与简洁状态；P0问题优先穿插。P1整体、Sora、完整动画、项目搜索、真实流式终端、P2/P3仍未完成；无push/发布。
- 实际仓库仍为 `C:\\Users\\q1684\\Desktop\\安卓端编译器\\CodePocket-Android`；配置cwd的OneDrive路径不存在，所有命令指定真实workdir。该路径写入/构建需工具沙箱升级。保留verification、dist和.tools，勿清理旧设备备份。

## 2026-10-17 审查记录（历史）

新需求按要求先审查，未修改业务代码；客观结论见 [审查](UI_AUDIT_2026-10-17.md)，原业务基线 `b935079`、[审查 APK](../dist/CodePocket-UI-audit-baseline-debug.apk) 保留。原99项Kotlin中98通过/1跳过，源码与字节码各13项、模板各7项通过。在线Wandbox仅第一例成功，第二例TLS EOF、第三例未执行；不是全通过。当前已获“继续”确认并完成P1-A主机阶段。

'''
text = text[:start] + current + text[end:]; write(p, text)

p = 'docs/UI_REDESIGN_PLAN_2026-10-17.md'; text = base(p)
text = text.replace('# CodePocket UI 重设计实施计划（待确认）', '# CodePocket UI 重设计实施计划（P1-A 主机阶段已完成）')
text = text.replace('此文件是可评审的下一步计划，**没有把线框或静态界面记为已实现**。', '审查后用户已回复“继续”；P1-A 已完成代码和主机验证，详见 [结果](P1A_SHELL.md)。下文线框是设计参考，不是设备截图或实机验收证据。')
text = text.replace('P1-A（下一批，待确认）', 'P1-A（代码/主机完成）')
text = text.replace('当前确认范围只建议 P1-A，其他批次不是本轮已实施或默认一次性授权的重构。', '本批确认并交付 P1-A，下一批按顺序推进 P1-B；其他批次仍待分批实现，未列为已完成。')
text = text.replace('## 预计修改文件（当前尚未实施）', '## P1-A 文件分工（实际结果见本批报告）')
text = text.replace('下一批先完成手机竖屏，不一次性重排所有窗口分支。', 'P1-A 已完成手机外壳；P1-B 再逐步重排横屏/平板分支。')
text = text.replace('## 统一视觉令牌建议', '## 统一视觉令牌与后续验证')
text = text.replace('以下是 Modern 风格初始方案，实施时统一存放并实际验证对比度、字体缩放和触控；不是已完成的主题移植。', '以下初始方案已落实到外壳令牌；深色强调文字实际采用 #4DAAFC，控件强调色保留 #0078D4，以满足普通文字 ≥4.5:1 的静态检查。字体缩放、触控与完整主题仍待实机验证，不能称完整主题移植。')
write(p, text)
print(json.dumps({key: result[key] for key in ('build_duration', 'kotlin', 'lint', 'protected_inputs_unchanged', 'apk')}, ensure_ascii=False, indent=2))
