"""Inspect the frozen signed-index LLVM package; never execute Android binaries."""
import hashlib
import io
import json
from pathlib import Path
import sys
import tarfile
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
from cpp_runtime_elf import ar_members, elf, patch_dynamic

evidence=Path(__file__).resolve().parent
meta=json.loads((evidence/'symbolizer-package-candidate.json').read_text())
package=ROOT/'.tools/cpp-runtime/downloads/llvm_21.1.8-3_aarch64.deb'
partial=package.with_name(package.name+'.partial')
if not package.exists():
    assert partial.stat().st_size==int(meta['Size'])
    assert hashlib.sha256(partial.read_bytes()).hexdigest()==meta['SHA256']
    partial.replace(package)
assert hashlib.sha256(package.read_bytes()).hexdigest()==meta['SHA256']
target=ROOT/'.tools/cpp-local-probe/llvm-symbolizer'
found=False
for name,data in ar_members(package):
    if not name.startswith('data.tar'):continue
    with tarfile.open(fileobj=io.BytesIO(data),mode='r|*') as archive:
        for item in archive:
            if item.name.endswith('/usr/bin/llvm-symbolizer') and item.isfile():
                target.write_bytes(archive.extractfile(item).read());found=True
assert found
lock=json.loads((ROOT/'app/cpp-runtime-lock.json').read_text())
patched,_=patch_dynamic(target,lock['aliases'])
output=target.with_name('libcodepocket_symbolizer.so');output.write_bytes(patched)
result=dict(package=meta,original=elf(target),patched=elf(output))
(evidence/'symbolizer-input.json').write_text(json.dumps(result,indent=2)+'\n')
assert result['patched']['aarch64'] and result['patched']['load_segments_16k_compatible']
print(json.dumps(result,indent=2))
