"""Optional live API smoke test; sends only the tiny programs below to Wandbox."""
import argparse
import json
import urllib.request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--proxy', help='Optional HTTP proxy URL')
    args = parser.parse_args()
    opener = urllib.request.build_opener(urllib.request.ProxyHandler(
        {'http': args.proxy, 'https': args.proxy} if args.proxy else {}))

    def request(path, payload=None):
        data = None if payload is None else json.dumps(payload).encode()
        req = urllib.request.Request('https://wandbox.org/api/' + path, data=data,
                                     headers={'Content-Type': 'application/json', 'User-Agent': 'CodePocket/0.2'})
        with opener.open(req, timeout=40) as response:
            return json.load(response)

    compilers = request('list.json')
    candidates = [c['name'] for c in compilers if c.get('language') == 'C++'
                  and c.get('compiler-option-raw') and 'gcc' in c['name']]
    compiler = next((c for prefix in ('gcc-14', 'gcc-15', 'gcc-13', 'gcc-12', 'gcc-16')
                     for c in candidates if c.startswith(prefix)), candidates[0])
    programs = [
        ('stdin_stl', '#include <bits/stdc++.h>\nint main(){std::vector<int> v(2);std::cin>>v[0]>>v[1];std::sort(v.begin(),v.end());std::cout<<v[0]+v[1]<<"\\n";}', '3 5', '8\n'),
        ('compile_error', 'int main(){ return invalid_symbol; }', '', None),
        ('runtime_exit', 'int main(){ return 7; }', '', None),
    ]
    for name, code, stdin, output in programs:
        result = request('compile.json', {'compiler': compiler, 'code': code, 'stdin': stdin,
                                         'compiler-option-raw': '-std=c++17', 'save': False})
        print(json.dumps({'case': name, 'compiler': compiler, 'response': result}, ensure_ascii=False), flush=True)
        if output is not None:
            assert str(result['status']) == '0' and result['program_output'] == output
        elif name == 'compile_error':
            assert str(result['status']) != '0' and 'error:' in result.get('compiler_error', '')
        else:
            assert str(result['status']) == '7'


if __name__ == '__main__':
    main()
