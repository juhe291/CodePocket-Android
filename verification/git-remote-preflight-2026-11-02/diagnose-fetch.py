"""Bound one isolated fetch to expose a blocked fixture stack, without restarting the live probe."""
import faulthandler
import unittest
from probe_https import HttpsProbe
faulthandler.dump_traceback_later(10,exit=True)
unittest.TextTestRunner(verbosity=2).run(unittest.TestSuite([HttpsProbe('test_01_fetches_real_pack_and_symbolic_head_over_verified_tls')]))
faulthandler.cancel_dump_traceback_later()
