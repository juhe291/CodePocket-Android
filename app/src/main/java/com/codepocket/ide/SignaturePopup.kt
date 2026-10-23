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
        setPadding(dp(8), 0, dp(8), 0); gravity = Gravity.CENTER_VERTICAL; minHeight=dp(48); setSingleLine()
    }
    private val scroll = HorizontalScrollView(activity).apply { minimumHeight=dp(48); isHorizontalScrollBarEnabled = false; addView(signature) }
    private val details = TextView(activity).apply {
        textSize = 11f; setTextColor(muted); maxLines = 2; setPadding(dp(8), dp(2), dp(8), 0)
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val disclaimer = TextView(activity).apply {
        textSize = 10f; setTextColor(muted); setPadding(dp(8), 0, dp(8), 0); minHeight=dp(20); maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val heading = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, 0, 0) }
    private var recentlyClosedDocument: String? = null
    private var recentlyClosedAt = Long.MIN_VALUE
    private var result: SignatureResult? = null
    private var variant = 0
    private val popup = PopupWindow(content, 1, 1, false).apply {
        isOutsideTouchable = false; inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        elevation = dp(6).toFloat(); animationStyle = 0; enterTransition = null; exitTransition = null
        setBackgroundDrawable(GradientDrawable().apply { setColor(backgroundColor); cornerRadius = dp(8).toFloat(); setStroke(dp(1), com.codepocket.ide.ui.DesignTokens.forContext(activity).border) })
        if (Build.VERSION.SDK_INT >= 29) setIsLaidOutInScreen(true)
        setOnDismissListener {
            recentlyClosedDocument = result?.request?.document; recentlyClosedAt = android.os.SystemClock.uptimeMillis()
            Animations.cancel(content)
        }
    }
    val isShowing get() = popup.isShowing

    init {
        heading.addView(title, LinearLayout.LayoutParams(0, dp(48), 1f))
        fun control(icon:com.codepocket.ide.ui.IdeIcon,description:Int,action:()->Unit) {
            heading.addView(com.codepocket.ide.ui.IdeControls.icon(activity,com.codepocket.ide.ui.DesignTokens.forContext(activity),icon,activity.getString(description),action),LinearLayout.LayoutParams(dp(48),dp(48)))
        }
        control(com.codepocket.ide.ui.IdeIcon.BACK,R.string.signature_previous) { cycle(-1) }
        control(com.codepocket.ide.ui.IdeIcon.NEXT,R.string.signature_next) { cycle(1) }
        control(com.codepocket.ide.ui.IdeIcon.CLOSE,R.string.signature_close) { close() }
        content.addView(heading)
        content.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        content.addView(details, LinearLayout.LayoutParams(-1, -2))
        content.addView(disclaimer, LinearLayout.LayoutParams(-1, -2))
    }

    fun show(next: SignatureResult, animate: Boolean): Boolean {
        val alreadyShowing = isShowing
        if (result?.request?.document != next.request.document || result?.openOffset != next.openOffset ||
            result?.reference?.name != next.reference.name) variant = 0
        result = next; render()
        if (!reposition()) return false
        popup.exitTransition = if (com.codepocket.ide.animation.MotionEnvironment.enabled(activity, animate))
            android.transition.Fade().apply { duration = 80 } else null
        val continuing = next.request.document == recentlyClosedDocument &&
            android.os.SystemClock.uptimeMillis() - recentlyClosedAt in 0..350
        Animations.popup(content, alreadyShowing || continuing, animate)
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
        if (activity.isDestroyed || activity.isFinishing) { dismiss(false); return false }
        val anchor = editorSurface.anchor() ?: run { dismiss(false); return false }
        val request = result?.request ?: run { dismiss(false); return false }
        val matches = anchor.matches(request.document, request.revision, request.caret)
        if (!matches) { dismiss(false); return false }
        val visible = anchor.visible
        val width=minOf(dp(460),visible.right-visible.left)
        if(width<dp(260)){dismiss(false);return false}
        details.visibility=View.VISIBLE
        fun measure(){content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))}
        measure()
        val minimum=heading.measuredHeight+scroll.measuredHeight+disclaimer.measuredHeight
        fun place()=CompletionGeometry.place(CompletionBounds(visible.left,visible.top,visible.right,visible.bottom),anchor.x,anchor.top,anchor.bottom,width,content.measuredHeight,minimum,dp(3))
        var placed=place()?:run{dismiss(false);return false}
        if(placed.height<content.measuredHeight){details.visibility=View.GONE;measure();placed=place()?:run{dismiss(false);return false}}
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
            catch (_: WindowManager.BadTokenException) { dismiss(false); return false }
        }
        return true
    }

    fun dismiss(animated: Boolean = true) { if (!animated) popup.exitTransition = null; Animations.cancel(content); popup.dismiss() }
}
