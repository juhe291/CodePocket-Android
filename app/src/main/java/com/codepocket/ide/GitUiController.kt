package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import com.codepocket.ide.animation.DialogMotion
import com.codepocket.ide.ui.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

internal interface GitUiHost {
    val workspace:Workspace
    val projectOpen:Boolean
    val blocked:Boolean
    val currentPath:String?
    val currentText:String
    val dirty:Boolean
    val visible:Boolean
    val motion:Boolean
    fun save(next:()->Unit)
    fun retainDraft(next:()->Unit)
    fun open(path:String,next:()->Unit)
    fun draftKey(path:String):String?
    fun readDraft(key:String):Draft?
    fun status(message:String)
    fun changed()
    fun closePanel()
    fun openProject()
    fun reader(title:String,scope:String,text:String)
    fun size(dialog:AlertDialog,width:Int)
    fun exportRecovery()
}

/** Owns source-control drafts/requests; Main retains the shared sidebar host and editor transactions. */
internal class GitUiController(private val activity:Activity,private val host:GitUiHost,
    private val factory:()->GitWorkbench,private val remote:((View)->Unit)?=null):Closeable {
    private val prefs=activity.getSharedPreferences("codepocket_v2",Context.MODE_PRIVATE)
    private val handler=Handler(Looper.getMainLooper())
    private val executor=Executors.newSingleThreadExecutor()
    private var backend:GitWorkbench?=null
    private var task:Future<*>?=null
    private var cancelled=AtomicBoolean(true)
    @Volatile private var generation=0L
    @Volatile private var closed=false
    private var panel:SourceControlPanel?=null
    private var response:GitResponse?=null
    private var scope=""
    private var scroll=0
    private var name="";private var email="";private var message=""
    private var restoreCommit=false
    private var commitDialog:AlertDialog?=null
    private var historyMenu:android.widget.PopupMenu?=null
    var busy=false
        private set
    private fun draft()=JSONObject().put("name",name).put("email",email).put("message",message).toString()
    private fun saveDraft(){if(scope.isNotEmpty())prefs.edit().putString("gitCommitDraft:$scope",draft()).apply()}
    fun checkpoint():String {
        saveDraft();scroll=panel?.checkpoint()?:scroll
        return JSONObject().put("scope",scope).put("draft",draft()).put("scroll",scroll).put("commitOpen",commitDialog?.isShowing==true).toString()
    }
    fun bind(workspace:Workspace,saved:String?=null) {
        val same=scope==workspace.identity;detach()
        scope=workspace.identity;if(!same)scroll=0
        val state=runCatching{JSONObject(saved?:"{}")}.getOrDefault(JSONObject())
        val checkpoint=state.takeIf{it.optString("scope")==scope}
        val value=runCatching{JSONObject(checkpoint?.optString("draft")?:prefs.getString("gitCommitDraft:$scope",null)?:"{}")}.getOrDefault(JSONObject())
        name=value.optString("name",prefs.getString("gitAuthorName","").orEmpty()).take(128)
        email=value.optString("email",prefs.getString("gitAuthorEmail","").orEmpty()).take(254);message=value.optString("message").take(4000)
        restoreCommit=false
        checkpoint?.let{scroll=it.optInt("scroll").coerceAtLeast(0);restoreCommit=it.optBoolean("commitOpen")}
        response=null
    }
    fun view(palette:IdePalette):View = panel?:SourceControlPanel(activity,palette,::refresh,
        {response?.state?.let{launch("init",expected=it.snapshot)}},::inspect,::change,::showCommit,::history,
        host::closePanel,host::openProject,::errorDetails,::recoveryDetails,host::exportRecovery,remote).also{panel=it;it.restore(scroll);render()}
    fun inputChanged(){panel?.setUnsaved(host.projectOpen&&host.dirty)}
    private fun render(){panel?.render(host.workspace.title,host.projectOpen,response,busy,host.dirty)}
    fun refresh(){if(host.projectOpen)launch("status") else render()}
    private fun engine()=backend?:factory().also{backend=it}
    private fun launch(action:String,fields:JSONObject=JSONObject(),expected:String?=null,saved:Map<String,String> = emptyMap(),done:((GitResponse)->Unit)?=null):Boolean {
        if(closed || busy || host.blocked || !host.projectOpen){if(!closed)host.status("请等待当前文件或 Git 操作完成");return false}
        val workspace=host.workspace;val token=++generation;val cancellation=AtomicBoolean(false);cancelled=cancellation
        val engine=engine();busy=true;render()
        task=executor.submit {
            val result=try{engine.perform(workspace,action,fields,expected,saved){closed || generation!=token || cancellation.get()}}
                catch(_:InterruptedException){return@submit}
            handler.post {
                if(closed || token!=generation || host.workspace!==workspace)return@post
                busy=false;task=null;response=result;render()
                if(result.ok) {
                    if(action in GitProtocol.writes){host.changed();host.status(when(action){"init"->"Git 仓库已初始化";"stage"->"已暂存";"unstage"->"已取消暂存";else->"Git 提交完成"})}
                    done?.invoke(result)
                    if(restoreCommit && action=="status"){restoreCommit=false;if(result.state!!.stagedPaths.isNotEmpty()&&host.visible)showCommit()}
                } else host.status(SourceControlPanel.errorText(result.error?:"backend"))
            }
        }
        return true
    }
    private fun inspect(change:GitChange) {
        launch("diff",JSONObject().put("path",change.path).put("staged",change.staged)){result->if(host.visible)
            host.reader("${if(change.staged)"暂存区" else "工作树"}差异",host.workspace.title+" · "+change.path,result.text.orEmpty())}
    }
    private fun change(change:GitChange) {
        if(busy || host.blocked)return
        val state=response?.state?:return
        if(change.staged){launch("unstage",JSONObject().put("paths",JSONArray().put(change.path)),state.snapshot);return}
        val workspace=host.workspace
        fun stage(saved:Map<String,String> = emptyMap()){if(host.workspace===workspace)launch("stage",JSONObject().put("paths",JSONArray().put(change.path)),state.snapshot,saved)}
        if(host.currentPath==change.path) {
            fun savedStage(){if(host.workspace===workspace && host.currentPath==change.path && !host.dirty)stage(mapOf(change.path to DocumentTree.digest(host.currentText)))}
            if(host.dirty && change.path !in response!!.sourceFiles) {
                DialogMotion.show(AlertDialog.Builder(activity).setTitle("暂存已删除文件")
                    .setMessage("当前编辑仍有未保存内容。可以保留恢复草稿，仅暂存磁盘上的删除；工作正文不会丢弃。")
                    .setPositiveButton("保留草稿后暂存"){_,_->host.retainDraft{stage()}}.setNegativeButton("取消",null).create(),host.motion)
            } else if(host.dirty)host.save(::savedStage) else if(change.path in response!!.sourceFiles)savedStage() else stage()
            return
        }
        val key=host.draftKey(change.path)
        if(key==null){stage();return}
        val expected=state.snapshot
        WorkspaceIo.executor.execute {
            val draft=runCatching{host.readDraft(key)}
            handler.post {
                if(closed || host.workspace!==workspace || busy || response?.state?.snapshot!=expected)return@post
                val value=draft.getOrNull()
                if(draft.isFailure){host.status("无法核对恢复草稿，请先打开文件检查");return@post}
                if(value!=null && (value.path!=change.path || DocumentTree.digest(value.text)!=response?.sourceFiles?.get(change.path)?.sha256)) {
                    DialogMotion.show(AlertDialog.Builder(activity).setTitle("文件另有恢复草稿")
                        .setMessage(change.path+"\n请选择先打开并保存，或仅暂存已保存的磁盘版本；恢复草稿会保留。")
                        .setPositiveButton("打开并保存"){_,_->host.open(change.path){this@GitUiController.change(change)}}
                        .setNeutralButton("仅暂存磁盘版本"){_,_->stage()}.setNegativeButton("取消",null).create(),host.motion)
                } else stage()
            }
        }
    }
    private fun showCommit() {
        if(busy || host.blocked)return
        val state=response?.state?:return;if(state.stagedPaths.isEmpty())return
        val workspace=host.workspace;val body=WorkbenchForms.body(activity)
        body.addView(WorkbenchForms.caption(activity,"${workspace.title}\n将提交已暂存的 ${state.stagedPaths.size} 个文件；未暂存和未保存内容保留。"))
        fun field(label:String,value:String,key:String,limit:Int,multiline:Boolean=false):EditText {
            body.addView(WorkbenchForms.caption(activity,label))
            return EditText(activity).apply {
                setText(value);tag=key;contentDescription=label;textSize=14f;setSingleLine(!multiline)
                filters=arrayOf(InputFilter.LengthFilter(limit));minHeight=IdeControls.dp(activity,48)
                if(multiline){minLines=3;maxLines=6;inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE}
                body.addView(this,LinearLayout.LayoutParams(-1,-2))
            }
        }
        val author=field("提交姓名",name,"git-author",128);val address=field("提交邮箱",email,"git-email",254)
        address.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        val text=field("提交说明",message,"git-message",4000,true)
        val watcher=object:TextWatcher {
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){name=author.text.toString();email=address.text.toString();message=text.text.toString()}
            override fun afterTextChanged(s:Editable?){}
        }
        listOf(author,address,text).forEach{it.addTextChangedListener(watcher)}
        val dialog=DialogMotion.show(AlertDialog.Builder(activity).setTitle("提交 · ${state.branch}")
            .setView(WorkbenchForms.scroll(activity,body)).setPositiveButton("提交暂存区",null).setNegativeButton("取消",null).create(),host.motion)
        commitDialog=dialog;host.size(dialog,480)
        dialog.setOnDismissListener{if(commitDialog===dialog)commitDialog=null;saveDraft()}
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            name=author.text.toString();email=address.text.toString();message=text.text.toString();saveDraft()
            when {
                name.isBlank() || name.any{it in "<>\r\n\u0000"}->author.error="请填写有效姓名"
                !Regex("[^\\s<>@]+@[^\\s<>@]+").matches(email)->address.error="请填写有效邮箱"
                message.isBlank()->text.error="请填写提交说明"
                host.workspace!==workspace->{dialog.dismiss();host.status("工作区已切换")}
                else->if(launch("commit",JSONObject().put("name",name).put("email",email).put("message",message),state.snapshot){
                    prefs.edit().putString("gitAuthorName",name).putString("gitAuthorEmail",email).apply();message="";saveDraft()
                })dialog.dismiss()
            }
        }
    }
    private fun history() {
        launch("history"){result->if(host.visible) {
            val anchor=panel?.findViewWithTag<View>("git-history")?:return@launch
            if(result.commits.isEmpty()){host.reader("提交历史",host.workspace.title,"尚无提交");return@launch}
            val popup=DialogMotion.menu(activity,anchor,host.motion);historyMenu=popup
            (popup as? WorkbenchMenu)?.setHeading("提交历史 · ${result.state?.branch}")
            result.commits.forEachIndexed{index,item->popup.menu.add(0,index,0,item.id.take(7)+"  "+item.message.lineSequence().firstOrNull().orEmpty())
                ;(popup as? WorkbenchMenu)?.setItemAppearance(index,IdeIcon.SOURCE_CONTROL)}
            popup.setOnMenuItemClickListener{choice->val item=result.commits[choice.itemId]
                launch("show_commit",JSONObject().put("id",item.id)){details->if(host.visible)details.commit?.let{commit->
                    host.reader("提交 ${commit.id.take(7)}",host.workspace.title,commit.id+"\n"+commit.author+"\n\n"+commit.message)}};true}
            popup.show()
        }}
    }
    private fun errorDetails(){host.reader("Git 操作详情",host.workspace.title,
        SourceControlPanel.errorText(response?.error?:"backend")+"\n错误代码："+response?.error+response?.detail?.let{"\n\n$it"}.orEmpty())}
    private fun recoveryDetails() {
        val workspace=host.workspace;val token=generation;val engine=engine()
        executor.execute {
            val value=runCatching{engine.recoveryDescription(workspace)}
            handler.post{if(!closed && generation==token && host.workspace===workspace && host.visible)
                host.reader("Git 恢复记录",workspace.title,value.getOrElse{it.message?:"无法读取恢复记录"})}
        }
    }
    fun export(workspace:Workspace,output:java.io.OutputStream) {engine().exportRecovery(workspace,output)}
    fun detach() {
        saveDraft();scroll=panel?.checkpoint()?:scroll;panel=null
        generation++;cancelled.set(true);task?.cancel(true);task=null;busy=false
        backend?.close();backend=null;commitDialog?.dismiss();commitDialog=null;historyMenu?.dismiss();historyMenu=null
    }
    override fun close(){closed=true;detach();handler.removeCallbacksAndMessages(null);executor.shutdownNow()}
}
