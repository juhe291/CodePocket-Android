"""Bounded HTTPS transport for private Git snapshots. Never reads credential helpers.

Repository publication, push confirmation/receipts and encrypted credential storage
remain the Android caller's responsibility. TLS overrides are Python test seams,
not request JSON fields, and must still enforce certificate and hostname checks.
"""
import base64
import ipaddress
import re
import ssl
import time
from urllib.parse import quote, unquote, urlsplit, urlunsplit

import urllib3
from dulwich.client import Urllib3HttpGitClient
from dulwich.config import ConfigFile


class RemoteFailure(Exception):
    def __init__(self, code):
        self.code=code
        super().__init__(code)


def require(value, code):
    if not value:raise RemoteFailure(code)


def canonical_url(value):
    try:
        require(isinstance(value,str) and 0<len(value)<=2048,'url_invalid')
        require(not any(ord(c)<=32 or ord(c)==127 for c in value) and '\\' not in value,'url_invalid')
        require('?' not in value and '#' not in value and not re.search(r'%(?![0-9A-Fa-f]{2})',value),'url_invalid')
        parsed=urlsplit(value)
        require(parsed.scheme.lower()=='https' and parsed.username is None and parsed.password is None,'url_invalid')
        host=parsed.hostname;require(host,'url_invalid')
        if ':' in host:host='['+ipaddress.IPv6Address(host).compressed+']'
        else:
            host=host.encode('idna').decode('ascii').lower().rstrip('.')
            require(host and re.fullmatch(r'[a-z0-9.-]+',host),'url_invalid')
        port=parsed.port;require(port is None or 1<=port<=65535,'url_invalid')
        authority=host+((':'+str(port)) if port is not None and port!=443 else '')
        path=parsed.path.rstrip('/')
        require(not path or path.startswith('/'),'url_invalid')
        parts=[]
        for part in path.split('/')[1:]:
            decoded=unquote(part,encoding='utf-8',errors='strict')
            require(decoded and decoded not in ('.','..') and not any(ord(c)<=32 or ord(c)==127 or c in '/\\' for c in decoded),'url_invalid')
            parts.append(quote(decoded,safe='-._~'))
        return urlunsplit(('https',authority,'/'+('/'.join(parts)) if parts else '','',''))
    except RemoteFailure:raise
    except (ValueError,UnicodeError):raise RemoteFailure('url_invalid') from None


class NetworkBudget:
    def __init__(self,seconds=120,max_bytes=128_000_000,max_requests=32,cancelled=lambda:False,clock=time.monotonic):
        require(0<seconds<=120 and 0<max_bytes<=128_000_000 and 0<max_requests<=32,'limit')
        self.clock=clock;self.deadline=clock()+seconds;self.cancelled=cancelled
        self.maximum=max_bytes;self.bytes=0;self.max_requests=max_requests;self.requests=0
    def check(self):
        require(not self.cancelled(),'cancelled')
        require(self.clock()<self.deadline,'timeout')
    def remaining(self):
        self.check();return self.deadline-self.clock()
    def charge(self,size):
        self.check();self.bytes+=size;require(self.bytes<=self.maximum,'transfer_limit')
    def request(self):
        self.check();self.requests+=1;require(self.requests<=self.max_requests,'request_limit')


class _Pool(urllib3.PoolManager):
    def __init__(self,url,authorization,context,budget):
        self.target=urlsplit(url);self.authorization=authorization;self.budget=budget
        self.responses=[];self.write_started=False
        super().__init__(ssl_context=context,cert_reqs=ssl.CERT_REQUIRED,retries=False,
            maxsize=1,block=True,headers={'User-Agent':'CodePocket-Git'})
    @staticmethod
    def origin(parsed):return parsed.scheme.lower(),parsed.hostname,parsed.port or 443
    def scope(self,method,url):
        try:
            parsed=urlsplit(url)
            require(parsed.username is None and parsed.password is None and not parsed.fragment,'url_scope')
            require(self.origin(parsed)==self.origin(self.target),'url_scope')
            base=self.target.path.rstrip('/')
            if method=='GET':
                require(parsed.path==base+'/info/refs' and parsed.query in ('service=git-upload-pack','service=git-receive-pack'),'url_scope')
            elif method=='POST':
                require(parsed.path in (base+'/git-upload-pack',base+'/git-receive-pack') and not parsed.query,'url_scope')
            else:raise RemoteFailure('url_scope')
            return parsed
        except RemoteFailure:raise
        except (ValueError,UnicodeError):raise RemoteFailure('url_scope') from None
    def request(self,method,url,**options):
        parsed=self.scope(method,url);self.budget.request()
        headers={k:v for k,v in options.get('headers',{}).items() if k.lower() not in ('authorization','proxy-authorization','cookie')}
        if self.authorization is not None:headers['Authorization']=self.authorization
        options['headers']=headers
        body=options.get('body')
        if body is not None:
            if not isinstance(body,bytes):
                chunks=[];length=0
                for chunk in body:
                    self.budget.check();require(isinstance(chunk,bytes),'protocol')
                    length+=len(chunk);require(length<=min(64_000_000,self.budget.maximum-self.budget.bytes),'transfer_limit')
                    chunks.append(chunk)
                body=b''.join(chunks)
            self.budget.charge(len(body));options['body']=body;headers['Content-Length']=str(len(body))
        remaining=self.budget.remaining()
        options.update(redirect=False,retries=False,pool_timeout=min(10,remaining),
            timeout=urllib3.Timeout(connect=min(10,remaining),read=min(10,remaining),total=remaining))
        # This is intentionally conservative: after a receive-pack request begins,
        # cancellation cannot promise that the remote ref was never updated.
        if method=='POST' and parsed.path.endswith('/git-receive-pack'):self.write_started=True
        try:response=super().request(method,url,**options)
        except urllib3.exceptions.SSLError:raise RemoteFailure('tls') from None
        except urllib3.exceptions.TimeoutError:raise RemoteFailure('timeout') from None
        except urllib3.exceptions.MaxRetryError as error:
            raise RemoteFailure('tls' if isinstance(error.reason,urllib3.exceptions.SSLError) else 'network') from None
        except urllib3.exceptions.HTTPError:raise RemoteFailure('network') from None
        raw_close=response.close
        def close():
            try:raw_close()
            finally:response.release_conn()
        response.close=close;self.responses.append(response)
        if response.status!=200:
            close()
            raise RemoteFailure('authentication' if response.status in (401,403) else 'remote_not_found' if response.status==404
                else 'redirect' if 300<=response.status<400 else 'remote_http')
        raw=response.read
        def read(amount=None,*args,**kwargs):
            self.budget.check()
            limit=self.budget.maximum-self.budget.bytes+1
            amount=limit if amount is None or amount<0 else min(amount,limit)
            try:data=raw(amount,*args,**kwargs)
            except urllib3.exceptions.SSLError:raise RemoteFailure('tls') from None
            except urllib3.exceptions.TimeoutError:raise RemoteFailure('timeout') from None
            except urllib3.exceptions.HTTPError:raise RemoteFailure('network') from None
            self.budget.charge(len(data));return data
        response.read=read;return response
    def close(self):
        try:
            for response in self.responses:response.close()
        finally:
            self.responses.clear();self.authorization=None;self.clear()


class GitHttps:
    def __init__(self,url,username=None,token=None,*,_ssl_context=None,_budget=None):
        self.url=canonical_url(url)
        if username is None and token is None:authorization=None
        else:
            require(isinstance(username,str) and 0<len(username)<=128 and ':' not in username and
                not any(ord(c)<=32 or ord(c)==127 for c in username),'credentials')
            require(isinstance(token,str) and 0<len(token)<=4096 and
                not any(ord(c)<=32 or ord(c)==127 for c in token),'credentials')
            authorization='Basic '+base64.b64encode((username+':'+token).encode('utf-8')).decode('ascii')
        context=_ssl_context or ssl.create_default_context()
        require(context.verify_mode==ssl.CERT_REQUIRED and context.check_hostname,'tls_policy')
        self.pool=_Pool(self.url,authorization,context,_budget or NetworkBudget())
        # Auth belongs to the scoped pool, so Dulwich get_url/config cannot embed it.
        self.client=Urllib3HttpGitClient(self.url,pool_manager=self.pool,config=ConfigFile(),
            dumb=False,quiet=True,include_tags=False)
    def close(self):self.pool.close()
    def __enter__(self):return self
    def __exit__(self,*args):self.close()
