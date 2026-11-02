"""Archive only JUnit results produced by this phase and the exact current inputs."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2];OUT=Path(__file__).parent
phase=sys.argv[1];assert phase in ('first','engine')
log=OUT/('native-plan-host.log' if phase=='first' else 'native-plan-engine.log')
assert 'BUILD SUCCESSFUL' in log.read_text(encoding='utf-8',errors='replace')
folder=OUT/('native-'+phase+'-junit');folder.mkdir()
classes=['GitRemotePlanTest','GitWorkspaceStoreTest','SafGitBinaryIntegrationTest']
stats={};identities=[]
for name in classes:
    source=ROOT/('app/build/test-results/testDebugUnitTest/TEST-com.codepocket.ide.'+name+'.xml')
    tree=ET.parse(source).getroot();assert tree.get('name')=='com.codepocket.ide.'+name
    stats[name]={k:int(tree.get(k)) for k in ('tests','failures','errors','skipped')}
    assert not any(stats[name][k] for k in ('failures','errors','skipped'))
    identities.extend(case.get('classname')+'.'+case.get('name') for case in tree.findall('testcase'))
    shutil.copyfile(source,folder/source.name)
assert stats['GitRemotePlanTest']['tests']==(17 if phase=='first' else 18)
assert stats['GitWorkspaceStoreTest']['tests']==19 and stats['SafGitBinaryIntegrationTest']['tests']==3
sources={}
for base in ('app/src','workspace-core/src','tests'):
    for path in sorted((ROOT/base).rglob('*')):
        if path.is_file() and path.suffix in ('.kt','.java','.py','.xml'):
            sources[path.relative_to(ROOT).as_posix()]=hashlib.sha256(path.read_bytes()).hexdigest()
(OUT/('native-'+phase+'-sources.json')).write_text(json.dumps(sources,indent=2)+'\n',encoding='utf-8')
(OUT/('native-'+phase+'-results.json')).write_text(json.dumps(dict(stats=stats,identities=sorted(identities),
    tests=len(identities),new_apk_built=False,android_execution=False),indent=2)+'\n',encoding='utf-8')
print(json.dumps(dict(phase=phase,stats=stats,tests=len(identities))))
