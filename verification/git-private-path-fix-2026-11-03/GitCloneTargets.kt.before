package com.codepocket.ide

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

internal data class GitCloneTarget(val selector:String,val identity:String,val title:String,val store:GitWorkspaceStore)

/** Managed clone roots are siblings of the default project, with metadata outside the Git tree. */
internal class GitCloneTargets(context:Context) {
    private val app=context.applicationContext
    private val prefix="internal-git:"
    private fun parent()=File(app.filesDir,"git-projects").absoluteFile.also{require(it==it.canonicalFile && (it.isDirectory || it.mkdirs() || it.isDirectory))}
    private fun directory(selector:String):File {
        require(selector.startsWith(prefix));val id=selector.removePrefix(prefix);require(Regex("[a-f0-9-]{36}").matches(id))
        return File(parent(),id).also{require(it==it.canonicalFile && it.isDirectory)}
    }
    fun managed(selector:String?)=selector?.startsWith(prefix)==true
    fun createPrivate(name:String):GitCloneTarget {
        Workspace.validateName(name)
        val id=UUID.randomUUID().toString();val folder=File(parent(),id);require(folder.mkdir());val root=File(folder,"worktree");require(root.mkdir())
        val selector=prefix+id
        val bytes=JSONObject().put("version",1).put("name",name).put("state","preparing").toString().toByteArray()
        FileOutputStream(File(folder,"project.json")).use{it.write(bytes);it.fd.sync()}
        return GitCloneTarget(selector,selector,name+" · 内部克隆（卸载会清除）",GitWorkspaceStore(FileGitGateway(root),true))
    }
    fun fromTree(uri:Uri):GitCloneTarget {
        val gateway=SafGateway(app.contentResolver,uri)
        require(gateway.children(gateway.root.id).isEmpty()) { "克隆目标必须是新的空目录" }
        return GitCloneTarget(uri.toString(),"saf:"+DocumentTree.digest(uri.toString()),gateway.root.name+" · 已授权目录",GitWorkspaceStore(gateway))
    }
    fun open(selector:String):Workspace {
        val folder=directory(selector);val info=File(folder,"project.json")
        require(info.isFile && info==info.canonicalFile && info.length()<=8192)
        val json=JSONObject(info.readText());require(json.getInt("version")==1);val name=json.getString("name");Workspace.validateName(name)
        val state=if(json.getString("state")=="ready")"" else " · 克隆未完成，可检查恢复记录"
        return Workspace.fromPrivateProject(File(folder,"worktree"),selector,name+" · 内部克隆（卸载会清除）"+state)
    }
    fun completed(target:GitCloneTarget) {
        if(!managed(target.selector))return
        val folder=directory(target.selector);val source=File(folder,"project.json");val json=JSONObject(source.readText()).put("state","ready")
        val temporary=File(folder,"project-${UUID.randomUUID()}.next");require(temporary.createNewFile())
        try{FileOutputStream(temporary).use{it.write(json.toString().toByteArray());it.fd.sync()};android.system.Os.rename(temporary.path,source.path)}
        finally{require(temporary.delete() || !temporary.exists())}
    }
    fun list():List<Pair<String,String>> = parent().listFiles().orEmpty().filter{it.isDirectory && Regex("[a-f0-9-]{36}").matches(it.name)}
        .mapNotNull{folder->runCatching{val selector=prefix+folder.name;selector to open(selector).title}.getOrNull()}
}
