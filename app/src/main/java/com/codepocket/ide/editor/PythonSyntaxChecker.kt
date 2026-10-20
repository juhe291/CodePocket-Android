package com.codepocket.ide.editor

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import org.json.JSONObject

/** Only called on the analysis worker; the IDE process compiles but never runs user code. */
class PythonSyntaxChecker(context: Context) {
    private val application = context.applicationContext
    private var unavailable = false
    fun check(code: String): SyntaxReport {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        if (unavailable) return SyntaxReport(status = "Python 语法检查暂不可用")
        return try {
            synchronized(startLock) { if (!Python.isStarted()) Python.start(AndroidPlatform(application)) }
            val result = JSONObject(Python.getInstance().getModule("diagnostics").callAttr("check_syntax", code).toString())
            val values = result.getJSONArray("issues")
            val issues = (0 until minOf(values.length(), 40)).map { index ->
                val item = values.getJSONObject(index)
                InlineIssue(item.getInt("start"), item.getInt("end"), item.getString("message"), item.getString("source"))
            }.filter { it.start >= 0 && it.end >= it.start && it.end <= code.length }
            SyntaxReport(issues, result.optString("status"))
        } catch (_: Exception) {
            unavailable = true; SyntaxReport(status = "Python 语法检查暂不可用")
        } catch (_: LinkageError) {
            unavailable = true; SyntaxReport(status = "Python 语法检查暂不可用")
        }
    }
    companion object { private val startLock = Any() }
}
