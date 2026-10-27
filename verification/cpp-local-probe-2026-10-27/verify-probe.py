"""Audit preflight claims from real files. This is not an APK or Android execution gate."""
import hashlib
import json
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
CACHE=ROOT/'.tools/cpp-local-probe'
def sha(file):
    with file.open('rb')as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
signature=(PHASE/'index-signature.log').read_text(encoding='utf-8-sig')
assert '[GNUPG:] VALIDSIG CC72CF8BA7DBFA0182877D045A897D96E57CF20C ' in signature
index=ROOT/'.tools/cpp-semantic-probe/Packages.gz'
assert sha(index)=='1ac9be7f2688cc4e549b217eceae9e536f35be770bec231970718afb2590c3df'
signed=(ROOT/'.tools/cpp-semantic-probe/InRelease').read_text()
assert any(line.split()==[sha(index),str(index.stat().st_size),'main/binary-aarch64/Packages.gz']for line in signed.splitlines())
packages=json.loads((PHASE/'package-candidates.json').read_text())['packages']
for name,meta in packages.items():
    filename=Path(meta['Filename']).name
    candidates=[CACHE/'debs'/filename,ROOT/'.tools/cpp-semantic-probe/debs'/filename]
    file=next(file for file in candidates if file.is_file())
    assert sha(file)==meta['SHA256'] and file.stat().st_size==int(meta['Size'])
native=json.loads((PHASE/'candidate-inspection.json').read_text())
assert not native['missing'] and native['compiler_found'] and native['linker_found']
assert len(native['native_files'])==3 and not native['android_execution']
for item in native['native_files']:
    assert sha(CACHE/'staged-native'/item['staged_name'])==item['staged_sha256'] and item['alignment_16k']
bundle=json.loads((PHASE/'minimal-bundle.json').read_text())
assert bundle['not_an_apk'] and bundle['raw_bytes']==6500974
for item in bundle['files']:
    file=CACHE/item['file'];assert sha(file)==item['sha256'] and file.stat().st_size==item['bytes']
cross=json.loads((PHASE/'cross-build-results.json').read_text())
assert cross['host_real_shared_library_execution'] and cross['host_cases']==4
assert cross['actual_compile_and_link_failures_verified']==2 and cross['frozen_termux_crt_and_compiler_rt_linked']
assert 'clang version 21.' in cross['android_split_frontend']
for key,filename in [('android_program','program.so'),('android_split_program','split-program.so'),('android_runner','libcodepocket_cpp_runner.so')]:
    inspected=cross[key];assert inspected['aarch64'] and inspected['load_segments_16k_compatible'] and not inspected['text_relocations']
    assert sha(CACHE/'android-build'/filename)==inspected['sha256']
assert not cross['android_execution'] and not cross['production_integrated']
host=json.loads((PHASE/'host-tool-provenance.json').read_text())
assert host['https_byte_ranges'] and not host['full_archive_sha256_verified']
for item in host['files']:
    name=Path(item['name']).name;file=CACHE/'host-ndk30'/('bin/'+name if name!='source.properties'else name)
    assert sha(file)==item['sha256'] and item['zip_crc_verified']
licenses={}
for name in ('clang','lld','compiler-rt'):
    file=PHASE/'upstream'/(name+'-LICENSE.txt');assert file.stat().st_size>15000
    assert 'LLVM' in file.read_text() and 'Apache' in file.read_text()
    licenses[name]=dict(url=f'https://raw.githubusercontent.com/llvm/llvm-project/llvmorg-21.1.8/{name}/LICENSE.TXT',sha256=sha(file),bytes=file.stat().st_size)
changed=subprocess.check_output(['git','diff','c429a51','--name-only','--','app','workspace-core','editor-assist','editor-sora','scripts','tests','.github'],cwd=ROOT,text=True)
assert not changed.strip(),changed
apk=ROOT/'dist/CodePocket-cpp-semantics-2026-10-27-debug.apk'
assert sha(apk)=='d828aff66043ec8bfd9c877b8f7b53873f2a7205a66b657ff248e7f78694d4cc'
result=dict(preflight_verified=True,index_signature_verified_again=True,android_candidate_tools=2,
    future_format_candidate_inspected=True,minimal_extra_raw_bytes=bundle['raw_bytes'],minimal_extra_probe_zip_bytes=bundle['probe_zip_bytes'],
    real_host_shared_library_cases=4,real_cross_failure_cases=2,android_elf_cross_builds=3,
    licenses=licenses,production_sources_unchanged=True,current_apk_unchanged=True,new_apk_built=False,
    android_execution=False,production_integrated=False,full_goal_complete=False)
(PHASE/'results.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
