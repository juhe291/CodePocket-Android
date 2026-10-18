"""Real child CPython + OS pipes, not substituted input/output or Android PTY claims."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"app/src/main/python"))
import runner

BOOTSTRAP = r'''import importlib.util,json,sys
from pathlib import Path
spec=importlib.util.spec_from_file_location("runner",sys.argv[1])
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
args=json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
r.TIME_LIMIT_SECONDS=args.get("limit",6)
events_path=Path(sys.argv[4])
class Events:
 def onState(self,state,sequence):
  with events_path.open("a",encoding="utf-8") as f:f.write(json.dumps([state,sequence])+"\n")
value=r.run_terminal(args["code"],args.get("input",""),str(Path(sys.argv[2]).parent/"main.py"),str(Path(sys.argv[2]).parent),0,1,2,args.get("interactive",True),Events())
Path(sys.argv[3]).write_text(value,encoding="utf-8")
'''

class ProcessRun:
    def __init__(self,code,**args):
        root=Path(__file__).resolve().parents[1]/".tools/test-tmp"
        root.mkdir(parents=True,exist_ok=True)
        self.temp=tempfile.TemporaryDirectory(dir=root)
        self.folder=Path(self.temp.name)
        bootstrap=self.folder/"child.py";bootstrap.write_text(BOOTSTRAP,encoding="utf-8")
        source=Path(runner.__file__)
        if hasattr(runner,"__packaged_bytecode__"):
            source=self.folder/"runner.pyc";source.write_bytes(runner.__packaged_bytecode__)
        request=self.folder/"request.json";request.write_text(json.dumps(dict(code=code,**args)),encoding="utf-8")
        self.result_path=self.folder/"result.json";self.events_path=self.folder/"events.jsonl"
        self.process=subprocess.Popen([sys.executable,"-u","-B",str(bootstrap),str(source),str(request),str(self.result_path),str(self.events_path)],
            stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,cwd=self.folder)
        self.condition=threading.Condition();self.output=[bytearray(),bytearray()]
        def read(pipe,index):
            while True:
                chunk=os.read(pipe.fileno(),4096)
                if not chunk:break
                with self.condition:self.output[index].extend(chunk);self.condition.notify_all()
        self.threads=[threading.Thread(target=read,args=(pipe,i),daemon=True) for i,pipe in enumerate((self.process.stdout,self.process.stderr))]
        for thread in self.threads:thread.start()
    def wait_text(self,text,index=0,timeout=8):
        target=text.encode("utf-8");end=time.monotonic()+timeout
        with self.condition:
            while target not in self.output[index]:
                left=end-time.monotonic()
                if left<=0:raise AssertionError(f"live output missing {text!r}: {self.output}")
                self.condition.wait(min(left,.1))
    def send(self,text):
        self.process.stdin.write(text.encode("utf-8"));self.process.stdin.flush()
    def finish(self):
        self.process.wait(timeout=12)
        for thread in self.threads:thread.join(timeout=2)
        if self.process.returncode:raise AssertionError(f"child failed {self.process.returncode}: {self.output}")
        return json.loads(self.result_path.read_text(encoding="utf-8"))
    def text(self,index=0):return bytes(self.output[index]).decode("utf-8",errors="replace").replace("\r\n","\n")
    def states(self):return [json.loads(line) for line in self.events_path.read_text(encoding="utf-8").splitlines()]
    def close(self):
        if self.process.poll() is None:self.process.kill();self.process.wait(timeout=5)
        for thread in self.threads:thread.join(timeout=2)
        for pipe in (self.process.stdin,self.process.stdout,self.process.stderr):
            if not pipe.closed:pipe.close()
        self.temp.cleanup()

class TestTerminalPython(unittest.TestCase):
    def launch(self,code,**args):
        value=ProcessRun(code,**args);self.addCleanup(value.close);return value
    def test_prompt_without_newline_then_multiple_inputs(self):
        p=self.launch('a=input("first> ")\nb=input("second> ")\nprint(a+":"+b)')
        p.wait_text("first> ");self.assertIsNone(p.process.poll());p.send("hello\n")
        p.wait_text("second> ");self.assertNotIn("hello:world",p.text());p.send("world\n")
        self.assertEqual(p.finish()["exit_code"],0);self.assertIn("hello:world",p.text())
        states=p.states();self.assertEqual([x[1] for x in states],list(range(1,len(states)+1)))
        self.assertEqual([x[0] for x in states],["running","waiting","running","waiting","running"])
    def test_stdout_is_live_before_program_finishes(self):
        p=self.launch('print("ready")\ninput()\nprint("done")');p.wait_text("ready")
        self.assertIsNone(p.process.poll());p.send("\n");p.finish();self.assertIn("done",p.text())
    def test_eof_is_real_closed_stdin(self):
        p=self.launch('import sys\nprint("read> ",end="")\nprint(repr(sys.stdin.read()))')
        p.wait_text("read> ");p.send("line\n");p.process.stdin.close()
        self.assertEqual(p.finish()["exit_code"],0);self.assertIn("'line\\n'",p.text())
    def test_input_eof_traceback_is_live(self):
        p=self.launch('input("eof> ")');p.wait_text("eof> ");p.process.stdin.close()
        result=p.finish();self.assertIn("EOFError",p.text(1));self.assertEqual(result["error_line"],1)
    def test_wait_does_not_consume_execution_budget(self):
        p=self.launch('print(input("wait> "))',limit=.2);p.wait_text("wait> ")
        threading.Event().wait(.45);p.send("after wait\n")
        self.assertEqual(p.finish()["exit_code"],0);self.assertIn("after wait",p.text())
    def test_cpu_loop_hits_real_trace_deadline(self):
        p=self.launch('while True: pass',limit=.15);result=p.finish()
        self.assertEqual(result["error_kind"],"timeout");self.assertIn("TimeoutError",p.text(1))
    def test_output_cap_is_enforced_on_live_stream(self):
        p=self.launch('for i in range(60000): print("x",end="")')
        result=p.finish();self.assertIn("50000",p.text(1));self.assertLessEqual(len(result["stdout"]),50000)
        self.assertLessEqual(len(p.text()),50000)
    def test_prefill_uses_existing_buffer_without_waiting(self):
        p=self.launch('print(input()+input())',input="a\nb\n",interactive=False)
        self.assertEqual(p.finish()["stdout"],"ab\n");self.assertNotIn("waiting",[x[0] for x in p.states()])
    def test_binary_input_and_output_share_real_descriptors(self):
        p=self.launch('import sys\nsys.stdout.buffer.write(b"binary> ")\na=sys.stdin.buffer.readline()\nsys.stdout.buffer.write(a)')
        p.wait_text("binary> ");p.send("bytes\n");p.finish();self.assertIn("bytes",p.text())
    def test_native_os_write_is_visible_on_same_stream(self):
        p=self.launch('import os,sys\nos.write(sys.stdout.fileno(),b"native-out")\nos.write(sys.stderr.fileno(),b"native-err")')
        p.finish();self.assertIn("native-out",p.text());self.assertIn("native-err",p.text(1))
    def test_unicode_and_ansi_are_not_removed(self):
        p=self.launch('print("\\x1b[32m你好\\x1b[0m")');p.finish();self.assertIn("\x1b[32m你好\x1b[0m",p.text())
    def test_real_traceback_and_line_metadata(self):
        p=self.launch('print("before")\nraise ValueError("broken")');result=p.finish()
        self.assertEqual(result["error_line"],2);self.assertIn("ValueError: broken",p.text(1));self.assertIn("before",p.text())
    def test_workspace_imports_still_work(self):
        p=self.launch('import pathlib\npathlib.Path("helper.py").write_text("answer=42")\nimport helper\nprint(helper.answer)')
        self.assertEqual(p.finish()["exit_code"],0);self.assertIn("42",p.text())
    def test_kill_while_waiting_then_fresh_run(self):
        p=self.launch('input("blocked> ")');p.wait_text("blocked> ")
        p.process.kill();p.process.wait(timeout=5);self.assertFalse(p.result_path.exists())
        other=self.launch('print("fresh")');self.assertEqual(other.finish()["exit_code"],0)
        self.assertNotIn("blocked",other.text())
    def test_duplicate_fds_keep_builtin_input_on_managed_readline(self):
        p=self.launch('import sys\nprint(sys.stdin.fileno()!=0,sys.stdout.fileno()!=1)\nprint(input("managed> "))')
        p.wait_text("managed> ");p.send("real\n");p.finish();self.assertIn("True True",p.text())
        self.assertIn("waiting",[x[0] for x in p.states()])
    def test_exit_status_is_preserved(self):
        p=self.launch('raise SystemExit(7)');self.assertEqual(p.finish()["exit_code"],7)

if __name__=="__main__":unittest.main()
