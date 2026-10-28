"""Actual host runner output for Main/Sora's diagnostic contract, not Android execution."""
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'app/src/main/python'))
from runner import run_code
entry,root,overlays=sys.argv[1:]
print(run_code(Path(entry).read_bytes().decode('utf-8-sig'),'',entry,root,overlays_path=overlays))
