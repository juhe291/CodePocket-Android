"""Current cross-ELF, exact pins and old APK preservation. No Android execution."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile
ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'scripts'))
from cpp_runtime_elf import elf
def sha(file):
    with file.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
lock=json.loads((ROOT/'app/cpp-runtime-lock.json').read_text())
assert lock['packages']['llvm']==json.loads((PHASE/'symbolizer-package-candidate.json').read_text())
assert sha(ROOT/'.tools/cpp-runtime/downloads/llvm_21.1.8-3_aarch64.deb')==lock['packages']['llvm']['SHA256']
images=[]
for item in lock['libraries']:
    path=ROOT/'.tools/cpp-runtime/jni/arm64-v8a'/item['packaged_name']
    assert sha(path)==item['sha256'] and path.stat().st_size==item['bytes']
    image=elf(path);assert image['aarch64'] and image['load_segments_16k_compatible'] and not image['text_relocations']
    images.append(image)
for name in ['libcodepocket_execute.so','codepocket_cpp_runner']:
    image=elf(ROOT/'.tools/cpp-execution-jni/libs/arm64-v8a'/name)
    assert image['aarch64'] and image['load_segments_16k_compatible'] and not image['text_relocations']
    assert set(image['needed'])<=set(['libc.so','libm.so','libdl.so'])
    images.append(image)
old=ROOT/'dist/CodePocket-cpp-semantics-2026-10-27-debug.apk'
assert sha(old)=='d828aff66043ec8bfd9c877b8f7b53873f2a7205a66b657ff248e7f78694d4cc'
with zipfile.ZipFile(old) as archive:
    for item in lock['libraries'][:9]:assert hashlib.sha256(archive.read('lib/arm64-v8a/'+item['packaged_name'])).hexdigest()==item['sha256']
    assert archive.read('assets/cpp-runtime/headers.zip')==(ROOT/'.tools/cpp-runtime/assets/cpp-runtime/headers.zip').read_bytes()
result=dict(images=images,old_nine_inputs_and_header_bytes_unchanged=True,previous_apk_sha256=sha(old),
    android_execution=False,new_apk_built=False,whole_batch_complete=False)
(PHASE/'fault-input-results.json').write_text(json.dumps(result,indent=2)+'\n')
print('12 pinned tools/closure inputs + 2 current cross images checked; previous APK unchanged; no Android execution or new APK')
