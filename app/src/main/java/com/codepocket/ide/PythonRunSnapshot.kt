package com.codepocket.ide

import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.UUID

/** Owns only run metadata and an optional SAF copy. Internal program writes keep their real CWD. */
internal class PythonRunSnapshot private constructor(val directory:File,val root:File,val entry:File,
    val manifest:File,private val validate:()->Boolean):Closeable {
    private var closed=false
    fun isCurrent()=!closed&&manifest.isFile&&validate()
    override fun close(){
        fun remove(file:File){
            require(file==directory||file.path.startsWith(directory.path+File.separator))
            if(file.isDirectory&&file.canonicalFile==file)file.listFiles()?.forEach(::remove)
            require(file.delete()||!file.exists())
        }
        remove(directory)
        closed=true
    }
    companion object {
        const val MAX_MANIFEST_BYTES=8_000_000L
        private val jobName=Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
        private fun relative(path:String)=path.isNotEmpty()&&!path.contains('\\')&&!path.contains(':')&&
            path.none{it<' '||it=='\u007f'}&&path.split('/').none{it.isEmpty()||it=="."||it==".."}
        fun capture(parent:File,workspace:Workspace,drafts:DraftStore,keys:Map<String,String>,path:String,code:String):PythonRunSnapshot {
            require(relative(path)&&path.lowercase().endsWith(".py")&&code.length<=Workspace.MAX_CHARS&&!code.contains('\u0000'))
            require(parent.canonicalFile==parent.absoluteFile&&(parent.isDirectory||parent.mkdirs()))
            val directory=File(parent,UUID.randomUUID().toString());require(directory.mkdir())
            var snapshot:PythonRunSnapshot?=null
            try {
                val root=workspace.preparePythonRun(File(directory,"workspace"))
                val entry=File(root,path)
                require(entry.canonicalFile==entry&&entry.isFile&&entry.length()<=1_000_000)
                val saved=PythonSources.local(root)
                val entryText=entry.inputStream().use{Workspace.utf8Reader(it).use{r->Workspace.readLimited(r,Workspace.MAX_CHARS)}}.removePrefix("\uFEFF")
                require(entryText==code){"入口文件在准备运行期间发生变化，请重新运行"}
                val projectKeys=keys.filterKeys{it!=path&&PythonSources.isSource(it)&&it in saved}
                val overlays=drafts.analysisTexts(projectKeys)
                require(overlays.size<=PythonSources.MAX_FILES&&overlays.values.sumOf{it.length}<=PythonSources.MAX_UNITS)
                val files=JSONObject()
                overlays.forEach{(name,text)->
                    require(relative(name)&&text.length<=Workspace.MAX_CHARS&&!text.contains('\u0000'))
                    files.put(name,text)
                }
                val manifest=File(directory,"overlays.json")
                manifest.writeText(JSONObject().put("version",1).put("files",files).toString())
                require(manifest.length()<=MAX_MANIFEST_BYTES)
                snapshot=PythonRunSnapshot(directory,root,entry,manifest){
                    workspace.pythonSources()==saved&&drafts.analysisTexts(projectKeys)==overlays
                }
                require(snapshot.isCurrent()){"项目或恢复草稿在准备运行期间发生变化，请重试"}
                return snapshot
            } catch(failure:Throwable){
                runCatching{(snapshot?:PythonRunSnapshot(directory,directory,directory,File(directory,"overlays.json")){false}).close()}
                    .exceptionOrNull()?.let(failure::addSuppressed)
                throw failure
            }
        }
        /** Private service accepts a direct canonical job, never an arbitrary overlay file. */
        fun open(parent:File,directory:File,projectRoot:File,entry:File):PythonRunSnapshot {
            require(parent.canonicalFile==parent.absoluteFile&&directory.canonicalFile==directory.absoluteFile)
            require(directory.parentFile==parent&&jobName.matches(directory.name)&&directory.isDirectory)
            require(projectRoot.canonicalFile==projectRoot.absoluteFile&&projectRoot.isDirectory)
            require(entry.canonicalFile==entry.absoluteFile&&entry.isFile&&entry.path.startsWith(projectRoot.path+File.separator))
            require(!projectRoot.path.startsWith(parent.path+File.separator)||projectRoot==File(directory,"workspace"))
            val manifest=File(directory,"overlays.json")
            require(manifest.canonicalFile==manifest&&manifest.isFile&&manifest.length()<=MAX_MANIFEST_BYTES)
            val data=JSONObject(manifest.inputStream().use{Workspace.utf8Reader(it).use{r->Workspace.readLimited(r,MAX_MANIFEST_BYTES.toInt())}})
            require(data.getInt("version")==1&&data.length()==2)
            val files=data.getJSONObject("files");require(files.length()<=PythonSources.MAX_FILES)
            var units=0
            for(name in files.keys()){
                require(relative(name)&&PythonSources.isSource(name)&&files.get(name) is String)
                val text=files.getString(name);units+=text.length
                val target=File(projectRoot,name)
                require(text.length<=Workspace.MAX_CHARS&&units<=PythonSources.MAX_UNITS&&!text.contains('\u0000'))
                require(target.canonicalFile==target&&target.isFile&&target.path.startsWith(projectRoot.path+File.separator))
            }
            return PythonRunSnapshot(directory,projectRoot,entry,manifest){true}
        }
    }
}
