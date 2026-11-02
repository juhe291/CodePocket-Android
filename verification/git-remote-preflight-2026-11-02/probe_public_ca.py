"""Read one public Git advertisement with the exact CA bundle from the delivered APK."""
import hashlib
import json
from pathlib import Path
import ssl
import sys

from probe_https import inspect_runtime_ca, urllib3

PHASE=Path(__file__).resolve().parent
URL='https://github.com/octocat/Hello-World.git/info/refs?service=git-upload-pack'
ca=inspect_runtime_ca()
pool=urllib3.PoolManager(cert_reqs=ssl.CERT_REQUIRED,ca_certs=ca['path'],retries=False,
    timeout=urllib3.Timeout(connect=10,read=15,total=30),ssl_minimum_version=ssl.TLSVersion.TLSv1_2)
try:
    response=pool.request('GET',URL,headers={'Git-Protocol':'version=1','User-Agent':'CodePocket-Git-preflight'},
        preload_content=False,redirect=False,retries=False)
    try:
        assert response.status==200,response.status
        assert response.headers.get('Content-Type')=='application/x-git-upload-pack-advertisement'
        data=response.read(2_000_001);assert 0<len(data)<=2_000_000
        assert data.startswith(b'001e# service=git-upload-pack\n0000')
        (PHASE/'public-refs-advertisement.bin').write_bytes(data)
        result=dict(url=URL,http_status=response.status,content_type=response.headers.get('Content-Type'),
            bytes=len(data),sha256=hashlib.sha256(data).hexdigest(),runtime_ca=ca,credentials_sent=False,
            redirect_followed=False,read_only=True,android_execution=False)
        (PHASE/'public-ca-result.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
        print(json.dumps(result,indent=2))
    finally:response.close()
finally:pool.clear()
