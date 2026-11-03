"""Execute the two new regressions against the preserved pre-fix production module."""
import importlib.util
from pathlib import Path
import sys
import unittest

ROOT=Path(__file__).resolve().parents[2]
sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'app/src/main/python'),str(ROOT/'.tools/git-probe/site')]
source=Path(__file__).with_name('git_remote-before-report-retention.py')
spec=importlib.util.spec_from_file_location('git_remote',source)
module=importlib.util.module_from_spec(spec);sys.modules['git_remote']=module;spec.loader.exec_module(module)
names=['test_git_remote.GitRemoteTests.test_confirmed_push_does_not_depend_on_a_second_local_status_read',
    'test_git_remote.GitRemoteTests.test_confirmed_server_report_survives_a_transport_cleanup_error']
result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromNames(names))
assert result.testsRun==2 and len(result.failures)==2 and not result.errors,'Preserved baseline differs from the diagnosed failures'
sys.exit(1)
