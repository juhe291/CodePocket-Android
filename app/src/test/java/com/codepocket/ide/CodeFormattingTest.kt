package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/** Real host engines, also used by Main/Sora integration tests. Never replaces the production module. */
internal class HostFormatting(private val root: File) : SemanticTransport {
    private var engine: CppLspEngine? = null
    private var style: String? = null
    override fun analyze(payload: String): String {
        val json = JSONObject(payload)
        if (json.getBoolean("python")) {
            val repository = CppFixtures.root
            val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", true)
            val python = System.getProperty("codepocket.hostPython")
                ?: if (windows) File(repository, ".tools/python313-full/tools/python.exe").path else "python3"
            val process = ProcessBuilder(python,
                "-B", "-c", "import sys,code_formatting;print(code_formatting.format_document(sys.stdin.read()))")
                .apply {
                    environment()["PYTHONUTF8"] = "1"
                    environment()["PYTHONPATH"] = listOf(File(repository,".tools/formatting-probe/site"),
                        File(repository,"app/src/main/python")).filter { it.isDirectory }
                        .joinToString(File.pathSeparator) { it.path }
                }
                .redirectError(File(root, "python-stderr.log")).start()
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
            val result = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            check(process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0)
            return result
        }
        val options = FormatOptions(json.getInt("indent"), json.getBoolean("spaces"))
        if (style != options.style()) {
            engine?.close(); File(root, ".clang-format").writeText(options.style()); File(root,"document.cpp").writeText("")
            engine = create(root); style = options.style()
        }
        val code = json.getString("code")
        return CodeFormatting.response(code, false, CodeFormatting.cppEdits(code, json.getInt("start"), json.getInt("end"),
            engine!!.format(code,"document.cpp") { false }))
    }
    override fun close() { engine?.close(); engine = null }
    companion object {
        fun create(root: File, own: (AnalysisProcess) -> Unit = {}): CppLspEngine {
            val suffix = if (System.getProperty("os.name").orEmpty().startsWith("Windows", true)) ".exe" else ""
            val executable = System.getProperty("codepocket.hostClangd")?.let(::File)
                ?: File(CppFixtures.root,".tools/cpp-runtime/host/clangd_21.1.8/bin/clangd$suffix")
            check(executable.isFile)
            val child = ProcessBuilder(executable.path,"--enable-config=false","--background-index=false","--clang-tidy=false",
                "--log=error","-j=1").directory(root).redirectError(File(root,"clangd-stderr.log")).start()
            val process = object : AnalysisProcess {
                override val input = child.inputStream; override val output = child.outputStream; override val errors = child.errorStream
                override fun close() { child.destroy(); if (!child.waitFor(1,TimeUnit.SECONDS)) { child.destroyForcibly(); child.waitFor(1,TimeUnit.SECONDS) } }
            }
            own(process)
            return CppLspEngine(process,root,listOf("-xc++","-std=c++17","-nostdinc","-nostdinc++"),true)
        }
    }
}

class CodeFormattingTest {
    @get:Rule val temp = TemporaryFolder()
    private fun edit(line:Int, from:Int, to:Int, text:String) = JSONObject().put("newText",text).put("range",JSONObject()
        .put("start",JSONObject().put("line",line).put("character",from))
        .put("end",JSONObject().put("line",line).put("character",to)))
    @Test fun cppUtf16CrLfSelectionAndReverseOrderedEditsAreValidated() {
        val source="// 😀中文\r\nint x=1;\r\n"
        val values=JSONArray().put(edit(1,5,6," = ")).put(edit(1,3,4,"  "))
        val result=CodeFormatting.cppEdits(source,source.indexOf('1'),source.indexOf(';'),values)
        assertEquals("// 😀中文\r\nint  x = 1;\r\n",result.text)
        assertEquals(result.text!!.indexOf('1'),result.start);assertEquals(result.text.indexOf(';'),result.end)
        assertEquals(result,CodeFormatting.decode(source,false,CodeFormatting.response(source,false,result)))
    }
    @Test fun malformedOverlappingSurrogateAndOverlargeEditsFailClosed() {
        val source="😀 x"
        for(values in listOf(JSONArray().put(edit(0,1,2," ")),JSONArray().put(edit(1,0,0,"x")),
            JSONArray().put(edit(0,3,4,"a")).put(edit(0,3,4,"b")),
            JSONArray().put(edit(0,0,3,"a")).put(edit(0,2,4,"b")),
            JSONArray().put(edit(0,0,0,"a")).put(edit(0,0,0,"b")),
            JSONArray().put(edit(0,0,0,"\uD800")),JSONArray().put(edit(0,0,0,"x".repeat(Workspace.MAX_CHARS))))) {
            assertThrows(Exception::class.java){CodeFormatting.cppEdits(source,0,0,values)}
        }
    }
    @Test fun insertedCppWhitespaceStaysOutsideTheOriginalSelectedToken() {
        val source="int value=1;"
        val result=CodeFormatting.cppEdits(source,4,9,JSONArray().put(edit(0,9,9," ")).put(edit(0,10,10," ")))
        assertEquals("int value = 1;",result.text);assertEquals("value",result.text!!.substring(result.start,result.end))
    }
    @Test fun responseMustMatchSourceEngineLimitsAndSelection() {
        val raw=CodeFormatting.response("x",true,FormatResult("x\n",0,0))
        assertTrue(CodeFormatting.decode("x",true,raw).available)
        assertFalse(CodeFormatting.decode("y",true,raw).available)
        assertFalse(CodeFormatting.decode("x",false,raw).available)
        assertFalse(CodeFormatting.decode("x",true,null).available)
        assertFalse(CodeFormatting.decode("x",true,JSONObject(raw).put("start",3).toString()).available)
        assertFalse(CodeFormatting.decode("x",true,JSONObject(raw).put("start",0.5).toString()).available)
        assertFalse(CodeFormatting.decode("x",true," ".repeat(CodeFormatting.MAX_JSON+1)).available)
        assertThrows(Exception::class.java){CodeFormatting.payload(EditorSnapshot("k",1,"\uD800",0,0),true,FormatOptions())}
        assertFalse(AppSettings.fromJson("{\"version\":10}").formatOnSave)
        val enabled=AppSettings(formatOnSave=true);assertEquals(enabled,AppSettings.fromJson(enabled.toJson()))
        assertTrue(SettingsDialog.Draft.restore(AppSettings(),SettingsDialog.Draft(enabled).checkpoint()).value.formatOnSave)
    }
    @Test fun actualCppFormatterUsesPrivateStylePreservesIncludeOrderAndDoesNotWriteDraft() {
        val root=temp.newFolder().canonicalFile
        HostFormatting(root).use { transport ->
            val client=CodeFormattingClient(transport)
            val source="#include \"z.h\"\n#include \"a.h\"\nint main(){\nif(true){\nint x=1+2;\n}\n}\n"
            for(width in listOf(2,4,8)) for(spaces in listOf(true,false)) {
                val options=FormatOptions(width,spaces);val result=client.format(EditorSnapshot("k",1,source,source.indexOf("x="),source.indexOf("x=")+1),false,options)
                assertTrue(result.error,result.available);assertTrue(result.text!!.startsWith("#include \"z.h\"\n#include \"a.h\""))
                assertTrue(result.text.contains((if(spaces) " ".repeat(width*2) else "\t\t")+"int x = 1 + 2;"))
                assertEquals('x',result.text[result.start]);assertEquals("",File(root,"document.cpp").readText())
                assertEquals(result.text,client.format(EditorSnapshot("k",2,result.text,result.start,result.end),false,options).text)
            }
            val large="// untouched\n".repeat(4000)+"int main(){return 0;}\n"
            assertTrue(large.length>45000)
            assertTrue(client.format(EditorSnapshot("k",3,large,0,0),false,FormatOptions()).available)
        }
    }
    @Test fun actualPythonClientUsesUnsavedSourceAndMapsUnicodeSelection() {
        HostFormatting(temp.newFolder().canonicalFile).use { transport ->
            val source="# 😀中文\r\ndef f():\r\n    value=1+2\r\n    return value\r\n"
            val result=CodeFormattingClient(transport).format(EditorSnapshot("k",1,source,source.indexOf("value"),source.indexOf("value")+5),true,FormatOptions(8,false))
            assertTrue(result.error,result.available);assertTrue(result.text!!.contains("\r\n\tvalue = 1 + 2"))
            assertEquals("value",result.text.substring(result.start,result.end))
        }
    }
    @Test fun largerFormattingFramesDoNotRelaxTheOriginalSemanticFrameLimit() {
        val data=JSONObject().put("result","x".repeat(2_100_000)).toString().toByteArray()
        val bytes="Content-Length: ${data.size}\r\n\r\n".toByteArray()+data
        assertThrows(Exception::class.java){CppLspConnection.readFrame(ByteArrayInputStream(bytes))}
        assertEquals(2_100_000,CppLspConnection.readFrame(ByteArrayInputStream(bytes),8_000_000).getString("result").length)
        assertThrows(Exception::class.java){CppLspConnection.readFrame(ByteArrayInputStream("Content-Length: 8000001\r\n\r\n".toByteArray()),8_000_000)}
    }
}
