from pathlib import Path
import shutil
repo = Path(r'C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android')
stage = Path(__file__).parent
evidence = repo / 'verification/p1a-shell-2026-10-17'
shutil.copy2(evidence / 'build.log', evidence / 'build-third.log')
for name in ('DesignTokens.kt', 'ShellUiTest.kt'):
    folder = 'app/src/test/java/com/codepocket/ide' if name.endswith('Test.kt') else 'app/src/main/java/com/codepocket/ide/ui'
    (repo / folder / name).write_bytes((stage / name).read_bytes())
p = repo / 'app/src/main/java/com/codepocket/ide/MainActivity.kt'
s = p.read_text(encoding='utf-8')
assert 'private val accent get() = palette.accent\n' in s
s = s.replace('private val accent get() = palette.accent\n', 'private val accent get() = palette.accentText\n')
old = '        updateImeLayout(); captureSession(); persistNavigation(); restorePanelViewport(stamp)'
assert s.count(old) == 1
s = s.replace(old, '''        updateImeLayout()
        // Collapsing an IME-only full panel may reveal the editor only after resolving layout.
        if (!expanded && editorRegion.visibility == View.VISIBLE) editor.requestFocus()
        captureSession(); persistNavigation(); restorePanelViewport(stamp)''')
p.write_text(s, encoding='utf-8', newline='\n')
print('Separated readable accent text from control accent; made post-collapse editor focus explicit')
