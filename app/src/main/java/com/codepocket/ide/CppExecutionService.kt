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
            var failureStage="校验终端与运行副本"
            var programBegan:Long?=null
            fun elapsed()=(System.nanoTime()-(programBegan?:began))/1000000
            fun reason(error:Throwable)=error.javaClass.simpleName+": "+(error.message?.takeIf{it.isNotBlank()}?:"未提供异常说明")
            val result=try {
                executeRun(request.args,{destroyed||request.cancelled.get()}, { process ->
                    synchronized(ownership) {
                        if(process==null) {
                            if(processOwner===request){ownedProcess=null;processOwner=null}
                        } else if(active!==request || destroyed || request.cancelled.get())process.close()
                        else {ownedProcess=process;processOwner=request}
                    }
                }, { stage ->
                    failureStage=stage
                    if(stage=="running"&&programBegan==null)programBegan=System.nanoTime()
                    if(!destroyed&&!request.cancelled.get())runCatching { request.reply.send(Message.obtain(null,STATE).apply {
                        data=Bundle().apply{putLong("id",request.id);putString("stage",stage)}
                    }) }
                })
            } catch (_: CancellationException) { RunResult("","已停止本地 C++ 运行","130",elapsed(),engine=ENGINE,kind=RunKind.CANCELLED) }
            catch (error: TimeoutException) { RunResult("",error.message.orEmpty(),"124",elapsed(),engine=ENGINE,kind=RunKind.TIMEOUT) }
            catch (error: Exception) { RunResult("","本地 C++ 在“$failureStage”阶段执行失败：${reason(error)}","—",elapsed(),engine=ENGINE,kind=RunKind.SERVICE) }
            catch (error: LinkageError) { RunResult("","本地 C++ 在“$failureStage”阶段原生运行器不可用：${reason(error)}","—",elapsed(),engine=ENGINE,kind=RunKind.SERVICE) }
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
                    response(request,completed)
                }
            }
        }
    }
    private fun executeNative(args: Bundle, cancelled: () -> Boolean, own: (ExecutionProcess?) -> Unit, stage: (String) -> Unit): RunResult {
        val preparationBegan=System.nanoTime()
        fun preparationMs()=(System.nanoTime()-preparationBegan)/1000000
        stage("校验运行副本")
        val directory=File(requireNotNull(args.getString("job"))).absoluteFile
        val snapshot=CppRunSnapshot.open(File(cacheDir,"cpp-runs").canonicalFile,directory,requireNotNull(args.getString("entry")))
        return snapshot.use {
            val input=args.getString("stdin").orEmpty();require(input.length<=20000)
            stage("连接真实终端")
            @Suppress("DEPRECATION")
            val tty=listOf("ttyIn","ttyOut","ttyErr").map{requireNotNull(args.getParcelable<ParcelFileDescriptor>(it))}
            tty.forEach{require(NativeTerminalIO.dimensions(it.fd)!=null){"本地 C++ 需要真实终端"}}
            val native=File(applicationInfo.nativeLibraryDir).canonicalFile
            val environment=listOf("PATH=/system/bin","LD_LIBRARY_PATH=${native.path}","HOME=${snapshot.source.path}","TMPDIR=${snapshot.build.path}","LANG=C.UTF-8")
            stage("准备本地工具链")
            val builder=CppLocalBuilder({step,root->NativeExecutionProcess(step.arguments,root.path,environment)},own,cancelled)
            val build=try { builder.build(snapshot,CppBuildScope.valueOf(args.getString("scope",CppBuildScope.CURRENT.name)),runtime.toolchain(),stage) }
            catch(error:IllegalArgumentException) {
                if(error.message.orEmpty().contains("main"))return@use RunResult("",error.message.orEmpty(),"1",preparationMs(),engine=ENGINE,kind=RunKind.COMPILE)
                throw error
            }
            if(build.program==null) {
                val diagnostic=build.error
                return@use RunResult(build.stdout,build.stderr,build.exit.code?.toString()?:"signal ${build.exit.signal}",preparationMs(),
                    errorLine=diagnostic?.line,engine=ENGINE,kind=build.failureKind,
                    errorFile=diagnostic?.let{File(requireNotNull(args.getString("projectRoot")),it.file).path},errorSourceHash=diagnostic?.hash)
            }
            if(cancelled())throw CancellationException()
            android.system.Os.chmod(build.program.path,292) // 0444 before dlopen by the installed runner.
            val fixedInput=args.getBoolean("fixedInput")
            val caseFiles=if(fixedInput)CppCaseFiles.create(snapshot.build,input)else null
            val program=NativeTerminalEndpoint(tty[0].fd,tty[1].fd).use { endpoint ->
                CppTerminalRunner({size->NativeExecutionProcess(caseFiles?.arguments(runtime.installedRunner(),build.program)?:listOf(runtime.installedRunner().path,build.program.path),
                    snapshot.source.path,environment,terminal=size,cpu=if(fixedInput)6 else 10,memoryMb=256)},endpoint,cancelled,own,
                    timeoutMs=if(fixedInput)10000 else 300000).run(if(fixedInput)"" else input,interactive=!fixedInput){stage("running")}
            }
            val caseOutput=caseFiles?.read()
            // SIGKILL alone does not prove a CPU timeout (it can also be OOM or user code).
            val kind=when { program.exit.memoryExceeded||caseOutput?.limited==true->RunKind.RUNTIME;program.exit.successful->RunKind.SUCCESS;program.exit.signal==24->RunKind.TIMEOUT;else->RunKind.RUNTIME }
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
            val message=if(program.exit.memoryExceeded)"本地 C++ 实际驻留内存超过预算，已停止该程序进程组" else program.exit.signal?.let {
                val text=if(kind==RunKind.TIMEOUT) "本地 C++ 超过 CPU 时间限制" else "本地 C++ 因信号 $it 结束"
                diagnostic?.let{"${it.file}:${it.line}: $text"}?:"$text（未能定位到项目源码）"
            }.orEmpty()
            val caseLimit=if(caseOutput?.limited==true)"\n用例输出超过 50000 字符，只保留前 50000 字符" else ""
            RunResult(caseOutput?.stdout?:program.stdout,build.stderr+(caseOutput?.stderr.orEmpty())+
                (if(fixedInput)program.stdout else "")+message+caseLimit,program.exit.code?.toString()?:"signal ${program.exit.signal}",program.elapsedMs,
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
