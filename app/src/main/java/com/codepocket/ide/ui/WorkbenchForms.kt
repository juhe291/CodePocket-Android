package com.codepocket.ide.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Presentation only: callers retain every validation, save and filesystem transaction. */
enum class SurfaceTextTone { MUTED, DANGER }
object WorkbenchForms {
    fun body(context:Context)=LinearLayout(context).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(IdeControls.dp(context,20),IdeControls.dp(context,12),IdeControls.dp(context,20),IdeControls.dp(context,8))
    }
    fun caption(context:Context,text:String)=TextView(context).apply{
        this.text=text;textSize=11f;setTextColor(DesignTokens.forContext(context).muted);tag=SurfaceTextTone.MUTED
        setPadding(0,0,0,IdeControls.dp(context,8))
    }
    fun location(context:Context,label:String,path:String,icon:IdeIcon=IdeIcon.FOLDER):View {
        val p=DesignTokens.forContext(context);val dp={v:Int->IdeControls.dp(context,v)}
        return LinearLayout(context).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.TOP;setPadding(dp(12),dp(10),dp(12),dp(10))
            background=WorkbenchStyle.shape(context,p.panel,p.border,4)
            addView(ImageView(context).apply{setImageDrawable(IdeIconDrawable(context,icon,p.muted,dp(18)));importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(dp(24),dp(24)))
            val words=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),0,0,0)}
            words.addView(caption(context,label))
            words.addView(TextView(context).apply{
                text=path;textSize=12f;setTextColor(p.foreground);maxLines=3;ellipsize=android.text.TextUtils.TruncateAt.MIDDLE
                contentDescription="$label：$path"
            })
            addView(words,LinearLayout.LayoutParams(0,-2,1f))
        }
    }
    fun scroll(context:Context,body:View)=ScrollView(context).apply{isFillViewport=false;addView(body)}
    fun searchResult(context:Context,path:String,line:Int,code:String):View {
        val p=DesignTokens.forContext(context);val dp={v:Int->IdeControls.dp(context,v)}
        return LinearLayout(context).apply{
            orientation=LinearLayout.VERTICAL;minimumHeight=dp(64);setPadding(dp(12),dp(10),dp(12),dp(10))
            background=IdeControls.feedback(context,p)
            addView(TextView(context).apply{
                text="$path:$line";textSize=12f;setTextColor(p.accentText);setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.MIDDLE
                setCompoundDrawablesWithIntrinsicBounds(IdeIconDrawable(context,IdeIconDrawable.file(path),IdeIconDrawable.color(IdeIconDrawable.file(path),p),dp(16)),null,null,null)
                compoundDrawablePadding=dp(8)
            })
            addView(TextView(context).apply{
                text=code.take(160);textSize=12f;typeface=Typeface.MONOSPACE;setTextColor(p.foreground)
                setPadding(dp(24),dp(6),0,0);setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END
            })
            contentDescription="$path:$line\n$code"
        }
    }
}
