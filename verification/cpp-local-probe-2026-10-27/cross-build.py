"""Real host execution and Android ELF cross-build evidence, explicitly NOT Android execution."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
PHASE = Path(__file__).resolve().parent
CACHE = ROOT / '.tools/cpp-local-probe'
sys.path.insert(0, str(ROOT / 'scripts'))
from cpp_runtime_elf import elf


def run(arguments, cwd, tag, input=None, environment=None):
    environment = dict(os.environ) if environment is None else dict(environment)
    temporary = CACHE/'temporary'; temporary.mkdir(parents=True,exist_ok=True)
    environment['TEMP'] = str(temporary); environment['TMP'] = str(temporary)
    result = subprocess.run([str(arg) for arg in arguments], cwd=cwd, input=input, capture_output=True,
        encoding='utf-8', errors='replace', env=environment, timeout=60)
    record = dict(command=[str(arg) for arg in arguments], cwd=str(cwd), returncode=result.returncode,
        stdout=result.stdout, stderr=result.stderr)
    (PHASE / (tag + '.json')).write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    assert result.returncode == 0, (tag, result.returncode, result.stderr[:2000])
    return result


def main():
    fixtures = PHASE / 'fixtures'
    host = CACHE / 'host-build'; host.mkdir(parents=True, exist_ok=True)
    cpp = Path('C:/mingw64/bin/g++.exe'); cc = Path('C:/mingw64/bin/gcc.exe')
    assert cpp.is_file() and cc.is_file()
    environment = dict(os.environ); environment['PATH'] = str(cpp.parent) + os.pathsep + environment.get('PATH','')
    version = run([cpp,'--version'],host,'host-version',environment=environment).stdout.splitlines()[0]
    run([cpp,'-std=c++17','-shared','-Wl,--export-all-symbols',fixtures/'main.cpp',fixtures/'helper.cpp','-o','program.dll'],host,'host-build-library',environment=environment)
    run([cc,fixtures/'runner.c','-o','runner.exe'],host,'host-build-runner',environment=environment)
    result = run([host/'runner.exe','./program.dll'],host,'host-execute','9 2\n',environment)
    assert result.stdout.replace('\r\n','\n') == 'ready\n2:9:2\ncaught\nbye\n', result.stdout
    failed = subprocess.run([str(host/'runner.exe'),'./program.dll'],cwd=host,input='bad\n',capture_output=True,text=True,env=environment,timeout=10)
    assert failed.returncode == 2 and failed.stdout.replace('\r\n','\n') == 'ready\nbye\n'
    (PHASE/'host-invalid-input.json').write_text(json.dumps(dict(returncode=failed.returncode,stdout=failed.stdout,stderr=failed.stderr),indent=2)+'\n')
    run([cpp,'-std=c++17','-shared','-Wl,--export-all-symbols',fixtures/'argv-main.cpp','-o','argv-program.dll'],host,'host-build-argv',environment=environment)
    argv_result=run([host/'runner.exe','./argv-program.dll'],host,'host-execute-argv',environment=environment)
    assert argv_result.stdout.replace('\r\n','\n')=='1:program\n'
    run([cpp,'-std=c++17','-shared','-Wl,--export-all-symbols',fixtures/'helper.cpp','-o','no-main.dll'],host,'host-build-no-main',environment=environment)
    missing=subprocess.run([str(host/'runner.exe'),'./no-main.dll'],cwd=host,capture_output=True,text=True,env=environment,timeout=10)
    assert missing.returncode==127 and 'missing main entry' in missing.stderr
    (PHASE/'host-missing-entry.json').write_text(json.dumps(dict(returncode=missing.returncode,stdout=missing.stdout,stderr=missing.stderr),indent=2)+'\n')

    toolchain = ROOT/'.tools/android-sdk/ndk/27.2.12479018/toolchains/llvm/prebuilt/windows-x86_64'
    compiler = toolchain/'bin/clang++.exe'; ccompiler = toolchain/'bin/clang.exe'
    android = CACHE/'android-build'; android.mkdir(parents=True,exist_ok=True)
    tool_version = run([compiler,'--version'],android,'cross-compiler-version').stdout.splitlines()[0]
    common = ['--target=aarch64-linux-android24','-Wl,-z,max-page-size=16384']
    runtime = json.loads((ROOT/'app/cpp-runtime-lock.json').read_text())
    libcxx = next(item for item in runtime['libraries'] if item['member']=='lib/libc++_shared.so')
    library = ROOT/'.tools/cpp-runtime/jni/arm64-v8a'/libcxx['packaged_name']
    run([compiler,*common,'-std=c++17','-fPIC','-shared','-nostdlib++',fixtures/'main.cpp',fixtures/'helper.cpp',
        library,'-Wl,--no-undefined','-Wl,-soname,program.so','-o',android/'program.so'],android,'cross-build-library')
    run([ccompiler,*common,'-fPIE','-pie',fixtures/'runner.c','-ldl','-o',android/'libcodepocket_cpp_runner.so'],android,'cross-build-runner')
    # Also exercise the proposed split frontend/linker command with the actual frozen
    # Termux CRT/compiler-rt and the very same Android headers used by production clangd.
    headers=ROOT/'.tools/cpp-runtime/headers'
    frontend=CACHE/'host-ndk30/bin/clang.exe'
    assert frontend.is_file()
    split_version=run([frontend,'--version'],android,'split-compiler-version').stdout.splitlines()[0]
    compile_flags=['--target=aarch64-linux-android24','-std=c++17','-fPIC','-c','--no-default-config','-fno-color-diagnostics','-nostdinc++',
        '-isystem',headers/'sysroot/include/c++/v1','-isystem',headers/'sysroot/include',
        '-isystem',headers/'sysroot/include/aarch64-linux-android','-resource-dir='+str(headers/'clang')]
    for source in ('main','helper'):
        run([frontend,*compile_flags,fixtures/(source+'.cpp'),'-o',android/(source+'.o')],android,'split-compile-'+source)
    support=CACHE/'support/lib'
    builtins=support/'clang/21/lib/linux/libclang_rt.builtins-aarch64-android.a'
    assert builtins.is_file()
    platform=toolchain/'sysroot/usr/lib/aarch64-linux-android/24'
    run([toolchain/'bin/ld.lld.exe','-flavor','gnu','-shared','--eh-frame-hdr','--no-undefined',
        '-z','relro','-z','now','-z','max-page-size=16384','--hash-style=both','--exclude-libs=ALL',
        '-soname','split-program.so','-o',android/'split-program.so',support/'crtbegin_so.o',
        android/'main.o',android/'helper.o',library,'-L'+str(platform),'-lc','-lm','-ldl',builtins,support/'libunwind.a',support/'crtend_so.o'],
        android,'split-link-frozen-runtime')
    plugin=elf(android/'program.so'); runner=elf(android/'libcodepocket_cpp_runner.so')
    split=elf(android/'split-program.so')
    for item in (plugin,runner,split):
        assert item['aarch64'] and item['load_segments_16k_compatible'] and not item['text_relocations']
        assert all(note['api']<=24 for note in item['android_notes'])
    assert libcxx['packaged_name'] in plugin['needed'] and 'libc++_shared.so' not in plugin['needed']
    assert libcxx['packaged_name'] in split['needed'] and 'libc++_shared.so' not in split['needed']
    assert plugin['entry']==0 and plugin['interpreter'] is None
    assert split['entry']==0 and split['interpreter'] is None
    assert runner['entry']>0 and runner['interpreter']=='/system/bin/linker64'
    symbols=run([toolchain/'bin/llvm-readelf.exe','--dyn-syms',android/'split-program.so'],android,'split-dynamic-symbols').stdout
    assert any('FUNC' in line and 'GLOBAL' in line and 'UND' not in line and line.rstrip().endswith(' main') for line in symbols.splitlines())
    bad_env=dict(os.environ);bad_env['TEMP']=str(CACHE/'temporary');bad_env['TMP']=bad_env['TEMP']
    compile_error=subprocess.run([str(frontend),*[str(arg) for arg in compile_flags],str(fixtures/'broken.cpp'),'-o',str(android/'broken.o')],
        cwd=android,capture_output=True,text=True,encoding='utf-8',errors='replace',env=bad_env,timeout=30)
    assert compile_error.returncode!=0 and 'broken.cpp:2:' in compile_error.stderr and 'missing_identifier' in compile_error.stderr
    (PHASE/'real-compile-error.json').write_text(json.dumps(dict(returncode=compile_error.returncode,stdout=compile_error.stdout,stderr=compile_error.stderr),indent=2)+'\n')
    incomplete_link=subprocess.run([str(toolchain/'bin/ld.lld.exe'),'-flavor','gnu','-shared','--no-undefined','-o',str(android/'broken-link.so'),
        str(support/'crtbegin_so.o'),str(android/'main.o'),str(library),'-L'+str(platform),'-lc','-lm','-ldl',str(builtins),str(support/'libunwind.a'),str(support/'crtend_so.o')],
        cwd=android,capture_output=True,text=True,encoding='utf-8',errors='replace',env=bad_env,timeout=30)
    assert incomplete_link.returncode!=0 and 'undefined symbol' in incomplete_link.stderr and 'sorted_values' in incomplete_link.stderr
    (PHASE/'real-link-error.json').write_text(json.dumps(dict(returncode=incomplete_link.returncode,stdout=incomplete_link.stdout,stderr=incomplete_link.stderr),indent=2)+'\n')
    report=dict(host_compiler=version,host_real_shared_library_execution=True,host_cases=4,
        android_cross_compiler=tool_version,android_program=plugin,android_runner=runner,
        android_split_frontend=split_version,
        android_split_program=split,frozen_termux_crt_and_compiler_rt_linked=True,
        actual_compile_and_link_failures_verified=2,
        uses_actual_packaged_private_libcxx=True,android_execution=False,production_integrated=False,
        limitations=['Host GCC execution does not validate Android dlopen/SELinux/PTY.',
            'NDK host Clang is not the Termux Android Clang executable; its startup remains unverified.',
            'Basic mode uses NDK27 Clang18. Split mode uses official NDK30 Clang21 with frozen headers/CRT/compiler-rt, NDK27 platform import stubs and host LLD18; Android startup still requires validation.'])
    (PHASE/'cross-build-results.json').write_text(json.dumps(report,indent=2)+'\n')
    print('Four real host shared-library cases passed; Android program/runner cross-built and inspected; NO Android execution')


if __name__=='__main__': main()
