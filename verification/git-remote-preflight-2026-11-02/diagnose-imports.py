"""Bounded diagnostic for the live probe's import phase; no Git operations."""
import faulthandler
from pathlib import Path
import sys
faulthandler.dump_traceback_later(10,repeat=False,exit=True)
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'.tools/git-probe/site'))
for name in ('ssl','dulwich.client','dulwich.config','dulwich.graph','dulwich.repo','dulwich.server','dulwich.web'):
    print('IMPORT',name,flush=True)
    __import__(name)
    print('READY',name,flush=True)
faulthandler.cancel_dump_traceback_later()
