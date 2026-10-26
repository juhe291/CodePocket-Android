"""Record real backend responses for Android host integration tests; not device execution."""
import argparse, hashlib, json
from pathlib import Path
import sys, tempfile

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--site',type=Path,required=True);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();root=Path(__file__).resolve().parents[1]
    sys.path.insert(0,str(args.site.resolve()));sys.path.insert(0,str(root/'app/src/main/python'))
    import semantics
    cases={}
    with tempfile.TemporaryDirectory(prefix='semantic-fixtures-',dir=root/'.tools') as temporary:
        project=Path(temporary)
        (project/'sibling.py').write_text('class Token:\n    def run(self): pass\n',encoding='utf-8')
        definitions={
            'list':('items: list[str] = []\nitems.ap','complete',None),
            'json':('import json\njson.lo','complete',None),
            'instance':('class Learner:\n    def solve(self, task): pass\nLearner().so','complete',None),
            'module':('from sibling import Token\nToken().ru','complete',None),
            'middle':("value='text'\nvalue.uppxx",'complete',len("value='text'\nvalue.upp")),
            'unicode':("name='😀中文'\r\nitems=[];items.ap",'complete',None),
            'signature':("def combine(left: int, right: str='x'): return right\ncombine(1,",'signature',None),
            'shadowed':('def print(custom: str): pass\nprint(custom=','signature',None),
            'unknown':('unknown_object.','complete',None),
            'prefix':("project_value=1\np",'complete',None),
            'snippet':('for','complete',None),
        }
        for name,(code,mode,caret) in definitions.items():
            request=dict(code=code,caret=semantics._units(code) if caret is None else caret,root=str(project),path='main.py',project=True,mode=mode)
            raw=semantics.analyze(json.dumps(request),str(project/'cache'))
            answer=json.loads(raw);assert answer['available'],(name,answer)
            cases[name]=dict(code=code,caret=request['caret'],mode=mode,response=raw)
    output=dict(engine=semantics.ENGINE,backend_sha256=hashlib.sha256(Path(semantics.__file__).read_bytes().replace(b'\r\n',b'\n')).hexdigest(),
        real_backend=True,android_execution=False,cases=cases)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(output,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Recorded',len(cases),'real Python responses; no Android execution')

if __name__=='__main__':main()
