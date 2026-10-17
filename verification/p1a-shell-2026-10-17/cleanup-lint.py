from pathlib import Path
import re
import shutil
repo = Path(r'C:\Users\q1684\Desktop\安卓端编译器\CodePocket-Android')
evidence = repo / 'verification/p1a-shell-2026-10-17'
shutil.copy2(evidence / 'build.log', evidence / 'build-before-lint-cleanup.log')
shutil.copy2(evidence / 'lint-results-debug.xml', evidence / 'lint-before-cleanup.xml')
p = repo / 'app/src/main/java/com/codepocket/ide/ui/MainToolbar.kt'
s = p.read_text(encoding='utf-8')
s = s.replace('import android.content.Context', 'import android.annotation.SuppressLint\nimport com.codepocket.ide.R\nimport android.content.Context')
s = s.replace('class MainToolbar(', '@SuppressLint("ViewConstructor") // Programmatic shell: requires live command callbacks, never inflated from XML.\nclass MainToolbar(')
s = s.replace('class PanelToolbar(', '@SuppressLint("ViewConstructor") // Programmatic shell; XML construction would have no command callbacks.\nclass PanelToolbar(')
s = s.replace('text = "运行输出"', 'text = context.getString(R.string.panel_title)')
s = s.replace('IdeIcon.MAXIMIZE, "最大化运行面板", onMaximize', 'IdeIcon.MAXIMIZE, context.getString(R.string.panel_maximize), onMaximize')
s = s.replace('IdeIcon.CLOSE, "收起运行面板", onClose', 'IdeIcon.CLOSE, context.getString(R.string.panel_close), onClose')
s = s.replace('IdeIcon.MORE, "运行面板操作")', 'IdeIcon.MORE, context.getString(R.string.panel_size_menu))')
s = s.replace('if (isMaximized) "还原运行面板" else "最大化运行面板"', 'context.getString(if (isMaximized) R.string.panel_restore else R.string.panel_maximize)')
p.write_text(s, encoding='utf-8', newline='\n')
p = repo / 'app/src/main/res/values/strings.xml'
s = p.read_text(encoding='utf-8')
removed = ('completion_hint', 'panel_size_symbol', 'panel_width_symbol', 'panel_maximize_symbol', 'panel_restore_symbol', 'panel_close_symbol')
for name in removed:
    s, count = re.subn(r'    <string name="' + name + r'">[^\n]*</string>\n', '', s)
    assert count == 1, name
s = s.replace('<string name="panel_title">运行面板</string>', '<string name="panel_title">运行输出</string>')
p.write_text(s, encoding='utf-8', newline='\n')
print('Reused panel labels; removed six obsolete text/symbol resources; documented programmatic constructors')
