package com.codepocket.ide.animation

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import com.codepocket.ide.R

object DialogMotion {
    fun show(dialog: AlertDialog, enabled: Boolean, drawer: Boolean = false): AlertDialog {
        // Install the native decor before selecting the animation, so its theme cannot replace the App-off state.
        dialog.create()
        val style = if (MotionEnvironment.enabled(dialog.context, enabled)) {
            if (drawer) R.style.WorkbenchDrawerWindowMotion else R.style.WorkbenchWindowMotion
        } else 0
        dialog.window?.setWindowAnimations(style)
        dialog.show()
        // AlertDialog installs its decor/theme during show; reapply after that installation as well.
        dialog.window?.setWindowAnimations(style)
        com.codepocket.ide.ui.WorkbenchStyle.dialog(dialog)
        if(drawer && style!=0)slideDrawerContent(dialog,enabled)
        return dialog
    }
    /** The Window stays at the activity-bar edge; only its clipped custom content moves. */
    private fun slideDrawerContent(dialog:AlertDialog,enabled:Boolean) {
        val frame=dialog.findViewById<ViewGroup>(android.R.id.custom)?:return
        val content=frame.getChildAt(0)?:return
        frame.clipChildren=true;frame.clipToPadding=true
        val layout=object:View.OnLayoutChangeListener {
            override fun onLayoutChange(view:View,left:Int,top:Int,right:Int,bottom:Int,oldLeft:Int,oldTop:Int,oldRight:Int,oldBottom:Int) {
                if(content.width<=0 || content.height<=0)return
                content.removeOnLayoutChangeListener(this)
                if(dialog.isShowing)Animations.slideIn(content,-content.width.toFloat(),duration=200,
                    enabled=MotionEnvironment.enabled(dialog.context,enabled))
            }
        }
        val attachment=object:View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view:View){}
            override fun onViewDetachedFromWindow(view:View){
                content.removeOnLayoutChangeListener(layout);content.removeOnAttachStateChangeListener(this)
                Animations.cancelTree(content)
            }
        }
        content.addOnAttachStateChangeListener(attachment)
        content.addOnLayoutChangeListener(layout)
        if(content.isLaidOut)layout.onLayoutChange(content,content.left,content.top,content.right,content.bottom,0,0,0,0)
    }
    fun menu(context: Context, anchor: View, enabled: Boolean): PopupMenu =
        com.codepocket.ide.ui.WorkbenchMenu(context, anchor, enabled)
}
