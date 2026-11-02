package com.codepocket.ide.ui

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.codepocket.ide.GitResponse
import com.codepocket.ide.GitState

internal data class GitChange(val path:String,val staged:Boolean,val kind:String)

/** One native source-control surface, rehosted in the existing drawer/sidebar. */
internal class SourceControlPanel(context:Context,private val palette:IdePalette,
    private val refresh:()->Unit,private val initialize:()->Unit,private val inspect:(GitChange)->Unit,
    private val change:(GitChange)->Unit,private val commit:()->Unit,private val history:()->Unit,
    private val close:()->Unit,private val open:()->Unit,private val details:()->Unit,
    private val recovery:()->Unit,private val exportRecovery:()->Unit):LinearLayout(context) {
    private val content=LinearLayout(context).apply{orientation=VERTICAL}
    private val scroll=ScrollView(context).apply{isFillViewport=false;addView(content)}
    private val scope=text("",12f,palette.muted)
    private val dirtyHint=text("当前文件有未保存内容；暂存该文件时会先保存",12f,palette.muted).apply{setPadding(dp(12),0,dp(12),dp(8));visibility=View.GONE}
    private var pendingScroll:Int?=null
    private val refreshButton=IdeControls.icon(context,palette,IdeIcon.REFRESH,"刷新源代码管理",refresh).apply{tag="git-refresh"}
    private val historyButton=IdeControls.icon(context,palette,IdeIcon.SOURCE_CONTROL,"提交历史",history).apply{tag="git-history"}
    init {
        orientation=VERTICAL;setBackgroundColor(palette.surface);contentDescription="源代码管理侧栏"
        val heading=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),0,0,0)}
        heading.addView(text("源代码管理",12f,palette.muted).apply{setSingleLine();ellipsize=TextUtils.TruncateAt.END},LayoutParams(0,dp(48),1f))
        heading.addView(refreshButton,LayoutParams(dp(48),dp(48)));heading.addView(historyButton,LayoutParams(dp(48),dp(48)))
        heading.addView(IdeControls.icon(context,palette,IdeIcon.CLOSE,"关闭源代码管理",close),LayoutParams(dp(48),dp(48)))
        addView(heading,LayoutParams(-1,-2));scope.setPadding(dp(12),0,dp(12),dp(10));scope.maxLines=2;scope.ellipsize=TextUtils.TruncateAt.MIDDLE
        addView(scope,LayoutParams(-1,-2));addView(dirtyHint,LayoutParams(-1,-2));addView(scroll,LayoutParams(-1,0,1f))
    }
    private fun dp(value:Int)=IdeControls.dp(context,value)
    private fun text(value:String,size:Float=13f,color:Int=palette.foreground,bold:Boolean=false)=TextView(context).apply {
        text=value;textSize=size;setTextColor(color);if(bold)setTypeface(null,Typeface.BOLD);includeFontPadding=false
    }
    private fun action(title:String,key:String,enabled:Boolean=true,callback:()->Unit) {
        content.addView(IdeControls.key(context,palette,title,title,callback).apply{
            tag=key;isEnabled=enabled;alpha=if(enabled)1f else .5f
        },LayoutParams(-1,-2).apply{setMargins(dp(8),dp(4),dp(8),dp(4))})
    }
    private fun note(value:String)=content.addView(text(value,12f,palette.muted).apply{setPadding(dp(12),dp(12),dp(12),dp(12))},LayoutParams(-1,-2))
    fun checkpoint()=pendingScroll?:scroll.scrollY
    fun restore(position:Int){pendingScroll=position.coerceAtLeast(0)}
    fun setUnsaved(value:Boolean){dirtyHint.visibility=if(value)View.VISIBLE else View.GONE}
    fun render(title:String,opened:Boolean,response:GitResponse?,busy:Boolean,unsaved:Boolean) {
        val oldScroll=pendingScroll?:scroll.scrollY;content.removeAllViews();val state=response?.state
        setUnsaved(opened&&unsaved)
        scope.text=title.substringBefore("（").substringBefore(" · ")+(state?.branch?.let{" · $it"}?:"")
        scope.contentDescription=title+(state?.branch?.let{" · $it"}?:"")
        refreshButton.isEnabled=opened&&!busy;refreshButton.alpha=if(refreshButton.isEnabled)1f else .5f
        historyButton.isEnabled=opened&&!busy&&state?.head!=null;historyButton.alpha=if(historyButton.isEnabled)1f else .5f
        when {
            !opened->{note("打开项目后使用源代码管理");action("打开项目","git-open",callback=open)}
            response?.error!=null->{note(errorText(response.error));action("重试","git-retry",!busy,refresh)
                action("错误详情","git-error-details",callback=details)
                if(response.error=="recovery_required") {action("恢复记录","git-recovery",callback=recovery);action("导出恢复包","git-export-recovery",callback=exportRecovery)}}
            state==null->note(if(busy)"正在读取 Git…" else "点击刷新读取 Git 状态")
            !state.repository->{note(if(busy)"正在初始化 Git…" else "此项目尚未初始化 Git")
                action("初始化仓库","git-init",!busy,initialize)}
            else->{
                if(busy)note("正在处理 Git…")
                val staged=state.staged.flatMap{(kind,paths)->paths.map{GitChange(it,true,kind)}}
                val working=state.unstaged.map{GitChange(it,false,if(it in response.sourceFiles)"modify" else "delete")}
                val untracked=state.untracked.map{GitChange(it,false,"untracked")}
                if(staged.isEmpty() && working.isEmpty() && untracked.isEmpty())note(if(state.head==null)"尚无提交；新建文件后可暂存并提交" else "工作树干净")
                if(staged.isNotEmpty())action("提交已暂存的 ${staged.size} 个文件","git-commit",!busy,commit)
                group("已暂存",staged,busy);group("未暂存",working,busy);group("未跟踪",untracked,busy)
            }
        }
        if(state!=null&&!busy)pendingScroll=null
        scroll.post{scroll.scrollTo(0,oldScroll)}
    }
    private fun group(title:String,changes:List<GitChange>,busy:Boolean) {
        if(changes.isEmpty())return
        content.addView(text("$title · ${changes.size}",12f,palette.muted,true).apply{setPadding(dp(12),dp(14),dp(12),dp(6))},LayoutParams(-1,-2))
        changes.sortedBy{it.path}.forEach{item->
            val row=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(48);background=IdeControls.feedback(context,palette);tag="git-change:${item.staged}:${item.path}"}
            val marker=when(item.kind){"add"->"A";"delete"->"D";"untracked"->"?";else->"M"}
            row.addView(text(marker,12f,palette.accentText,true).apply{gravity=Gravity.CENTER},LayoutParams(dp(30),-1))
            val words=LinearLayout(context).apply{orientation=VERTICAL;setPadding(0,dp(8),0,dp(8))}
            words.addView(text(item.path.substringAfterLast('/')).apply{setSingleLine();ellipsize=TextUtils.TruncateAt.END})
            item.path.substringBeforeLast('/',"").takeIf{it.isNotEmpty()}?.let{parent->words.addView(text(parent,11f,palette.muted).apply{setSingleLine();ellipsize=TextUtils.TruncateAt.MIDDLE})}
            row.addView(words,LayoutParams(0,-2,1f));row.isFocusable=true;row.isClickable=true
            IdeControls.describe(row,"${if(item.staged)"暂存区" else "工作树"}差异：${item.path}",false);row.setOnClickListener{if(!busy)inspect(item)}
            row.addView(IdeControls.icon(context,palette,if(item.staged)IdeIcon.UNDO else IdeIcon.ADD,
                "${if(item.staged)"取消暂存" else "暂存已保存版本"}：${item.path}"){change(item)}.apply{
                isEnabled=!busy;alpha=if(busy).5f else 1f;tag="git-${if(item.staged)"unstage" else "stage"}:${item.path}"
            },LayoutParams(dp(48),dp(48)))
            content.addView(row,LayoutParams(-1,-2))
        }
    }
    companion object {
        fun errorText(code:String)=when(code) {
            "stale","workspace_changed"->"工作区已变化，请刷新后重试"
            "locked"->"Git 正被其他操作使用，请稍后刷新"
            "configuration","repository_layout"->"此仓库包含暂不支持的配置或布局"
            "operation_in_progress"->"请先处理仓库中未完成的合并或变基"
            "recovery_required"->"Git 操作未完成，恢复备份已保留"
            "timeout"->"Git 操作超时，工作正文保留"
            "limit","input_limit","output_limit"->"Git 工作区或结果超过处理上限"
            "no_changes"->"没有已暂存的改动"
            "identity"->"请检查提交姓名与邮箱"
            "cancelled","superseded"->"Git 操作已取消"
            else->"Git 操作未完成，请查看错误详情"
        }
    }
}
