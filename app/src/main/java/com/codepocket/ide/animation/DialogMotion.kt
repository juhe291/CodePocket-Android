package com.codepocket.ide.animation

import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.view.View
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
        return dialog
    }
    fun menu(context: Context, anchor: View, enabled: Boolean): PopupMenu =
        com.codepocket.ide.ui.WorkbenchMenu(context, anchor, enabled)
}
