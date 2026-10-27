package com.codepocket.ide

import android.content.Context
import org.json.JSONObject
import java.io.Closeable

class CppSemanticClient(private val transport: SemanticTransport) : Closeable {
    constructor(context: Context) : this(AndroidSemanticTransport(context, CppAnalysisService::class.java))
    fun analyze(request: CompletionRequest, location: SemanticLocation, mode: String): SemanticAnalysis {
        if (!request.cpp || request.code.length > 45000 || request.caret != request.end ||
            !CppSemanticProtocol.boundary(request.code, request.caret) || mode !in setOf("complete", "signature")) return SemanticAnalysis(false, error = "unsupported")
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        return try {
            val payload = JSONObject().put("code", request.code).put("caret", request.caret).put("root", location.root)
                .put("path", location.path).put("mode", mode).toString()
            CppSemanticProtocol.decode(request, transport.analyze(payload), mode)
        } catch (error: InterruptedException) { throw error }
        catch (_: Exception) { SemanticAnalysis(false, error = "cpp_unavailable") }
    }
    override fun close() = transport.close()
}
