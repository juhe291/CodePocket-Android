"""Read actual pinned clangd header-path edits. Separate evidence; no App source edits."""
import importlib.util
import json
from pathlib import Path
import uuid
ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('saved_lsp_probe',ROOT/'verification/cpp-semantic-probe-2026-10-27/probe-lsp.py')
probe=importlib.util.module_from_spec(spec);spec.loader.exec_module(probe);probe.PHASE=PHASE
root=(ROOT/'.tools/cpp-header-completion'/str(uuid.uuid4())).resolve();root.mkdir(parents=True)
(root/'model.hpp').write_text('struct Model {};\n')
(root/'sub').mkdir();(root/'sub/file.hpp').write_text('struct Sub {};\n')
cache=ROOT/'.tools/cpp-runtime';headers=cache/'headers'
flags=['-xc++','-std=c++17','--target=aarch64-linux-android24','--sysroot='+str(headers/'sysroot'),
    '-resource-dir='+str(headers/'clang'),'-isystem',str(headers/'sysroot/include/c++/v1'),
    '-isystem',str(headers/'sysroot/include'),'-isystem',str(headers/'sysroot/include/aarch64-linux-android'),'-I'+str(root)]
server=probe.Server(cache/'host/clangd_21.1.8/bin/clangd.exe',root,flags)
cases={}
try:
    fixtures=[('system','#include <iost',None,'iostream'),('project','#include "mod',None,'model.hpp'),
        ('nested','#include "sub/fi',None,'file.hpp'),('middle','#include "sub/fiOLD.hpp"\n',len('#include "sub/fi'),'file.hpp'),
        ('closed-system','#include <vector>\n',len('#include <vec'),'vector'),
        ('directory','#include "su',None,'sub/'),('directory-next','#include "sub/',None,'file.hpp'),
        ('system-nested','#include <sys/sta',None,'stat.h'),
        ('unicode-crlf','// 😀中文\r\n#include "sub/fi',None,'file.hpp'),
        ('closed-project','#include "model.hpp"\n',len('#include "mod'),'model.hpp')]
    for index,(name,code,caret,expected) in enumerate(fixtures):
        path=root/(name+'.cpp');path.write_text('')
        server.open(path,code)
        caret=probe.units(code) if caret is None else caret
        response=server.request('textDocument/completion',dict(textDocument=dict(uri=path.as_uri()),position=probe.position(code,caret)),timeout=30)
        cases[name]=dict(code=code,caret=caret,response=response,names=probe.names(response),expected=expected)
        (PHASE/'host-header-fixtures.json').write_text(json.dumps(dict(real_backend=True,android_execution=False,cases=cases),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        assert any(expected in item for item in probe.names(response)),(name,response)
        print(name,probe.names(response))
finally:server.close()
