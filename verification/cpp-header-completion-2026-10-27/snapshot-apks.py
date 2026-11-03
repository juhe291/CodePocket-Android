import hashlib
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
output=Path(__file__).resolve().parent/'before-apks.json'
assert not output.exists()
rows=[]
for file in sorted((ROOT/'dist').glob('*.apk')):
    with file.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    rows.append(dict(name=file.name,bytes=file.stat().st_size,sha256=digest))
assert len(rows)==91,len(rows)
output.write_text(json.dumps(rows,indent=2)+'\n');print(f'Preserved {len(rows)} historical APK identities.')
