package com.codepocket.ide

import android.app.Activity
import com.codepocket.ide.animation.Animations
import com.codepocket.editor.surface.EditorSurface
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/** Read-only, non-focusable reference. Never takes the editor's IME, selection or keyboard events. */
class SignaturePopup(private val activity: Activity, private val editor: View,
    private val editorSurface: EditorSurface,
    private val backgroundColor: Int, private val foregroundColor: Int, private val muted: Int,
    private val accent: Int, private val close: () -> Unit) {
    private fun dp(value: Int) = (activity.resources.displayMetrics.density * value + .5f).toInt()
    private val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val title = TextView(activity).apply { textSize = 11f; setTextColor(muted); maxLines = 1 }
    private val signature = TextView(activity).apply {
        textSize = 13f; typeface = Typeface.MONOSPACE; setTextColor(foregroundColor)
        setPadding(dp(8), 0, dp(8), 0); gravity = Gravity.CENTER_VERTICAL; setSingleLine()
    }
    private val scroll = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false; addView(signature) }
    private val details = TextView(activity).apply {
        textSize = 11f; setTextColor(muted); maxLines = 2; setPadding(dp(8), dp(2), dp(8), 0)
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val disclaimer = TextView(activity).apply {
        textSize = 10f; setTextColor(muted); setPadding(dp(8), 0, dp(8), 0); maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private var result: SignatureResult? = null
    private var variant = 0
    private val popup = PopupWindow(content, 1, 1, false).apply {
        isOutsideTouchable = false; inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        elevation = dp(6).toFloat(); animationStyle = 0; enterTransition = null; exitTransition = null
        setBackgroundDrawable(GradientDrawable().apply { setColor(backgroundColor); setStroke(dp(1), muted) })
        if (Build.VERSION.SDK_INT >= 29) setIsLaidOutInScreen(true)
        setOnDismissListener { Animations.cancel(content) }
    }
    val isShowing get() = popup.isShowing

    init {
        val heading = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, 0, 0) }
        heading.addView(title, LinearLayout.LayoutParams(0, dp(44), 1f))
        fun control(label: String, description: Int, action: () -> Unit) = TextView(activity).apply {
            text = label; textSize = 18f; gravity = Gravity.CENTER; setTextColor(foregroundColor)
            contentDescription = activity.getString(description); setOnClickListener { action() }
            heading.addView(this, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        control("‹", R.string.signature_previous) { cycle(-1) }
        control("›", R.string.signature_next) { cycle(1) }
        control("×", R.string.signature_close) { close() }
        content.addView(heading)
        content.addView(scroll, LinearLayout.LayoutParams(-1, dp(44)))
        content.addView(details, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addView(disclaimer, LinearLayout.LayoutParams(-1, dp(20)))
    }

    fun show(next: SignatureResult, animate: Boolean): Boolean {
        val alreadyShowing = isShowing
        if (result?.request?.document != next.request.document || result?.openOffset != next.openOffset ||
            result?.reference?.name != next.reference.name) variant = 0
        result = next; render()
        if (!reposition()) return false
        Animations.popup(content, alreadyShowing, animate)
        return true
    }

    private fun cycle(delta: Int) {
        val size = result?.reference?.forms?.size ?: return
        variant = (variant + delta + size) % size
        render()
    }

    private fun render() {
        val match = result ?: return
        val reference = match.reference
        variant = variant.coerceIn(reference.forms.indices)
        val form = reference.forms[variant]
        title.text = activity.getString(R.string.signature_heading, variant + 1, reference.forms.size)
        val label = form.label(reference.name)
        val range = form.range(reference.name, match.active(variant))
        signature.text = SpannableString(label).apply {
            range?.let { (start, end) ->
                setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                setSpan(ForegroundColorSpan(accent), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        details.text = reference.description
        disclaimer.setText(if (reference.cpp) R.string.signature_cpp_notice else R.string.signature_python_notice)
        signature.contentDescription = activity.getString(R.string.signature_accessibility, label,
            range?.let { label.substring(it.first, it.second) } ?: activity.getString(R.string.signature_unmapped))
        scroll.post { scroll.scrollTo((signature.paint.measureText(label.take(range?.first ?: 0)).toInt() - dp(30)).coerceAtLeast(0), 0) }
    }

    fun reposition(): Boolean {
        if (activity.isDestroyed || activity.isFinishing) { dismiss(); return false }
        val anchor = editorSurface.anchor() ?: run { dismiss(); return false }
        val request = result?.request ?: run { dismiss(); return false }
        val matches = anchor.matches(request.document, request.revision, request.caret)
        if (!matches) { dismiss(); return false }
        val visible = anchor.visible
        val placed = CompletionGeometry.place(CompletionBounds(visible.left, visible.top, visible.right, visible.bottom),
            anchor.x, anchor.top, anchor.bottom, dp(460), dp(156), dp(144), dp(3)) ?: run { dismiss(); return false }
        if (placed.width < dp(260)) { dismiss(); return false }
        var px = placed.x; var py = placed.y
        if (Build.VERSION.SDK_INT < 29) {
            val screen = IntArray(2); val local = IntArray(2)
            activity.window.decorView.getLocationOnScreen(screen); activity.window.decorView.getLocationInWindow(local)
            px -= screen[0] - local[0]; py -= screen[1] - local[1]
        }
        if (popup.isShowing) popup.update(px, py, placed.width, placed.height)
        else {
            popup.width = placed.width; popup.height = placed.height
            @Suppress("RtlHardcoded")
            try { popup.showAtLocation(editor, Gravity.TOP or Gravity.LEFT, px, py) }
            catch (_: WindowManager.BadTokenException) { dismiss(); return false }
        }
        return true
    }

    fun dismiss() { Animations.cancel(content); popup.dismiss() }
}
