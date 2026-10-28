package com.codepocket.ide

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.codepocket.ide.animation.DialogMotion
import com.codepocket.ide.ui.*
import org.json.JSONObject

/** Full-window settings center. Drafts never write preferences before a successful Apply. */
object SettingsDialog {
    private val categoryIcons=listOf(IdeIcon.PALETTE,IdeIcon.CODE,IdeIcon.INPUT,IdeIcon.SAVE,IdeIcon.TERMINAL,IdeIcon.MOVE,IdeIcon.HELP)
    private val categoryHelp=listOf("界面主题与配色","字体、缩进与输入","候选列表与按键行为","自动保存与文件保护","终端显示与代码运行","过渡效果与代码光标","快捷键、使用说明与开源许可")
    private val categories=listOf("外观","编辑器","补全与快捷键","文件与保存","终端与运行","动画","帮助与许可")
    data class Draft(val original:AppSettings,var value:AppSettings=original,var category:Int=-1,var query:String="",var scroll:Int=0) {
        fun checkpoint()=JSONObject().put("original",original.toJson()).put("draft",value.toJson()).put("category",category).put("query",query).put("scroll",scroll).toString()
        companion object {
            fun restore(current:AppSettings,json:String?):Draft=runCatching {
                val j=JSONObject(json?:return@runCatching Draft(current))
                Draft(current,AppSettings.fromJson(j.optString("draft",current.toJson())),j.optInt("category",-1).coerceIn(-1,6),j.optString("query").take(500),j.optInt("scroll").coerceAtLeast(0))
            }.getOrDefault(Draft(current))
        }
    }
    private data class Spec(val key:String,val category:Int,val title:String,val help:String,val bool:((AppSettings)->Boolean)?=null,
        val toggle:((AppSettings,Boolean)->AppSettings)?=null,val options:List<String> = emptyList(),
        val selected:((AppSettings)->Int)?=null,val choose:((AppSettings,Int)->AppSettings)?=null)
    private fun specs(activity:Activity):List<Spec> = buildList {
        fun toggle(key:String,c:Int,t:String,h:String,get:(AppSettings)->Boolean,set:(AppSettings,Boolean)->AppSettings){add(Spec(key,c,t,h,get,set))}
        fun choice(key:String,c:Int,t:String,h:String,options:List<String>,get:(AppSettings)->Int,set:(AppSettings,Int)->AppSettings){add(Spec(key,c,t,h,options=options,selected=get,choose=set))}
        choice("theme",0,"界面主题","工作台、菜单与设置使用同一套主题",listOf("深色","浅色","跟随系统"),{it.theme.ordinal},{s,i->s.copy(theme=UiTheme.values()[i])})
        choice("syntax",1,"语法主题","当前为词法高亮，尚非深度语义分析",listOf("跟随界面","Dark+","Light+"),{it.syntax.ordinal},{s,i->s.copy(syntax=SyntaxTheme.values()[i])})
        choice("fontSize",1,"代码字号","预览与实际字号均使用 sp",(10..28).map{"$it sp"},{it.fontSize-10},{s,i->s.copy(fontSize=i+10)})
        choice("tabWidth",1,"缩进宽度","Tab 和智能缩进共同使用",listOf("2 列","4 列","8 列"),{listOf(2,4,8).indexOf(it.tabWidth)},{s,i->s.copy(tabWidth=listOf(2,4,8)[i])})
        toggle("useSpaces",1,"使用空格缩进","关闭后插入 Tab 字符",{it.useSpaces},{s,v->s.copy(useSpaces=v)})
        toggle("lineNumbers",1,"显示行号","与源码逻辑行保持一致",{it.lineNumbers},{s,v->s.copy(lineNumbers=v)})
        toggle("wordWrap",1,"自动换行","按编辑区可用宽度折行",{it.wordWrap},{s,v->s.copy(wordWrap=v)})
        toggle("autoIndent",1,"自动缩进","换行后继承适当缩进",{it.autoIndent},{s,v->s.copy(autoIndent=v)})
        toggle("autoPairs",1,"补齐括号和引号","保留已有组合输入和撤销行为",{it.autoPairs},{s,v->s.copy(autoPairs=v)})
        toggle("autoCompletion",2,"基础词法补全","提供关键词、缓冲区符号及内置候选",{it.autoCompletion},{s,v->s.copy(autoCompletion=v)})
        toggle("signatureHelp",2,activity.getString(R.string.signature_setting),"显示支持的函数参数与简短说明",{it.signatureHelp},{s,v->s.copy(signatureHelp=v)})
        toggle("completionTab",2,"Tab 接受补全","关闭时 Tab 始终执行缩进",{it.completionTab},{s,v->s.copy(completionTab=v)})
        toggle("completionEnter",2,"Enter 接受选中候选","只接受方向键明确选中的候选",{it.completionEnter},{s,v->s.copy(completionEnter=v)})
        choice("completionArrows",2,"候选方向键","默认上下键选择候选；Esc 关闭后移动代码光标",listOf("主动触发后导航候选","候选可见时导航","始终移动代码光标"),{it.completionArrows.ordinal},{s,i->s.copy(completionArrows=CompletionArrows.values()[i])})
        toggle("symbolBar",2,"编程符号栏","代码输入时显示 Tab、括号等快捷操作",{it.symbolBar},{s,v->s.copy(symbolBar=v)})
        toggle("autoSave",3,"自动保存","编辑停顿后写回；失败时保留恢复草稿",{it.autoSave},{s,v->s.copy(autoSave=v)})
        choice("autoSaveDelayMs",3,"自动保存延迟","离开页面仍保留现有保存保护",listOf("700 ms","1500 ms","3000 ms"),{listOf(700,1500,3000).indexOf(it.autoSaveDelayMs).coerceAtLeast(0)},{s,i->s.copy(autoSaveDelayMs=listOf(700,1500,3000)[i])})
        choice("terminalFontSize",4,"终端字号","真实终端与运行结果共用字号偏好",(10..24).map{"$it sp"},{it.terminalFontSize-10},{s,i->s.copy(terminalFontSize=i+10)})
        choice("terminalTheme",4,"终端配色","切换配色保留正在运行的会话",listOf("跟随界面","CodePocket Dark","Nord","Dracula","Light"),{it.terminalTheme.ordinal},{s,i->s.copy(terminalTheme=TerminalTheme.values()[i])})
        choice("cppBackend",4,"C++ 运行方式","云端须确认发送；本地失败不会自动上传",listOf("Wandbox 云端","设备本地 · Clang21"),{it.cppBackend.ordinal},{s,i->s.copy(cppBackend=CppBackend.values()[i])})
        choice("cppBuildScope",4,"本地 C++ 构建范围","项目模式编译所有 C/C++ 源文件；多个独立 main 请用当前文件",listOf("当前文件（可引用项目头文件）","整个项目"),{it.cppBuildScope.ordinal},{s,i->s.copy(cppBuildScope=CppBuildScope.values()[i])})
        toggle("collapsePanelOnIme",4,"输入代码时临时收起终端","仅在键盘实际占用底部空间时生效",{it.collapsePanelOnIme},{s,v->s.copy(collapsePanelOnIme=v)})
        // Retain the serialized legacy preference; this bottom-only workbench exposes no side-dock switch.
        toggle("animations",5,"动画总开关","遵从系统减少动画与省电策略",{it.animations},{s,v->s.copy(animations=v)})
        toggle("cursorMovement",5,"平滑移动代码光标","键入约60ms，短距离导航约80ms；实际输入位置即时更新",{it.cursorMovement},{s,v->s.copy(cursorMovement=v)})
        toggle("cursorBlink",5,"柔和闪烁代码光标","输入时清晰显示，停顿后柔和闪烁；关闭后常亮",{it.cursorBlink},{s,v->s.copy(cursorBlink=v)})
    }
    // Property identities stay stable across translated labels and search rendering.
    private fun section(key:String):String?=when(key){
        "syntax","fontSize","lineNumbers","wordWrap"->"显示与配色"
        "tabWidth","useSpaces","autoIndent","autoPairs"->"缩进与输入"
        "autoCompletion","signatureHelp"->"候选与参考"
        "completionTab","completionEnter","completionArrows"->"补全按键"
        "symbolBar"->"触屏输入"
        "terminalFontSize","terminalTheme"->"终端显示"
        "cppBackend","cppBuildScope"->"代码运行"
        "collapsePanelOnIme"->"窗口与键盘"
        "animations"->"动画策略"
        "cursorMovement","cursorBlink"->"代码光标"
        else->null
    }
    private fun optionIcon(spec:Spec):IdeIcon=when(spec.key){
        "theme","syntax","terminalTheme"->IdeIcon.PALETTE
        "fontSize","terminalFontSize"->IdeIcon.CASE
        "tabWidth"->IdeIcon.BRACKETS
        "autoSaveDelayMs"->IdeIcon.SAVE
        "completionArrows"->IdeIcon.NEXT
        else->categoryIcons[spec.category]
    }
    fun checkpoint(dialog:AlertDialog?):String? = if(dialog?.isShowing!=true)null else (dialog.window?.decorView?.getTag(R.id.settings_checkpoint) as? (() -> String))?.invoke()
    fun show(activity:Activity,current:AppSettings,save:(AppSettings)->Unit):AlertDialog = show(activity,current,save,null,null)
    fun show(activity:Activity,current:AppSettings,save:(AppSettings)->Unit,initialState:String?,
        apply:((AppSettings,(Boolean)->Unit)->Unit)?):AlertDialog {
        val draft=Draft.restore(current,initialState);val p=DesignTokens.forContext(activity);val dp={v:Int->IdeControls.dp(activity,v)}
        val root=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(p.surface);setPadding(dp(16),dp(8),dp(16),0)}
        val header=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL}
        val back=IdeControls.icon(activity,p,IdeIcon.BACK,"返回设置分类"){}
        header.addView(back,LinearLayout.LayoutParams(dp(48),dp(48)))
        header.addView(TextView(activity).apply{text="设置";textSize=18f;setTextColor(p.foreground);setTypeface(null,Typeface.BOLD)},LinearLayout.LayoutParams(0,dp(48),1f))
        header.addView(ImageView(activity).apply{setImageDrawable(IdeIconDrawable(activity,IdeIcon.SETTINGS,p.muted,dp(20)))},LinearLayout.LayoutParams(dp(32),dp(20)))
        root.addView(header)
        root.addView(View(activity).apply{setBackgroundColor(p.border)},LinearLayout.LayoutParams(-1,dp(1)).apply{bottomMargin=dp(12)})
        val search=EditText(activity).apply{hint="搜索设置";setSingleLine();setText(draft.query);textSize=14f;setPadding(dp(12),0,dp(12),0);contentDescription="搜索设置"}
        root.addView(search,LinearLayout.LayoutParams(-1,dp(48)).apply{bottomMargin=dp(12)})
        val body=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL}
        val nav=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(0,dp(8),dp(12),0)}
        val scroller=ScrollView(activity).apply{isFillViewport=true;setBackgroundColor(p.editor)}
        val details=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(16),dp(20),dp(24))}
        val navScroller=ScrollView(activity).apply{addView(nav)}
        scroller.addView(details);body.addView(navScroller,LinearLayout.LayoutParams(dp(220),-1));body.addView(scroller,LinearLayout.LayoutParams(0,-1,1f))
        root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
        val feedback=TextView(activity).apply{textSize=12f;setTextColor(p.error);setPadding(dp(8),dp(4),dp(8),dp(4));visibility=View.GONE}
        root.addView(feedback)
        val dialog=AlertDialog.Builder(activity).setView(root).setNegativeButton("取消",null).setPositiveButton("应用",null).create()
        var busy=false;val all=specs(activity);var render:()->Unit={}
        // An async Apply owns one submitted draft. Do not accept changes which its callback would discard.
        fun updateEnabled(view:View=root){
            view.isEnabled=!busy
            if(view is ViewGroup)for(i in 0 until view.childCount)updateEnabled(view.getChildAt(i))
        }
        fun setBusy(value:Boolean){
            busy=value;updateEnabled()
            dialog.setCancelable(!value)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=!value
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).text=if(value)"正在应用…" else "应用"
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled=!value
        }
        fun heading(title:String,help:String=""){
            details.addView(TextView(activity).apply{text=title;textSize=18f;setTextColor(p.foreground);setTypeface(null,Typeface.BOLD);setPadding(0,dp(8),0,dp(12))})
            if(help.isNotBlank())details.addView(TextView(activity).apply{text=help;textSize=12f;setTextColor(p.muted);setPadding(0,0,0,dp(12))})
        }
        fun engineInfo(icon:IdeIcon,title:Int,body:Int){
            val entry=LinearLayout(activity).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.TOP;setPadding(0,dp(12),0,dp(12))}
            entry.addView(ImageView(activity).apply{
                setImageDrawable(IdeIconDrawable(activity,icon,IdeIconDrawable.color(icon,p),dp(20)))
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },LinearLayout.LayoutParams(dp(24),dp(24)))
            val words=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,0,0)}
            words.addView(TextView(activity).apply{setText(title);textSize=13f;setTextColor(p.foreground);setTypeface(null,Typeface.BOLD)})
            words.addView(TextView(activity).apply{setText(body);textSize=12f;setTextColor(p.muted);setLineSpacing(dp(2).toFloat(),1f);setPadding(0,dp(5),0,0)})
            entry.addView(words,LinearLayout.LayoutParams(0,-2,1f));details.addView(entry,LinearLayout.LayoutParams(-1,-2))
        }
        fun categoryRow(index:Int):View {
            val selected=index==draft.category
            val row=LinearLayout(activity).apply{
                orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(68)
                setPadding(dp(12),dp(10),dp(12),dp(10));isFocusable=true;isClickable=true
                background=if(selected)WorkbenchStyle.shape(activity,p.selection,radius=4) else IdeControls.feedback(activity,p)
                contentDescription=categories[index]+if(selected)"，已选择" else ""
            }
            row.addView(ImageView(activity).apply{
                setImageDrawable(IdeIconDrawable(activity,categoryIcons[index],if(selected)p.accentText else p.muted,dp(20)))
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },LinearLayout.LayoutParams(dp(24),dp(24)))
            val words=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,dp(8),0)}
            words.addView(TextView(activity).apply{text=categories[index];textSize=13f;setTextColor(p.foreground)})
            words.addView(TextView(activity).apply{text=categoryHelp[index];textSize=11f;setTextColor(p.muted);setPadding(0,dp(3),0,0)})
            row.addView(words,LinearLayout.LayoutParams(0,-2,1f))
            row.addView(ImageView(activity).apply{setImageDrawable(IdeIconDrawable(activity,IdeIcon.CHEVRON_RIGHT,p.muted,dp(14)))},LinearLayout.LayoutParams(dp(16),dp(16)))
            row.setOnClickListener{if(!busy){draft.scroll=0;draft.category=index;search.setText("");draft.query="";render()}}
            return row
        }
        fun groupCaption(title:String){
            val line=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(20),0,dp(4))}
            line.addView(TextView(activity).apply{text=title;textSize=12f;setTextColor(p.muted);setTypeface(null,Typeface.BOLD)},LinearLayout.LayoutParams(-2,-2))
            line.addView(View(activity).apply{setBackgroundColor(p.border);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(0,dp(1),1f).apply{marginStart=dp(12)})
            details.addView(line,LinearLayout.LayoutParams(-1,-2))
        }
        fun terminalPreview(){
            val preview=LinearLayout(activity).apply{setPadding(dp(12),dp(12),dp(12),dp(12));gravity=Gravity.CENTER_VERTICAL}
            com.codepocket.ide.terminal.shell.TerminalAppearance.palette(draft.value.terminalTheme.name,draft.value.isDark(activity.resources.configuration.uiMode and 0x30 == 0x20)).ansi.slice(1..6).forEach{c->preview.addView(View(activity).apply{background=WorkbenchStyle.shape(activity,c,radius=4)},LinearLayout.LayoutParams(dp(24),dp(16)).apply{marginEnd=dp(8)})}
            details.addView(preview)
            details.addView(TextView(activity).apply{text="配色示意 · 应用后生效";textSize=12f;setTextColor(p.muted)})
        }
        fun row(spec:Spec){
            val item=LinearLayout(activity).apply{gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(72);setPadding(0,dp(14),0,dp(14));background=IdeControls.feedback(activity,p)}
            val words=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
            words.addView(TextView(activity).apply{text=spec.title;textSize=14f;setTextColor(p.foreground);setTypeface(null,Typeface.BOLD)})
            words.addView(TextView(activity).apply{text=spec.help;textSize=12f;setTextColor(p.muted);setPadding(0,dp(6),dp(12),0)})
            item.addView(words,LinearLayout.LayoutParams(0,-2,1f))
            if(spec.bool!=null){
                val toggle=Switch(activity).apply{isChecked=spec.bool.invoke(draft.value);contentDescription=spec.title;minimumWidth=dp(48);minHeight=dp(48)}
                toggle.setOnCheckedChangeListener{_,value->if(!busy)draft.value=spec.toggle!!.invoke(draft.value,value)}
                item.addView(toggle,LinearLayout.LayoutParams(dp(56),dp(48)));item.setOnClickListener{if(!busy)toggle.isChecked=!toggle.isChecked}
            } else {
                val value=spec.options[spec.selected!!.invoke(draft.value).coerceIn(0,spec.options.lastIndex)]
                words.addView(TextView(activity).apply{text=value;textSize=12f;setTextColor(p.accentText);setPadding(0,dp(8),0,0)})
                item.addView(ImageView(activity).apply{setImageDrawable(IdeIconDrawable(activity,IdeIcon.CHEVRON_RIGHT,p.muted,dp(16)))},LinearLayout.LayoutParams(dp(24),dp(24)))
                item.setOnClickListener {
                    if(busy)return@setOnClickListener
                    draft.scroll=scroller.scrollY
                    val selected=spec.selected.invoke(draft.value).coerceIn(0,spec.options.lastIndex)
                    val choices=spec.options.mapIndexed{index,label->WorkbenchChoice("${spec.key}:$index",label,"",optionIcon(spec),index==selected,monospaceTitle=spec.key=="fontSize"||spec.key=="terminalFontSize")}
                    val scope=categories[spec.category]+(section(spec.key)?.let{" · $it"}?:"")
                    val picker=AlertDialog.Builder(activity).setCustomTitle(WorkbenchForms.header(activity,spec.title,scope,optionIcon(spec)))
                        .setSingleChoiceItems(WorkbenchChoiceAdapter(activity,choices),selected,null).setNegativeButton("取消",null).create()
                    DialogMotion.show(picker,current.animations)
                    picker.window?.setLayout(minOf(dp(560),(root.width-dp(32)).coerceAtLeast(dp(200))),-2)
                    picker.listView.setOnItemClickListener{_,_,index,_->if(!busy){draft.value=spec.choose!!.invoke(draft.value,index);picker.dismiss();render()}}
                }
            }
            details.addView(item,LinearLayout.LayoutParams(-1,-2))
            details.addView(View(activity).apply{setBackgroundColor(p.border)},LinearLayout.LayoutParams(-1,dp(1)))
        }
        fun discard(){
            if(busy)return
            if(draft.value==current)dialog.dismiss()
            else DialogMotion.show(AlertDialog.Builder(activity).setTitle("放弃未应用的设置？").setMessage("编辑器内容和现有设置保持。")
                .setNegativeButton("继续编辑",null).setPositiveButton("放弃修改"){_,_->dialog.dismiss()}.create(),current.animations)
        }
        var lastWide:Boolean?=null
        render={
            val decor=dialog.window?.decorView
            val width=if((decor?.width?:0)>0)decor!!.width/activity.resources.displayMetrics.density else activity.resources.configuration.screenWidthDp.toFloat()
            val height=if((decor?.height?:0)>0)decor!!.height/activity.resources.displayMetrics.density else activity.resources.configuration.screenHeightDp.toFloat()
            val wide=WorkspaceLayoutPolicy.settingsSplit(width.toInt(),height.toInt())
            lastWide=wide;navScroller.visibility=if(wide)View.VISIBLE else View.GONE;nav.removeAllViews();details.removeAllViews()
            details.setPadding(dp(if(wide)28 else 16),dp(16),dp(if(wide)28 else 16),dp(24))
            if(wide&&draft.category<0)draft.category=0
            if(wide)categories.indices.forEach{nav.addView(categoryRow(it))}
            back.visibility=if(!wide&&draft.category>=0&&draft.query.isBlank())View.VISIBLE else View.GONE
            when {
                draft.query.isNotBlank()->{
                    details.addView(WorkbenchForms.sectionHeading(activity,"搜索结果","",IdeIcon.SEARCH))
                    val matches=all.filter{(it.title+it.help+categories[it.category]).contains(draft.query,true)}
                    if(matches.isEmpty())heading("没有匹配的设置")
                    matches.groupBy{it.category to section(it.key)}.forEach{(group,items)->
                        groupCaption(categories[group.first]+(group.second?.let{" · $it"}?:""))
                        items.forEach(::row)
                    }
                }
                draft.category<0->{details.addView(WorkbenchForms.sectionHeading(activity,"工作台偏好","",IdeIcon.SETTINGS));categories.indices.forEach{details.addView(categoryRow(it))}}
                else->{
                    details.addView(WorkbenchForms.sectionHeading(activity,categories[draft.category],"",categoryIcons[draft.category]))
                    all.filter{it.category==draft.category}.groupBy{section(it.key)}.forEach{(title,items)->
                        if(title!=null)groupCaption(title)
                        items.forEach(::row)
                        if(draft.category==4&&title=="终端显示")terminalPreview()
                    }
                    if(draft.category==0){
                        val uiDark=draft.value.isDark(activity.resources.configuration.uiMode and 0x30 == 0x20)
                        val codeDark=draft.value.syntaxIsDark(uiDark)
                        val samplePalette=DesignTokens.palette(codeDark)
                        val example=LinearLayout(activity).apply{
                            orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(16))
                            background=WorkbenchStyle.shape(activity,samplePalette.editor,samplePalette.border,4)
                            contentDescription="主题预览示意，不是编辑文件"
                        }
                        example.addView(TextView(activity).apply{
                            text="main.cpp";textSize=12f;setTextColor(samplePalette.muted)
                            setCompoundDrawablesWithIntrinsicBounds(IdeIconDrawable(activity,IdeIcon.CPP,samplePalette.accentText,dp(18)),null,null,null)
                            compoundDrawablePadding=dp(8);setPadding(0,0,0,dp(12))
                        })
                        example.addView(com.codepocket.editorlab.SyntaxThemePreview.create(activity,codeDark,draft.value.fontSize))
                        details.addView(example,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(20)})
                        details.addView(TextView(activity).apply{text="主题预览 · ${if(codeDark)"Dark+" else "Light+"} · 应用后生效";textSize=11f;setTextColor(p.muted);setPadding(0,dp(8),0,0)})
                    }
                    if(draft.category==3)heading("文件安全",activity.getString(R.string.settings_save_help))
                    if(draft.category==2)details.addView(WorkbenchStyle.menuRow(activity,"补全按键说明").apply{
                        setOnClickListener{if(!busy)ReadOnlyDetails.show(activity,"补全按键说明",categories[2],
                            activity.getString(R.string.completion_key_help),IdeIcon.INPUT,false,current.animations,null,null,null)}
                    })
                    if(draft.category==4){
                        heading(activity.getString(R.string.settings_run_modes))
                        engineInfo(IdeIcon.PYTHON,R.string.settings_python_mode,R.string.settings_python_mode_help)
                        engineInfo(IdeIcon.CPP,R.string.settings_cpp_mode,R.string.settings_cpp_mode_help)
                    }
                    if(draft.category==6){
                        heading("CodePocket","原生 Android C++ / Python 学习工作台")
                        details.addView(WorkbenchStyle.menuRow(activity,"C++ 语义分析许可与源码").apply{
                            setOnClickListener{if(!busy)CppLicenses.show(activity,current.animations)}
                        })
                        listOf("快捷键说明" to activity.getString(R.string.shortcuts_help),"运行与权限说明" to activity.getString(R.string.python_terminal_help),
                            "Lucide 图标许可证" to activity.assets.open("icon-licenses/LUCIDE-LICENSE.txt").bufferedReader().use{it.readText()},
                            "Sora 编辑器与语法许可" to activity.assets.open("SORA-NOTICE.txt").bufferedReader().use{it.readText()},
                            "GNU LGPL 2.1" to activity.assets.open("LGPL-2.1.txt").bufferedReader().use{it.readText()},
                            "Python 语义分析许可" to listOf("semantic-licenses/JEDI-LICENSE.txt","semantic-licenses/PARSO-LICENSE.txt","semantic-licenses/TYPESHED-LICENSE.txt","semantic-licenses/DJANGO-STUBS-LICENSE.txt").joinToString("\n\n"){path->path+"\n"+activity.assets.open(path).bufferedReader().use{it.readText()}},
                            "语法与依赖许可证" to listOf("textmate/VSCODE-LICENSE.txt", "textmate/VSCODE-ThirdPartyNotices.txt", "textmate/licenses/APACHE-2.0.txt", "textmate/licenses/EPL-2.0.txt", "textmate/licenses/JONI-MIT.txt", "textmate/licenses/JCODINGS-MIT.txt", "textmate/licenses/DESUGAR-GPL2.txt", "textmate/licenses/DESUGAR-ASSEMBLY-EXCEPTION.txt").joinToString("\n\n") { path -> path + "\n" + activity.assets.open(path).bufferedReader().use { it.readText() } }).forEach{(title,text)->
                            details.addView(WorkbenchStyle.menuRow(activity,title).apply{setOnClickListener{if(!busy)
                                ReadOnlyDetails.show(activity,title,categories[6],text,IdeIcon.HELP,false,current.animations,null,null,null)}})
                        }
                    }
                }
            }
            scroller.post{scroller.scrollTo(0,draft.scroll)}
            updateEnabled() // Window changes may create new controls while Apply is still waiting.
        }
        back.setOnClickListener{if(!busy){draft.category=-1;draft.scroll=0;render()}}
        search.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){if(!busy){draft.query=s.toString();draft.scroll=0;render()}}
            override fun afterTextChanged(s:Editable?){}
        })
        dialog.setOnKeyListener{_,key,event->
            if(key==android.view.KeyEvent.KEYCODE_BACK&&event.action==android.view.KeyEvent.ACTION_UP){
                if(busy)return@setOnKeyListener true
                if(draft.category>=0&&lastWide==false&&draft.query.isBlank()){draft.category=-1;draft.scroll=0;render()}else discard();true
            }else false
        }
        DialogMotion.show(dialog,current.animations)
        dialog.window?.setLayout(-1,-1)
        // A full-height Window does not expand AlertDialog's wrap-content parent/custom panels.
        // Keep native Apply/Cancel buttons, but give our scrollable settings body the remaining height.
        dialog.findViewById<ViewGroup>(android.R.id.custom)?.let { custom ->
            custom.layoutParams=custom.layoutParams.apply{height=ViewGroup.LayoutParams.MATCH_PARENT}
            (custom.parent as? View)?.let { panel ->
                panel.layoutParams=panel.layoutParams.apply{
                    if(this is LinearLayout.LayoutParams){height=0;weight=1f}else height=ViewGroup.LayoutParams.MATCH_PARENT
                }
                (panel.parent as? View)?.let { parent ->
                    parent.layoutParams=parent.layoutParams.apply{height=ViewGroup.LayoutParams.MATCH_PARENT}
                }
            }
        }
        (dialog.getButton(AlertDialog.BUTTON_POSITIVE).parent as? View)?.setBackgroundColor(p.surface)
        search.setCompoundDrawablesWithIntrinsicBounds(IdeIconDrawable(activity,IdeIcon.SEARCH,p.muted,dp(16)),null,null,null)
        search.compoundDrawablePadding=dp(8)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        dialog.window?.decorView?.setTag(R.id.settings_checkpoint,{draft.scroll=scroller.scrollY;draft.checkpoint()})
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener{discard()}
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(!busy){setBusy(true);feedback.visibility=View.GONE
                val finished:(Boolean)->Unit={ok->if(dialog.isShowing){setBusy(false);if(ok)dialog.dismiss() else {feedback.text="设置尚未应用，请处理保存错误后重试";feedback.visibility=View.VISIBLE}}}
                try{if(apply!=null)apply(draft.value.normalized(),finished) else {save(draft.value.normalized());finished(true)}}catch(e:Exception){finished(false);feedback.text=e.message}
            }
        }
        root.addOnLayoutChangeListener{_,_,_,_,_,_,_,_,_->
            val decor=dialog.window!!.decorView
            val wide=WorkspaceLayoutPolicy.settingsSplit((decor.width/activity.resources.displayMetrics.density).toInt(),(decor.height/activity.resources.displayMetrics.density).toInt())
            if(wide!=lastWide)render()
        }
        render();return dialog
    }
}
