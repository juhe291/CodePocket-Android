"""Read a pinned Git engine's official metadata; no dependency installation."""
import json
from pathlib import Path
import urllib.request

PHASE = Path(__file__).resolve().parent
VERSION = '1.2.20'
url = f'https://pypi.org/pypi/dulwich/{VERSION}/json'
with urllib.request.urlopen(url, timeout=30) as response:
    data = response.read(4_000_001)
assert len(data) <= 4_000_000
metadata = json.loads(data)
assert metadata['info']['name'] == 'dulwich' and metadata['info']['version'] == VERSION
target = PHASE / f'dulwich-{VERSION}-metadata.json'
target.write_bytes(data)
wheels = [item for item in metadata['urls'] if item['filename'].endswith('-py3-none-any.whl') and not item['yanked']]
print(json.dumps(dict(version=VERSION, requires_python=metadata['info']['requires_python'],
                     requires_dist=metadata['info']['requires_dist'],
                     license_expression=metadata['info'].get('license_expression'),
                     pure_wheels=[{k:item[k] for k in ('filename','size','digests','url')} for item in wheels]), indent=2))
