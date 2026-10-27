package com.codepocket.ide

import android.app.Service
import android.content.Intent
import android.os.*
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** Private :cpp_run coordinator. Cancellation closes a native generation, never the Android app. */
class CppExecutionService : Service() {
    private class Request(val id: Long, val args: Bundle, val reply: Messenger) {
        val cancelled = AtomicBoolean()
        var death: IBinder.DeathRecipient? = null
    }
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val ownership = Any()
    @Volatile private var destroyed = false
    @Volatile private var active: Request? = null
    private var ownedProcess: ExecutionProcess? = null
    private var processOwner: Request? = null
    private val runtime by lazy { CppRuntime(this) }
    private var executeRun: (Bundle, () -> Boolean, (ExecutionProcess?) -> Unit, (String) -> Unit) -> RunResult = ::executeNative
    private fun response(request: Request, result: RunResult) {
        if (!destroyed) runCatching { request.reply.send(Message.obtain(null,RESULT).apply { data=result.toBundle(request.id) }) }
    }
    private fun closeDescriptors(args: Bundle) {
        @Suppress("DEPRECATION")
        for (name in listOf("ttyIn","ttyOut","ttyErr")) runCatching { args.getParcelable<ParcelFileDescriptor>(name)?.close() }
    }
    private fun cancel(request: Request) {
        request.cancelled.set(true)
        synchronized(ownership) { if(processOwner===request)ownedProcess?.close() }
    }
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        val id=message.data.getLong("id")
        when(message.what) {
            CANCEL -> active?.takeIf{it.id==id}?.let(::cancel)
            RUN -> {
                val reply=message.replyTo
                if(reply==null || id<=0 || destroyed){closeDescriptors(message.data);return@Handler true}
                val request=Request(id,Bundle(message.data),reply)
                if(active!=null) {
                    // A replay must not finish the already running request with the same identity.
                    if(active?.id!=id)response(request,RunResult("","本地 C++ 运行器仍在释放上一项任务","—",0,engine=ENGINE,kind=RunKind.SERVICE))
                    closeDescriptors(request.args)
                } else start(request)
            }
        }
        true
    })
    private fun start(request: Request) {
        active=request
        val began=System.nanoTime()
        val death=IBinder.DeathRecipient { cancel(request) };request.death=death
        try { request.reply.binder.linkToDeath(death,0) }
        catch (_: RemoteException) { active=null;closeDescriptors(request.args);return }
        worker.execute {
            val result=try {
                executeRun(request.args,{destroyed||request.cancelled.get()}, { process ->
                    synchronized(ownership) {
                        if(process==null) {
                            if(processOwner===request){ownedProcess=null;processOwner=null}
                        } else if(active!==request || destroyed || request.cancelled.get())process.close()
                        else {ownedProcess=process;processOwner=request}
                    }
                }, { stage ->
                    if(!destroyed&&!request.cancelled.get())runCatching { request.reply.send(Message.obtain(null,STATE).apply {
                        data=Bundle().apply{putLong("id",request.id);putString("stage",stage)}
                    }) }
                })
            } catch (_: CancellationException) { RunResult("","已停止本地 C++ 运行","130",0,engine=ENGINE,kind=RunKind.CANCELLED) }
            catch (error: TimeoutException) { RunResult("",error.message.orEmpty(),"124",0,engine=ENGINE,kind=RunKind.TIMEOUT) }
            catch (error: Exception) { RunResult("","本地 C++ 执行失败：${error.message}","—",0,engine=ENGINE,kind=RunKind.SERVICE) }
            catch (error: LinkageError) { RunResult("","本地 C++ 原生运行器不可用：${error.message}","—",0,engine=ENGINE,kind=RunKind.SERVICE) }
            finally {
                synchronized(ownership) {
                    if(processOwner===request){ownedProcess?.close();ownedProcess=null;processOwner=null}
                }
                closeDescriptors(request.args)
                runCatching{request.reply.binder.unlinkToDeath(death,0)}
            }
            val completed=if(request.cancelled.get())result.copy(kind=RunKind.CANCELLED,exitCode="130",stderr="已停止本地 C++ 运行")else result
            handler.post {
                if(active===request) {
                    active=null
                    response(request,completed.copy(elapsedMs=(System.nanoTime()-began)/1000000))
                }
            }
        }
    }
    private fun executeNative(args: Bundle, cancelled: () -> Boolean, own: (ExecutionProcess?) -> Unit, stage: (String) -> Unit): RunResult {
        val directory=File(requireNotNull(args.getString("job"))).absoluteFile
        val snapshot=CppRunSnapshot.open(File(cacheDir,"cpp-runs").canonicalFile,directory,requireNotNull(args.getString("entry")))
        return snapshot.use {
            val input=args.getString("stdin").orEmpty();require(input.length<=20000)
            @Suppress("DEPRECATION")
            val tty=listOf("ttyIn","ttyOut","ttyErr").map{requireNotNull(args.getParcelable<ParcelFileDescriptor>(it))}
            tty.forEach{require(NativeTerminalIO.dimensions(it.fd)!=null){"本地 C++ 需要真实终端"}}
            val native=File(applicationInfo.nativeLibraryDir).canonicalFile
            val environment=listOf("PATH=/system/bin","LD_LIBRARY_PATH=${native.path}","HOME=${snapshot.source.path}","TMPDIR=${snapshot.build.path}","LANG=C.UTF-8")
            stage("准备本地工具链")
            val builder=CppLocalBuilder({step,root->NativeExecutionProcess(step.arguments,root.path,environment)},own,cancelled)
            val build=try { builder.build(snapshot,CppBuildScope.valueOf(args.getString("scope",CppBuildScope.CURRENT.name)),runtime.toolchain(),stage) }
            catch(error:IllegalArgumentException) {
                if(error.message.orEmpty().contains("main"))return@use RunResult("",error.message.orEmpty(),"1",0,engine=ENGINE,kind=RunKind.COMPILE)
                throw error
            }
            if(build.program==null) {
                val diagnostic=build.error
                return@use RunResult(build.stdout,build.stderr,build.exit.code?.toString()?:"signal ${build.exit.signal}",0,
                    errorLine=diagnostic?.line,engine=ENGINE,kind=if(build.exit.signal==24)RunKind.TIMEOUT else RunKind.COMPILE,
                    errorFile=diagnostic?.let{File(requireNotNull(args.getString("projectRoot")),it.file).path},errorSourceHash=diagnostic?.hash)
            }
            if(cancelled())throw CancellationException()
            android.system.Os.chmod(build.program.path,292) // 0444 before dlopen by the installed runner.
            val program=NativeTerminalEndpoint(tty[0].fd,tty[1].fd).use { endpoint ->
                CppTerminalRunner({size->NativeExecutionProcess(listOf(runtime.installedRunner().path,build.program.path),
                    snapshot.source.path,environment,terminal=size,cpu=10,memoryMb=256)},endpoint,cancelled,own).run(input){stage("running")}
            }
            // SIGKILL alone does not prove a CPU timeout (it can also be OOM or user code).
            val kind=when { program.exit.successful->RunKind.SUCCESS;program.exit.signal==24->RunKind.TIMEOUT;else->RunKind.RUNTIME }
            var diagnostic: CppBuildError?=null
            val fault=program.fault
            if(fault!=null && fault.signal==program.exit.signal) {
                try {
                    val addresses=CppFaultLocation.offsets(build.program,fault)
                    if(addresses.isNotEmpty()) {
                        stage("定位运行故障")
                        val symbols=CppCommandExecutor({NativeExecutionProcess(CppFaultLocation.arguments(runtime.symbolizer(),build.program,addresses),
                            snapshot.source.path,environment+"DEBUGINFOD_URLS=",cpu=5,memoryMb=768)},cancelled,own,5000,65536).run()
                        if(symbols.exit.successful)diagnostic=CppFaultLocation.diagnostic(snapshot,symbols.stdout)
                    }
                } catch(error:CancellationException) {throw error}
                catch (_:Exception) {if(cancelled())throw CancellationException()}
            }
            val message=program.exit.signal?.let {
                val text=if(kind==RunKind.TIMEOUT) "本地 C++ 超过 CPU 时间限制" else "本地 C++ 因信号 $it 结束"
                diagnostic?.let{"${it.file}:${it.line}: $text"}?:"$text（未能定位到项目源码）"
            }.orEmpty()
            RunResult(program.stdout,build.stderr+message,program.exit.code?.toString()?:"signal ${program.exit.signal}",0,
                errorLine=diagnostic?.line,engine=ENGINE,kind=kind,
                errorFile=diagnostic?.let{File(requireNotNull(args.getString("projectRoot")),it.file).path},errorSourceHash=diagnostic?.hash)
        }
    }
    override fun onBind(intent: Intent): IBinder=messenger.binder
    override fun onDestroy() {
        destroyed=true;active?.let(::cancel);worker.shutdownNow();handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    companion object { const val RUN=1;const val CANCEL=2;const val STATE=3;const val RESULT=4;const val ENGINE="C++ · 本地 Clang21" }
}
