from pathlib import Path
from PIL import Image, ImageChops
import json
phase=Path.cwd()/'verification/workbench-tab-stability-2026-10-24'
checks=[]
for prefix in ['phoneTypingAndSavingKeepTabCoordinates','tabletLightLargeLongNameKeepsCloseTargetAndFullPath']:
    a,b,c=[Image.open(phase/'previews'/f'{prefix}-{state}.png').convert('RGB') for state in ['clean','dirty','saved']]
    bbox=ImageChops.difference(a,b).getbbox()
    assert bbox is not None and bbox[2]-bbox[0]<=8 and bbox[3]-bbox[1]<=8,(prefix,bbox)
    assert ImageChops.difference(a,c).getbbox() is None,prefix
    checks.append(dict(scene=prefix,dirty_changed_bbox=bbox,clean_equals_saved=True))
(phase/'marker-pixel-checks.json').write_text(json.dumps(checks,indent=2)+'\n',encoding='utf-8')
print(json.dumps(checks))
