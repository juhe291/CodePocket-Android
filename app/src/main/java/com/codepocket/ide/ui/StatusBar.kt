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
    fun setStatus(layout:StatusBarLayout) {
        if(state==layout)return
        state=layout
        val message=layout.state.substringBefore(" · ").let { if(it.startsWith("C++17") || it.startsWith("Python 3"))"就绪" else it }
        if(statusText.text.toString()!=message)statusText.text=message
        IdeControls.describe(this,layout.format());IdeControls.describe(statusText,layout.state)
        details.text="${layout.language}  ·  Ln ${layout.line}, Col ${layout.column}"
    }
    fun setText(text:String){statusText.text=text}
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=MeasureSpec.getSize(widthMeasureSpec)
        state?.let { val value=if(width/resources.displayMetrics.density>=650 && resources.configuration.fontScale<=1.3f)
            "Ln ${it.line}, Col ${it.column}   ·   ${it.indent}   ·   ${it.encoding}   ·   ${it.language}"
            else "Ln ${it.line}:${it.column}  ${it.language}"
            if(details.text.toString()!=value)details.text=value }
        details.maxWidth=(width*.55f).toInt()
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
}
