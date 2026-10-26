package com.codepocket.ide.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
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
    /** Stack controls when available width or scaled text would squeeze the query. */
    fun searchControls(context:Context,query:EditText,button:Button)=object:LinearLayout(context){
        private var stacked:Boolean?=null
        init { gravity=Gravity.CENTER_VERTICAL; minimumHeight=IdeControls.dp(context,48) }
        override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
            val stack=MeasureSpec.getSize(widthMeasureSpec)<IdeControls.dp(context,360) || resources.configuration.fontScale>1.3f
            if(stacked!=stack){
            stacked=stack
            val target=if(stack)VERTICAL else HORIZONTAL
            if(orientation!=target)orientation=target
            query.layoutParams=LayoutParams(if(stack)-1 else 0,-2,if(stack)0f else 1f)
            button.layoutParams=LayoutParams(-2,-2).apply{
                if(stack){gravity=Gravity.END;topMargin=IdeControls.dp(context,8)}else marginStart=IdeControls.dp(context,8)
            }
            }
            super.onMeasure(widthMeasureSpec,heightMeasureSpec)
        }
    }.apply{
        query.minimumHeight=IdeControls.dp(context,48);button.minimumHeight=IdeControls.dp(context,48)
        addView(query);addView(button)
    }
    fun scroll(context:Context,body:View)=ScrollView(context).apply{isFillViewport=false;addView(body)}
    /** Decorative heading only: no extra command, focus target or editable text. */
    fun sectionHeading(context:Context,title:String,subtitle:String,icon:IdeIcon):View {
        val p=DesignTokens.forContext(context);val dp={v:Int->IdeControls.dp(context,v)}
        return LinearLayout(context).apply{
            orientation=LinearLayout.VERTICAL
            val line=LinearLayout(context).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;minimumHeight=dp(44)}
            line.addView(ImageView(context).apply{
                setImageDrawable(IdeIconDrawable(context,icon,p.accentText,dp(20)))
                setPadding(dp(8),dp(8),dp(8),dp(8))
                background=WorkbenchStyle.shape(context,p.panel,p.border,6)
                importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },LinearLayout.LayoutParams(dp(36),dp(36)))
            val words=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,0,0)}
            words.addView(TextView(context).apply{
                text=title;textSize=16f;setTypeface(null,Typeface.BOLD);setTextColor(p.foreground)
            })
            if(subtitle.isNotBlank())words.addView(caption(context,subtitle).apply{
                setPadding(0,dp(4),0,0);maxLines=3
                ellipsize=android.text.TextUtils.TruncateAt.MIDDLE;contentDescription=subtitle
            })
            line.addView(words,LinearLayout.LayoutParams(0,-2,1f))
            addView(line,LinearLayout.LayoutParams(-1,-2))
            addView(View(context).apply{setBackgroundColor(p.border);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},
                LinearLayout.LayoutParams(-1,dp(1)).apply{topMargin=dp(12);bottomMargin=dp(8)})
        }
    }
    fun header(context:Context,title:String,subtitle:String,icon:IdeIcon):View = body(context).apply{
        setPadding(IdeControls.dp(context,20),IdeControls.dp(context,18),IdeControls.dp(context,20),IdeControls.dp(context,4))
        addView(sectionHeading(context,title,subtitle,icon))
    }
    fun details(context:Context,text:String,monospace:Boolean=false):ScrollView {
        val p=DesignTokens.forContext(context)
        return scroll(context,TextView(context).apply{
            this.text=text;textSize=13f;setTextColor(p.foreground);setTextIsSelectable(true)
            if(monospace)typeface=Typeface.MONOSPACE
            setPadding(IdeControls.dp(context,20),IdeControls.dp(context,10),IdeControls.dp(context,20),IdeControls.dp(context,16))
        })
    }
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
