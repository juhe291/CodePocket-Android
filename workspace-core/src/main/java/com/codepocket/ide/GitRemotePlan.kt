package com.codepocket.ide

import org.json.JSONObject
import java.io.File
import java.net.URI

/** A result manifest from our private engine, never a server-supplied publication request.
 * It is read outside `work`, matched to the request/response and rehashed before use. */
class GitRemotePlan private constructor(
    val root:File,val action:String,val before:String,val after:String,val url:String,
    val remoteBranch:String,val beforeHead:String?,val afterHead:String?,val localBranch:String,
    internal val files:Map<String,GitFileVersion>,internal val directories:Set<String>
) {
    companion object {
        const val MAX_BYTES=8_000_000
        val actions=setOf("configure","clone","fetch","fast_forward")
        private val hash=Regex("[a-f0-9]{64}")
        private val oid=Regex("[a-f0-9]{40}")
        private fun string(json:JSONObject,key:String)=json.get(key).also{require(it is String)} as String
        private fun head(json:JSONObject,key:String):String? = if(json.isNull(key))null else string(json,key).also{require(oid.matches(it))}
        internal fun path(value:String,rootAllowed:Boolean=false):String {
            if(value.isEmpty()){require(rootAllowed);return value}
            require(value.length<=512 && !File(value).isAbsolute && ':' !in value && '\\' !in value)
            val parts=value.split('/');require(parts.size<=32)
            parts.forEach(Workspace::validateName)
            require(parts.drop(1).none{it.trimEnd('.',' ').equals(".git",true)})
            require(!parts.first().trimEnd('.',' ').equals(".git",true) || parts.first()==".git")
            return value
        }
        internal fun branch(value:String):String {
            require(value.isNotEmpty() && value.length<=255 && value!="@" && !value.endsWith('.') &&
                ".." !in value && "@{" !in value && value.none{it.code<=32 || it.code==127 || it in "~^:?*[\\"})
            path(value);value.split('/').forEach{require(!it.startsWith('.') && !it.endsWith(".lock"))}
            return value
        }
        private fun remote(value:String):String {
            require(value.length in 1..2048 && value.none{it.code<=32 || it.code==127 || it=='\\'})
            val uri=URI(value)
            require(uri.scheme=="https" && !uri.host.isNullOrEmpty() && uri.rawUserInfo==null && uri.rawQuery==null && uri.rawFragment==null)
            return value
        }
        fun read(snapshot:GitWorkspaceSnapshot,action:String,expectedBefore:String,expectedAfter:String,
            expectedUrl:String,expectedBranch:String):GitRemotePlan {
            require(action in actions && hash.matches(expectedBefore) && hash.matches(expectedAfter))
            remote(expectedUrl);branch(expectedBranch)
            val source=File(snapshot.root.parentFile,"result.plan")
            require(source.absoluteFile==source.canonicalFile && source.isFile && source.length() in 1..MAX_BYTES.toLong())
            val json=JSONObject(source.readText(Charsets.UTF_8))
            require(json.keys().asSequence().toSet()==setOf("version","root","action","before","after","url","remote_branch",
                "before_head","after_head","local_branch","files","directories"))
            require(json.get("version")==1 && string(json,"root")==snapshot.root.absolutePath && string(json,"action")==action &&
                string(json,"before")==expectedBefore && string(json,"after")==expectedAfter && string(json,"url")==expectedUrl &&
                string(json,"remote_branch")==expectedBranch)
            val rawFiles=json.getJSONObject("files");val rawDirectories=json.getJSONArray("directories")
            require(rawFiles.length()+rawDirectories.length()<=GitWorkspaceStore.MAX_ENTRIES+1)
            val directories=(0 until rawDirectories.length()).map {
                require(rawDirectories.get(it) is String);path(rawDirectories.getString(it),true)
            }.also{require(it.size==it.distinct().size && "" in it)}.toSet()
            var total=0L
            val files=rawFiles.keys().asSequence().associateWith{key->
                path(key);val entry=rawFiles.getJSONObject(key)
                require(entry.keys().asSequence().toSet()==setOf("sha256","size"))
                val sha=string(entry,"sha256");val size=entry.get("size")
                require(hash.matches(sha) && (size is Int || size is Long))
                val length=(size as Number).toLong();require(length in 0..GitWorkspaceStore.MAX_FILE.toLong())
                total+=length;require(total<=GitWorkspaceStore.MAX_TOTAL)
                GitFileVersion(sha,length.toInt())
            }
            require(files.keys.intersect(directories).isEmpty())
            for(name in files.keys+directories.filter{it.isNotEmpty()})require(name.substringBeforeLast('/',"") in directories)
            val local=string(json,"local_branch").also{if(it!="HEAD")branch(it)}
            return GitRemotePlan(snapshot.root,action,expectedBefore,expectedAfter,expectedUrl,expectedBranch,
                head(json,"before_head"),head(json,"after_head"),local,files.toMap(),directories.toSet())
        }
    }
}

/** The old local metadata publisher stays strict. Only these separately approved changes
 * may enter the forthcoming worktree journal, after live-workspace freshness checks. */
internal object GitRemotePolicy {
    private data class Head(val id:String?,val branch:String?)
    private fun read(root:File,tree:GitTreeVersion,path:String):ByteArray? {
        val version=tree.files[path]?:return null
        val source=File(root,path);require(source.absoluteFile==source.canonicalFile && source.isFile && source.length()==version.size.toLong())
        return source.readBytes().also{require(GitWorkspaceStore.digest(it)==version.sha256)}
    }
    private fun head(root:File,tree:GitTreeVersion):Head {
        if(".git" !in tree.directories)return Head(null,null)
        var reference="HEAD";val seen=mutableSetOf<String>()
        repeat(8) {
            require(seen.add(reference)) { "Git HEAD 包含引用循环" }
            val data=read(root,tree,".git/$reference")?.toString(Charsets.US_ASCII)?.trim()
            if(data==null) {
                if(reference=="HEAD")error("Git HEAD 缺失")
                val packed=read(root,tree,".git/packed-refs")?.toString(Charsets.US_ASCII)?.lineSequence()
                    ?.filter{it.isNotBlank() && !it.startsWith('#') && !it.startsWith('^')}
                    ?.filter{it.substringAfter(' ',"")==reference}?.toList().orEmpty()
                require(packed.size<=1)
                val id=packed.singleOrNull()?.substringBefore(' ')
                require(id==null || Regex("[a-f0-9]{40}").matches(id))
                return Head(id,reference.removePrefix("refs/heads/"))
            }
            if(data.startsWith("ref: ")) {
                reference=data.removePrefix("ref: ")
                require(reference.startsWith("refs/heads/"));GitRemotePlan.branch(reference.removePrefix("refs/heads/"))
            } else {
                require(Regex("[a-f0-9]{40}").matches(data))
                return Head(data,if(reference=="HEAD")"HEAD" else reference.removePrefix("refs/heads/"))
            }
        }
        error("Git HEAD 引用过深")
    }
    private fun metadata(path:String)=path==".git" || path.startsWith(".git/")
    private fun immutable(path:String)=Regex("\\.git/objects/[a-f0-9]{2}/[a-f0-9]{38}").matches(path) ||
        Regex("\\.git/objects/pack/pack-[a-f0-9]{40}\\.(pack|idx|rev)").matches(path)
    fun changes(snapshot:GitWorkspaceSnapshot,after:GitTreeVersion,plan:GitRemotePlan):List<GitMetadataChange> {
        require(plan.root==snapshot.root && plan.files==after.files.mapValues{(_,v)->v.copy(executable=null)} && plan.directories==after.directories) {
            "Git 结果清单与实际快照不符"
        }
        val before=snapshot.before
        val oldHead=head(snapshot.beforeRoot,before);val newHead=head(snapshot.root,after)
        require(oldHead.id==plan.beforeHead && newHead.id==plan.afterHead && newHead.branch==plan.localBranch)
        val bodies=plan.action in setOf("clone","fast_forward")
        if(bodies) {
            require(snapshot.worktreeBackedUp) { "Git 正文更新必须先备份原工作区" }
            // Validate every backup now, before a transaction can change any original.
            for((path,_) in before.files)read(snapshot.beforeRoot,before,path)
            require(before.directories.all{File(snapshot.beforeRoot,it).let{folder->folder.absoluteFile==folder.canonicalFile && folder.isDirectory}})
        } else {
            require(before.files.filterKeys{!metadata(it)}==after.files.filterKeys{!metadata(it)} &&
                before.directories.filter{!metadata(it)}.toSet()==after.directories.filter{!metadata(it)}.toSet()) { "此远端操作不能改变正文" }
        }
        if(plan.action=="clone")require(before.files.isEmpty() && before.directories==setOf("") && plan.localBranch==plan.remoteBranch)
        else {
            require(oldHead.branch==newHead.branch)
            if(plan.action!="fast_forward")require(oldHead==newHead)
            else require(oldHead.id!=null && oldHead.branch!=null && oldHead.branch!="HEAD")
        }
        require(before.directories.filter(::metadata).all{it in after.directories}) { "远端操作不能删除已有 Git 目录" }
        val changes=(before.files.keys+after.files.keys).sorted().mapNotNull { path->
            val old=before.files[path];val new=after.files[path]
            if(old==new)null else GitMetadataChange(path,old,new)
        }
        val localReference=".git/refs/heads/${plan.localBranch}"
        val remoteReference=".git/refs/remotes/origin/${plan.remoteBranch}"
        for(change in changes.filter{metadata(it.path)}) {
            val path=change.path;GitRemotePlan.path(path)
            require(change.after!=null) { "远端操作不能删除已有 Git 元数据" }
            val objectFile=immutable(path)
            if(objectFile) {
                require(change.before==null && plan.action in setOf("clone","fetch")) { "Git 对象不可覆盖" }
                if(path.contains("/pack/")) {
                    val stem=path.substringBeforeLast('.')
                    require("$stem.pack" in after.files && "$stem.idx" in after.files) { "Git pack 与索引必须成对存在" }
                }
            } else {
                val allowed=when(plan.action) {
                    "configure"->setOf(".git/config")
                    "fetch"->setOf(remoteReference,".git/logs/refs/remotes/origin/${plan.remoteBranch}")
                    "fast_forward"->setOf(".git/index",".git/HEAD",localReference,".git/logs/HEAD",".git/logs/refs/heads/${plan.localBranch}")
                    "clone"->setOf(".git/config",".git/description",".git/info/exclude",".git/HEAD",".git/index",localReference,
                        remoteReference,".git/logs/HEAD",".git/logs/refs/heads/${plan.localBranch}",".git/logs/refs/remotes/origin/${plan.remoteBranch}")
                    else->emptySet()
                }
                require(path in allowed) { "Git 元数据超出已确认的远端操作范围" }
            }
        }
        val addedDirectories=after.directories-before.directories
        val initialDirectories=setOf(".git",".git/branches",".git/hooks",".git/info",".git/logs",".git/objects",
            ".git/objects/info",".git/objects/pack",".git/refs",".git/refs/heads",".git/refs/tags")
        for(path in addedDirectories.filter(::metadata)) {
            GitRemotePlan.path(path)
            require(changes.any{it.path.startsWith("$path/")} || plan.action=="clone" && path in initialDirectories) {
                "Git 结果包含无关元数据目录"
            }
        }
        return changes
    }
}
