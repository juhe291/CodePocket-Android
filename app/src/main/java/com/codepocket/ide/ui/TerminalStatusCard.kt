package com.codepocket.ide.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.codepocket.ide.R

/** An IDE status card, never terminal output. Real PTY content stays in the existing TerminalView. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic callbacks; never inflated from XML.
class TerminalStatusCard(context:Context, retry:Runnable, details:Runnable, about:Runnable):ScrollView(context) {
    private fun dp(n:Int)=IdeControls.dp(context,n)
    private val body=LinearLayout(context).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(16))}
    private val engine=TextView(context).apply{setText(R.string.shell_status_engine);textSize=11f;setCompoundDrawablePadding(dp(8))}
    private val title=TextView(context).apply{textSize=17f;setTypeface(null,Typeface.BOLD);setPadding(0,dp(12),0,dp(8));contentDescription=context.getString(R.string.shell_status_heading);accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
    private val description=TextView(context).apply{textSize=13f;setLineSpacing(dp(3).toFloat(),1f)}
    private var failed=false
    private var state:Int?=null
    private val primary=TerminalChrome.key(context,DesignTokens.forContext(context),context.getString(R.string.shell_status_start),context.getString(R.string.shell_status_start),retry).apply{setTypeface(null)}
    private val secondary=TerminalChrome.key(context,DesignTokens.forContext(context),context.getString(R.string.shell_status_about),context.getString(R.string.shell_status_about),Runnable{if(failed)details.run() else about.run()}).apply{setTypeface(null)}
    init {
        isFillViewport=true;isFocusable=false;clipToPadding=false;setPadding(dp(4),dp(8),dp(4),dp(8))
        body.addView(engine);body.addView(title);body.addView(description)
        val actions=LinearLayout(context).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(0,dp(12),0,0)}
        actions.addView(primary,LinearLayout.LayoutParams(0,-2,1f));actions.addView(secondary,LinearLayout.LayoutParams(0,-2,1f))
        body.addView(actions);addView(body,LayoutParams(-1,-2));setPalette(DesignTokens.forContext(context));bind(false,false)
    }
    fun setPalette(palette:IdePalette) {
        body.background=GradientDrawable().apply{setColor(palette.panel);cornerRadius=dp(8).toFloat();setStroke(dp(1),palette.border)}
        title.setTextColor(palette.foreground);engine.setTextColor(palette.muted);description.setTextColor(palette.muted)
        engine.setCompoundDrawablesWithIntrinsicBounds(IdeIconDrawable(context,IdeIcon.TERMINAL,palette.muted,dp(16)),null,null,null)
        for(button in listOf(primary,secondary)){button.setTextColor(palette.foreground);button.background=IdeControls.feedback(context,palette)}
    }
    fun bind(failed:Boolean,preparing:Boolean) {
        val next=if(failed)2 else if(preparing)1 else 0
        if(state==next)return
        state=next;this.failed=failed
        title.setText(if(failed)R.string.shell_status_failed_title else if(preparing)R.string.shell_status_connecting_title else R.string.shell_status_empty_title)
        description.setText(if(failed)R.string.shell_status_failed_body else if(preparing)R.string.shell_status_connecting_body else R.string.shell_status_empty_body)
        primary.setText(if(failed)R.string.shell_status_retry else if(preparing)R.string.shell_status_preparing else R.string.shell_status_start)
        primary.contentDescription=primary.text;primary.isEnabled=!preparing;primary.alpha=if(preparing).5f else 1f
        secondary.setText(if(failed)R.string.shell_status_details else R.string.shell_status_about);secondary.contentDescription=secondary.text
    }
}
