package com.codepocket.ide

import android.content.Intent
import android.os.*
import org.json.JSONObject
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.util.ReflectionHelpers
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Actual pinned CPython/Dulwich/TLS behind the production Messenger service. No GitHub account. */
internal object HostRemoteGit {
    val repository get()=CppFixtures.root
    val python:String get()=System.getProperty("codepocket.hostPython")?:if(System.getProperty("os.name").orEmpty().startsWith("Windows",true))
        File(repository,".tools/python313-full/tools/python.exe").path else "python3"
    fun process(vararg arguments:String)=ProcessBuilder(listOf(python,"-B")+arguments).directory(repository).apply {
        environment()["PYTHONUTF8"]="1"
        environment()["PYTHONPATH"]=listOf(File(repository,".tools/git-probe/site"),File(repository,"app/src/main/python"),
            File(repository,"verification/git-remote-preflight-2026-11-02")).joinToString(File.pathSeparator){it.path}
    }
    fun backend(root:File,request:String,cancelled:AtomicBoolean,plan:File?):String {
        val output=File(root.parentFile,"host-response-${UUID.randomUUID()}.json");val errors=File(root.parentFile,"host-errors-${UUID.randomUUID()}.log")
        val script="import json,ssl,sys,git_remote,git_https,probe_https\n"+
            "def transport(url,user,token,budget): return git_https.GitHttps(url,user,token,_ssl_context=ssl.create_default_context(cafile=str(probe_https.CERT)),_budget=budget)\n"+
            "print(git_remote.run(sys.argv[1],sys.stdin.read(),lambda:sys.argv[3]=='true',sys.argv[2] or None,transport_factory=transport))"
        val child=process("-c",script,root.path,plan?.path?:"",cancelled.get().toString()).redirectOutput(output).redirectError(errors).start()
        try {
            child.outputStream.bufferedWriter(Charsets.UTF_8).use{it.write(request)}
            check(child.waitFor(30,TimeUnit.SECONDS)){"Pinned host remote engine timed out"};check(child.exitValue()==0){errors.readText()}
            return output.readText(Charsets.UTF_8).trim()
        }finally{if(child.isAlive){child.destroyForcibly();child.waitFor(2,TimeUnit.SECONDS)}}
    }
    class Server:Closeable {
        private val errors=File(RuntimeEnvironment.getApplication().cacheDir,"tls-server-${UUID.randomUUID()}.log")
        private val child=process(File(repository,"verification/git-remote-workbench-2026-11-02/serve-fixture.py").path).redirectError(errors).start()
        private val reader=child.inputStream.bufferedReader(Charsets.UTF_8)
        private val writer=child.outputStream.bufferedWriter(Charsets.UTF_8)
        private val banner=JSONObject(checkNotNull(reader.readLine()){errors.readText()})
        val url=banner.getString("url");val firstHead=banner.getString("head")
        val credentials=GitCredentials("fixture","fixture-only-pat-never-a-real-account")
        @Synchronized fun command(request:JSONObject):JSONObject {
            writer.write(request.toString());writer.newLine();writer.flush()
            return JSONObject(checkNotNull(reader.readLine()){errors.readText()})
        }
        fun advance(changes:Map<String,ByteArray?>):String {
            val values=JSONObject();changes.forEach{(path,value)->values.put(path,value?.let{java.util.Base64.getEncoder().encodeToString(it)}?:JSONObject.NULL)}
            return command(JSONObject().put("action","advance").put("changes",values)).getString("head")
        }
        fun head()=command(JSONObject().put("action","head")).getString("head")
        fun requests()=command(JSONObject().put("action","requests")).getInt("count")
        override fun close() {
            runCatching{writer.write("{\"action\":\"stop\"}\n");writer.flush()}
            if(!child.waitFor(5,TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor(2,TimeUnit.SECONDS)}
            reader.close();writer.close()
        }
    }
    class Channel:SemanticTransport {
        private val controller=Robolectric.buildService(GitRemoteService::class.java).create()
        private val service=controller.get()
        private val remote=Messenger(service.onBind(Intent()))
        private val next=AtomicLong()
        var after:((JSONObject,GitRemoteReply)->Unit)?=null
        init {val factory:(File,String,AtomicBoolean,File?)->String=::backend;ReflectionHelpers.setField(service,"backend",factory)}
        override fun analyze(payload:String):String? {
            val latch=CountDownLatch(1);var result:String?=null;val id=next.incrementAndGet()
            val reply=Messenger(Handler(Looper.getMainLooper()){message->if(message.data.getLong("id")==id){result=message.data.getString("result");latch.countDown()};true})
            remote.send(Message.obtain(null,PythonAnalysisService.ANALYZE).apply{replyTo=reply;data=Bundle().apply{putLong("id",id);putString("payload",payload)}})
            try {
                check(latch.await(40,TimeUnit.SECONDS)){"Host Messenger remote operation did not return"}
                after?.invoke(JSONObject(payload).getJSONObject("request"),GitRemoteProtocol.decode(result));return result
            }finally{remote.send(Message.obtain(null,PythonAnalysisService.CANCEL).apply{data=Bundle().apply{putLong("id",id)}})}
        }
        override fun close(){controller.destroy();Shadows.shadowOf(Looper.getMainLooper()).idle()}
    }
}
