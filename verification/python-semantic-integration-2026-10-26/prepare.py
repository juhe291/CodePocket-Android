from pathlib import Path
import hashlib,json,zipfile
root=Path.cwd();phase=root/'verification/python-semantic-integration-2026-10-26'
old=root/'verification/workbench-details-batch-2026-10-26'
for name in ('targeted.ps1','full-build.ps1','verify-artifact.ps1','host-tests.init.gradle'):
    path=phase/name
    if path.exists():continue
    text=(old/name).read_text(encoding='utf-8-sig')
    text=text.replace('workbench-details-batch-2026-10-26','python-semantic-integration-2026-10-26').replace('details-batch-tmp-2026-10-26','semantics-tmp')
    text=text.replace('"-Pcodepocket.buildPython=$taskRepository/.tools/python313-full/tools/python.exe"',
        '"-Pcodepocket.buildPython=$taskRepository/.tools/python313-full/tools/python.exe" "-Pcodepocket.semanticWheels=$taskRepository/.tools/semantic-wheels"')
    if name=='targeted.ps1':
        start=text.index('--tests ');end=text.index(' --offline',start)
        text=text[:start]+'--tests *SemanticProtocolTest* --tests *PythonSemanticIntegrationTest* --tests *PythonAnalysisServiceTest* --tests *CompletionInteractionTest* --tests *AssistSurfaceIntegrationTest* --tests *MainSurfaceIntegrationTest*'+text[end:]
    if name=='verify-artifact.ps1':
        text=text.replace("$env:JAVA_HOME=", "$env:PYTHONPATH=Join-Path $taskRepository '.tools/semantic-probe/site'\n$env:JAVA_HOME=",1)
        marker="& './.tools/android-sdk/build-tools/35.0.0/apksigner.bat'"
        at=text.index(marker)
        text=text[:at]+'& $taskPython -B scripts/verify_semantics_apk.py app/build/outputs/apk/debug/app-debug.apk --output "$taskEvidence/semantic-apk-inspection.json" *> "$taskEvidence/semantic-apk.log"\nif($LASTEXITCODE -ne 0){throw "Packaged semantic tests failed"}\n'+text[at:]
    path.write_text(text,encoding='utf-8')
before=phase/'before-apks.json'
if not before.exists():before.write_text(json.dumps([dict(name=p.name,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in sorted((root/'dist').glob('*.apk'))],indent=2)+'\n',encoding='utf-8')
licenses=root/'app/src/main/assets/semantic-licenses';licenses.mkdir(exist_ok=True)
for package,label in [('jedi','JEDI'),('parso','PARSO')]:
    wheel=next((root/'.tools/semantic-wheels').glob(package+'-*.whl'))
    with zipfile.ZipFile(wheel) as archive:
        name=next(n for n in archive.namelist() if '.dist-info/' in n and (n.endswith('/LICENSE') or n.endswith('/LICENSE.txt')))
        (licenses/(label+'-LICENSE.txt')).write_bytes(archive.read(name))
        if package=='jedi':
            for source,dest in [('jedi/third_party/typeshed/LICENSE','TYPESHED-LICENSE.txt'),('jedi/third_party/django-stubs/LICENSE.txt','DJANGO-STUBS-LICENSE.txt')]:
                (licenses/dest).write_bytes(archive.read(source))
print('Prepared offline batch scripts, prior APK hashes and exact wheel licenses')
