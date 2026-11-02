package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.codepocket.ide.animation.DialogMotion
import com.codepocket.ide.ui.*
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal interface GitRemoteUiHost {
    val workspace:Workspace
    val projectOpen:Boolean
    val blocked:Boolean
    val dirty:Boolean
    val motion:Boolean
    fun save(next:()->Unit)
    fun guard():GitRemoteWriteGuard?
    fun reload(guard:GitRemoteWriteGuard)
    fun changed()
    fun status(message:String)
    fun reader(title:String,scope:String,text:String)
    fun size(dialog:AlertDialog,width:Int)
    fun chooseCloneDirectory()
    fun openClone(selector:String)
    fun busyChanged()
    fun exportRecovery()
}

/** All network actions are explicit. Forms retain only non-secret fields across recreation. */
internal class GitRemoteUiController(private val activity:Activity,private val host:GitRemoteUiHost,
    private val factory:()->GitRemoteWorkbench={GitRemoteWorkbench(activity)},
    private val vault:GitCredentialVault=GitCredentialVault(activity),saved:String?=null):Closeable {
    private val handler=Handler(Looper.getMainLooper())
    private val executor=Executors.newSingleThreadExecutor()
    private var backend:GitRemoteWorkbench?=null
    private var task:Future<*>?=null
    @Volatile private var generation=0L
    @Volatile private var closed=false
    private var dialog:AlertDialog?=null
    private var secretDialog:AlertDialog?=null
    private var clearSecret:(()->Unit)?=null
    private var choiceDialog:AlertDialog?=null
    private var menu:PopupMenu?=null
    private var scope=""
    private var url="";private var branch="main";private var name="项目克隆"
    private var form="";private var external=false
    private var pendingClone:JSONObject?=null
    private var fetched:GitRemoteRun?=null
    var busy=false
        private set
    init {runCatching{JSONObject(saved?:"{}").also {
        scope=it.optString("scope");url=it.optString("url");branch=it.optString("branch","main");name=it.optString("name","项目克隆")
        form=it.optString("form");external=it.optBoolean("external");pendingClone=it.optJSONObject("pendingClone")
    }}}
    fun checkpoint():String=JSONObject().put("scope",scope).put("url",url).put("branch",branch).put("name",name)
        .put("form",form).put("external",external).put("pendingClone",pendingClone?:JSONObject.NULL).toString()
    fun restoreForm(){val value=form;form="";if(value in setOf("clone","configure","fetch"))showForm(value)}
    private fun engine()=backend?:factory().also{backend=it}
    private fun localScope(){if(scope!=host.workspace.identity){scope=host.workspace.identity;url="";branch="main";fetched=null}}
    fun showMenu(anchor:View) {
        localScope();menu?.dismiss()
        val popup=DialogMotion.menu(activity,anchor,host.motion);menu=popup
        (popup as? WorkbenchMenu)?.setHeading("远端 Git")
        val actions=if(busy)listOf("取消当前操作") else listOf("克隆仓库…","配置远端…","获取远端更新…","快进当前分支…","推送当前分支…","凭据…","推送记录","恢复记录","导出恢复包","清理已确认推送记录…")
        actions.forEachIndexed{index,title->popup.menu.add(0,index,0,title).isEnabled=busy || index in setOf(0,5) || host.projectOpen
            ;(popup as? WorkbenchMenu)?.setItemAppearance(index,if(busy)IdeIcon.STOP else if(index==0)IdeIcon.FOLDER_OPEN else IdeIcon.SOURCE_CONTROL)}
        popup.setOnMenuItemClickListener {item->
            if(busy)cancel() else when(item.itemId) {
                0->showForm("clone")
                1->configuredForm("configure")
                2->configuredForm("fetch")
                3->fastForward()
                4->push()
                5->credentials()
                6->receipts()
                7->recovery()
                8->host.exportRecovery()
                9->confirm("清理已确认推送记录","清理本机已明确成功、拒绝或未发送的推送回执。结果未知和进行中的记录会保留。","清理") {
                    launch("正在清理已确认记录…",{engine,_->engine.clearConfirmedReceipts();GitRemoteRun(reply=localSuccess())}){host.status("已确认推送记录已清理")}
                }
            };true
        };popup.show()
    }
    private fun launch(title:String,work:(GitRemoteWorkbench,()->Boolean)->GitRemoteRun,done:(GitRemoteRun)->Unit) {
        if(closed || busy || host.blocked){host.status("请等待文件、运行或 Git 操作完成");return}
        val workspace=host.workspace;val token=++generation;val engine=engine()
        busy=true;host.busyChanged();host.status(title)
        task=executor.submit {
            val result=try{work(engine){closed || generation!=token || Thread.currentThread().isInterrupted}}
                catch(_:InterruptedException){return@submit}
                catch(error:GitCredentialFailure){GitRemoteRun(storageError=error.code)}
                catch(_:Exception){GitRemoteRun(storageError="storage")}
            handler.post {
                if(closed || generation!=token || host.workspace!==workspace)return@post
                busy=false;task=null;host.busyChanged()
                if(result.ok)done(result) else failure(result)
            }
        }
    }
    private fun failure(result:GitRemoteRun) {
        val code=result.error?:"backend";val message=errorText(code);host.status(message)
        host.reader("远端 Git 详情",host.workspace.title,message+"\n错误代码："+code+
            result.draftPaths.takeIf{it.isNotEmpty()}?.joinToString("\n",prefix="\n\n恢复草稿：\n").orEmpty()+
            if(result.receipt!=null)"\n\n可从远端 Git → 推送记录查看持久回执。" else "")
    }
    private fun configuredForm(kind:String) {
        if(!host.projectOpen)return
        val workspace=host.workspace
        launch("正在读取本地远端配置…",{engine,cancelled->engine.perform(workspace,"remote_info",cancelled=cancelled).let {
            if(kind=="configure" && it.error in setOf("url_invalid","remote_not_configured"))engine.perform(workspace,"snapshot",cancelled=cancelled) else it
        }}){result->
            url=result.reply.remote?.url?:url;branch=result.reply.state?.branch?.takeUnless{it=="HEAD"}?:branch;showForm(kind)
        }
    }
    private fun show(dialog:AlertDialog,width:Int=520) {
        this.dialog?.dismiss();this.dialog=dialog
        DialogMotion.show(dialog,host.motion);host.size(dialog,width)
        dialog.setOnDismissListener{if(this.dialog===dialog){this.dialog=null;form=""}}
    }
    private fun field(body:LinearLayout,label:String,value:String,key:String,limit:Int,change:(String)->Unit):EditText {
        body.addView(WorkbenchForms.caption(activity,label))
        return EditText(activity).apply {
            tag=key;contentDescription=label;setSingleLine();setText(value);filters=arrayOf(InputFilter.LengthFilter(limit))
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            body.addView(this,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=IdeControls.dp(activity,12)})
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){change(s.toString())}
                override fun afterTextChanged(s:Editable?){}
            })
        }
    }
    private fun validated():JSONObject=JSONObject().put("url",GitRemoteAddress.canonical(url.trim())).put("branch",GitRemoteProtocol.branch(branch.trim()))
    fun showForm(kind:String) {
        if(closed || busy || host.blocked)return
        localScope()
        val body=WorkbenchForms.body(activity)
        body.addView(WorkbenchForms.sectionHeading(activity,when(kind){"clone"->"克隆仓库";"configure"->"配置远端";else->"获取远端更新"},
            if(kind=="clone")"HTTPS · 新的空目录" else host.workspace.title,IdeIcon.SOURCE_CONTROL))
        val address=field(body,"仓库 HTTPS 地址",url,"git-remote-url",2048){url=it}
        val selected=field(body,"远端分支",branch,"git-remote-branch",255){branch=it}
        if(kind=="clone") {
            field(body,"内部项目名称",name,"git-clone-name",128){name=it}
            body.addView(RadioGroup(activity).apply {
                orientation=RadioGroup.VERTICAL
                addView(RadioButton(activity).apply{id=View.generateViewId();text="新建内部项目（卸载会清除）";tag="git-clone-private";isChecked=!external;setOnClickListener{external=false}})
                addView(RadioButton(activity).apply{id=View.generateViewId();text="选择系统授权的空目录";tag="git-clone-saf";isChecked=external;setOnClickListener{external=true}})
            })
        }
        body.addView(Button(activity).apply{tag="git-remote-branches";text="查询远端分支";setOnClickListener {
            val fields=try{validated()}catch(_:Exception){address.error="请输入完整的 HTTPS 仓库地址和有效分支";return@setOnClickListener}
            val target=fields.getString("url")
            launch("正在查询远端分支…",{engine,cancelled->engine.branches(target,vault.load(target),cancelled)}){result->
                if(!address.isAttachedToWindow || runCatching{GitRemoteAddress.canonical(url.trim())}.getOrNull()!=target)return@launch
                val names=result.reply.remote!!.branches.keys.toTypedArray()
                if(names.isEmpty()){host.status("远端尚无分支，可使用当前分支名克隆空仓库");return@launch}
                val picker=AlertDialog.Builder(activity).setTitle("选择远端分支").setItems(names){_,index->selected.setText(names[index])}.setNegativeButton("取消",null).create()
                choiceDialog=picker;DialogMotion.show(picker,host.motion);host.size(picker,480)
            }
        }})
        body.addView(Button(activity).apply{tag="git-remote-credentials";text="凭据…";setOnClickListener{credentials()}})
        val confirmation=WorkbenchForms.caption(activity,when(kind){"fetch"->"只更新 Git 对象和远端跟踪引用，编辑区和本地分支保持当前版本。";"configure"->"设置 origin 和当前分支的跟踪目标，不连接网络。";else->"下载到独立项目，现有项目文件和恢复草稿会保留。"})
        body.addView(confirmation)
        val dialog=AlertDialog.Builder(activity).setTitle("远端 Git").setView(WorkbenchForms.scroll(activity,body))
            .setPositiveButton(if(kind=="clone")"选择目标并克隆" else if(kind=="fetch")"获取" else "保存配置",null).setNegativeButton("取消",null).create()
        show(dialog);form=kind
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val fields=try{validated().also{if(kind=="clone")Workspace.validateName(name.trim())}}catch(_:Exception){address.error="请检查 HTTPS 地址、分支和项目名称";return@setOnClickListener}
            url=fields.getString("url");branch=fields.getString("branch")
            dialog.dismiss()
            if(kind=="clone") {
                pendingClone=JSONObject(fields.toString()).put("name",name.trim())
                if(external)host.chooseCloneDirectory() else cloneTo(null)
            } else {
                val workspace=host.workspace
                launch(if(kind=="fetch")"正在获取远端更新…" else "正在保存远端配置…",{engine,cancelled->
                    engine.perform(workspace,kind,fields,credentials=if(kind=="fetch")vault.load(url) else null,cancelled=cancelled)
                }){result->fetched=if(kind=="fetch")result else null;host.changed();host.status(if(kind=="fetch")"已获取 ${branch} · ${result.reply.remote?.head?.take(7)?:"空分支"}" else "远端配置已保存")}
            }
        }
    }
    fun pickerCancelled(){pendingClone=null}
    fun cloneTo(uri:Uri?) {
        if(closed || busy || host.blocked){host.status("请等待当前文件或 Git 操作完成，再选择克隆目标");return}
        val request=pendingClone?:return;pendingClone=null
        val targets=GitCloneTargets(activity);var target:GitCloneTarget?=null
        launch("正在克隆仓库…",{engine,cancelled->
            val destination=if(uri==null)targets.createPrivate(request.getString("name")) else targets.fromTree(uri);target=destination
            val fields=JSONObject(request.toString());fields.remove("name")
            engine.perform(destination.store,destination.identity,"clone",fields,credentials=vault.load(fields.getString("url")),cancelled=cancelled)
                .also{if(it.ok)targets.completed(destination)}
        }){host.status("克隆完成");host.openClone(target!!.selector)}
    }
    private fun fetchedOrMessage():GitRemoteRun?=fetched?.takeIf{it.reply.remote?.url==url && it.reply.remote.branch==branch}?:run {
        host.status("请先明确获取目标分支，再执行快进或推送");null
    }
    private fun confirm(title:String,text:String,positive:String,action:()->Unit) {
        show(AlertDialog.Builder(activity).setTitle(title).setView(WorkbenchForms.details(activity,text,true))
            .setPositiveButton(positive){_,_->if(!host.blocked&&!busy)action()}.setNegativeButton("取消",null).create())
    }
    private fun fastForward() {
        if(host.blocked || busy)return
        if(host.dirty){host.save{if(!host.dirty)fastForward()};return}
        val fetch=fetchedOrMessage()?:return;val state=fetch.reply.state!!;val remote=fetch.reply.remote!!
        if(state.head==null || remote.head==null){host.status("当前或远端分支尚无提交，不能执行快进");return}
        val guard=host.guard()?:return
        confirm("快进当前分支",url+"\n远端分支："+branch+"\n本地提交："+state.head+"\n目标提交："+remote.head+
            "\n\n仅允许干净工作树安全快进；会更新项目文件，不进行合并或重置。","快进") {
            val workspace=host.workspace
            launch("正在安全快进…",{engine,cancelled->engine.perform(workspace,"fast_forward",validated().put("expected_head",state.head)
                .put("expected_remote",remote.head),state.snapshot,guard=guard,cancelled=cancelled)}) {
                fetched=null;host.changed();host.status("当前分支已快进");host.reload(guard)
            }
        }
    }
    private fun push() {
        if(host.blocked || busy)return
        val fetch=fetchedOrMessage()?:return;val workspace=host.workspace
        launch("正在核对本地提交…",{engine,cancelled->engine.perform(workspace,"snapshot",cancelled=cancelled)}){current->
            val state=current.reply.state!!
            if(state.head==null){host.status("请先完成本地提交");return@launch}
            val remote=fetch.reply.remote!!
            confirm("推送当前分支",url+"\n远端分支："+branch+"\n已获取的远端提交："+remote.head+"\n待推送的本地提交："+state.head+
                "\n\n只推送这一分支的已提交历史。远端变化或非快进会拒绝，不允许强制推送。","推送") {
                launch("正在推送…",{engine,cancelled->engine.perform(workspace,"push",validated().put("expected_head",state.head)
                    .put("expected_remote",remote.head?:JSONObject.NULL),state.snapshot,vault.load(url),cancelled=cancelled)}){result->
                    fetched=null;host.status(if(result.reply.outcome=="up_to_date")"远端已经是此提交" else "推送完成，回执已保存")
                }
            }
        }
    }
    private fun credentials() {
        val target=try{GitRemoteAddress.canonical(url.trim())}catch(_:Exception){host.status("请先在克隆或远端配置中输入 HTTPS 仓库地址");return}
        val body=WorkbenchForms.body(activity);body.addView(WorkbenchForms.location(activity,"凭据仅用于此仓库",target,IdeIcon.SOURCE_CONTROL))
        val username=field(body,"用户名","","git-credential-user",128){}
        val token=field(body,"访问令牌","","git-credential-token",4096){}.apply {
            inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled=false;setSaveFromParentEnabled(false)
            if(android.os.Build.VERSION.SDK_INT>=26){setAutofillHints(null);importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS}
        }
        username.isSaveEnabled=false;username.setSaveFromParentEnabled(false)
        val remember=CheckBox(activity).apply{text="加密保存在此设备";tag="git-credential-remember";isChecked=false};body.addView(remember)
        body.addView(WorkbenchForms.caption(activity,"默认仅供当前应用会话使用。公共仓库可不填写；此处使用令牌，不使用网页登录密码。"))
        val credentialDialog=AlertDialog.Builder(activity).setTitle("Git 凭据").setView(WorkbenchForms.scroll(activity,body))
            .setPositiveButton("使用凭据",null).setNeutralButton("清除凭据",null).setNegativeButton("关闭",null).create()
        // Keep a non-secret remote form underneath; closing credentials returns to it.
        clearSecret?.invoke();secretDialog?.dismiss();secretDialog=credentialDialog
        clearSecret={username.setText("");token.setText("")}
        DialogMotion.show(credentialDialog,host.motion);host.size(credentialDialog,520)
        credentialDialog.setOnDismissListener{username.setText("");token.setText("");if(secretDialog===credentialDialog){secretDialog=null;clearSecret=null}}
        credentialDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value=try{GitCredentials(username.text.toString().trim(),token.text.toString())}catch(_:Exception){token.error="请输入有效的用户名和访问令牌";return@setOnClickListener}
            val keep=remember.isChecked
            launch("正在保存凭据…",{_,_->vault.save(target,value,keep);GitRemoteRun(reply=localSuccess())}) {
                credentialDialog.dismiss();host.status(if(keep)"凭据已加密保存" else "凭据仅在当前会话中使用")
            }
        }
        credentialDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            val choices=arrayOf("清除此仓库凭据","清除所有 Git 凭据")
            val picker=AlertDialog.Builder(activity).setTitle("清除凭据").setItems(choices){_,index->
                launch("正在清除凭据…",{_,_->if(index==0)vault.clear(target) else vault.clearAll();GitRemoteRun(reply=localSuccess())}) {
                    credentialDialog.dismiss();host.status("凭据已清除")
                }
            }.setNegativeButton("取消",null).create();choiceDialog=picker;DialogMotion.show(picker,host.motion);host.size(picker,480)
        }
    }
    private fun localSuccess()=GitRemoteReply(state=GitState("0".repeat(64),false,null,null,emptyMap(),emptyList(),emptyList()))
    private fun receipts() {
        val identity=host.workspace.identity
        launch("正在读取推送记录…",{engine,_->
            val records=engine.receipts(identity)
            GitRemoteRun(reply=localSuccess(),detail=records.joinToString("\n\n"){it.url+"\n"+it.branch+" · "+it.head+"\n状态："+it.state+it.error?.let{code->" · "+errorText(code)}.orEmpty()}.ifEmpty{"尚无推送记录"})
        }){host.reader("推送记录",host.workspace.title,it.detail.orEmpty())}
    }
    private fun recovery() {
        val workspace=host.workspace
        launch("正在读取恢复记录…",{engine,_->GitRemoteRun(reply=localSuccess(),detail=engine.recoveryDescription(workspace.identity,workspace.gitStore()))}) {
            host.reader("Git 恢复记录",workspace.title,it.detail.orEmpty())
        }
    }
    fun cancel(){val active=busy;generation++;task?.cancel(true);task=null;backend?.close();backend=null;busy=false
        if(active){host.busyChanged();host.status("已请求取消；推送结果请核对持久回执")}}
    fun detach(){cancel();dialog?.dismiss();dialog=null;clearSecret?.invoke();clearSecret=null;secretDialog?.dismiss();secretDialog=null;choiceDialog?.dismiss();choiceDialog=null;menu?.dismiss();menu=null;fetched=null}
    override fun close(){closed=true;detach();vault.forgetSession();handler.removeCallbacksAndMessages(null);executor.shutdownNow()}
    companion object {
        fun errorText(code:String)=when(code) {
            "authentication"->"认证失败，请检查仓库权限和访问令牌"
            "credential_save","credential_unreadable","credential_key"->"无法使用设备上的加密凭据，请重新输入或明确清除旧凭据"
            "credential_clear"->"凭据清除失败，请重试"
            "push_unknown"->"未取得明确推送结果。远端可能已接受，请重新获取并核对提交，不要直接重试"
            "remote_changed","stale","workspace_changed","editor_changed"->"确认后内容或远端发生变化，请重新获取并核对"
            "drafts_pending"->"项目有恢复草稿，请先打开这些文件处理后再快进"
            "dirty"->"工作树或暂存区有改动，请先完成本地提交"
            "non_fast_forward","needs_fetch","remote_rejected"->"不能安全快进或推送，请先获取并核对分支历史"
            "target_not_empty"->"克隆目标必须是新的空目录"
            "recovery_required"->"上次 Git 写入需要检查恢复记录；备份仍保留"
            "tls","tls_policy"->"HTTPS 证书校验失败"
            "network","remote_http","timeout"->"远端连接失败或超时，请检查地址与网络"
            "remote_not_configured"->"请先配置 origin 的 HTTPS 地址"
            "not_repository"->"请先在源代码管理中初始化仓库"
            "cancelled"->"操作已取消"
            "branch_not_found","branch"->"远端分支不存在或分支名称无效"
            "busy"->"另一项 Git 操作仍在进行"
            else->"远端 Git 操作未完成，文件和恢复记录请按详情检查"
        }
    }
}
