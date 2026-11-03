"""Stage 0: bounded host reproduction of the Python native-call timeout limitation."""
import json
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
child = """import sys, json, time
sys.path.insert(0, 'app/src/main/python')
import runner
runner.TIME_LIMIT_SECONDS = 0.04
started = time.monotonic()
result = json.loads(runner.run_code('import time\\ntime.sleep(0.25)\\nprint(42)', ''))
print(json.dumps({'configured_limit_ms': 40, 'wall_ms': round((time.monotonic()-started)*1000), 'result': result}, ensure_ascii=False))
"""
completed = subprocess.run([sys.executable, '-B', '-c', child], cwd=root,
                           capture_output=True, text=True, encoding='utf-8', timeout=3, check=True)
report = json.loads(completed.stdout)
assert report['wall_ms'] >= 200, report
assert report['result']['error_kind'] == 'timeout', report
report['conclusion'] = 'Native sleep is not interrupted at the configured bytecode deadline.'
report['device_execution'] = False
print(json.dumps(report, indent=2, ensure_ascii=False))
