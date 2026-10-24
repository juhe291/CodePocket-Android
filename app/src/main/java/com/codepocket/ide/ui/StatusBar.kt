package com.codepocket.ide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Compact workbench footer. Live failure/save text is never replaced with a fabricated success. */
@SuppressLint("ViewConstructor")
class StatusBar(context:Context,private val palette:IdePalette):LinearLayout(context) {
    private val dark=palette.editor!=0xFFFFFFFF.toInt()
    private val ink=if(dark)0xFFADB5C0.toInt() else palette.foreground
    private fun label()=TextView(context).apply {
        textSize=11f;setTextColor(ink);gravity=Gravity.CENTER_VERTICAL
        setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END
        setPadding(IdeControls.dp(context,10),IdeControls.dp(context,5),IdeControls.dp(context,10),IdeControls.dp(context,5))
    }
    private val statusText=label()
    private val details=label()
    private var state:StatusBarLayout?=null
    init {
        orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
        setBackgroundColor(palette.surface)
        addView(statusText,LayoutParams(0,-2,1f));addView(details,LayoutParams(-2,-2))
    }
    private fun summary(value:String):String {
        val first=value.substringBefore(" · ")
        if(first.startsWith("C++17")||first.startsWith("Python 3"))return "就绪"
        if(first.startsWith("✓ 已保存"))return "已保存"
        if(first=="未保存"){
            val reason=value.substringAfter(" · ","").substringBefore(" · ")
            if(reason.startsWith("✓"))return first
            if(reason.startsWith("保存失败"))return "保存失败"
            if(reason.startsWith("✕")||"失败" in reason||"错误" in reason||"冲突" in reason)
                return reason.substringBefore("，").substringBefore("：").substringBefore(" (")
        }
        return first
    }
    fun setStatus(layout:StatusBarLayout) {
        if(state==layout)return
        state=layout
        val message=summary(layout.state)
        if(statusText.text.toString()!=message)statusText.text=message
        statusText.setTextColor(if(message.startsWith("保存失败")||message.startsWith("✕")||message.startsWith("文件冲突"))palette.error else ink)
        IdeControls.describe(this,layout.format());IdeControls.describe(statusText,layout.state)
        details.text="${layout.language}  ·  Ln ${layout.line}, Col ${layout.column}"
        IdeControls.describe(details,"Ln ${layout.line}, Col ${layout.column} · ${layout.indent} · ${layout.encoding} · ${layout.language}")
    }
    fun setText(text:String){statusText.text=text}
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec)
        val widthDp=width/resources.displayMetrics.density
        val large=resources.configuration.fontScale>1.3f
        state?.let { val value=if(widthDp>=650 && !large)
            "Ln ${it.line}, Col ${it.column}   ·   ${it.indent}   ·   ${it.encoding}   ·   ${it.language}"
            else if(widthDp<400 && large)"Ln ${it.line}:${it.column}"
            else "Ln ${it.line}:${it.column}  ${it.language}"
            if(details.text.toString()!=value)details.text=value }
        details.maxWidth=(width*if(large).45f else .55f).toInt()
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
}
