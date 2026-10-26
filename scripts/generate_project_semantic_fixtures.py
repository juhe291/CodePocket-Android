"""Real Jedi results from immutable multi-file snapshots, for Main/Sora host integration."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--site', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    sys.path.insert(0, str(args.site.resolve()))
    sys.path.insert(0, str(root / 'app/src/main/python'))
    import semantics
    cases = {}
    definitions = {
        'unsaved_project': ('main.py', 'from api import payload\npayload.up', 'complete',
                            {'sibling.py': "payload='😀中文'\n", 'api.py': 'from sibling import payload\n'}),
        'project_signature': ('main.py', 'from api import combine\ncombine(1,', 'signature',
                              {'sibling.py': 'def combine(left: int, right: str): pass\n', 'api.py': 'from sibling import combine\n'}),
        'fresh_provider': ('external.py', 'from helper import payload\npayload.up', 'complete',
                           {'helper.py': "payload='live provider'\n"}),
    }
    with tempfile.TemporaryDirectory(prefix='project-fixtures-', dir=root / '.tools') as temporary:
        for tag, (path, code, mode, modules) in definitions.items():
            project = Path(temporary) / tag
            project.mkdir()
            content = dict(modules, **{path: code})
            for relative, text in content.items():
                (project / relative).write_text(text, encoding='utf-8')
            request = dict(code=code, caret=semantics._units(code), path=path, root=str(project), project=True, mode=mode)
            response = semantics.analyze(json.dumps(request), str(Path(temporary) / 'cache'))
            answer = json.loads(response)
            assert answer['available'], answer
            if mode == 'complete':
                assert 'upper' in [item['label'] for item in answer['items']], answer
                assert 'bit_count' not in [item['label'] for item in answer['items']], answer
            else:
                assert answer['signatures'][0]['parameters'] == ['left: int', 'right: str'], answer
                assert answer['signatures'][0]['active'] == 1, answer
            cases[tag] = dict(path=path, code=code, caret=request['caret'], mode=mode, content=content, response=response)
    output = dict(real_backend=True, android_execution=False, engine=semantics.ENGINE,
                  backend_sha256=hashlib.sha256(Path(semantics.__file__).read_bytes().replace(b'\r\n', b'\n')).hexdigest(), cases=cases)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print('Recorded 3 real cross-file responses; no Android execution')


if __name__ == '__main__':
    main()
