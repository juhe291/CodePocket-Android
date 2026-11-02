package com.codepocket.ide

import android.content.Context
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.UUID

internal data class GitRemoteInfo(val url:String?,val branch:String?=null,val head:String?=null,
    val configured:Boolean?=null,val defaultBranch:String?=null,val branches:Map<String,String?> = emptyMap())
internal data class GitRemoteReply(val state:GitState?=null,val remote:GitRemoteInfo?=null,val error:String?=null,
    val plan:String?=null,val pushHead:String?=null,val outcome:String?=null) {val ok get()=state!=null && error==null}
internal object GitRemoteProtocol {
    val publications=setOf("configure","clone","fetch","fast_forward")
    val operations=publications+setOf("snapshot","remote_info","remote_refs","push")
    val errors=setOf("input","input_limit","output_limit","invalid_response","snapshot_path","precondition","stale","branch","branch_not_found",
        "url_invalid","url_scope","remote_changed","remote_not_configured","remote_not_found","remote_rejected","remote_http","redirect","credentials",
        "authentication","tls","tls_policy","network","timeout","cancelled","busy","backend","protocol","repository","not_repository","repository_layout",
        "configuration","operation_in_progress","unsupported_tree","unsupported_index","object_format","object_type","limit","path","dirty","detached",
        "non_fast_forward","needs_fetch","untracked_collision","target_not_empty","checkout","plan_path","plan_limit","locked","engine",
        "transfer_limit","request_limit","push_unknown","push_intent")
    fun failure(code:String,head:String?=null)=JSONObject().put("ok",false).put("error",code.takeIf{it in errors}?:"backend")
        .also{if(head!=null && Regex("[a-f0-9]{40}").matches(head))it.put("push_head",head)}.toString()
    fun branch(value:String):String {
        GitProtocol.path(value);require(value.length<=255 && value!="@" && ".." !in value && "@{" !in value && !value.endsWith('.') &&
            value.none{it.code<=32 || it.code==127 || it in "~^:?*[\\"} && value.split('/').none{it.startsWith('.') || it.endsWith(".lock")})
        return value
    }
    fun oid(value:String)=value.also{require(Regex("[a-f0-9]{40}").matches(it))}
    private fun optional(json:JSONObject,key:String)=if(json.isNull(key))null else json.get(key).also{require(it is String)} as String
    private fun containsSecret(value:Any?,secret:String,depth:Int=0):Boolean {
        if(depth>8)return true
        return when(value) {
            is String->value.contains(secret)
            is JSONObject->value.keys().asSequence().any{it.contains(secret) || containsSecret(value.opt(it),secret,depth+1)}
            is org.json.JSONArray->(0 until value.length()).any{containsSecret(value.opt(it),secret,depth+1)}
            else->false
        }
    }
    fun decode(raw:String?):GitRemoteReply=try {
        require(raw!=null && raw.length<=GitProtocol.MAX_RESPONSE)
        val json=JSONObject(raw);require(json.get("ok") is Boolean)
        if(!json.getBoolean("ok")) {
            require(json.keys().asSequence().all{it in setOf("ok","error","push_head")})
            val code=json.getString("error");require(code in errors)
            GitRemoteReply(error=code,pushHead=optional(json,"push_head")?.let(::oid))
        } else {
            val allowed=setOf("ok","engine","repository","snapshot","head","branch","detached","staged","unstaged","untracked","remote","changed_files","plan","push_head","network_outcome")
            require(json.keys().asSequence().all{it in allowed})
            val base=GitProtocol.decode(raw);require(base.ok)
            val state=base.state!!;state.branch?.let{if(it!="HEAD")branch(it)}
            require(json.getJSONObject("staged").keys().asSequence().toSet()==if(state.repository)setOf("add","modify","delete") else emptySet<String>())
            if(json.has("detached"))require(json.get("detached") is Boolean)
            if(json.has("changed_files")){val count=json.get("changed_files");require(count is Int && count in 0..GitWorkspaceStore.MAX_ENTRIES)}
            val info=json.getJSONObject("remote");require(info.keys().asSequence().all{it in setOf("url","branch","head","configured","default_branch","branches")})
            val url=optional(info,"url")?.let(GitRemoteAddress::canonical)
            val names=info.optJSONObject("branches")?.let{branches->require(branches.length()<=GitWorkspaceStore.MAX_ENTRIES)
                branches.keys().asSequence().associate{branch(it) to optional(branches,it)?.let(::oid)}}?:emptyMap()
            val configured=if(info.has("configured"))info.get("configured").also{require(it is Boolean)} as Boolean else null
            val remote=GitRemoteInfo(url,optional(info,"branch")?.let(::branch),optional(info,"head")?.let(::oid),configured,
                optional(info,"default_branch")?.let(::branch),names)
            val plan=optional(json,"plan").also{require(it==null || it=="result.plan")}
            val outcome=optional(json,"network_outcome").also{require(it==null || it in setOf("pushed","up_to_date"))}
            GitRemoteReply(state,remote,plan=plan,pushHead=optional(json,"push_head")?.let(::oid),outcome=outcome)
        }
    }catch(_:Exception){GitRemoteReply(error="invalid_response")}
    fun checked(raw:String,request:JSONObject):String {
        val token=request.optString("token")
        val push=request.optString("action")=="push"
        if(token.isNotEmpty() && (raw.contains(token) || runCatching{containsSecret(JSONObject(raw),token)}.getOrDefault(false)))
            return failure(if(push)"push_unknown" else "backend",if(push)request.optString("expected_head") else null)
        val reply=decode(raw)
        if(reply.error=="invalid_response")return failure(if(push)"push_unknown" else "invalid_response",if(push)request.optString("expected_head") else null)
        if(reply.ok) {
            if(request.getString("action") in publications && reply.plan!="result.plan")return failure("invalid_response")
            if(request.getString("action")!="remote_info" && !request.isNull("url") && reply.remote?.url!=GitRemoteAddress.canonical(request.getString("url")))return failure("invalid_response")
            if(push && (reply.pushHead!=request.optString("expected_head") || reply.remote?.head!=reply.pushHead || reply.state?.head!=reply.pushHead || reply.outcome==null))
                return failure("push_unknown",request.optString("expected_head"))
        }
        return raw
    }
    fun packet(root:File,request:JSONObject,intent:String?=null)=JSONObject().put("root",root.absolutePath).put("request",request)
        .also{if(intent!=null)it.put("intent",intent)}.toString().also{require(it.length<=GitProtocol.MAX_REQUEST)}
}

/** Separate namespace: :git_remote startup must never remove :git's live jobs. */
internal object GitRemoteJobs {
    private val uuid=Regex("[a-f0-9-]{36}")
    fun parent(context:Context)=File(context.cacheDir,"git-remote-jobs").absoluteFile.also {
        require(it==it.canonicalFile && (it.isDirectory || it.mkdirs() || it.isDirectory))
    }
    fun next(context:Context)=File(parent(context),UUID.randomUUID().toString())
    fun root(context:Context,path:String,exists:Boolean=true):File {
        val root=File(path).absoluteFile;val job=root.parentFile?:error("缺少远端作业目录")
        require(root==root.canonicalFile && root.name=="work" && uuid.matches(job.name) && job.parentFile==parent(context) && (!exists || root.isDirectory))
        return root
    }
    fun lease(root:File)=File(root.parentFile,"engine.lease")
    fun discard(context:Context,root:File,force:Boolean=false) {
        GitRemoteJobs.root(context,root.path,false);if(!force && lease(root).exists())return
        val job=root.parentFile!!;require(job==job.canonicalFile && job.parentFile==parent(context))
        fun remove(file:File) {
            require(file==job || file.path.startsWith(job.path+File.separator))
            if(file.isDirectory && file==file.canonicalFile)file.listFiles()?.forEach(::remove)
            require(file.delete() || !file.exists())
        }
        if(job.exists())remove(job)
    }
    fun clearOldLeases(context:Context) {
        parent(context).listFiles()?.filter{it.isDirectory && uuid.matches(it.name) && File(it,"engine.lease").isFile}
            ?.forEach{runCatching{discard(context,File(it,"work"),true)}}
    }
}

internal class GitRemoteClient(private val transport:SemanticTransport):Closeable {
    constructor(context:Context):this(AndroidSemanticTransport(context,GitRemoteService::class.java,125))
    fun request(root:File,request:JSONObject,intent:String?=null):GitRemoteReply {
        if(Thread.currentThread().isInterrupted)throw InterruptedException()
        return try{GitRemoteProtocol.decode(transport.analyze(GitRemoteProtocol.packet(root,request,intent)))}
        catch(error:InterruptedException){throw error}
        catch(_:Exception){GitRemoteReply(error=if(request.optString("action")=="push")"push_unknown" else "network")}
    }
    fun push(root:File,request:JSONObject,workspace:String,ledger:GitPushLedger):Pair<GitRemoteReply,GitPushReceipt> {
        val intent=ledger.begin(workspace,root,request)
        val reply=try{this.request(root,request,intent.id)}catch(error:InterruptedException){
            runCatching{ledger.finish(intent.id,"unknown","push_unknown")};throw error
        }
        if(!ledger.get(intent.id).confirmed) {
            ledger.finish(intent.id,if(reply.error=="busy" || reply.error=="input_limit")"not_sent" else "unknown",reply.error?.takeIf{it in GitRemoteProtocol.errors})
        }
        val receipt=ledger.get(intent.id)
        return (if(!receipt.confirmed)reply.copy(error="push_unknown",pushHead=intent.head) else reply) to receipt
    }
    override fun close()=transport.close()
}
