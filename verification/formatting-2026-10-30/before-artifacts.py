"""Checkpoint existing deliverables before the single batch build; never modifies them."""
import hashlib,json
from pathlib import Path
root=Path(__file__).resolve().parents[2]
output=Path(__file__).with_name('before-apks.json')
assert not output.exists(), 'Preserve the first checkpoint'
rows=[]
for file in sorted((root/'dist').glob('*.apk')):
    with file.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    rows.append(dict(name=file.name,bytes=file.stat().st_size,sha256=digest))
output.write_text(json.dumps(rows,indent=2)+'\n')
print(f'{len(rows)} existing APKs checkpointed')
