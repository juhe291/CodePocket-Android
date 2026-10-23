import importlib.util,io,json,marshal,sys,types,zipfile
from pathlib import Path
root=Path.cwd();phase=root/'verification/workbench-diagnostic-ui-2026-10-24'
apk=root/'app/build/outputs/apk/debug/app-debug.apk'
with zipfile.ZipFile(apk) as z:
    packaged=z.read('assets/chaquopy/app.imy')
    previous=zipfile.ZipFile(root/'dist/CodePocket-light-theme-2026-10-24-debug.apk').read('assets/chaquopy/app.imy')
    assert packaged==previous
    bytecode=zipfile.ZipFile(io.BytesIO(packaged)).read('runner.pyc')
runner=types.ModuleType('runner');runner.__file__=str(apk)+'!runner.pyc';runner.__packaged_bytecode__=bytecode
exec(marshal.loads(bytecode[16:]),runner.__dict__);sys.modules['runner']=runner
sys.path.insert(0,str(root/'tests'));import test_terminal_runner as t
assert t.runner is runner
rows=[]
for i in range(40):
    p=t.ProcessRun('import pathlib\npathlib.Path("helper.py").write_text("answer=42")\nimport helper\nprint(helper.answer)')
    try:
        result=p.finish()
        row={'iteration':i,'exit_code':result['exit_code'],'stdout':p.text(),'stderr':p.text(1),'result':result}
        rows.append(row)
    finally:p.close()
report={'unchanged_python_archive':True,'cases':len(rows),'failures':[x for x in rows if x['exit_code']!=0],'all_cases':rows}
(phase/'workspace-import-probe.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k!='all_cases'},ensure_ascii=False,indent=2))
