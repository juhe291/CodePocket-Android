from pathlib import Path
import json,re,xml.etree.ElementTree as ET
phase=Path('verification/workbench-diagnostic-ui-2026-10-24')
text=ET.parse(phase/'probe-junit/TEST-com.codepocket.ide.MainSurfaceIntegrationTest.xml').getroot().findtext('system-out','')
rows=[];current=None
for line in text.splitlines():
    if line.startswith('DIAGNOSTIC_BEFORE '):
        current={'before':re.sub(r'internal:[^/]+/','internal:<test-document>/',line)};rows.append(current)
    elif line.startswith('DIAGNOSTIC_CLICK ') and current is not None:current['after_click']=line
    elif line.startswith('DIAGNOSTIC_IDLE ') and current is not None:current['after_idle']=re.sub(r'internal:[^/]+/','internal:<test-document>/',line)
    elif 'MainActivity.buildEditor' in line and current is not None:current['dismiss_caller']=line.strip()
assert len(rows)==2 and any('dismiss_caller' in r for r in rows)
(phase/'diagnostic-probe-observation.json').write_text(json.dumps({'prior_failures':2,'observations':rows},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
