package com.codepocket.ide

import android.os.Looper
import com.codepocket.editorlab.WorkbenchEditor
import org.junit.Assert.fail
import org.robolectric.Shadows
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.TimeUnit

/** Wait for the real asynchronous workspace, TextMate and Sora layout, without replacing any of them. */
internal fun awaitHostEditor(main: MainActivity): WorkbenchEditor {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    var state = "workspace not loaded"
    do {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val editor = ReflectionHelpers.getField<WorkbenchEditor?>(main, "editor")
        val busy = ReflectionHelpers.getField<Boolean>(main, "workspaceBusy")
        if (editor != null) {
            state = "workspaceBusy=$busy enabled=${editor.isEnabled} layout=${editor.isLayoutReady} syntax=${editor.syntaxState()}"
            if (!busy && editor.isEnabled && editor.isLayoutReady &&
                !editor.syntaxState().startsWith("语法加载中") &&
                editor.inputView().width > 0 && editor.inputView().height > 0) return editor
        }
        Thread.sleep(5)
    } while (System.nanoTime() < deadline)
    fail("Real editor did not become ready: $state")
    error("unreachable")
}
