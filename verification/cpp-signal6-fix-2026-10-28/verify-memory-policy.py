"""Execute the production RSS parser on proc-format fixtures, not Android."""
import hashlib,json,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];PHASE=Path(__file__).resolve().parent
CACHE=ROOT/'.tools/cpp-memory-policy';CACHE.mkdir(exist_ok=True)
proc=CACHE/'proc';proc.mkdir(exist_ok=True)
def process(pid,group,resident):
    directory=proc/str(pid);directory.mkdir(exist_ok=True)
    (directory/'stat').write_text(f'{pid} (tool (child)) S 1 {group} 0 0 0\n')
    (directory/'statm').write_text(f'1099511627776 {resident} 0 0 0 0 0\n')
process(201,201,10);process(202,201,20);process(300,300,999999999)
header=ROOT/'app/src/main/jni/execution_memory.h';source=CACHE/'policy.c'
source.write_text('#include "'+header.as_posix()+'"\n'+r'''
#include <assert.h>
int main(void) {
    uint64_t bytes=0;
    assert(cpp_group_resident_bytes("proc",201,4096,&bytes) && bytes==30*4096ULL);
    assert(bytes<256*1024*1024ULL);
    assert(cpp_group_resident_bytes("proc",201,16384,&bytes) && bytes==30*16384ULL);
    assert(bytes>20*16384ULL);
    assert(!cpp_group_resident_bytes("missing-proc",201,4096,&bytes));
    assert(!cpp_resident_bytes("proc","201",0,&bytes));
    FILE *file=fopen("proc/202/statm","w");assert(file);
    fputs("1099511627776 18446744073709551615\n",file);fclose(file);
    assert(cpp_group_resident_bytes("proc",201,16384,&bytes) && bytes==UINT64_MAX);
    puts("owned group, huge virtual/small RSS, 4K/16K pages, missing metrics, overflow: passed");
    return 0;
}
''',encoding='utf-8')
windows=os.name=='nt';cc=Path('C:/mingw64/bin/gcc.exe') if windows else Path('/usr/bin/cc');binary=CACHE/('policy.exe' if windows else 'policy')
env=dict(os.environ);env['PATH']=str(cc.parent)+os.pathsep+env.get('PATH','');records=[]
for args in ([str(cc),'-std=c11','-Wall','-Wextra',str(source),'-o',str(binary)],[str(binary)]):
    r=subprocess.run(args,cwd=CACHE,env=env,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=20)
    records.append(dict(arguments=args,exit=r.returncode,stdout=r.stdout,stderr=r.stderr));assert r.returncode==0,records[-1]
report=dict(production_header_sha256=hashlib.sha256(header.read_bytes()).hexdigest(),actual_c_parser_executed=True,
    page_sizes=[4096,16384],large_virtual_space_is_not_resident_memory=True,foreign_group_excluded=True,
    overflow_saturates=True,android_proc_or_signal_execution=False,records=records)
(PHASE/'memory-policy.json').write_text(json.dumps(report,indent=2)+'\n')
print(records[-1]['stdout'].strip()+'; actual production C parser, not Android execution')
