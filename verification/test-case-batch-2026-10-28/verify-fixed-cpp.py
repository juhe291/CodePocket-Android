"""Execute the actual installed-runner C source with a host shared library and finite files.
Host process/EOF evidence only: Android execution and its POSIX ownership/limits are separate.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2];PHASE=Path(__file__).resolve().parent
CACHE=ROOT/'.tools/test-case-host';CACHE.mkdir(exist_ok=True)
windows=os.name=='nt';cc=Path('C:/mingw64/bin/gcc.exe') if windows else Path('/usr/bin/gcc')
cpp=cc.with_name('g++.exe' if windows else 'g++')
env=dict(os.environ);env['PATH']=str(cc.parent)+os.pathsep+env.get('PATH','')
env['TEMP']=env['TMP']=str(ROOT/'.tools/semantics-tmp')
runner=CACHE/('case-runner.exe' if windows else 'case-runner')
library=CACHE/('case-program.dll' if windows else 'case-program.so')
source=CACHE/'program.cpp'
source.write_text('#include <iostream>\nextern "C" int main(){long x=0,sum=0;while(std::cin>>x)sum+=x;std::cout<<sum<<"\\n";std::cerr<<"diagnostic\\n";return 0;}\n',encoding='utf-8')
commands=[]
def command(args,**kwargs):
    result=subprocess.run(list(map(str,args)),cwd=CACHE,env=env,capture_output=True,timeout=15,**kwargs)
    commands.append(dict(args=list(map(str,args)),exit=result.returncode,stdout=result.stdout.decode('utf-8','replace'),stderr=result.stderr.decode('utf-8','replace')))
    return result
assert command([cc,'-std=c11','-Wall','-Wextra',ROOT/'app/src/main/jni/cpp_runner.c','-o',runner]+([] if windows else ['-ldl'])).returncode==0,commands[-1]
if library.exists():library.chmod(0o644)
assert command([cpp,'-std=c++17','-shared',source,'-o',library]+(['-Wl,--export-all-symbols'] if windows else ['-fPIC'])).returncode==0,commands[-1]
if not windows:library.chmod(0o444)
cases=[]
for name,data,expected in [('no-final-newline',b'6 7',b'13\n'),('empty-eof',b'',b'0\n'),('multiline',b'1\n2\n3\n',b'6\n')]:
    incoming=CACHE/(name+'.stdin');out=CACHE/(name+'.stdout');err=CACHE/(name+'.stderr')
    incoming.write_bytes(data);out.write_bytes(b'stale');err.write_bytes(b'stale')
    result=command([runner,library,incoming,out,err])
    assert result.returncode==0 and result.stdout==result.stderr==b'',commands[-1]
    actual=out.read_bytes();error=err.read_bytes()
    assert actual==expected and error==b'diagnostic\n',(name,actual,error)
    cases.append(dict(name=name,input_sha256=hashlib.sha256(data).hexdigest(),stdout=actual.decode(),stderr=error.decode(),passed=True))
# Keep the previous interactive entry/argv path working; this is real redirected host stdin.
normal=command([runner,library],input=b'8 9\n')
assert normal.returncode==0 and normal.stdout.replace(b'\r\n',b'\n')==b'17\n' and normal.stderr.replace(b'\r\n',b'\n')==b'diagnostic\n'
cases.append(dict(name='original-entry-preserved',passed=True))
missing=command([runner,library,CACHE/'missing-input',CACHE/'no-final-newline.stdout',CACHE/'no-final-newline.stderr'])
assert missing.returncode==125
cases.append(dict(name='missing-input-rejected',passed=True))
report=dict(cases=cases,commands=commands,runner_source_sha256=hashlib.sha256((ROOT/'app/src/main/jni/cpp_runner.c').read_bytes()).hexdigest(),
    actual_production_runner_source=True,android_execution=False,android_ownership_and_limits_executed=not windows)
(PHASE/'fixed-cpp-host.json').write_text(json.dumps(report,indent=2)+'\n')
print('Actual host production runner: finite EOF/stdout/stderr, original entry and rejection: 5 passed; no Android execution')
