"""Reproducible real Jedi host capability probe; does not claim Android integration."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import time


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--site',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    sys.path.insert(0,str(args.site.resolve()))
    import jedi
    import parso
    assert jedi.__version__=='0.20.1',jedi.__version__
    results=[]
    with tempfile.TemporaryDirectory(prefix='codepocket-jedi-') as temp:
        root=Path(temp)
        project=jedi.Project(root,load_unsafe_extensions=False)
        def complete(name,code,expected,path=None):
            began=time.perf_counter()
            try:
                items=jedi.Script(code=code,path=path or root/'main.py',project=project).complete()
                names=[x.name for x in items]
                passed=expected in names
                results.append(dict(case=name,passed=passed,expected=expected,names=names[:30],elapsed_ms=round((time.perf_counter()-began)*1000,2)))
            except Exception as error:
                results.append(dict(case=name,passed=False,error=repr(error)))
        complete('typed_list_member','items: list[str] = []\nitems.ap','append')
        complete('instance_method','class Learner:\n    def solve(self, task):\n        return task\nstudent = Learner()\nstudent.so','solve')
        complete('standard_library','import json\njson.lo','loads')
        disk=root/'unsaved.py';disk.write_text('value = 1\n',encoding='utf8')
        complete('unsaved_text_overrides_disk',"value = 'hello'\nvalue.up",'upper',disk)
        results.append(dict(case='disk_untouched',passed=disk.read_text(encoding='utf8')=='value = 1\n'))
        marker=root/'MUST_NOT_EXECUTE'
        (root/'sibling.py').write_text('from pathlib import Path\nPath('+repr(str(marker))+').write_text("executed")\nclass Token:\n    def run(self):\n        return 1\n',encoding='utf8')
        complete('project_module_static_members','from sibling import Token\nToken().ru','run')
        results.append(dict(case='project_top_level_not_executed',passed=not marker.exists()))
        unicode_code="label = '😀中文'; items = []; items.ap"
        complete('unicode_codepoint_column',unicode_code,'append')
        results[-1].update(jedi_column=len(unicode_code),utf16_offset=len(unicode_code.encode('utf-16-le'))//2)
        began=time.perf_counter()
        signatures=jedi.Script(code="def combine(left: int, right: str = 'x') -> str:\n    return right\ncombine(1,",path=root/'signature.py',project=project).get_signatures()
        results.append(dict(case='signature_active_parameter',passed=bool(signatures) and signatures[0].name=='combine' and signatures[0].index==1,signatures=[dict(name=x.name,active=x.index,parameters=[p.description for p in x.params]) for x in signatures],elapsed_ms=round((time.perf_counter()-began)*1000,2)))
    wheels={p.name:dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in (args.site.parents[1]/'semantic-wheels').glob('*.whl')}
    answer=dict(python=sys.version,jedi=jedi.__version__,parso=parso.__version__,cases=results,passed=sum(x['passed'] for x in results),total=len(results),wheels=wheels,android_verified=False,app_integrated=False)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(answer,ensure_ascii=False,indent=2),encoding='utf8')
    print(json.dumps(answer,ensure_ascii=False,indent=2))
    return 0 if all(x['passed'] for x in results) else 1


if __name__=='__main__':
    raise SystemExit(main())
