"""Remote Git on an owned private snapshot, never directly on Android/SAF originals.

The Android caller publishes validated successful results with its own journal,
protects unsaved buffers, and durably tracks push intent before making the call.
"""
import json
import hashlib
import os
from pathlib import Path
import re
import stat
import time

from dulwich.config import ConfigFile
from dulwich.diff_tree import tree_changes
from dulwich.graph import can_fast_forward
from dulwich.index import update_working_tree, ConflictedIndexEntry, FLAG_VALID, EXTENDED_FLAG_SKIP_WORKTREE, EXTENDED_FLAG_INTEND_TO_ADD
from dulwich.object_store import iter_tree_contents
from dulwich.objects import Blob, Commit
from dulwich.refs import check_ref_format

import git_workspace as local
from git_https import GitHttps, NetworkBudget, RemoteFailure, canonical_url, require

OPERATIONS={'remote_info','remote_refs','configure','clone','fetch','fast_forward','push'}
WRITES={'configure','clone','fetch','fast_forward','push'}
FIELDS={'action','expected','url','branch','expected_head','expected_remote','username','token','modeAvailable'}
IDENTITY=b'CodePocket <local@codepocket.invalid>'
PUBLICATIONS={'configure','clone','fetch','fast_forward'}
MAX_PLAN=8_000_000


def branch_name(value):
    require(isinstance(value,str) and 0<len(value)<=255,'branch')
    local.path_name(value)
    require(check_ref_format(b'refs/heads/'+value.encode('utf-8')),'branch')
    return value


def object_id(value):
    require(isinstance(value,str) and re.fullmatch('[a-f0-9]{40}',value),'input')
    return value.encode('ascii')


def state(root,mode):
    result=json.loads(local.run(str(root),json.dumps(dict(action='status',modeAvailable=mode))))
    require(result.get('ok'),result.get('error','repository'))
    return result


def raw_config(root):return ConfigFile.from_path(str(root/'.git/config'),expand_includes=False)


def one(config,section,key):
    try:values=list(config.get_multivar(section,key))
    except KeyError:return None
    require(len(values)<=1,'configuration')
    return values[0] if values else None


def configured_url(root):
    value=one(raw_config(root),(b'remote',b'origin'),b'url')
    return canonical_url(value.decode('utf-8','strict')) if value is not None else None


def save_origin(root,url,branch,current_branch):
    config=raw_config(root)
    config.set((b'remote',b'origin'),b'url',url.encode('utf-8'))
    config.set((b'remote',b'origin'),b'fetch',b'+refs/heads/*:refs/remotes/origin/*')
    if current_branch!='HEAD':
        section=(b'branch',branch_name(current_branch).encode('utf-8'))
        config.set(section,b'remote',b'origin');config.set(section,b'merge',b'refs/heads/'+branch_name(branch).encode('utf-8'))
    config.write_to_path(str(root/'.git/config'))


def heads(result):
    require(getattr(result,'object_format','sha1') in (None,'sha1',b'sha1'),'object_format')
    require(len(result.refs)<=local.MAX_ENTRIES,'limit')
    selected={}
    for name,oid in result.refs.items():
        if not name.startswith(b'refs/heads/'):continue
        name=branch_name(name[len(b'refs/heads/'):].decode('utf-8','strict'))
        require(oid is None or re.fullmatch(b'[a-f0-9]{40}',oid),'protocol')
        selected[name]=oid
    symbolic=result.symrefs.get(b'HEAD')
    default=branch_name(symbolic[len(b'refs/heads/'):].decode('utf-8')) if symbolic and symbolic.startswith(b'refs/heads/') else None
    return selected,default


def validate_objects(repo,budget):
    total=0
    for count,oid in enumerate(repo.object_store,1):
        budget.check();require(count<=local.MAX_ENTRIES,'limit')
        obj=repo[oid];size=obj.raw_length();total+=size
        require(size<=local.MAX_FILE and total<=local.MAX_TOTAL,'limit')


def checkout_files(repo,oid,budget):
    require(isinstance(repo[oid],Commit),'object_type')
    files={};total=0;seen=set()
    for count,entry in enumerate(iter_tree_contents(repo.object_store,repo[oid].tree,include_trees=True)):
        budget.check();require(count<=local.MAX_ENTRIES,'limit')
        if entry.path==b'':continue
        path=local.path_name(entry.path.decode('utf-8','strict'))
        require(len(path.split('/'))<=32 and all(len(p)<=128 for p in path.split('/')),'path')
        require(all(p.rstrip('. ').casefold()!='.git' for p in path.split('/')),'path')
        if os.name=='nt':require(path.casefold() not in seen,'path')
        seen.add(path.casefold())
        if stat.S_ISDIR(entry.mode):continue
        require(entry.mode in (0o100644,0o100755),'unsupported_tree')
        blob=repo[entry.sha];require(isinstance(blob,Blob),'object_type')
        size=blob.raw_length();total+=size
        require(size<=local.MAX_FILE and total<=local.MAX_TOTAL,'limit')
        if path.split('/')[-1]=='.gitattributes':
            require(size<=65536,'configuration')
            require(not re.search(rb'(?:^|\s)[!-]?(?:filter|working-tree-encoding)(?:=|\s|$)',blob.data),'configuration')
        files[path]=(entry.sha,entry.mode)
    return files


def clean_index(repo,current):
    require(not current['unstaged'] and not any(current['staged'].values()),'dirty')
    config=raw_config(Path(repo.path))
    require(not config.get_boolean((b'core',),b'sparseCheckout',False),'unsupported_tree')
    for _,entry in repo.open_index().items():
        require(not isinstance(entry,ConflictedIndexEntry),'dirty')
        require(not entry.flags&FLAG_VALID and not entry.extended_flags&(EXTENDED_FLAG_SKIP_WORKTREE|EXTENDED_FLAG_INTEND_TO_ADD),'unsupported_index')


def checkout(repo,old,new,mode,budget,current):
    incoming=checkout_files(repo,new,budget)
    previous=checkout_files(repo,old,budget) if old is not None else {}
    root=Path(repo.path)
    # Include ignored files and directory/file transitions, not just status.untracked.
    for path in incoming:
        target=root/path
        if path not in previous and target.exists():
            require(target.is_dir() and any(p.startswith(path+'/') for p in previous),'untracked_collision')
        parts=path.split('/')
        for i in range(1,len(parts)):
            parent='/'.join(parts[:i]);file=root/parent
            require(not file.is_file() or parent in previous,'untracked_collision')
    old_tree=repo[old].tree if old is not None else None
    changes=list(tree_changes(repo.object_store,old_tree,repo[new].tree))
    budget.check()
    config=repo.get_config_stack()
    update_working_tree(repo,old_tree,repo[new].tree,iter(changes),
        honor_filemode=mode and config.get_boolean((b'core',),b'filemode',False),
        blob_normalizer=repo.get_blob_normalizer(config=config,tree=repo[new].tree),
        allow_overwrite_modified=False,force_remove_untracked=False,config=config)
    budget.check();after=local.status(repo)
    # Until HEAD moves, the new index is intentionally staged against the old
    # commit. Validate its worktree now and its HEAD relation only after the CAS.
    require(not after['unstaged'],'checkout')
    return len(changes)


def set_ref(repo,reference,old,new,message):
    require(repo.refs.set_if_equals(reference,old,new,committer=IDENTITY,message=message),'stale')


def default_transport(url,username,token,budget):return GitHttps(url,username,token,_budget=budget)


def operate(root,request,cancelled,transport_factory):
    action=request.get('action');require(action in OPERATIONS and set(request)<=FIELDS,'input')
    mode=request.get('modeAvailable',True);require(isinstance(mode,bool),'input')
    budget=NetworkBudget(cancelled=cancelled);budget.check()
    before=state(root,mode)
    if action in WRITES:
        require(isinstance(request.get('expected'),str) and re.fullmatch('[a-f0-9]{64}',request['expected']),'precondition')
        require(request['expected']==before['snapshot'],'stale')
    url=canonical_url(request['url']) if request.get('url') is not None else None
    selected=branch_name(request['branch']) if request.get('branch') is not None else None
    if action=='clone':
        require(not list(root.iterdir()),'target_not_empty');require(url,'url_invalid')
        budget.check();initialized=json.loads(local.run(str(root),json.dumps(dict(action='init',expected=before['snapshot'],modeAvailable=mode))))
        require(initialized.get('ok'),initialized.get('error','repository'))
    else:require(before['repository'] or action=='remote_refs','not_repository')
    if action=='remote_refs':
        require(url,'url_invalid')
        with transport_factory(url,request.get('username'),request.get('token'),budget) as transport:
            advertised=transport.client.get_refs(transport.url,protocol_version=1);branches,default=heads(advertised)
            return before|dict(remote=dict(url=url,default_branch=default,branches={k:v.decode() if v else None for k,v in sorted(branches.items())}))
    with local.LocalRepo(str(root),mode) as repo:
        current=local.status(repo)
        if action=='configure':
            require(url,'url_invalid');selected=selected or current['branch'];branch_name(selected)
            budget.check();save_origin(root,url,selected,current['branch'])
            return state(root,mode)|dict(remote=dict(url=url,branch=selected))
        origin=configured_url(root)
        if action=='remote_info':
            return before|dict(remote=dict(url=origin,configured=origin is not None))
        if action!='clone':
            require(origin is not None,'remote_not_configured');require(url is None or url==origin,'remote_changed');url=origin
        extra={}
        if action=='fast_forward':
            require(selected,'branch');require(not current['detached'],'detached')
            expected_head=object_id(request.get('expected_head'));target=object_id(request.get('expected_remote'))
            require(repo.head()==expected_head,'stale');clean_index(repo,current)
            tracked=b'refs/remotes/origin/'+selected.encode('utf-8')
            require(repo.refs.read_ref(tracked)==target,'remote_changed')
            require(target in repo.object_store,'needs_fetch')
            require(can_fast_forward(repo,expected_head,target),'non_fast_forward')
            changed=checkout(repo,expected_head,target,mode,budget,current)
            set_ref(repo,b'HEAD',expected_head,target,b'CodePocket fast-forward')
            extra=dict(remote=dict(url=url,branch=selected,head=target.decode()),changed_files=changed)
        elif action in ('clone','fetch'):
            with transport_factory(url,request.get('username'),request.get('token'),budget) as transport:
                advertised=transport.client.get_refs(transport.url,protocol_version=1);branches,default=heads(advertised)
                selected=selected or default
                require(selected,'branch');require(selected in branches or not branches,'branch_not_found')
                target=branches.get(selected)
                if target is not None:
                    wanted_ref=b'refs/heads/'+selected.encode('utf-8')
                    def wants(refs,depth=None):
                        require(refs.get(wanted_ref)==target,'remote_changed')
                        return [target] if target not in repo.object_store else []
                    fetched=transport.client.fetch(transport.url,repo,determine_wants=wants,protocol_version=1)
                    require(fetched.refs.get(wanted_ref)==target,'remote_changed');validate_objects(repo,budget)
                    require(isinstance(repo[target],Commit),'object_type')
                    reference=b'refs/remotes/origin/'+selected.encode('utf-8')
                    old=repo.refs.read_ref(reference)
                    set_ref(repo,reference,old,target,b'CodePocket fetch')
                changed=0
                if action=='clone':
                    branch=b'refs/heads/'+selected.encode('utf-8');repo.refs.set_symbolic_ref(b'HEAD',branch)
                    if target is not None:
                        changed=checkout(repo,None,target,mode,budget,current)
                        set_ref(repo,branch,None,target,b'CodePocket clone')
                    save_origin(root,url,selected,selected)
                extra=dict(remote=dict(url=url,branch=selected,head=target.decode() if target else None),changed_files=changed)
        elif action=='push':
            require(selected,'branch');head=object_id(request.get('expected_head'))
            require(repo.head()==head,'stale');require('expected_remote' in request,'precondition')
            expected=object_id(request['expected_remote']) if request['expected_remote'] is not None else None
            reference=b'refs/heads/'+selected.encode('utf-8')
            with transport_factory(url,request.get('username'),request.get('token'),budget) as transport:
                def update(refs):
                    actual=refs.get(reference);require(actual==expected,'remote_changed')
                    if actual is not None:
                        require(actual in repo.object_store,'needs_fetch')
                        require(can_fast_forward(repo,actual,head),'non_fast_forward')
                    return {reference:head}
                try:
                    pushed=transport.client.send_pack(transport.url,update,repo.generate_pack_data)
                    require(not any(pushed.ref_status.values()),'remote_rejected')
                except Exception as error:
                    if transport.pool.write_started and not isinstance(error,RemoteFailure):
                        return dict(ok=False,error='push_unknown',push_head=head.decode())
                    if transport.pool.write_started and isinstance(error,RemoteFailure) and error.code!='remote_rejected':
                        return dict(ok=False,error='push_unknown',push_head=head.decode())
                    raise
            # A completed single-ref report is authoritative even if cancellation
            # arrives now; Android must persist this receipt before dismissing UI.
            return state(root,mode)|dict(remote=dict(url=url,branch=selected,head=head.decode()),
                push_head=head.decode(),network_outcome='up_to_date' if expected==head else 'pushed')
        else:raise RemoteFailure('input')
        budget.check();result=state(root,mode)
        if action in ('clone','fast_forward'):
            require(not result['unstaged'] and not any(result['staged'].values()),'checkout')
        return result|extra


def plan_destination(root,value):
    target=Path(value)
    require(target.is_absolute() and target==target.resolve() and target==root.parent/'result.plan'
        and not target.exists() and not target.is_symlink(),'plan_path')
    return target


def publication_plan(root,request,before,result,destination,cancelled):
    deadline=time.monotonic()+15;files={};directories=[''];total=0;count=0
    def check():
        require(not cancelled(),'cancelled');require(time.monotonic()<deadline,'timeout')
    def visit(folder,depth):
        nonlocal total,count
        check();require(depth<=32,'limit')
        for entry in sorted(os.scandir(folder),key=lambda item:item.name):
            check();count+=1;require(count<=local.MAX_ENTRIES,'limit')
            path=Path(entry.path);relative=path.relative_to(root).as_posix();mode=entry.stat(follow_symlinks=False).st_mode
            require(not entry.is_symlink() and path==path.resolve(),'path')
            if stat.S_ISDIR(mode):directories.append(relative);visit(path,depth+1)
            else:
                require(stat.S_ISREG(mode),'path');size=entry.stat(follow_symlinks=False).st_size
                total+=size;require(size<=local.MAX_FILE and total<=local.MAX_TOTAL,'limit')
                digest=hashlib.sha256();read=0
                with path.open('rb') as stream:
                    while chunk:=stream.read(65536):
                        check();read+=len(chunk);require(read<=size,'stale');digest.update(chunk)
                require(read==size,'stale');files[relative]=dict(sha256=digest.hexdigest(),size=size)
    visit(root,0)
    # The manifest has no platform-dependent stat bits. Native publication checks
    # actual modes separately; SAF has no executable-bit API.
    plan=dict(version=1,root=str(root),action=request['action'],before=before['snapshot'],after=result['snapshot'],
        url=result['remote']['url'],remote_branch=result['remote']['branch'],
        before_head=before['head'],after_head=result['head'],local_branch=result['branch'],files=files,directories=directories)
    require(state(root,request.get('modeAvailable',True))['snapshot']==result['snapshot'],'stale')
    data=json.dumps(plan,ensure_ascii=True,separators=(',',':')).encode('ascii');require(len(data)<=MAX_PLAN,'plan_limit')
    check()
    # An interrupted private write is never reported successful or reused.
    # Its owned job is retained/discarded by Android; existing files are untouched.
    with destination.open('xb') as stream:
        stream.write(data);stream.flush();os.fsync(stream.fileno())
    result['plan']='result.plan'


def run(root,raw_request,cancelled=lambda:False,plan_output=None,*,transport_factory=default_transport):
    try:
        require(isinstance(raw_request,str) and len(raw_request)<=100_000,'input_limit')
        path=Path(root);require(path.is_absolute() and path==path.resolve() and path.is_dir(),'snapshot_path')
        request=json.loads(raw_request);require(isinstance(request,dict),'input')
        destination=None;before=None
        if plan_output is not None:
            require(request.get('action') in PUBLICATIONS and set(request)<=FIELDS,'input')
            destination=plan_destination(path,plan_output);before=state(path,request.get('modeAvailable',True))
        result=operate(path,request,cancelled,transport_factory)
        if destination is not None and result.get('ok'):
            publication_plan(path,request,before,result,destination,cancelled)
    except RemoteFailure as error:result=dict(ok=False,error=error.code)
    except local.Rejected as error:result=dict(ok=False,error=str(error))
    except (ValueError,UnicodeError,TypeError):result=dict(ok=False,error='input')
    except Exception:result=dict(ok=False,error='protocol')
    output=json.dumps(result,ensure_ascii=True,separators=(',',':'))
    return output if len(output)<=local.MAX_JSON else json.dumps(dict(ok=False,error='output_limit'))


def run_android(root,raw_request,cancellation,plan_output=None):
    """Chaquopy's Java AtomicBoolean is queried at each bounded network/read step."""
    return run(root,raw_request,lambda:bool(cancellation.get()),plan_output)
