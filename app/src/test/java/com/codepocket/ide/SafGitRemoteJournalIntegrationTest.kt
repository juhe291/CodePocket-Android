package com.codepocket.ide

import android.provider.DocumentsContract.Document
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.UUID

/** Actual ContentResolver/PFD publication, still host evidence rather than Android device acceptance. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class SafGitRemoteJournalIntegrationTest {
    private fun cases():JSONArray {
        val repo=generateSequence(File(checkNotNull(System.getProperty("user.dir")))){it.parentFile}
            .first{File(it,"verification/git-remote-workspace-2026-11-02/native-engine-fixtures.json").isFile}
        return JSONObject(File(repo,"verification/git-remote-workspace-2026-11-02/native-engine-fixtures.json").readText()).getJSONArray("cases")
    }
    private fun replace(from:File,to:File){from.copyTo(to,true);check(from.delete())}
    private fun decode(value:String)=java.util.Base64.getDecoder().decode(value)
    private fun appFolder(name:String)=File(RuntimeEnvironment.getApplication().filesDir,"$name-${UUID.randomUUID()}").apply{check(mkdirs())}
    private fun parentId(provider:SafGitBinaryIntegrationTest.Provider,path:String):String =
        if(path.isEmpty())SafGitBinaryIntegrationTest.Provider.ROOT else provider.nodes.values.single{it.file.relativeTo(provider.disk).invariantSeparatorsPath==path}.id
    private fun populate(provider:SafGitBinaryIntegrationTest.Provider,tree:JSONObject) {
        val dirs=tree.getJSONArray("directories")
        (0 until dirs.length()).map{dirs.getString(it)}.filter{it.isNotEmpty()}.sortedBy{it.count{c->c=='/'}}.forEach{path->
            provider.createDocument(parentId(provider,path.substringBeforeLast('/',"")),Document.MIME_TYPE_DIR,path.substringAfterLast('/'))
        }
        val files=tree.getJSONObject("files");files.keys().forEach{path->
            provider.put(path.substringAfterLast('/'),decode(files.getString(path)),parentId(provider,path.substringBeforeLast('/',"")))
        }
    }
    private fun diskFiles(root:File)=root.walkTopDown().filter{it.isFile}.associate{it.relativeTo(root).invariantSeparatorsPath to it.readBytes().toList()}
    private fun expectedFiles(tree:JSONObject)=tree.getJSONObject("files").let{files->files.keys().asSequence().associateWith{decode(files.getString(it)).toList()}}
    private fun prepare(store:GitWorkspaceStore,case:JSONObject):Pair<GitWorkspaceSnapshot,GitRemotePlan> {
        val copy=store.capture(File(RuntimeEnvironment.getApplication().cacheDir,"remote-job-${UUID.randomUUID()}"),true)
        val owned=copy.root.canonicalPath+File.separator
        copy.root.walkBottomUp().filter{it!=copy.root}.forEach{check(it.canonicalPath.startsWith(owned));check(it.delete())}
        val after=case.getJSONObject("after");val dirs=after.getJSONArray("directories")
        (0 until dirs.length()).map{dirs.getString(it)}.filter{it.isNotEmpty()}.sortedBy{it.count{c->c=='/'}}
            .forEach{path->File(copy.root,path).let{check(it.isDirectory || it.mkdirs())}}
        val files=after.getJSONObject("files");files.keys().forEach{path->File(copy.root,path).writeBytes(decode(files.getString(path)))}
        File(copy.root.parentFile,"result.plan").writeText(case.getJSONObject("plan").put("root",copy.root.absolutePath).toString())
        val request=case.getJSONObject("request");val response=case.getJSONObject("response")
        return copy to GitRemotePlan.read(copy,request.getString("action"),request.getString("expected"),response.getString("snapshot"),request.getString("url"),request.getString("branch"))
    }
    @Test fun actualEngineResultsPublishThroughRealSafBinaryStreamsForAllFourOperations() {
        val cases=cases()
        for(i in 0 until cases.length()) {
            val case=cases.getJSONObject(i);val (provider,gateway)=SafGitBinaryIntegrationTest().provider();populate(provider,case.getJSONObject("before"))
            val store=GitWorkspaceStore(gateway,false,::replace);val (copy,plan)=prepare(store,case);val journal=appFolder("remote-recovery")
            store.publishRemote(copy,plan,journal)
            val expected=case.getJSONObject("after");assertEquals(case.getString("name"),expectedFiles(expected),diskFiles(provider.disk))
            val directories=expected.getJSONArray("directories")
            assertEquals((0 until directories.length()).map{directories.getString(it)}.toSet(),
                setOf("")+provider.disk.walkTopDown().drop(1).filter{it.isDirectory}.map{it.relativeTo(provider.disk).invariantSeparatorsPath}.toSet())
            assertTrue(provider.writeCalls>0);assertTrue(provider.nodes.keys.all{it.startsWith("opaque:")})
            GitWorkspaceStore(gateway,false,::replace).recover(journal);assertFalse(File(journal,"pending.json").exists())
        }
    }
    @Test fun realSafPartialSourceWriteRetainsItsCurrentBytesAndExportsBothVersions() {
        val case=cases().getJSONObject(2);assertEquals("fast_forward",case.getString("name"))
        val (provider,gateway)=SafGitBinaryIntegrationTest().provider();populate(provider,case.getJSONObject("before"))
        val store=GitWorkspaceStore(gateway,false,::replace);val (copy,plan)=prepare(store,case);val journal=appFolder("partial-recovery")
        provider.beforeOpenWrite={node->if(node.file.name=="main.py"){node.file.writeBytes(byteArrayOf(99));throw IOException("provider partially truncated before opening stream")}}
        assertThrows(GitRecoveryRequired::class.java){store.publishRemote(copy,plan,journal)}
        provider.beforeOpenWrite=null
        assertArrayEquals(byteArrayOf(99),File(provider.disk,"main.py").readBytes())
        assertThrows(GitRecoveryRequired::class.java){GitWorkspaceStore(gateway,false,::replace).recover(journal)}
        val out=java.io.ByteArrayOutputStream();store.exportRecovery(journal,out);val exported=linkedMapOf<String,ByteArray>()
        java.util.zip.ZipInputStream(out.toByteArray().inputStream()).use{zip->while(true){val entry=zip.nextEntry?:break;exported[entry.name]=zip.readBytes()}}
        assertArrayEquals(decode(case.getJSONObject("before").getJSONObject("files").getString("main.py")),exported["before/main.py"])
        assertArrayEquals(decode(case.getJSONObject("after").getJSONObject("files").getString("main.py")),exported["after/main.py"])
        assertArrayEquals(byteArrayOf(99),exported["current/main.py"]);assertTrue(File(journal,"pending.json").exists())
    }
    @Test fun providerPermissionLossRejectsFastForwardBeforeAnyOriginalMutation() {
        val case=cases().getJSONObject(2);val (provider,gateway)=SafGitBinaryIntegrationTest().provider();populate(provider,case.getJSONObject("before"))
        val store=GitWorkspaceStore(gateway,false,::replace);val (copy,plan)=prepare(store,case);val journal=appFolder("readonly-recovery")
        val before=diskFiles(provider.disk);provider.writable=false
        assertThrows(IllegalArgumentException::class.java){store.publishRemote(copy,plan,journal)}
        assertEquals(before,diskFiles(provider.disk));assertEquals(0,provider.writeCalls);assertFalse(File(journal,"pending.json").exists())
    }
}
