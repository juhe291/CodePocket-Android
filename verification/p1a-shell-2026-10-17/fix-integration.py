from pathlib import Path
import shutil
repo = Path(r'C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android')
stage = Path(__file__).parent
evidence = repo / 'verification/p1a-shell-2026-10-17'
shutil.copy2(evidence / 'build.log', evidence / 'build-first-failed.log')
(repo / 'app/src/main/java/com/codepocket/ide/ui/MainToolbar.kt').write_bytes((stage / 'MainToolbar.kt').read_bytes())
p = repo / 'app/src/main/java/com/codepocket/ide/MainActivity.kt'
s = p.read_text(encoding='utf-8')
a = '        captureSession(); hideCompletion()\n        presetInputExpanded = expanded'
assert s.count(a) == 1
s = s.replace(a, '        val stamp = capturePanelViewport(); hideCompletion()\n        presetInputExpanded = expanded')
a = '        updateImeLayout(); persistNavigation()\n    }\n\n    private fun applyPanelLayout()'
assert s.count(a) == 1
s = s.replace(a, '        updateImeLayout(); captureSession(); persistNavigation(); restorePanelViewport(stamp)\n    }\n\n    private fun applyPanelLayout()')
s = s.replace('IdeCommand.PANEL_SIZE -> showPanelSizeMenu(panelToolbar.menuAnchor)', 'IdeCommand.PANEL_SIZE -> showPanelSizeMenu(if (panelToolbar.isShown) panelToolbar.menuAnchor else mainToolbar.menuAnchor)')
# A first dirty transition updates the main title without rebuilding the tab tree per keystroke.
a = '                dirty = true; editRevision++\n                snippetSession'
assert s.count(a) == 1
s = s.replace(a, '                if (!dirty) { dirty = true; updateShellTitle() }; editRevision++\n                snippetSession')
p.write_text(s, encoding='utf-8', newline='\n')
p = repo / 'app/src/main/res/values/strings.xml'
s = p.read_text(encoding='utf-8')
start = s.index('    <string name="shell_help">'); end = s.index('</string>', start)
s = s[:start] + s[start:end].replace('\n\n', r'\n\n') + s[end:]
p.write_text(s, encoding='utf-8', newline='\n')
print('Fixed toolbar initialization and collapsed-input focus persistence')
