package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.codepocket.ide.ui.IdeIcon
import com.codepocket.ide.ui.ReadOnlyDetails
import java.io.Closeable
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal data class InstalledAppUpdateVersion(val name:String,val code:Long) {
    val label get()="当前版本 $name ($code)"
    companion object {
        @Suppress("DEPRECATION") fun read(context:Context):InstalledAppUpdateVersion {
            val info=context.packageManager.getPackageInfo(context.packageName,0)
            return InstalledAppUpdateVersion(info.versionName?:"未知",if(android.os.Build.VERSION.SDK_INT>=28)info.longVersionCode else info.versionCode.toLong())
        }
    }
}

/** Owns only a manual metadata request and the shared read-only dialog. */
internal class AppUpdateDialog(private val activity:Activity,
    private val repository:AppUpdateRepository?=AppUpdateRepository.parse(BuildConfig.UPDATE_REPOSITORY),
    private val factory:(AppUpdateRepository)->AppUpdateSource={GitHubAppUpdateSource(it)}):Closeable {
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var dialog:AlertDialog?=null
    private var body:TextView?=null
    private var task:Future<*>?=null
    @Volatile private var source:AppUpdateSource?=null
    @Volatile private var closed=false
    private var generation=0L
    private var checking=false
    private var action:(()->Unit)?=null
    private var registered=false
    private val installed=InstalledAppUpdateVersion.read(activity)
    private val lifecycle=object:Application.ActivityLifecycleCallbacks {
        override fun onActivityDestroyed(owner:Activity){if(owner===activity)close()}
        override fun onActivityCreated(owner:Activity,state:Bundle?){}
        override fun onActivityStarted(owner:Activity){}
        override fun onActivityResumed(owner:Activity){}
        override fun onActivityPaused(owner:Activity){}
        override fun onActivityStopped(owner:Activity){}
        override fun onActivitySaveInstanceState(owner:Activity,state:Bundle){}
    }
    val isShowing get()=dialog?.isShowing==true
    private fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap{views(view.getChildAt(it))}else emptyList()
    fun show() {
        if(closed || isShowing || activity.isFinishing || activity.isDestroyed)return
        activity.application.registerActivityLifecycleCallbacks(lifecycle);registered=true
        dialog=ReadOnlyDetails.show(activity,"检查更新",repository?.let{"GitHub · ${it.identity}"}?:"CodePocket",
            installed.label+"\n\n正在检查正式版本…",IdeIcon.REFRESH,false,false,"更新检查结果","重新检查",Runnable{},Runnable{close()})
        body=views(dialog!!.window!!.decorView).filterIsInstance<TextView>().single{it.contentDescription=="更新检查结果"}.also{it.tag="app-update-result"}
        dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).apply{tag="app-update-action";setOnClickListener{action?.invoke()}}
        dialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).tag="app-update-close"
        checkNow()
    }
    private fun checkNow() {
        if(closed || checking)return
        val repo=repository
        if(repo==null){render(AppUpdateResult(AppUpdateKind.UNCONFIGURED));return}
        val current=AppUpdateVersion.parse(installed.name)
        if(current==null){body?.text=installed.label+"\n\n无法识别当前安装版本，请从正式发布页核对。";setAction("查看发布页"){external(repo.releasesPage)};return}
        checking=true;val token=++generation
        body?.text=installed.label+"\n\n正在检查正式版本…"
        action=null;dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.apply{visibility=View.VISIBLE;text="检查中…";isEnabled=false}
        task=worker.submit {
            val result=try {
                val next=factory(repo);source=next
                if(closed || token!=generation){next.cancel();return@submit}
                next.check(current)
            }catch(_:CancellationException){return@submit}
            catch(_:Exception){AppUpdateResult(AppUpdateKind.FAILED,failure=AppUpdateFailure.NETWORK)}
            if(!closed)handler.post{if(!closed && token==generation && dialog?.isShowing==true){checking=false;source=null;render(result)}}
        }
    }
    private fun render(result:AppUpdateResult) {
        val release=result.release
        body?.text=summary(result,installed)
        when(result.kind) {
            AppUpdateKind.AVAILABLE->setAction(actionLabel(result)){external(release!!.asset?.url?:release.page)}
            AppUpdateKind.NO_RELEASE->setAction("查看发布页"){repository?.let{external(it.releasesPage)}}
            AppUpdateKind.FAILED->setAction("重新检查",::checkNow)
            else->setAction(null,null)
        }
    }
    private fun setAction(label:String?,callback:(()->Unit)?) {
        action=callback;dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.apply{
            text=label;isEnabled=callback!=null;visibility=if(callback==null)View.GONE else View.VISIBLE
        }
    }
    private fun external(url:String) {
        if(closed)return
        runCatching{activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))}
            .onFailure{body?.append("\n\n没有可打开下载链接的浏览器。")}
    }
    override fun close() {
        if(closed)return
        closed=true;generation++;task?.cancel(true);runCatching{source?.cancel()};source=null
        worker.shutdownNow();handler.removeCallbacksAndMessages(null)
        if(registered){activity.application.unregisterActivityLifecycleCallbacks(lifecycle);registered=false}
        val current=dialog;dialog=null;body=null;if(current?.isShowing==true)current.dismiss()
    }
    companion object {
        /** One presentation for the manual dialog and the quiet startup check; no second wording set. */
        internal fun summary(result:AppUpdateResult,installed:InstalledAppUpdateVersion):String {
            val release=result.release
            val headline=when(result.kind) {
                AppUpdateKind.AVAILABLE->"发现新版本 ${release!!.version}"
                AppUpdateKind.CURRENT->"当前已是最新正式版本"
                AppUpdateKind.AHEAD->"当前安装版本比最新正式版本更新"
                AppUpdateKind.NO_RELEASE->"更新源尚无可访问的正式版本。可以打开发布页核对。"
                AppUpdateKind.UNCONFIGURED->"此安装包尚未配置正式更新源。"
                AppUpdateKind.FAILED->when(result.failure) {
                    AppUpdateFailure.RATE_LIMIT->"GitHub 请求次数已达上限，请稍后重试。"
                    AppUpdateFailure.TIMEOUT->"检查超时，请稍后重试。"
                    AppUpdateFailure.RESPONSE->"更新源返回的信息无法识别，请稍后重试。"
                    AppUpdateFailure.TOO_LARGE->"更新信息过大，请从发布页核对。"
                    else->"无法连接更新源，请检查网络后重试。"
                }
            }
            return buildString {
                append(installed.label).append("\n\n").append(headline)
                if(release!=null){
                    append("\n正式版本 ").append(release.version)
                    if(result.kind==AppUpdateKind.AVAILABLE && release.asset==null)append("\n暂无可直接选择的适配 APK，请查看发布页。")
                    release.asset?.let{if(result.kind==AppUpdateKind.AVAILABLE)append("\n").append(it.name).append(" · ").append((it.bytes+1048575)/1048576).append(" MB")}
                    append("\n\n更新说明\n").append(release.notes.ifBlank{"暂无更新说明。"})
                }
            }
        }
        internal fun actionLabel(result:AppUpdateResult):String?=when(result.kind) {
            AppUpdateKind.AVAILABLE->if(result.release!!.asset==null)"查看发布页" else "前往下载"
            AppUpdateKind.NO_RELEASE->"查看发布页"
            AppUpdateKind.FAILED->"重新检查"
            else->null
        }
    }
}
