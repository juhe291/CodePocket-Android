package com.codepocket.ide.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.codepocket.ide.CompletionItem
import com.codepocket.ide.CompletionKind
import com.codepocket.ide.R

/** A non-focusable list row. Narrow layouts yield metadata width to the actual candidate name. */
class CompletionCandidateRow(context:Context,private val labelColor:Int,private val base:Int,private val muted:Int):LinearLayout(context){
    private val p=DesignTokens.forContext(context)
    private fun dp(v:Int)=IdeControls.dp(context,v)
    private val glyph=ImageView(context).apply{importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO}
    private val label=TextView(context).apply{textSize=13f;typeface=Typeface.MONOSPACE;setTextColor(labelColor);setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END}
    private val kind=TextView(context).apply{textSize=11f;setTextColor(muted);setSingleLine()}
    val requiredHeight get()=maxOf(dp(48),maxOf(label.lineHeight,kind.lineHeight)+paddingTop+paddingBottom)
    init {
        orientation=HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(8),dp(6),dp(8),dp(6));minimumHeight=requiredHeight
        addView(glyph,LayoutParams(dp(20),dp(20)))
        addView(label,LayoutParams(0,-2,1f).apply{marginStart=dp(8);marginEnd=dp(8)})
        addView(kind,LayoutParams(-2,-2))
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
        val usable=(MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight-dp(36)).coerceAtLeast(0)
        val desired=kind.paint.measureText(kind.text.toString())
        val visible=if(kind.text.isNotEmpty()&&desired<=usable/3f)VISIBLE else GONE
        if(kind.visibility!=visible)kind.visibility=visible
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
    fun bind(item:CompletionItem,selected:Boolean){
        label.text=context.getString(R.string.completion_item,"",item.label).trim();kind.text=item.kind.title
        val icon=when(item.kind){CompletionKind.KEYWORD->IdeIcon.CASE;CompletionKind.TYPE->IdeIcon.HEADER;CompletionKind.BUILTIN->IdeIcon.BRACKETS;CompletionKind.IDENTIFIER->IdeIcon.TEXT;CompletionKind.SNIPPET->IdeIcon.CODE}
        val color=when(item.kind){CompletionKind.TYPE->IdeIconDrawable.color(IdeIcon.HEADER,p);CompletionKind.KEYWORD->p.accentText;else->muted}
        glyph.setImageDrawable(IdeIconDrawable(context,icon,color,dp(18)))
        isSelected=selected;setBackgroundColor(if(selected)p.selection else base)
        IdeControls.describe(this,context.getString(R.string.completion_accessibility,item.label,item.kind.title))
    }
}
