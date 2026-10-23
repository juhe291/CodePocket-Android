package com.codepocket.ide.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.ImageButton
import android.widget.TextView

/** Terminal chrome follows the workbench; ANSI/program colors remain owned by the emulator. */
object TerminalChrome {
    private fun dp(context: Context, value: Int) = IdeControls.dp(context, value)
    private fun shape(context: Context, fill: Int, border: Int) = GradientDrawable().apply {
        setColor(fill); cornerRadius=dp(context,4).toFloat();if(border!=0)setStroke(dp(context,1),border)
    }
    private fun buttonBackground(context: Context, palette: IdePalette) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_selected),shape(context,palette.selection,0))
        addState(intArrayOf(android.R.attr.state_pressed),shape(context,palette.selection,palette.border))
        addState(intArrayOf(android.R.attr.state_hovered),shape(context,palette.overlay,palette.border))
        addState(intArrayOf(android.R.attr.state_focused),shape(context,palette.selection,palette.accent))
        addState(intArrayOf(),shape(context,android.graphics.Color.TRANSPARENT,0))
    }.let { InsetDrawable(it,dp(context,3),dp(context,8),dp(context,3),dp(context,8)) }

    fun action(context: Context, palette: IdePalette, icon: IdeIcon, description: String, callback: Runnable): ImageButton =
        IdeControls.icon(context,palette,icon,description) { callback.run() }.apply {
            setPadding(dp(context,14),dp(context,14),dp(context,14),dp(context,14))
            background=InsetDrawable(IdeControls.feedback(context,palette),dp(context,5),dp(context,7),dp(context,5),dp(context,7))
            isFocusable=true
        }

    fun key(context: Context, palette: IdePalette, title: String, description: String, callback: Runnable): TextView =
        TextView(context).apply {
            text=title; textSize=11f;typeface=Typeface.MONOSPACE;setTextColor(palette.foreground)
            gravity=Gravity.CENTER;includeFontPadding=false;minHeight=dp(context,48);minWidth=dp(context,48)
            setPadding(dp(context,10),0,dp(context,10),0);background=buttonBackground(context,palette)
            isFocusable=true;isClickable=true;IdeControls.describe(this,description);setOnClickListener{callback.run()}
        }

    fun channel(context: Context, palette: IdePalette, title: String, icon: IdeIcon, selected: Boolean, callback: () -> Unit): TextView =
        object: TextView(context) {
            private val line=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=palette.accent}
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                if(isSelected)canvas.drawRoundRect(dp(context,10).toFloat(),height-dp(context,2).toFloat(),width-dp(context,10).toFloat(),height.toFloat(),dp(context,1).toFloat(),dp(context,1).toFloat(),line)
            }
        }.apply {
            text=title;textSize=12f;setTextColor(if(selected)palette.foreground else palette.muted)
            isSelected=selected;isFocusable=true;isClickable=true;gravity=Gravity.CENTER;includeFontPadding=false
            minWidth=dp(context,64);minHeight=dp(context,48);setPadding(dp(context,12),0,dp(context,12),0)
            compoundDrawablePadding=dp(context,7)
            setCompoundDrawablesWithIntrinsicBounds(IdeIconDrawable(context,icon,if(selected)palette.accentText else palette.muted,dp(context,17)),null,null,null)
            background=IdeControls.feedback(context,palette);setOnClickListener{callback()}
            IdeControls.describe(this,"$title${if(selected)"，当前选中" else ""}")
        }

    fun session(context: Context, palette: IdePalette, name: String, description: String, callback: () -> Unit): TextView =
        TextView(context).apply {
            text=name;textSize=12f;setTextColor(palette.foreground);gravity=Gravity.CENTER_VERTICAL
            setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END;includeFontPadding=false
            setPadding(dp(context,12),0,dp(context,12),0);compoundDrawablePadding=dp(context,8)
            setCompoundDrawablesWithIntrinsicBounds(IdeIconDrawable(context,IdeIcon.TERMINAL,palette.muted,dp(context,17)),null,IdeIconDrawable(context,IdeIcon.DOWN,palette.muted,dp(context,14)),null)
            background=buttonBackground(context,palette);minHeight=dp(context,48);isFocusable=true;isClickable=true
            IdeControls.describe(this,description);setOnClickListener{callback()}
        }
}
