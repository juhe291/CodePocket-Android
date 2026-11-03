package com.codepocket.ide

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.UUID

internal data class GitCommitInfo(val id:String,val message:String,val author:String,val time:Long,val truncated:Boolean=false)
internal data class GitState(val snapshot:String,val repository:Boolean,val head:String?,val branch:String?,
    val staged:Map<String,List<String>>,val unstaged:List<String>,val untracked:List<String>) {
    val stagedPaths get()=staged.values.flatten()
}
internal data class GitResponse(val state:GitState?=null,val error:String?=null,val detail:String?=null,
    val text:String?=null,val commits:List<GitCommitInfo> = emptyList(),val commit:GitCommitInfo?=null,
    val sourceFiles:Map<String,GitFileVersion> = emptyMap()) {
    val ok get()=state!=null && error==null
}

internal object GitProtocol {
    const val MAX_REQUEST=100_000
    const val MAX_RESPONSE=450_000
    val writes=setOf("init","stage","unstage","commit")
    val operations=writes+setOf("status","diff","history","show_commit")
    fun failure(reason:String)=JSONObject().put("ok",false).put("error",reason).toString()
    fun path(value:String):String {
        require(value.isNotEmpty() && value.length<=512 && !File(value).isAbsolute && ':' !in value && '\\' !in value)
        val parts=value.split('/');require(parts.size<=32 && parts.none{it==".git" || it=="." || it==".."})
        parts.forEach(Workspace::validateName);return value
    }
    fun packet(root:File,request:JSONObject)=JSONObject().put("root",root.absolutePath).put("request",request).toString()
        .also{require(it.length<=MAX_REQUEST)}
    private fun paths(array:JSONArray):List<String> {
        require(array.length()<=GitWorkspaceStore.MAX_ENTRIES)
        return (0 until array.length()).map{require(array.get(it) is String);path(array.getString(it))}
            .also{require(it.size==it.distinct().size)}
    }
    private fun commit(json:JSONObject):GitCommitInfo {
        val id=json.getString("id");require(Regex("[a-f0-9]{40}").matches(id))
        require(json.get("message") is String && json.get("author") is String)
        val time=json.get("time");require(time is Int || time is Long)
        val seconds=(time as Number).toLong();require(seconds in -62135596800L..253402300799L)
        return GitCommitInfo(id,json.getString("message"),json.getString("author"),seconds,json.optBoolean("truncated",false))
    }
    fun decode(raw:String?):GitResponse = try {
        require(raw!=null && raw.length<=MAX_RESPONSE)
        val json=JSONObject(raw);require(json.get("ok") is Boolean)
        if(!json.getBoolean("ok"))GitResponse(error=json.optString("error","backend").take(80))
        else {
            require(json.getString("engine")=="dulwich 1.2.20" && json.get("repository") is Boolean)
            val token=json.getString("snapshot");require(Regex("[a-f0-9]{64}").matches(token))
            val repository=json.getBoolean("repository")
            val head=if(json.isNull("head"))null else json.getString("head").also{require(Regex("[a-f0-9]{40}").matches(it))}
            val branch=if(json.isNull("branch"))null else json.getString("branch").also{require(it.isNotBlank() && it.length<=4096)}
            require(repository || head==null && branch==null)
            require(!repository || branch!=null)
            val staged=json.getJSONObject("staged")
            val groups=if(repository)listOf("add","modify","delete").associateWith{paths(staged.getJSONArray(it))} else emptyMap()
            require(groups.values.flatten().let{it.size==it.distinct().size})
            val state=GitState(token,repository,head,branch,groups,paths(json.getJSONArray("unstaged")),paths(json.getJSONArray("untracked")))
            val history=json.optJSONArray("commits")?.let{array->require(array.length()<=50);(0 until array.length()).map{commit(array.getJSONObject(it))}}?:emptyList()
            val message=if(json.has("text"))json.getString("text") else null
            GitResponse(state,text=message,commits=history,commit=if(json.has("id"))commit(json) else null)
        }
    } catch(_:Exception) {GitResponse(error="invalid_response")}
}

/** A lease keeps the UI from deleting a snapshot while the private service uses it. */
internal object GitJobs {
    private val name=Regex("[a-f0-9-]{36}")
    fun parent(context:Context)=File(context.cacheDir.canonicalFile,"git-jobs").absoluteFile.also {
        require(it==it.canonicalFile && (it.isDirectory || it.mkdirs() || it.isDirectory))
    }
    fun next(context:Context)=File(parent(context),UUID.randomUUID().toString())
    fun root(context:Context,path:String,exists:Boolean=true):File {
        val root=File(path).absoluteFile;val job=root.parentFile?:error("Git快照无父目录")
        require(root==root.canonicalFile && root.name=="work" && name.matches(job.name) && job.parentFile==parent(context))
        require(!exists || root.isDirectory);return root
    }
    fun lease(root:File)=File(root.parentFile,"engine.lease")
    fun discard(context:Context,root:File,force:Boolean=false) {
        GitJobs.root(context,root.absolutePath,false)
        val job=root.parentFile!!;if(!force && lease(root).exists())return
        require(job==job.canonicalFile && job.parentFile==parent(context))
        fun remove(file:File) {
            require(file==job || file.absolutePath.startsWith(job.absolutePath+File.separator))
            val regular=file.absoluteFile==file.canonicalFile
            if(file.isDirectory && regular)file.listFiles()?.forEach(::remove)
            if(regular)file.setWritable(true,true)
            require(file.delete() || !file.exists())
        }
        if(job.exists())remove(job)
    }
    fun clearOldLeases(context:Context) {
        // Called only from a newly created :git process, so a previous process
        // cannot still own these leases. Unleased fresh UI jobs stay untouched.
        parent(context).listFiles()?.filter{it.isDirectory && name.matches(it.name) && File(it,"engine.lease").isFile}
            ?.forEach{runCatching{discard(context,File(it,"work"),true)}}
    }
}

internal class GitClient(private val transport:SemanticTransport):Closeable {
    fun request(root:File,request:JSONObject):GitResponse {
        if(Thread.currentThread().isInterrupted)throw InterruptedException()
        return try {GitProtocol.decode(transport.analyze(GitProtocol.packet(root,request)))}
        catch(error:InterruptedException){throw error}
        catch(_:Exception){GitResponse(error="transport")}
    }
    override fun close()=transport.close()
}
