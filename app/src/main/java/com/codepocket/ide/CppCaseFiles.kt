package com.codepocket.ide

import java.io.File

internal data class CppCaseOutput(val stdout:String,val stderr:String,val limited:Boolean)
/** Per-run regular files give the real C/C++ program EOF and keep stderr out of answer comparison. */
internal class CppCaseFiles private constructor(private val directory:File,val input:File,val output:File,val error:File){
    fun arguments(runner:File,program:File)=listOf(runner.path,program.path,input.path,output.path,error.path)
    fun read():CppCaseOutput{
        fun readFile(file:File):Pair<String,Boolean>{
            require(file.canonicalFile==file&&file.parentFile==directory&&file.isFile){"用例输出文件已移除或重定向"}
            val bytes=file.inputStream().use{stream->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(out.size()<=220000){val n=stream.read(buffer,0,minOf(buffer.size,220001-out.size()));if(n<0)break;out.write(buffer,0,n)}
                out.toByteArray()
            }
            val text=bytes.toString(Charsets.UTF_8)
            return text.take(50000) to (bytes.size>220000||text.length>50000)
        }
        val out=readFile(output);val err=readFile(error)
        return CppCaseOutput(out.first,err.first,out.second||err.second)
    }
    companion object{
        fun create(directory:File,text:String):CppCaseFiles{
            require(directory.canonicalFile==directory.absoluteFile&&directory.isDirectory&&text.length<=20000)
            val owned=mutableListOf<File>()
            fun file(suffix:String)=File.createTempFile(".case-",suffix,directory).also{owned.add(it)}
            try{
                val input=file(".stdin").apply{writeBytes(text.toByteArray(Charsets.UTF_8))}
                return CppCaseFiles(directory,input,file(".stdout"),file(".stderr"))
            }catch(failure:Throwable){owned.forEach{runCatching{it.delete()}};throw failure}
        }
    }
}
