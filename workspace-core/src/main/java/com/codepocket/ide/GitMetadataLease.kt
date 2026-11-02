package com.codepocket.ide

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Standard Git lock names protect private filesystem publication against other Git writers.
 * SAF providers have no exclusive-create contract, so they retain journal/checksum protection. */
internal class GitMetadataLease(root:File,private val locks:List<File>):Closeable {
    private val marker="CodePocket "+UUID.randomUUID()+"\n"
    private val token=marker.toByteArray(Charsets.UTF_8)
    private val owned=mutableListOf<File>()
    private var closed=false
    val paths:Set<String>
        get()=owned.map{it.relativeTo(root).invariantSeparatorsPath}.toSet()
    private val root=root
    fun record()=org.json.JSONObject().put("token",marker).put("paths",org.json.JSONArray(locks.map{it.relativeTo(root).invariantSeparatorsPath}))
    fun acquire() {
        check(!closed && owned.isEmpty())
        try {
            locks.forEach{lock->
                require(lock.absoluteFile==lock.canonicalFile && lock.parentFile!!.isDirectory)
                check(lock.createNewFile()){ "Git 正被其他操作使用：${lock.name}" }
                owned.add(lock)
                FileOutputStream(lock).use{it.write(token);it.fd.sync()}
            }
        } catch(error:Throwable){runCatching{close()}.exceptionOrNull()?.let(error::addSuppressed);throw error}
    }
    fun verify(){check(!closed && owned.size==locks.size);owned.forEach{check(it.absoluteFile==it.canonicalFile && it.isFile && it.length()==token.size.toLong() && it.readBytes().contentEquals(token)){"Git 锁已在操作之外变化"}}}
    override fun close() {
        if(closed)return
        // Do not remove a lock another process replaced or changed.
        var failure:Throwable?=null
        owned.asReversed().forEach{lock->
            try{check(lock.absoluteFile==lock.canonicalFile && lock.isFile && lock.length()==token.size.toLong() && lock.readBytes().contentEquals(token) && lock.delete()){"Git 锁身份已变化，已停止清理"}}
            catch(error:Throwable){if(failure==null)failure=error else failure!!.addSuppressed(error)}
        }
        closed=true;failure?.let{throw it}
    }
}
