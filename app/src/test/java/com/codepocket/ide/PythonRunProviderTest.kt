package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/** The run reads live provider entries without rescanning or changing the editor/save mirror. */
class PythonRunProviderTest {
    @get:Rule val temp=TemporaryFolder()
    private class Provider:DocumentGateway {
        override val root=DocumentNode("root","project",true)
        val nodes=linkedMapOf(root.id to root);val parents=mutableMapOf<String,String>();val bytes=mutableMapOf<String,ByteArray>()
        var fail:String?=null;var writes=0
        override fun children(id:String)=nodes.values.filter{parents[it.id]==id}
        override fun read(id:String)=readBytes(id).toString(Charsets.UTF_8)
        override fun readBytes(id:String):ByteArray {if(id==fail)throw IOException("provider disconnected");return bytes.getValue(id)}
        override fun write(id:String,text:String){writes++;bytes[id]=text.toByteArray()}
        override fun create(parent:String,name:String,directory:Boolean):DocumentNode=DocumentNode("id-${nodes.size}",name,directory).also{nodes[it.id]=it;parents[it.id]=parent;if(!directory)bytes[it.id]=byteArrayOf()}
        override fun rename(id:String,name:String):DocumentNode=error("not used")
        override fun move(id:String,oldParent:String,newParent:String):DocumentNode=error("not used")
        override fun delete(id:String){nodes.remove(id);parents.remove(id);bytes.remove(id)}
        fun file(name:String,value:ByteArray)=create(root.id,name,false).also{bytes[it.id]=value}
    }
    @Test fun newModulesAndBinaryResourcesAppearWithoutReplacingTheEditorMirror(){
        val p=Provider();p.file("main.py","import helper\n".toByteArray());val mirror=temp.newFolder("mirror")
        val tree=DocumentTree(p,temp.newFolder("recovery"));tree.scan(mirror)
        p.file("helper.py","answer=42\n".toByteArray());p.file("data.bin",byteArrayOf(0,1,-1))
        val target=File(temp.root,"job");tree.copyForRun(target)
        assertEquals("answer=42\n",File(target,"helper.py").readText());assertArrayEquals(byteArrayOf(0,1,-1),File(target,"data.bin").readBytes())
        assertFalse(File(mirror,"helper.py").exists());assertEquals(listOf("main.py"),tree.children(""));assertEquals(0,p.writes)
    }
    @Test fun deletedProviderFilesAreNotReadFromTheStaleMirror(){
        val p=Provider();p.file("main.py","print(1)\n".toByteArray());val old=p.file("old.py","old=1\n".toByteArray())
        val mirror=temp.newFolder();val tree=DocumentTree(p,temp.newFolder());tree.scan(mirror);p.delete(old.id)
        val target=File(temp.root,"job");tree.copyForRun(target)
        assertFalse(File(target,"old.py").exists());assertEquals("old=1\n",File(mirror,"old.py").readText());assertEquals(0,p.writes)
    }
    @Test fun aProviderCycleAddedAfterOpeningIsRejectedAndOnlyTheOwnedJobIsRemoved(){
        val p=Provider();val tree=DocumentTree(p,temp.newFolder());tree.scan(temp.newFolder())
        p.parents[p.root.id]=p.root.id
        val sentinel=temp.newFile("unrelated.txt").apply{writeText("keep")};val target=File(temp.root,"job")
        assertThrows(IllegalArgumentException::class.java){tree.copyForRun(target)}
        assertFalse(target.exists());assertEquals("keep",sentinel.readText());assertEquals(0,p.writes)
    }
    @Test fun providerFailureRemovesThePartialJobWithoutTouchingTheMirror(){
        val p=Provider();p.file("a.py","value=1\n".toByteArray());val bad=p.file("z.bin",byteArrayOf(1,2,3))
        val mirror=temp.newFolder();val tree=DocumentTree(p,temp.newFolder());tree.scan(mirror);p.fail=bad.id
        val target=File(temp.root,"job");assertThrows(IOException::class.java){tree.copyForRun(target)}
        assertFalse(target.exists());assertEquals("value=1\n",File(mirror,"a.py").readText());assertEquals(0,p.writes)
    }
    @Test fun anExistingDestinationIsNeverOverwrittenOrDeleted(){
        val p=Provider();val tree=DocumentTree(p,temp.newFolder());tree.scan(temp.newFolder());val target=temp.newFolder("job")
        val sentinel=File(target,"keep.txt").apply{writeText("keep")}
        assertThrows(IllegalArgumentException::class.java){tree.copyForRun(target)};assertEquals("keep",sentinel.readText())
    }
}
