package com.codepocket.ide

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit

class CppLspEngineTest {
    @get:Rule val temp = TemporaryFolder()
    private class HostProcess(executable: File, root: File) : AnalysisProcess {
        private val process = ProcessBuilder(executable.path, "--enable-config=false", "--background-index=false", "--clang-tidy=false",
            "--header-insertion=never", "--function-arg-placeholders=false", "--completion-style=detailed", "--completion-parse=always",
            "--all-scopes-completion=false", "--use-dirty-headers", "-j=2", "--limit-results=30", "--pch-storage=memory", "--log=verbose").directory(root)
            .redirectError(File(CppFixtures.root, "verification/cpp-semantic-integration-2026-10-27/production-engine-stderr.log")).start()
        override val input = process.inputStream
        override val output = process.outputStream
        override val errors = process.errorStream
        override fun close() { process.destroy(); if (!process.waitFor(1, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(1, TimeUnit.SECONDS) } }
    }
    @Test fun productionEngineCommunicatesWithRealPinnedHostClangdAndAndroidHeaders() {
        val cache = File(CppFixtures.root, ".tools/cpp-runtime")
        val suffix = if (System.getProperty("os.name").orEmpty().startsWith("Windows", true)) ".exe" else ""
        val executable = System.getProperty("codepocket.hostClangd")?.let(::File)
            ?: File(cache, "host/clangd_21.1.8/bin/clangd$suffix")
        assertTrue("Prepare the pinned host clangd before this test", executable.isFile)
        val sysroot = System.getProperty("codepocket.cppSysroot")?.let(::File) ?: File(cache, "headers/sysroot")
        val resource = System.getProperty("codepocket.cppResource")?.let(::File) ?: File(cache, "headers/clang")
        assertTrue(File(sysroot, "include/c++/v1/vector").isFile)
        val root = temp.newFolder().canonicalFile; File(root, "main.cpp").writeText("")
        val flags = listOf("-xc++", "-std=c++17", "--target=aarch64-linux-android24", "--sysroot=${sysroot.path}",
            "-resource-dir=${resource.path}", "-isystem", File(sysroot, "include/c++/v1").path,
            "-isystem", File(sysroot, "include").path, "-isystem", File(sysroot, "include/aarch64-linux-android").path, "-I${root.path}")
        var engine = CppLspEngine(HostProcess(executable, root), root, flags)
        try {
            for (tag in listOf("dot", "arrow", "namespace", "vector", "unicode_crlf", "middle", "signature", "overload", "overload_arity", "prefix", "unknown", "scope", "unsaved_header", "updated_header", "closed_header")) {
                val fixture = CppFixtures.case(tag)
                if (tag in setOf("unsaved_header", "updated_header", "closed_header")) {
                    engine.close()
                    File(root, "model.hpp").writeText(if (fixture.isNull("unsaved_header")) "struct Token { void old_disk(); };\n" else fixture.getString("unsaved_header"))
                    engine = CppLspEngine(HostProcess(executable, root), root, flags)
                }
                val request = CppFixtures.request(tag)
                val raw = engine.analyze(request.code, request.caret, "main.cpp", fixture.getString("mode")) { false }
                val result = CppSemanticProtocol.decode(request, raw, fixture.getString("mode"))
                assertTrue("Actual production engine failed for $tag: $raw", result.available)
                val expected = CppFixtures.result(tag)
                assertEquals("$tag: $raw", expected.completion?.items?.map { it.label }, result.completion?.items?.map { it.label })
                assertEquals(tag, expected.signature?.reference?.forms?.map { it.parts }, result.signature?.reference?.forms?.map { it.parts })
                assertEquals("The active editor buffer must not be written to disk", "", File(root, "main.cpp").readText())
            }
        } finally { engine.close() }
    }
    @Test fun frameLengthIsUtf8BytesAndMalformedFramesFailClosed() {
        val data = JSONObject().put("jsonrpc", "2.0").put("id", 1).put("result", "😀中文").toString().toByteArray(Charsets.UTF_8)
        val frame = "Content-Length: ${data.size}\r\n\r\n".toByteArray(Charsets.US_ASCII) + data
        assertEquals("😀中文", CppLspConnection.readFrame(ByteArrayInputStream(frame)).getString("result"))
        for (header in listOf("Content-Length: -1\r\n\r\n", "Content-Length: 2000001\r\n\r\n", "Content-Length: 1\r\nContent-Length: 1\r\n\r\n", "NoLength: 2\r\n\r\n"))
            assertThrows(Exception::class.java) { CppLspConnection.readFrame(ByteArrayInputStream(header.toByteArray())) }
        assertThrows(Exception::class.java) { CppLspConnection.readFrame(ByteArrayInputStream("Content-Length: 4\r\n\r\n{}".toByteArray())) }
    }
}
