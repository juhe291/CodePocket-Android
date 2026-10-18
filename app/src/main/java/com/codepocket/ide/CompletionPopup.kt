package com.codepocket.ide

import android.app.Activity
import com.codepocket.editor.surface.EditorSurface
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView

/** Native non-focusable list: editor/IME retain ownership of input, selection and undo. */
class CompletionPopup(private val activity: Activity, private val editor: View,
    private val editorSurface: EditorSurface,
    private val backgroundColor: Int, private val foregroundColor: Int, private val muted: Int,
    private val accent: Int, private val accept: (Int) -> Unit, private val dismissed: () -> Unit) {
    private fun dp(value: Int) = (activity.resources.displayMetrics.density * value + .5f).toInt()
    private val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val details = TextView(activity).apply {
        textSize = 11f; setTextColor(muted); setPadding(dp(8), dp(4), dp(8), dp(4))
        maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private var items = emptyList<CompletionItem>()
    private var document: String? = null
    private var revision = -1L
    private var offset = -1
    var selectedIndex = 0
        private set
    var explicitSelection = false
        private set
    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = (convertView as? TextView) ?: TextView(activity).apply {
                textSize = 13f; typeface = Typeface.MONOSPACE; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), 0, dp(8), 0); minHeight = dp(44); setSingleLine()
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val item = items[position]
            row.text = activity.getString(R.string.completion_item, item.kind.icon, item.label)
            row.contentDescription = activity.getString(R.string.completion_accessibility, item.label, item.kind.title)
            row.setTextColor(if (position == selectedIndex) android.graphics.Color.WHITE else foregroundColor)
            row.setBackgroundColor(if (position == selectedIndex) accent else backgroundColor)
            return row
        }
    }
    private val list = ListView(activity).apply {
        adapter = this@CompletionPopup.adapter; dividerHeight = 0; isFocusable = false
        setOnItemClickListener { _, _, index, _ -> accept(index) }
    }
    private val popup = PopupWindow(content, 1, 1, false).apply {
        isOutsideTouchable = true; inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        elevation = dp(6).toFloat(); animationStyle = 0
        enterTransition = null; exitTransition = null
        setBackgroundDrawable(GradientDrawable().apply { setColor(backgroundColor); setStroke(dp(1), muted) })
        if (Build.VERSION.SDK_INT >= 29) setIsLaidOutInScreen(true)
        setOnDismissListener { content.animate().cancel(); content.alpha = 1f; dismissed() }
    }
    val isShowing get() = popup.isShowing

    init {
        val heading = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(TextView(activity).apply {
            setText(R.string.completion_lexical); textSize = 11f; setTextColor(muted); setPadding(dp(8), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, dp(32), 1f))
        heading.addView(TextView(activity).apply {
            text = "×"; textSize = 20f; setTextColor(foregroundColor); gravity = Gravity.CENTER
            setPadding(dp(8), 0, dp(8), 0); contentDescription = activity.getString(R.string.completion_close)
            setOnClickListener { dismiss() }
        }, LinearLayout.LayoutParams(dp(44), dp(32)))
        content.addView(heading); content.addView(list, LinearLayout.LayoutParams(-1, 0, 1f)); content.addView(details)
    }

    fun show(batch: CompletionBatch, animate: Boolean): Boolean {
        document = batch.request.document; revision = batch.request.revision; offset = batch.request.caret
        items = batch.items; selectedIndex = 0; explicitSelection = false
        adapter.notifyDataSetChanged(); updateDetails()
        if (!reposition()) return false
        if (animate) {
            content.alpha = .85f
            content.animate().alpha(1f).setDuration(90).start()
        }
        return true
    }

    fun move(delta: Int) {
        if (items.isEmpty()) return
        explicitSelection = true; selectedIndex = (selectedIndex + delta).coerceIn(0, items.lastIndex)
        adapter.notifyDataSetChanged(); list.setSelection(selectedIndex); updateDetails()
    }

    private fun updateDetails() {
        val item = items.getOrNull(selectedIndex) ?: return
        details.text = activity.getString(R.string.completion_detail, item.kind.title, item.detail)
    }

    fun reposition(): Boolean {
        if (activity.isDestroyed || activity.isFinishing) { dismiss(); return false }
        val anchor = editorSurface.anchor() ?: run { dismiss(); return false }
        val matches = anchor.matches(document, revision, offset)
        if (!matches) { dismiss(); return false }
        val visible = anchor.visible
        val desired = dp(32 + minOf(items.size, 5) * 44 + 60)
        val placed = CompletionGeometry.place(CompletionBounds(visible.left, visible.top, visible.right, visible.bottom),
            anchor.x, anchor.top, anchor.bottom, dp(380), desired, dp(76), dp(3)) ?: run { dismiss(); return false }
        details.visibility = if (placed.height >= dp(32 + 2 * 44 + 60)) View.VISIBLE else View.GONE
        var popupX = placed.x; var popupY = placed.y
        if (Build.VERSION.SDK_INT < 29) {
            val screen = IntArray(2); val local = IntArray(2)
            activity.window.decorView.getLocationOnScreen(screen); activity.window.decorView.getLocationInWindow(local)
            popupX -= screen[0] - local[0]; popupY -= screen[1] - local[1]
        }
        if (popup.isShowing) popup.update(popupX, popupY, placed.width, placed.height)
        else {
            popup.width = placed.width; popup.height = placed.height
            @Suppress("RtlHardcoded")
            try { popup.showAtLocation(editor, Gravity.TOP or Gravity.LEFT, popupX, popupY) }
            catch (_: WindowManager.BadTokenException) { dismiss(); return false }
        }
        return true
    }

    fun dismiss() { content.animate().cancel(); content.alpha = 1f; popup.dismiss() }
}
