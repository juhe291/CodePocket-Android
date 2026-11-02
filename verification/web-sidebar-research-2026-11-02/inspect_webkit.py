"""Inspect an existing official WebKit AAR without changing the app or running a build."""
import hashlib
import io
import json
from pathlib import Path
import sys
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2];PHASE=Path(__file__).resolve().parent
CACHE=(ROOT/'.tools/web-sidebar-probe').resolve();assert CACHE.is_relative_to(ROOT.resolve());CACHE.mkdir(exist_ok=True)
VERSION='1.18.0'
OFFICIAL=f'https://dl.google.com/dl/android/maven2/androidx/webkit/webkit/{VERSION}/webkit-{VERSION}'
BASE=(f'https://maven.aliyun.com/repository/google/androidx/webkit/webkit/{VERSION}/webkit-{VERSION}'
    if '--use-existing-mirror' in sys.argv else OFFICIAL)
files={}
for suffix in ('.pom','.aar'):
    request=urllib.request.Request(BASE+suffix,headers={'User-Agent':'CodePocket-dependency-inspection'})
    with urllib.request.urlopen(request,timeout=20) as response:
        assert response.status==200
        data=response.read(20_000_001);assert len(data)<=20_000_000
    path=CACHE/f'webkit-{VERSION}{suffix}';assert not path.exists() or path.read_bytes()==data
    if not path.exists():path.write_bytes(data)
    files[suffix]=dict(url=BASE+suffix,bytes=len(data),sha256=hashlib.sha256(data).hexdigest())
pom=ET.fromstring((CACHE/f'webkit-{VERSION}.pom').read_bytes());ns={'m':'http://maven.apache.org/POM/4.0.0'}
dependencies=[{key:item.findtext('m:'+key,namespaces=ns) for key in ('groupId','artifactId','version','scope')}
    for item in pom.findall('m:dependencies/m:dependency',ns)]
with zipfile.ZipFile(CACHE/f'webkit-{VERSION}.aar') as archive:
    assert archive.testzip() is None
    metadata=archive.read('META-INF/com/android/build/gradle/aar-metadata.properties').decode()
    props=dict(line.split('=',1) for line in metadata.splitlines() if '=' in line and not line.startswith('#'))
    manifest=archive.read('AndroidManifest.xml');text_manifest=manifest.decode('utf-8') if manifest.lstrip().startswith(b'<') else None
    native=[name for name in archive.namelist() if name.endswith(('.so','.dll','.exe'))]
    with zipfile.ZipFile(io.BytesIO(archive.read('classes.jar'))) as classes:
        names=classes.namelist()
        assert 'androidx/webkit/WebViewCompat.class' in names and 'androidx/webkit/WebViewAssetLoader.class' in names
        content_classes=len([name for name in names if name.endswith('.class')])
report=dict(version=VERSION,official_url=OFFICIAL,existing_local_mirror_used=BASE!=OFFICIAL,
    files=files,aar_metadata=props,manifest=text_manifest,dependencies=dependencies,
    native_payloads=native,classes=content_classes,
    compile_sdk_35_compatible=int(props.get('minCompileSdk','1'))<=35,
    runtime_or_build_verified=False,production_dependency_added=False,new_apk_built=False)
(PHASE/'webkit-aar-inspection.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,indent=2))
