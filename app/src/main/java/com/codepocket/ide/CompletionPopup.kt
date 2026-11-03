package com.codepocket.ide

import android.app.Activity
import com.codepocket.ide.animation.Animations
import com.codepocket.editor.surface.EditorSurface
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
    private val candidateHeight=com.codepocket.ide.ui.CompletionCandidateRow(activity,foregroundColor,backgroundColor,muted).requiredHeight
    private val details = TextView(activity).apply {
        textSize = 11f; setTextColor(muted); setPadding(dp(8), dp(4), dp(8), dp(4))
        maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END
        setBackgroundColor(com.codepocket.ide.ui.DesignTokens.forContext(activity).panel)
    }
    private var recentlyClosedDocument: String? = null
    private var recentlyClosedAt = Long.MIN_VALUE
    private var items = emptyList<CompletionItem>()
    private var document: String? = null
    private var revision = -1L
    private var offset = -1
    var selectedIndex = 0
        private set
    var explicitSelection = false
        private set
    var userInteracted = false
        private set
    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row=(convertView as? com.codepocket.ide.ui.CompletionCandidateRow)
                ?: com.codepocket.ide.ui.CompletionCandidateRow(activity,foregroundColor,backgroundColor,muted)
            row.bind(items[position],position==selectedIndex)
            return row
        }
    }
    private val list = ListView(activity).apply {
        adapter = this@CompletionPopup.adapter; dividerHeight = 0; isFocusable = false
        setOnTouchListener{_,event->if(event.actionMasked==android.view.MotionEvent.ACTION_DOWN)userInteracted=true;false}
        setOnItemClickListener { _, _, index, _ -> accept(index) }
    }
    private val popup = PopupWindow(content, 1, 1, false).apply {
        // Activity dismisses outside touches, preserving the toolbar Tab acceptance gesture.
        isOutsideTouchable = false; inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        elevation = dp(6).toFloat(); animationStyle = 0
        enterTransition = null; exitTransition = null
        setBackgroundDrawable(GradientDrawable().apply { setColor(backgroundColor); cornerRadius = dp(8).toFloat(); setStroke(dp(1), com.codepocket.ide.ui.DesignTokens.forContext(activity).border) })
        if (Build.VERSION.SDK_INT >= 29) setIsLaidOutInScreen(true)
        setOnDismissListener {
            recentlyClosedDocument = document; recentlyClosedAt = android.os.SystemClock.uptimeMillis()
            Animations.cancel(content); dismissed()
        }
    }
    val isShowing get() = popup.isShowing
    private val heading = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL;setBackgroundColor(com.codepocket.ide.ui.DesignTokens.forContext(activity).panel) }

    private val sourceHeading=TextView(activity).apply {
            setText(R.string.completion_lexical);setSingleLine();ellipsize=android.text.TextUtils.TruncateAt.END;contentDescription=activity.getString(R.string.completion_lexical); textSize = 11f; setTextColor(muted); setPadding(dp(8), 0, 0, 0)
    }
    var onDocumentation:((CompletionItem,String)->Unit)?=null
        set(value){field=value;documentationButton.visibility=if(value==null)View.GONE else View.VISIBLE}
    private val documentationButton=com.codepocket.ide.ui.IdeControls.icon(activity,com.codepocket.ide.ui.DesignTokens.forContext(activity),
        com.codepocket.ide.ui.IdeIcon.INFO,"查看候选说明") {
        val item=items.getOrNull(selectedIndex)
        if(isShowing && item!=null && editorSurface.anchor()?.matches(document,revision,offset)==true)onDocumentation?.invoke(item,sourceHeading.text.toString())
    }.apply{tag="completion-documentation";visibility=View.GONE}
    init {
        heading.addView(sourceHeading, LinearLayout.LayoutParams(0, dp(48), 1f))
        heading.addView(documentationButton,LinearLayout.LayoutParams(dp(48),dp(48)))
        heading.addView(com.codepocket.ide.ui.IdeControls.icon(activity,com.codepocket.ide.ui.DesignTokens.forContext(activity),com.codepocket.ide.ui.IdeIcon.CLOSE,
            activity.getString(R.string.completion_close)) { dismiss() }, LinearLayout.LayoutParams(dp(48),dp(48)))
        content.addView(heading); content.addView(list, LinearLayout.LayoutParams(-1, 0, 1f)); content.addView(details)
    }

    fun show(batch: CompletionBatch, animate: Boolean): Boolean {
        val alreadyShowing = isShowing
        document = batch.request.document; revision = batch.request.revision; offset = batch.request.caret
        items = batch.items; selectedIndex = 0; explicitSelection = false;userInteracted=false
        sourceHeading.text=if(batch.source==null)activity.getString(R.string.completion_lexical)
            else activity.getString(if(batch.currentFileOnly)R.string.completion_semantic_file else if(batch.request.cpp)R.string.completion_cpp_semantic else R.string.completion_semantic,batch.source)
        com.codepocket.ide.ui.IdeControls.describe(sourceHeading,sourceHeading.text.toString())
        adapter.notifyDataSetChanged(); updateDetails()
        if (!reposition()) return false
        popup.exitTransition = if (com.codepocket.ide.animation.MotionEnvironment.enabled(activity, animate))
            android.transition.Fade().apply { duration = 80 } else null
        val continuing = document == recentlyClosedDocument &&
            android.os.SystemClock.uptimeMillis() - recentlyClosedAt in 0..350
        Animations.popup(content, alreadyShowing || continuing, animate)
        return true
    }

    fun move(delta: Int) {
        if (items.isEmpty()) return
        explicitSelection = true;userInteracted=true; selectedIndex = (selectedIndex + delta).coerceIn(0, items.lastIndex)
        adapter.notifyDataSetChanged(); list.setSelection(selectedIndex); updateDetails(); reposition()
    }

    private fun updateDetails() {
        val item = items.getOrNull(selectedIndex) ?: return
        details.text = activity.getString(R.string.completion_detail, item.kind.title, item.detail)
    }

    fun reposition(): Boolean {
        if (activity.isDestroyed || activity.isFinishing) { dismiss(false); return false }
        val anchor = editorSurface.anchor() ?: run { dismiss(false); return false }
        val matches = anchor.matches(document, revision, offset)
        if (!matches) { dismiss(false); return false }
        val visible = anchor.visible
        val width=minOf(dp(380),visible.right-visible.left)
        details.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        val descriptionHeight=details.measuredHeight
        val desired = dp(48)+minOf(items.size,5)*candidateHeight+if(items.size>=2)descriptionHeight else 0
        val placed = CompletionGeometry.place(CompletionBounds(visible.left, visible.top, visible.right, visible.bottom),
            anchor.x, anchor.top, anchor.bottom, dp(380), desired, candidateHeight, dp(3)) ?: run { dismiss(false); return false }
        heading.visibility = if (placed.height >= dp(48)+candidateHeight) View.VISIBLE else View.GONE
        details.visibility = if (items.size>=2 && placed.height >= dp(48)+2*candidateHeight+descriptionHeight) View.VISIBLE else View.GONE
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
            catch (_: WindowManager.BadTokenException) { dismiss(false); return false }
        }
        return true
    }

    fun dismiss(animated: Boolean = true) { if (!animated) popup.exitTransition = null; Animations.cancel(content); popup.dismiss() }
}
