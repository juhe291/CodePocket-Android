package com.codepocket.ide.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Empty editor surface. Actions use the same guarded file and system-picker paths as the menus. */
object WorkbenchWelcome {
    fun create(context: Context, palette: IdePalette, projectOpen: Boolean,
        newFile: () -> Unit, openProject: () -> Unit): View {
        fun dp(value: Int) = IdeControls.dp(context, value)
        fun text(value: String, size: Float, color: Int) = TextView(context).apply {
            text = value; textSize = size; setTextColor(color)
        }
        val content = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(dp(560))
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(32), dp(28), dp(32))
            addView(text("CodePocket", 30f, palette.foreground).apply { setTypeface(null, Typeface.BOLD) })
            addView(text(if (projectOpen) "开始编辑" else "开始", 16f, palette.muted).apply {
                setPadding(0, dp(8), 0, dp(24))
            })
            fun action(label: String, icon: IdeIcon, onClick: () -> Unit) {
                addView(text(label, 15f, palette.accentText).apply {
                    minimumHeight = dp(56); gravity = Gravity.CENTER_VERTICAL
                    val drawable = IdeIconDrawable(context, icon, palette.accentText, dp(20)).apply { setBounds(0, 0, dp(20), dp(20)) }
                    setCompoundDrawablesRelative(drawable, null, null, null); compoundDrawablePadding = dp(12)
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    background = IdeControls.feedback(context, palette); isFocusable = true; isClickable = true
                    contentDescription = label; setOnClickListener { onClick() }
                }, LinearLayout.LayoutParams(-1, -2))
            }
            action("新建文件", IdeIcon.ADD_FILE, newFile)
            action("打开项目", IdeIcon.FOLDER_OPEN, openProject)
            addView(text(if (projectOpen) "从资源管理器打开文件，或新建文件。" else "打开文件夹，继续你的项目。", 12f, palette.muted).apply {
                setPadding(dp(12), dp(24), dp(12), 0)
            })
        }
        return ScrollView(context).apply {
            tag = "workbench-welcome"; contentDescription = "开始工作"; isFillViewport = true
            isFocusableInTouchMode = true; setBackgroundColor(palette.editor)
            addView(FrameLayout(context).apply {
                addView(content, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
            }, FrameLayout.LayoutParams(-1, -2))
        }
    }
}
