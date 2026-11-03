"""Build-time official-source selection and pinned download contracts; no Android execution."""
import hashlib
import io
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import prepare_cpp_runtime as runtime

class CppRuntimeDownloadTests(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.cache=Path(self.temporary.name)/'cache'
        self.cache_patch=patch.object(runtime,'CACHE',self.cache);self.cache_patch.start();self.addCleanup(self.cache_patch.stop)
        self.payload=b'fixed package bytes\x00\xff';self.digest=hashlib.sha256(self.payload).hexdigest()
        self.url='https://packages.termux.dev/apt/termux-main/pool/main/c/clang/clang.deb'
        self.lock=dict(repository='https://packages-cf.termux.dev/apt/termux-main')
    def package_url(self,*args):
        self.assertTrue(hasattr(runtime,'package_url'),'Explicit official repository selection is missing')
        return runtime.package_url(*args)
    def test_default_source_keeps_the_existing_lock_repository(self):
        self.assertEqual(self.lock['repository']+'/pool/package.deb',self.package_url(self.lock,'pool/package.deb'))
    def test_primary_source_changes_only_the_download_origin(self):
        before=dict(self.lock)
        self.assertEqual('https://packages.termux.dev/apt/termux-main/pool/package.deb',self.package_url(self.lock,'pool/package.deb','https://packages.termux.dev/apt/termux-main'))
        self.assertEqual(before,self.lock)
    def test_unknown_or_insecure_override_is_rejected(self):
        for source in ('http://packages.termux.dev/apt/termux-main','https://example.invalid/','https://packages.termux.dev/apt/termux-main?x=1'):
            with self.subTest(source=source),self.assertRaises(ValueError):self.package_url(self.lock,'pool/package.deb',source)
    def test_valid_cache_stays_offline_even_with_fetch_enabled(self):
        target=self.cache/'downloads/package.deb';target.parent.mkdir(parents=True);target.write_bytes(self.payload)
        with patch.object(runtime.urllib.request,'urlopen',side_effect=AssertionError('Unexpected network')):
            self.assertEqual(target,runtime.obtain('package.deb',self.digest,self.url,True))
    def test_existing_fallback_is_verified_without_network(self):
        fallback=Path(self.temporary.name)/'old-package.deb';fallback.write_bytes(self.payload)
        with patch.object(runtime.urllib.request,'urlopen',side_effect=AssertionError('Unexpected network')):
            self.assertEqual(fallback,runtime.obtain('package.deb',self.digest,self.url,False,[fallback]))
    def test_corrupt_cache_is_never_accepted_or_silently_replaced(self):
        target=self.cache/'downloads/package.deb';target.parent.mkdir(parents=True);target.write_bytes(b'wrong')
        with patch.object(runtime.urllib.request,'urlopen',side_effect=AssertionError('Unexpected network')),self.assertRaises(AssertionError):
            runtime.obtain('package.deb',self.digest,self.url,True)
        self.assertEqual(b'wrong',target.read_bytes())
    def test_missing_cache_without_fetch_does_not_contact_a_source(self):
        with patch.object(runtime.urllib.request,'urlopen',side_effect=AssertionError('Unexpected network')),self.assertRaises(FileNotFoundError):
            runtime.obtain('package.deb',self.digest,self.url,False)
    def test_download_is_promoted_only_after_the_original_digest_matches(self):
        with patch.object(runtime.urllib.request,'urlopen',return_value=io.BytesIO(self.payload)) as request:
            target=runtime.obtain('package.deb',self.digest,self.url,True)
        request.assert_called_once_with(self.url,timeout=60)
        self.assertEqual(self.payload,target.read_bytes());self.assertFalse(target.with_name(target.name+'.partial').exists())
    def test_wrong_download_digest_cannot_produce_a_valid_cached_package(self):
        with patch.object(runtime.urllib.request,'urlopen',return_value=io.BytesIO(b'wrong')),self.assertRaises(AssertionError):
            runtime.obtain('package.deb',self.digest,self.url,True)
        self.assertFalse((self.cache/'downloads/package.deb').exists())

if __name__=='__main__':unittest.main(verbosity=2)
