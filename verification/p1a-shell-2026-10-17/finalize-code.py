from pathlib import Path
import shutil
repo = Path(r'C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android')
stage = Path(__file__).parent
evidence = repo / 'verification/p1a-shell-2026-10-17'
shutil.copy2(evidence / 'build.log', evidence / 'build-second.log')
for name in ('WorkspaceLayoutPolicy.kt', 'ShellUiTest.kt'):
    folder = 'app/src/test/java/com/codepocket/ide' if name.endswith('Test.kt') else 'app/src/main/java/com/codepocket/ide/ui'
    (repo / folder / name).write_bytes((stage / name).read_bytes())
p = repo / 'app/src/main/java/com/codepocket/ide/MainActivity.kt'
s = p.read_text(encoding='utf-8')
assert s.count('    private var saving = false\n') == 1
s = s.replace('    private var saving = false\n', '    private var saving = false\n        set(value) { field = value; updateMainToolbarState() }\n')
p.write_text(s, encoding='utf-8', newline='\n')
print('Kept collapsed-panel output tabs reachable; synchronized toolbar saving state')
