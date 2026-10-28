package com.codepocket.ide

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import org.json.JSONObject

/** Real pinned APK assets and trusted app directory aliases; no Android ELF execution claim. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class CppRuntimePreparationTest {
    @get:Rule val temp=TemporaryFolder()
    private fun context(files:File):Context = object:ContextWrapper(RuntimeEnvironment.getApplication()){
        override fun getFilesDir()=files
        override fun getApplicationInfo()=ApplicationInfo(baseContext.applicationInfo).apply{
            nativeLibraryDir=File(CppFixtures.root,".tools/cpp-runtime/jni/arm64-v8a").canonicalPath
        }
    }
    @Test fun trustedFilesDirectoryAliasesDoNotPreventPreparingThePinnedCompilerInputs(){
        val actual=temp.newFolder("files").canonicalFile;File(actual,"alias").mkdir()
        val alias=File(actual,"alias/..")
        assertNotEquals(alias.absoluteFile,alias.canonicalFile)
        val paths=CppRuntime(context(alias)).toolchain()
        assertEquals("libcodepocket_clang.so",paths.compiler.name)
        assertTrue(File(actual,"cpp-runtime").isDirectory)
        assertTrue(File(paths.headers,"sysroot/include/c++/v1/vector").isFile)
    }
    @Test fun anExistingPrivateFileIsNeverOverwrittenByRuntimePreparation(){
        val actual=temp.newFolder().canonicalFile;val sentinel=File(actual,"cpp-runtime").apply{writeText("keep")}
        assertThrows(IllegalArgumentException::class.java){CppRuntime(context(actual)).toolchain()}
        assertEquals("keep",sentinel.readText())
    }
    @Test fun trustedAliasCanReuseCompletedPinnedInputsWithoutChangingTheirBytes(){
        val actual=temp.newFolder("cached-files").canonicalFile;File(actual,"alias").mkdir()
        val app=context(File(actual,"alias/.."))
        val manifest=JSONObject(app.assets.open("cpp-runtime/manifest.json").bufferedReader().use{it.readText()})
        // Completed cache fixture isolates the trusted-root boundary from host ZIP filesystem rules.
        val cache=File(actual,"cpp-runtime").apply{mkdir()}
        for(key in listOf("headers","build_support")){
            val hash=manifest.getJSONObject(key).getString("sha256")
            File(cache,hash).apply{mkdir();File(this,".ready").writeText(hash)}
        }
        val headers=File(cache,manifest.getJSONObject("headers").getString("sha256"))
        val vector=File(headers,"sysroot/include/c++/v1/vector").apply{parentFile!!.mkdirs();writeText("cached vector")}
        val paths=CppRuntime(app).toolchain()
        assertEquals("libcodepocket_clang.so",paths.compiler.name)
        assertEquals(headers,paths.headers);assertEquals("cached vector",vector.readText())
        assertEquals(2,cache.listFiles()!!.size)
    }
}
