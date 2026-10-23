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
import android.widget.ScrollView
import android.view.View
import com.codepocket.editor.surface.EditorSurface
import com.codepocket.ide.editor.InlineIssue
import com.codepocket.ide.ui.*

/** Read-only error hover. Never grabs the editor focus/IME or changes its buffer. */
class DiagnosticPopup(private val activity: Activity, private val editor: android.view.View, private val surface: EditorSurface) {
    private var popup: PopupWindow? = null
    val isShowing get() = popup?.isShowing == true
    private var shownAnchor: EditorSurface.Anchor? = null
    private var shownIssue: InlineIssue? = null
    fun dismiss() { popup?.dismiss(); popup = null; shownAnchor = null; shownIssue = null }
    /** A layout notification alone is not a cursor/viewport change. */
    fun onLayoutChanged() {
        if (!isShowing) return
        val previous = shownAnchor ?: return dismiss()
        val current = surface.anchor()
        if (current == null || !previous.matches(current.document, current.revision, current.offset) ||
            previous.x != current.x || previous.top != current.top || previous.bottom != current.bottom ||
            previous.visible.left != current.visible.left || previous.visible.top != current.visible.top ||
            previous.visible.right != current.visible.right || previous.visible.bottom != current.visible.bottom) dismiss()
    }
    fun onDiagnosticsChanged(current: InlineIssue?) {
        if (isShowing && current != shownIssue) dismiss()
    }
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
        val message=TextView(activity).apply {text=issue.message;textSize=13f;setTextColor(palette.foreground)}
        val scroller=ScrollView(activity).apply {isFocusable=false;addView(message)}
        content.addView(scroller,LinearLayout.LayoutParams(-1,0,1f))
        val width = minOf(dp(520), anchor.visible.right - anchor.visible.left - dp(8))
        if (width < dp(180)) return false
        message.measure(View.MeasureSpec.makeMeasureSpec((width-content.paddingLeft-content.paddingRight).coerceAtLeast(1),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        val minimum=dp(48)+kotlin.math.ceil(message.paint.fontSpacing.toDouble()).toInt()+content.paddingBottom
        val preferred=dp(48)+minOf(message.measuredHeight,dp(180))+content.paddingBottom
        val placed = CompletionGeometry.place(CompletionBounds(anchor.visible.left, anchor.visible.top, anchor.visible.right, anchor.visible.bottom),
            anchor.x, anchor.top, anchor.bottom, width, preferred, minimum, dp(4)) ?: return false
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
            popup = window; shownAnchor = anchor; shownIssue = issue; true
        } catch (_: WindowManager.BadTokenException) { window.dismiss(); false }
    }
}
