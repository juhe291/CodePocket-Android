package com.codepocket.ide

import android.content.pm.ProviderInfo
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.UUID

/** Real SafGateway + ContentResolver/PFD bytes. Host Android framework evidence, not device certification. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34])
class SafGitBinaryIntegrationTest {
    /** Robolectric's legacy query dispatch skips ContentResolver's Binder
     * conversion. Forward only that dispatch to the actual DocumentsProvider;
     * its tree checks, CRUD and PFD streams still run unchanged. */
    internal class ResolverBridge(private val actual:DocumentsProvider):ContentProvider() {
        override fun onCreate()=true
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,args:Array<out String>?,sortOrder:String?):Cursor {
            require(selection==null && args==null && sortOrder==null)
            return actual.query(uri,projection,Bundle(),null)!!
        }
        override fun getType(uri:Uri)=actual.getType(uri)
        override fun openFile(uri:Uri,mode:String)=actual.openFile(uri,mode)
        override fun call(method:String,arg:String?,extras:Bundle?)=actual.call(method,arg,extras)
        override fun insert(uri:Uri,values:ContentValues?):Uri=error("not used")
        override fun delete(uri:Uri,where:String?,args:Array<out String>?)=error("not used")
        override fun update(uri:Uri,values:ContentValues?,where:String?,args:Array<out String>?)=error("not used")
    }
    internal class Provider(val disk:File):DocumentsProvider() {
        data class Node(val id:String,val parent:String?,val name:String,val mime:String,val file:File)
        companion object {const val AUTHORITY="com.codepocket.ide.test.gitbinary";const val ROOT="opaque:root"}
        val nodes=linkedMapOf(ROOT to Node(ROOT,null,"项目",Document.MIME_TYPE_DIR,disk))
        var writable=true;var writeCalls=0;var lastMime:String?=null;private var counter=0
        val tree:Uri get()=DocumentsContract.buildTreeDocumentUri(AUTHORITY,ROOT)
        override fun onCreate()=true
        override fun queryRoots(projection:Array<out String>?):Cursor=MatrixCursor(projection?:arrayOf("root_id"))
        private fun cursor(projection:Array<out String>?,items:List<Node>):Cursor {
            val columns=projection?:arrayOf(Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_FLAGS)
            return MatrixCursor(columns).apply{items.forEach{node->addRow(columns.map{column->when(column){
                Document.COLUMN_DOCUMENT_ID->node.id
                Document.COLUMN_DISPLAY_NAME->node.name
                Document.COLUMN_MIME_TYPE->node.mime
                Document.COLUMN_FLAGS->if(!writable)0 else if(node.mime==Document.MIME_TYPE_DIR)
                    Document.FLAG_DIR_SUPPORTS_CREATE or Document.FLAG_SUPPORTS_DELETE else Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_DELETE
                else->null
            }}.toTypedArray())}}
        }
        override fun queryDocument(documentId:String,projection:Array<out String>?)=cursor(projection,listOf(nodes.getValue(documentId)))
        override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?)=
            cursor(projection,nodes.values.filter{it.parent==parentDocumentId})
        override fun isChildDocument(parentDocumentId:String,documentId:String):Boolean {
            var current:String?=documentId;val visited=mutableSetOf<String>()
            while(current!=null && visited.add(current)){if(current==parentDocumentId)return true;current=nodes[current]?.parent}
            return false
        }
        override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor {
            if('w' in mode){check(writable);writeCalls++}
            return ParcelFileDescriptor.open(nodes.getValue(documentId).file,ParcelFileDescriptor.parseMode(mode))
        }
        override fun createDocument(parentDocumentId:String,mimeType:String,displayName:String):String {
            check(writable);Workspace.validateName(displayName);val parent=nodes.getValue(parentDocumentId)
            val file=File(parent.file,displayName);check(!file.exists());check(if(mimeType==Document.MIME_TYPE_DIR)file.mkdir() else file.createNewFile())
            val id="opaque:node:${++counter}";nodes[id]=Node(id,parentDocumentId,displayName,mimeType,file);lastMime=mimeType;return id
        }
        override fun deleteDocument(documentId:String){val node=nodes.getValue(documentId);check(node.parent!=null && node.file.delete());nodes.remove(documentId)}
        fun put(name:String,data:ByteArray,parent:String=ROOT):String=createDocument(parent,"application/octet-stream",name).also{nodes.getValue(it).file.writeBytes(data)}
    }
    internal fun provider():Pair<Provider,SafGateway> {
        val app=RuntimeEnvironment.getApplication();val disk=File(app.filesDir,"git-provider-${UUID.randomUUID()}").apply{check(mkdirs())}
        val provider=Provider(disk)
        provider.attachInfo(app,ProviderInfo().apply{authority=Provider.AUTHORITY;exported=true;grantUriPermissions=true
            readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"})
        val bridge=ResolverBridge(provider).apply{attachInfo(app,ProviderInfo().apply{authority=Provider.AUTHORITY})}
        ShadowContentResolver.registerProviderInternal(Provider.AUTHORITY,bridge)
        return provider to SafGateway(app.contentResolver,provider.tree)
    }
    @Test fun realOpaqueDocumentStreamsKeepBinaryBytesAndTheOriginalOneMegabyteRunLimit(){
        val (provider,gateway)=provider();val bytes=ByteArray(1_200_000){(it%256).toByte()};val id=provider.put("object.bin",bytes)
        assertArrayEquals(bytes,gateway.readBinary(id,GitWorkspaceStore.MAX_FILE))
        assertThrows(IllegalArgumentException::class.java){gateway.readBytes(id)}
        val created=gateway.createBinary(Provider.ROOT,"index");assertEquals("application/octet-stream",provider.lastMime)
        val index=byteArrayOf(0,-1,-2,13,10,42);gateway.writeBytes(created.id,index)
        assertArrayEquals(index,gateway.readBinary(created.id,100));assertArrayEquals(index,provider.nodes.getValue(created.id).file.readBytes())
        assertEquals(provider.tree.toString(),gateway.identity)
    }
    @Test fun readonlyBinarySaveIsRejectedBeforeOpeningOrTruncatingTheOriginal(){
        val (provider,gateway)=provider();val original=byteArrayOf(0,1,-1,2);val id=provider.put("index",original)
        provider.writable=false
        assertThrows(IllegalArgumentException::class.java){gateway.writeBytes(id,byteArrayOf(9))}
        assertEquals(0,provider.writeCalls);assertArrayEquals(original,provider.nodes.getValue(id).file.readBytes())
    }
    @Test fun snapshotReadsLiveProviderBinaryMetadataAndDetectsARealSourceChange(){
        val (provider,gateway)=provider();val source=provider.put("main.py","old=1\n".toByteArray())
        val git=provider.createDocument(Provider.ROOT,Document.MIME_TYPE_DIR,".git")
        val id=provider.put("index",byteArrayOf(0,-1,5),git)
        val app=RuntimeEnvironment.getApplication();val destination=File(app.cacheDir,"git-snapshot-${UUID.randomUUID()}")
        val store=GitWorkspaceStore(gateway);val snapshot=store.capture(destination)
        assertArrayEquals(provider.nodes.getValue(id).file.readBytes(),File(snapshot.root,".git/index").readBytes())
        provider.nodes.getValue(source).file.writeText("new=2\n")
        assertThrows(IllegalArgumentException::class.java){store.assertFresh(snapshot)}
        assertEquals("old=1\n",File(snapshot.root,"main.py").readText());assertEquals(0,provider.writeCalls)
    }
}
