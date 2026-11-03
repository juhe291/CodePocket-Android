"""Probe the pinned real clangd formatter; reuse the existing LSP host harness only."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess

ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
CACHE=ROOT/'.tools/formatting-probe/cpp'
CACHE.mkdir(parents=True,exist_ok=True)
spec=importlib.util.spec_from_file_location('existing_cpp_host_harness',ROOT/'verification/cpp-semantic-probe-2026-10-27/probe-lsp.py')
harness=importlib.util.module_from_spec(spec)
spec.loader.exec_module(harness)
harness.PHASE=PHASE  # Its bounded stderr log belongs to this batch; do not overwrite old evidence.
executable=next((ROOT/'.tools/cpp-semantic-probe/host').rglob('clangd.exe'))

def units(text):return len(text.encode('utf-16-le'))//2

def offset(code,p):
    rows=code.splitlines(keepends=True)
    line=p['line'];col=p['character']
    if line==len(rows) and (not code or code.endswith('\n')):assert col==0;return units(code)
    assert 0<=line<len(rows)
    prefix=''.join(rows[:line]);body=rows[line].rstrip('\r\n')
    assert 0<=col<=units(body)
    body.encode('utf-16-le')[:col*2].decode('utf-16-le')
    return units(prefix)+col

def apply(code,edits):
    changes=[]
    for edit in edits:
        start=offset(code,edit['range']['start']);end=offset(code,edit['range']['end'])
        assert start<=end
        changes.append((start,end,edit['newText']))
    changes.sort();assert all(a[1]<=b[0] for a,b in zip(changes,changes[1:]))
    raw=code.encode('utf-16-le')
    for start,end,text in reversed(changes):raw=raw[:start*2]+text.encode('utf-16-le')+raw[end*2:]
    return raw.decode('utf-16-le')

style=dict(BasedOnStyle='LLVM',IndentWidth=4,TabWidth=4,ContinuationIndentWidth=4,
    UseTab='Never',ColumnLimit=100,SortIncludes='Never',SortUsingDeclarations='Never',
    ReflowComments='Never',BreakStringLiterals=False,RawStringFormats=[],FixNamespaceComments=False,
    InsertBraces=False,RemoveBracesLLVM=False,RemoveParentheses='Leave',RemoveSemicolon=False,
    QualifierAlignment='Leave',Standard='c++17')
(CACHE/'.clang-format').write_text(json.dumps(style),encoding='utf-8')
(CACHE/'z.h').write_text('#define ORDER_VALUE 3\n',encoding='utf-8')
(CACHE/'a.h').write_text('constexpr int answer=ORDER_VALUE;\n',encoding='utf-8')
server=harness.Server(executable,CACHE,['-xc++','-std=c++17','-nostdinc','-nostdinc++'])
assert server.initialize['result']['capabilities']['documentFormattingProvider']
cases=[]
env=dict(os.environ);env['PATH']='C:/mingw64/bin'+os.pathsep+env.get('PATH','')
definitions=[
    ('spacing','#include <iostream>\nint main(){int x=3;std::cout<<x+4<<"\\n";return 0;}\n','7\n'),
    ('include_order','#include "z.h"\n#include "a.h"\n#include <iostream>\nint main(){std::cout<<answer<<"\\n";}\n','3\n'),
    ('unicode_raw','#include <iostream>\n// 中文 😀 comment stays\nint main(){const char* text=R"(  raw\n   body 😀中文)";std::cout<<text<<"\\n";}\n','  raw\n   body 😀中文\n'),
    ('macro','#include <iostream>\n#define VALUE(x) \\\n+ ((x)+1)\nint main(){std::cout<<VALUE(4)<<"\\n";}\n','5\n'),
    ('crlf','#include <iostream>\r\nint main(){std::cout<<u8"😀中文"<<"\\n";}\r\n','😀中文\n'),
]
try:
    for tag,code,expected in definitions:
        path=CACHE/(tag+'.cpp');saved='// saved original remains untouched\n';path.write_text(saved,encoding='utf-8')
        server.open(path,code)
        reply=server.request('textDocument/formatting',dict(textDocument=dict(uri=path.as_uri()),options=dict(tabSize=4,insertSpaces=True)))
        assert 'error' not in reply,reply
        formatted=apply(code,reply['result'])
        assert formatted!=code and path.read_text()==saved
        if tag=='include_order':assert formatted.index('"z.h"')<formatted.index('"a.h"')
        if tag=='unicode_raw':assert 'R"(  raw\n   body 😀中文)"' in formatted and '// 中文 😀 comment stays' in formatted
        executions=[]
        for label,text in [('before',code),('after',formatted)]:
            source=CACHE/(tag+'-'+label+'.cpp');binary=CACHE/(tag+'-'+label+'.exe')
            source.write_bytes(text.encode('utf-8'))
            compile_result=subprocess.run(['C:/mingw64/bin/g++.exe','-std=c++17','-finput-charset=UTF-8','-fexec-charset=UTF-8',str(source),'-o',str(binary)],cwd=CACHE,env=env,capture_output=True,timeout=30)
            assert compile_result.returncode==0,(tag,label,compile_result.stderr.decode('utf-8','replace'))
            result=subprocess.run([str(binary)],cwd=CACHE,env=env,capture_output=True,timeout=5)
            actual=result.stdout.decode('utf-8').replace('\r\n','\n')
            assert result.returncode==0 and actual==expected,(tag,label,result.returncode,actual,result.stderr)
            executions.append(dict(stage=label,exit=result.returncode,stdout=actual))
        server.notify('textDocument/didChange',dict(textDocument=dict(uri=path.as_uri(),version=2),contentChanges=[dict(text=formatted)]))
        second=server.request('textDocument/formatting',dict(textDocument=dict(uri=path.as_uri()),options=dict(tabSize=4,insertSpaces=True)))
        assert 'error' not in second and apply(formatted,second['result'])==formatted,second
        cases.append(dict(case=tag,source=code,formatted=formatted,reply=reply,idempotent=True,saved_file_unchanged=True,executions=executions,passed=True))
    for width,tabs in [(2,False),(8,False),(4,True)]:
        options=dict(style,IndentWidth=width,TabWidth=width,ContinuationIndentWidth=width,UseTab='ForIndentation' if tabs else 'Never')
        (CACHE/'.clang-format').write_text(json.dumps(options),encoding='utf-8')
        code='void sample(){\nif(true){\nint value=1;\n}\n}\n';path=CACHE/f'indent-{width}-{tabs}.cpp';path.write_text('// saved\n')
        server.open(path,code);reply=server.request('textDocument/formatting',dict(textDocument=dict(uri=path.as_uri()),options=dict(tabSize=width,insertSpaces=not tabs)))
        assert 'error' not in reply,reply
        formatted=apply(code,reply['result']);assert ('\tif (true)' if tabs else ' '*width+'if (true)') in formatted,formatted
        cases.append(dict(case=f'indent-{width}-{tabs}',formatted=formatted,reply=reply,passed=True))
finally:
    server.close()
report=dict(engine=server.initialize['result']['serverInfo'],embedded_clang_format=True,style=style,cases=cases,
    passed=len(cases),harness_reused=True,parse_wait_not_required=True,android_execution=False,
    executable_sha256=hashlib.sha256(executable.read_bytes()).hexdigest())
(PHASE/'cpp-format-probe.json').write_text(json.dumps(report,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
print(f'Real pinned clangd formatter: {len(cases)} passed; before/after compile and execution: 5 pairs; Android not executed')
