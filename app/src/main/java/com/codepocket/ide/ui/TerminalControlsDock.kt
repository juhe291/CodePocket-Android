package com.codepocket.ide.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.codepocket.ide.R

/** Existing terminal actions only. A narrow dock keeps touch sizes and scrolls if even icons cannot fit. */
@SuppressLint("ViewConstructor") // Native-only callbacks; not an XML view.
class TerminalControlsDock(context:Context,palette:IdePalette,onKeyboard:Runnable,onControls:Runnable,
    onCopy:Runnable,onBottom:Runnable):HorizontalScrollView(context){
    private fun dp(value:Int)=IdeControls.dp(context,value)
    private val keyboard=TerminalChrome.key(context,palette,context.getString(R.string.terminal_dock_keyboard),
        context.getString(R.string.terminal_dock_keyboard_description),onKeyboard).apply{
        setTypeface(null);setCompoundDrawablesWithIntrinsicBounds(IdeIconDrawable(context,IdeIcon.INPUT,palette.muted,dp(17)),null,null,null)
    }
    private val controls=TerminalChrome.key(context,palette,context.getString(R.string.terminal_dock_controls),
        context.getString(R.string.terminal_dock_controls_description),onControls).apply{
        setTypeface(null);setCompoundDrawablesWithIntrinsicBounds(IdeIconDrawable(context,IdeIcon.BRACKETS,palette.muted,dp(17)),null,null,null)
    }
    private val keyboardParams=LinearLayout.LayoutParams(dp(92),dp(48))
    private val controlsParams=LinearLayout.LayoutParams(dp(100),dp(48))
    private val bar=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(5),0,dp(5),0)}
    private var compact:Boolean?=null
    init{
        isFillViewport=true;isFocusable=false;descendantFocusability=FOCUS_AFTER_DESCENDANTS
        isHorizontalScrollBarEnabled=true;setBackgroundColor(palette.panel)
        bar.addView(keyboard,keyboardParams);bar.addView(controls,controlsParams)
        bar.addView(View(context),LinearLayout.LayoutParams(0,1,1f))
        bar.addView(TerminalChrome.action(context,palette,IdeIcon.COPY,context.getString(R.string.terminal_dock_copy_description),onCopy),LinearLayout.LayoutParams(dp(48),dp(48)))
        bar.addView(TerminalChrome.action(context,palette,IdeIcon.DOWN,context.getString(R.string.terminal_dock_bottom_description),onBottom),LinearLayout.LayoutParams(dp(48),dp(48)))
        addView(bar,LayoutParams(-2,dp(48)))
    }
    fun setControlsExpanded(value:Boolean){controls.isSelected=value}
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
        val iconOnly=MeasureSpec.getSize(widthMeasureSpec)/resources.displayMetrics.density<320 || resources.configuration.fontScale>1.3f
        if(compact!=iconOnly){
            compact=iconOnly
            keyboard.text=if(iconOnly)"" else context.getString(R.string.terminal_dock_keyboard)
            controls.text=if(iconOnly)"" else context.getString(R.string.terminal_dock_controls)
            keyboardParams.width=dp(if(iconOnly)48 else 92);controlsParams.width=dp(if(iconOnly)48 else 100)
            keyboard.layoutParams=keyboardParams;controls.layoutParams=controlsParams
            keyboard.compoundDrawablePadding=if(iconOnly)0 else dp(6);controls.compoundDrawablePadding=keyboard.compoundDrawablePadding
            val inset=dp(if(iconOnly)15 else 10)
            keyboard.setPadding(inset,0,inset,0);controls.setPadding(inset,0,inset,0)
        }
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
}
