"""Save exact JUnit identities and current inputs at a completed local checkpoint."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
PHASE=Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('name');args=parser.parse_args()
assert args.name.replace('-','').isalnum()
target=PHASE/(args.name+'-junit');target.mkdir(exist_ok=True)
identities=[];totals=dict(tests=0,failures=0,errors=0,skipped=0)
for source in sorted((ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
    shutil.copy2(source,target/source.name);suite=ET.parse(source).getroot()
    for key in totals:totals[key]+=int(suite.attrib.get(key,0))
    for case in suite.findall('testcase'):
        identities.append(dict(classname=case.attrib['classname'],name=case.attrib['name'],
            failed=case.find('failure') is not None,skipped=case.find('skipped') is not None))
assert totals['tests']==len(identities)
(PHASE/(args.name+'-identities.json')).write_text(json.dumps(dict(totals=totals,cases=identities),indent=2)+'\n')
hashes={}
for directory in ['app/src/main/java','app/src/main/jni','app/src/main/res','app/src/test/java']:
    for source in sorted((ROOT/directory).rglob('*')):
        if source.is_file():hashes[source.relative_to(ROOT).as_posix()]=hashlib.sha256(source.read_bytes()).hexdigest()
for name in ['app/src/main/AndroidManifest.xml','app/build.gradle.kts','app/cpp-runtime-lock.json','scripts/prepare_cpp_runtime.py']:
    hashes[name]=hashlib.sha256((ROOT/name).read_bytes()).hexdigest()
(PHASE/(args.name+'-source-hashes.json')).write_text(json.dumps(hashes,indent=2)+'\n')
print(json.dumps(totals))
