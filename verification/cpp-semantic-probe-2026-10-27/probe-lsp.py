"""Actual clangd 21.1.8 LSP with the candidate Android headers. Host evidence only."""
import hashlib
import json
import os
from pathlib import Path
import queue
import subprocess
import threading
import time

PHASE = Path(__file__).resolve().parent
ROOT = PHASE.parents[1]
CACHE = ROOT / '.tools/cpp-semantic-probe'


def units(text): return len(text.encode('utf-16-le')) // 2


def position(code, caret):
    prefix = code.encode('utf-16-le')[:caret * 2].decode('utf-16-le')
    return dict(line=prefix.count('\n'), character=units(prefix.rsplit('\n', 1)[-1]))


class Server:
    def __init__(self, executable, project, flags):
        self.process = subprocess.Popen([str(executable), '--enable-config=false', '--background-index=false',
            '--clang-tidy=false', '--header-insertion=never', '--function-arg-placeholders=false',
            '--completion-style=detailed', '--completion-parse=always', '--use-dirty-headers', '-j=2', '--limit-results=30', '--pch-storage=memory', '--log=verbose'],
            cwd=project, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        self.responses = queue.Queue(); self.notifications = []; self.errors = bytearray(); self.serial = 0
        self.reader = threading.Thread(target=self._read, daemon=True); self.reader.start()
        self.err = threading.Thread(target=self._stderr, daemon=True); self.err.start()
        self.initialize = self.request('initialize', dict(processId=os.getpid(), rootUri=project.as_uri(),
            clientInfo=dict(name='CodePocket host probe', version='P2-C'),
            capabilities=dict(general=dict(positionEncodings=['utf-16']),
                textDocument=dict(completion=dict(completionItem=dict(snippetSupport=True, insertReplaceSupport=True,
                    documentationFormat=['plaintext'])),
                    signatureHelp=dict(signatureInformation=dict(documentationFormat=['plaintext'],
                        parameterInformation=dict(labelOffsetSupport=True))))),
            initializationOptions=dict(fallbackFlags=flags)), timeout=30)
        assert self.initialize['result']['serverInfo']['version'].startswith('clangd version 21.1.8'), self.initialize
        assert self.initialize['result']['capabilities'].get('positionEncoding', 'utf-16') == 'utf-16'
        self.notify('initialized', {})

    def _read(self):
        try:
            while True:
                headers = {}
                while True:
                    line = self.process.stdout.readline()
                    if not line: raise EOFError('clangd exited')
                    if line == b'\r\n': break
                    key, value = line.decode('ascii').split(':', 1); headers[key.lower()] = value.strip()
                length = int(headers['content-length']); assert 0 < length <= 2_000_000
                raw = self.process.stdout.read(length); assert len(raw) == length
                self.responses.put(json.loads(raw))
        except Exception as error: self.responses.put(error)

    def _stderr(self):
        for line in iter(self.process.stderr.readline, b''):
            self.errors.extend(line); del self.errors[:-64000]

    def send(self, message):
        raw = json.dumps(message, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
        self.process.stdin.write(f'Content-Length: {len(raw)}\r\n\r\n'.encode() + raw); self.process.stdin.flush()

    def notify(self, method, params): self.send(dict(jsonrpc='2.0', method=method, params=params))

    def request(self, method, params, timeout=15):
        self.serial += 1; id_ = self.serial
        self.send(dict(jsonrpc='2.0', id=id_, method=method, params=params))
        deadline = time.monotonic() + timeout
        while True:
            message = self.responses.get(timeout=max(0.001, deadline - time.monotonic()))
            if isinstance(message, Exception): raise message
            if message.get('id') == id_: return message
            self.notifications.append(message)
            if 'method' in message and 'id' in message:
                self.send(dict(jsonrpc='2.0', id=message['id'], error=dict(code=-32601, message='Unsupported client method')))

    def open(self, path, code, version=1):
        self.notify('textDocument/didOpen', dict(textDocument=dict(uri=path.as_uri(), languageId='cpp', version=version, text=code)))

    def close(self):
        try: self.request('shutdown', None, timeout=5); self.notify('exit', None); self.process.wait(5)
        except Exception: self.process.kill(); self.process.wait(5)
        self.reader.join(2); self.err.join(2)
        (PHASE / 'host-clangd-stderr.log').write_bytes(self.errors)


def items(response):
    result = response.get('result')
    return result if isinstance(result, list) else result.get('items', []) if result else []


def names(response): return [item.get('filterText', item['label']).strip() for item in items(response)]


def main():
    executable = next((CACHE / 'host').rglob('clangd.exe'))
    sysroot = CACHE / 'extracted/ndk-sysroot'
    resource = CACHE / 'extracted/clang/lib/clang/21'
    assert (sysroot / 'include/c++/v1/vector').is_file() and (resource / 'include/stddef.h').is_file()
    project = CACHE / 'host-project'; project.mkdir(exist_ok=True)
    flags = ['-xc++', '-std=c++17', '--target=aarch64-linux-android24', '--sysroot=' + str(sysroot),
             '-resource-dir=' + str(resource), '-isystem', str(sysroot / 'include/c++/v1'),
             '-isystem', str(sysroot / 'include'), '-isystem', str(sysroot / 'include/aarch64-linux-android'),
             '-I' + str(project)]
    # An unsafe project config must never be allowed to load plugins or query arbitrary drivers.
    (project / '.clangd').write_text('CompileFlags:\n  Add: [-Xclang, -load, /MUST_NOT_LOAD]\n')
    disk_header = 'struct Token { void old_disk(); };\n'
    unsaved_header = 'struct Token { void run(); };\n'
    (project / 'model.hpp').write_text(disk_header)
    token = 'struct Token { void run(); int value; };\n'
    definitions = {
        'dot': (token + 'void f(){Token item; item.ru', 'complete', None),
        'arrow': (token + 'void f(){Token* item; item->ru', 'complete', None),
        'namespace': ('namespace Library { void solve(int); int answer; }\nvoid f(){Library::so', 'complete', None),
        'vector': ('#include <vector>\nvoid f(){std::vector<int> items; items.pu', 'complete', None),
        'unicode_crlf': ('#include <vector>\r\nvoid f(){const char* name=u8"😀中文"; std::vector<int> items; items.pu', 'complete', None),
        'middle': (token + 'void f(){Token item; item.ruxx;}', 'complete', units(token + 'void f(){Token item; item.ru')),
        'signature': ('int combine(int left, const char* right){return left;}\nvoid f(){combine(1,', 'signature', None),
        'overload': ('void solve(int left, int right); void solve(double value, double scale);\nvoid f(){solve(1,', 'signature', None),
        'overload_arity': ('void solve(int left, int right); void solve(double value);\nvoid f(){solve(1,', 'signature', None),
        'unsaved_header': ('#include "model.hpp"\nvoid f(){Token item; item.ru', 'complete', None),
        'scope': ('void hidden(){int secret_local=1;}\nvoid f(){sec', 'complete', None),
        'unknown': ('void f(){unknown_object.', 'complete', None),
        'prefix': ('void f(){int count=1; cou', 'complete', None),
    }
    server = Server(executable, project, flags)
    results = {}; started = time.monotonic()
    try:
        for tag, (code, mode, caret) in definitions.items():
            path = project / (tag + '.cpp'); caret = units(code) if caret is None else caret
            if tag == 'unsaved_header': server.open(project / 'model.hpp', unsaved_header)
            path.write_text('// old saved content\n')
            server.open(path, code)
            before = time.monotonic()
            response = server.request('textDocument/completion' if mode == 'complete' else 'textDocument/signatureHelp',
                dict(textDocument=dict(uri=path.as_uri()), position=position(code, caret)))
            duration = time.monotonic() - before
            if tag in ('dot', 'arrow', 'middle', 'unsaved_header'): assert 'run' in names(response), (tag, response)
            if tag == 'namespace': assert 'solve' in names(response), response
            if tag in ('vector', 'unicode_crlf'): assert 'push_back' in names(response), response
            if tag in ('scope', 'unknown'): assert 'secret_local' not in names(response) and 'run' not in names(response), response
            if tag == 'unknown': assert not items(response), response
            if tag == 'prefix': assert 'count' in names(response), response
            if mode == 'signature':
                signatures = response['result']['signatures']; assert signatures, response
                assert response['result']['activeParameter'] == 1, response
                if tag == 'overload': assert len(signatures) == 2, response
                if tag == 'overload_arity': assert len(signatures) == 1, response
            assert path.read_text() == '// old saved content\n'
            assert (project / 'model.hpp').read_text() == disk_header
            results[tag] = dict(code=code, mode=mode, caret=caret, position=position(code, caret), response=response,
                                host_request_ms=round(duration * 1000, 3), path=path.name,
                                unsaved_header=unsaved_header if tag == 'unsaved_header' else None)
            server.notify('textDocument/didClose', dict(textDocument=dict(uri=path.as_uri())))
            print(tag + ': PASS (' + str(round(duration * 1000)) + ' ms)', flush=True)
        refresh_path = project / 'header_refresh.cpp'
        refresh_code = '#include "model.hpp"\nvoid f(){Token item; item.'
        refresh_path.write_text('// saved refresh fixture\n')
        server.open(refresh_path, refresh_code)
        for version, header, expected, forbidden in [(2, 'struct Token { void save(); };\n', 'save', {'run', 'old_disk'}), (3, None, 'old_disk', {'run', 'save'})]:
            if header is None:
                server.notify('textDocument/didClose', dict(textDocument=dict(uri=(project / 'model.hpp').as_uri())))
                # Closing alone can retain the preceding header preamble. The production client
                # must restart its bounded process when the other-file snapshot generation changes.
                server.close()
                (PHASE / 'host-before-context-restart.log').write_bytes((PHASE / 'host-clangd-stderr.log').read_bytes())
                server = Server(executable, project, flags)
                server.open(refresh_path, refresh_code)
            else:
                server.notify('textDocument/didChange', dict(textDocument=dict(uri=(project / 'model.hpp').as_uri(), version=version), contentChanges=[dict(text=header)]))
            server.notify('textDocument/didChange', dict(textDocument=dict(uri=refresh_path.as_uri(), version=version), contentChanges=[dict(text=refresh_code)]))
            response = server.request('textDocument/completion', dict(textDocument=dict(uri=refresh_path.as_uri()), position=position(refresh_code, units(refresh_code))))
            found = names(response)
            assert expected in found and not forbidden.intersection(found), response
            assert (project / 'model.hpp').read_text() == disk_header
            assert refresh_path.read_text() == '// saved refresh fixture\n'
            tag = 'updated_header' if header else 'closed_header'
            results[tag] = dict(code=refresh_code, mode='complete', caret=units(refresh_code), position=position(refresh_code, units(refresh_code)), response=response, path=refresh_path.name, unsaved_header=header)
            print(tag + ': PASS', flush=True)
        server.notify('textDocument/didClose', dict(textDocument=dict(uri=refresh_path.as_uri())))
        result = dict(engine='clangd 21.1.8', real_backend=True, android_execution=False, actual_android_headers=True,
                      host_archive_sha256='aeb5a68414c4d10b0867a479c9f21593043cc6d122e39de61b3801c89ebd57fc',
                      initialize=server.initialize, flags=flags, cases=results, tests=len(results), context_generation_restart=True,
                      elapsed_seconds=round(time.monotonic() - started, 3))
        (PHASE / 'host-semantic-fixtures.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    finally:
        server.close()
        (PHASE / 'host-notifications.json').write_text(json.dumps(server.notifications, ensure_ascii=False, indent=2), encoding='utf-8')
    assert b'MUST_NOT_LOAD' not in server.errors
    print('Real host LSP passed; this is NOT Android execution.', flush=True)


if __name__ == '__main__': main()
