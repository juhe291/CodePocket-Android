package com.codepocket.ide

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.codepocket.editor.surface.EditorSurface
import com.codepocket.ide.editor.InlineIssue
import com.codepocket.ide.ui.*

/** Read-only error hover. Never grabs the editor focus/IME or changes its buffer. */
class DiagnosticPopup(private val activity: Activity, private val editor: android.view.View, private val surface: EditorSurface) {
    private var popup: PopupWindow? = null
    val isShowing get() = popup?.isShowing == true
    fun dismiss() { popup?.dismiss(); popup = null }
    fun show(issue: InlineIssue): Boolean {
        dismiss()
        val anchor = surface.anchor() ?: return false
        val palette = DesignTokens.forContext(activity)
        fun dp(v: Int) = IdeControls.dp(activity, v)
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), dp(12)) }
        val title = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        title.addView(TextView(activity).apply { text = issue.source; textSize = 12f; setTextColor(palette.error); setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, dp(48), 1f))
        title.addView(IdeControls.icon(activity, palette, IdeIcon.COPY, "复制错误信息") {
            (activity.getSystemService(Activity.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(issue.source, issue.message))
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        title.addView(IdeControls.icon(activity, palette, IdeIcon.CLOSE, "关闭错误提示") { dismiss() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(title)
        content.addView(TextView(activity).apply { text = issue.message; textSize = 13f; maxLines = 6; setTextColor(palette.foreground); ellipsize = android.text.TextUtils.TruncateAt.END })
        val width = minOf(dp(520), anchor.visible.right - anchor.visible.left - dp(8))
        if (width < dp(180)) return false
        content.measure(android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(dp(240), android.view.View.MeasureSpec.AT_MOST))
        val placed = CompletionGeometry.place(CompletionBounds(anchor.visible.left, anchor.visible.top, anchor.visible.right, anchor.visible.bottom),
            anchor.x, anchor.top, anchor.bottom, width, content.measuredHeight, dp(72), dp(4)) ?: return false
        val window = PopupWindow(content, placed.width, placed.height, false).apply {
            isOutsideTouchable = false; inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            elevation = dp(6).toFloat(); animationStyle = 0
            setBackgroundDrawable(GradientDrawable().apply { setColor(palette.overlay); cornerRadius = dp(8).toFloat(); setStroke(dp(1), palette.border) })
            if (Build.VERSION.SDK_INT >= 29) setIsLaidOutInScreen(true)
        }
        var x = placed.x; var y = placed.y
        if (Build.VERSION.SDK_INT < 29) {
            val screen = IntArray(2); val local = IntArray(2)
            activity.window.decorView.getLocationOnScreen(screen); activity.window.decorView.getLocationInWindow(local)
            x -= screen[0] - local[0]; y -= screen[1] - local[1]
        }
        return try {
            @Suppress("RtlHardcoded")
            window.showAtLocation(editor, Gravity.TOP or Gravity.LEFT, x, y)
            popup = window; true
        } catch (_: WindowManager.BadTokenException) { window.dismiss(); false }
    }
}
