package com.codepocket.ide.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.codepocket.ide.R

/** Actual workspace IO state only; file recovery commands remain in MainActivity's existing dialog. */
object WorkspacePlaceholder {
    fun create(context:Context,palette:IdePalette,unavailable:Boolean=false):View {
        val dp={v:Int->IdeControls.dp(context,v)}
        return FrameLayout(context).apply {
            setBackgroundColor(palette.surface)
            addView(LinearLayout(context).apply {
                orientation=LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text=context.getString(R.string.workspace_brand);textSize=13f;setTextColor(palette.foreground)
                    gravity=Gravity.CENTER_VERTICAL;setPadding(dp(16),0,dp(16),0)
                },LinearLayout.LayoutParams(-1,dp(48)))
                addView(View(context).apply{setBackgroundColor(palette.border);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO},LinearLayout.LayoutParams(-1,dp(1)))
            },FrameLayout.LayoutParams(-1,-2))
            addView(ScrollView(context).apply {
                isFillViewport=true
                addView(LinearLayout(context).apply {
                    orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(24),dp(24),dp(24),dp(24))
                    addView(ImageView(context).apply {
                        setImageDrawable(IdeIconDrawable(context,if(unavailable)IdeIcon.INFO else IdeIcon.CODE,
                            if(unavailable)palette.error else palette.accentText,dp(24)))
                        setPadding(dp(12),dp(12),dp(12),dp(12))
                        background=WorkbenchStyle.shape(context,palette.panel,palette.border,8)
                        importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    },LinearLayout.LayoutParams(dp(48),dp(48)))
                    addView(TextView(context).apply {
                        setText(if(unavailable)R.string.workspace_unavailable else R.string.workspace_loading)
                        textSize=16f;setTextColor(palette.foreground);setTypeface(null,Typeface.BOLD)
                        gravity=Gravity.CENTER;setPadding(0,dp(20),0,0)
                        accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
                    },LinearLayout.LayoutParams(-1,-2))
                    if(unavailable)addView(TextView(context).apply {
                        setText(R.string.workspace_recovery_preserved);textSize=12f;setTextColor(palette.muted)
                        gravity=Gravity.CENTER;setPadding(0,dp(8),0,0)
                    },LinearLayout.LayoutParams(-1,-2))
                },FrameLayout.LayoutParams(-1,-2))
            },FrameLayout.LayoutParams(-1,-1).apply{topMargin=dp(49)})
        }
    }
}
