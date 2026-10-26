"""Run real semantic tests with the APK's own backend and dependency bytecode on the host."""
import argparse, hashlib, importlib.util, importlib.abc, importlib.machinery, io, json, marshal
from pathlib import Path
import sys, tempfile, types, unittest, zipfile

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk',type=Path);parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();root=Path(__file__).resolve().parents[1]
    with tempfile.TemporaryDirectory(prefix='apk-semantics-',dir=root/'.tools') as directory:
        target=Path(directory)
        with zipfile.ZipFile(args.apk) as apk:
            with zipfile.ZipFile(io.BytesIO(apk.read('assets/chaquopy/app.imy'))) as archive:
                bytecode=archive.read('semantics.pyc')
            for asset in apk.namelist():
                if asset.startswith('assets/chaquopy/requirements-') and asset.endswith('.imy'):
                    with zipfile.ZipFile(io.BytesIO(apk.read(asset))) as requirements:
                        for member in requirements.infolist():
                            destination=(target/member.filename).resolve()
                            assert destination.is_relative_to(target.resolve()),member.filename
                        requirements.extractall(target)
            for license in ('JEDI-LICENSE.txt','PARSO-LICENSE.txt','TYPESHED-LICENSE.txt','DJANGO-STUBS-LICENSE.txt'):
                assert len(apk.read('assets/semantic-licenses/'+license))>500
            config=json.loads(apk.read('assets/chaquopy/build.json'))
            assert {'jedi','parso'}<=set(config['extract_packages'])
        assert bytecode[:4]==importlib.util.MAGIC_NUMBER
        module=types.ModuleType('semantics');module.__file__=str(args.apk)+'!semantics.pyc'
        exec(marshal.loads(bytecode[16:]),module.__dict__);sys.modules['semantics']=module
        sys.path.insert(0,str(target));sys.path.insert(0,str(root/'tests'))
        class ActualBytecodeFinder(importlib.abc.MetaPathFinder):
            def find_spec(self,fullname,path=None,target_module=None):
                if fullname.split('.')[0] not in ('jedi','parso'):return None
                base=target.joinpath(*fullname.split('.'))
                package=base/'__init__.pyc';module=base.with_suffix('.pyc')
                file=package if package.exists() else module
                if not file.exists():return None
                loader=importlib.machinery.SourcelessFileLoader(fullname,str(file))
                return importlib.util.spec_from_file_location(fullname,file,loader=loader,
                    submodule_search_locations=[str(base)] if file==package else None)
        sys.meta_path.insert(0,ActualBytecodeFinder())
        import jedi,parso,test_semantics
        assert Path(jedi.__file__).resolve().is_relative_to(target.resolve())
        assert Path(parso.__file__).resolve().is_relative_to(target.resolve())
        assert jedi.__version__=='0.20.1' and parso.__version__=='0.8.7'
        assert isinstance(jedi.__loader__,importlib.machinery.SourcelessFileLoader)
        assert isinstance(parso.__loader__,importlib.machinery.SourcelessFileLoader)
        assert (target/'jedi/third_party/typeshed/stdlib/builtins.pyi').exists()
        assert (target/'parso/python/grammar313.txt').exists()
        assert test_semantics.semantics is module
        result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromModule(test_semantics))
        report=dict(apk_sha256=hashlib.sha256(args.apk.read_bytes()).hexdigest(),backend_bytecode_sha256=hashlib.sha256(bytecode).hexdigest(),
            tests=result.testsRun,failures=len(result.failures),errors=len(result.errors),
            jedi=jedi.__version__,parso=parso.__version__,dependencies_from_actual_apk=True,
            dependency_bytecode_loaded=True,filesystem_packages_verified=True,device_execution=False)
        args.output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(report,indent=2));return not result.wasSuccessful()

if __name__=='__main__':sys.exit(main())
