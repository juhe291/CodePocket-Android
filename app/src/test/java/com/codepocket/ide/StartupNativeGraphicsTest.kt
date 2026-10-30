package com.codepocket.ide

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.codepocket.ide.ui.IdeIcon
import com.codepocket.ide.ui.IdeIconDrawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.TimeUnit

/** Native path parsing is essential: legacy graphics accepts malformed VectorDrawable paths. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class StartupNativeGraphicsTest {
    private fun draw(icon: IdeIcon) {
        val drawable = IdeIconDrawable(RuntimeEnvironment.getApplication(), icon, 0xffabcdef.toInt(), 48)
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        try {
            drawable.setBounds(0, 0, 48, 48)
            drawable.draw(Canvas(bitmap))
            assertTrue("Icon $icon must contain visible pixels", (0 until 48).any { y ->
                (0 until 48).any { x -> bitmap.getPixel(x, y) ushr 24 != 0 }
            })
        } finally { bitmap.recycle() }
    }

    @Test fun terminalIconInflatesAndDrawsWithAndroidNativePathParser() { draw(IdeIcon.TERMINAL) }
    @Test fun runIconInflatesAndDrawsWithAndroidNativePathParser() { draw(IdeIcon.RUN) }
    @Test fun allIconsInflateAndDrawWithAndroidNativePathParser() {
        IdeIcon.entries.forEach { icon ->
            try { draw(icon) } catch (failure: Throwable) { throw AssertionError("Native icon $icon", failure) }
        }
    }

    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    private fun launch(theme: UiTheme) {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("codepocket_v2", 0).edit().clear()
            .putString("settings", AppSettings(theme = theme, animations = false).toJson()).commit()
        Workspace(RuntimeEnvironment.getApplication()) // Explicit existing-project fixture; app startup no longer seeds files.
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val main = controller.get()
        try {
            controller.setup().visible()
            // Exercise onCreate and its actual asynchronous workspace preparation, not a partial fixture.
            WorkspaceIo.executor.submit {}.get(10, TimeUnit.SECONDS)
            shadowOf(Looper.getMainLooper()).idle()
            val editor: com.codepocket.editorlab.WorkbenchEditor = ReflectionHelpers.getField(main, "editor")
            assertTrue(editor.text.toString().contains("#include"))
            val controls = descendants(main.window.decorView)
            assertTrue(controls.any { it.contentDescription == "打开终端" })
            assertFalse(controls.filterIsInstance<TextView>().any { it.text.toString().contains("正在载入 CodePocket") })
            val root = main.findViewById<View>(android.R.id.content)
            val metrics = main.resources.displayMetrics
            root.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(root.measuredWidth > 0)
        } finally {
            main.finish()
            controller.pause().stop().destroy()
            WorkspaceIo.executor.submit {}.get(10, TimeUnit.SECONDS)
        }
    }

    @Test fun phoneDarkCompletesActualStartup() { launch(UiTheme.DARK) }
    @Test fun phoneLightCompletesActualStartup() { launch(UiTheme.LIGHT) }
    @Test @Config(qualifiers = "w1000dp-h800dp") fun tabletDarkCompletesActualStartup() { launch(UiTheme.DARK) }
    @Test @Config(qualifiers = "w1000dp-h800dp") fun tabletLightCompletesActualStartup() { launch(UiTheme.LIGHT) }
}
