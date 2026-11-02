package com.codepocket.ide

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class GitCredentialFailure(val code:String):IOException(code)
internal class GitCredentials(val username:String,val token:String) {
    init {
        require(username.length in 1..128 && ':' !in username && username.none{it.code<=32 || it.code==127})
        require(token.length in 1..4096 && token.none{it.code<=32 || it.code==127})
        listOf(username,token).forEach{Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).encode(CharBuffer.wrap(it))}
    }
    override fun toString()="GitCredentials(redacted)"
}
internal interface GitCredentialKeys {fun key(create:Boolean):SecretKey;fun clear()}
internal object AndroidGitCredentialKeys:GitCredentialKeys {
    private const val ALIAS="codepocket.git.credentials.v1"
    private fun store()=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
    override fun key(create:Boolean):SecretKey {
        val store=store()
        if(store.containsAlias(ALIAS))return store.getKey(ALIAS,null) as? SecretKey?:throw GitCredentialFailure("credential_key")
        if(!create)throw GitCredentialFailure("credential_key")
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    override fun clear(){store().deleteEntry(ALIAS)}
}

/** Call on the existing background IO path. Only encrypted bytes enter noBackupFilesDir.
 * A failed key/encryption/write never turns into a plaintext or silent temporary fallback. */
internal class GitCredentialVault(private val directory:File,private val keys:GitCredentialKeys,
    private val replace:(File,File)->Unit={a,b->android.system.Os.rename(a.path,b.path)}) {
    constructor(context:Context):this(File(context.applicationContext.noBackupFilesDir,"git-credentials"),AndroidGitCredentialKeys)
    private val folder=directory.absoluteFile.also{require(it==it.canonicalFile && (it.isDirectory || it.mkdirs() || it.isDirectory))}
    private val lock=locks.computeIfAbsent(folder.path){Any()}
    private val session=mutableMapOf<String,GitCredentials>()
    private fun location(url:String)=File(folder,GitWorkspaceStore.digest(url.toByteArray())+".sealed").also{require(it.absoluteFile==it.canonicalFile)}
    private fun aad(url:String)=("CodePocket Git credentials v1\u0000"+url).toByteArray(Charsets.UTF_8)
    private fun delete(file:File){require(!file.exists() || file.isFile && file.delete())}
    fun hasRemembered(url:String)=synchronized(lock){location(GitRemoteAddress.canonical(url)).isFile}
    fun save(url:String,credentials:GitCredentials,remember:Boolean=false)=synchronized(lock) {
        val canonical=GitRemoteAddress.canonical(url);val target=location(canonical)
        try {
            if(!remember)delete(target) else {
                val plaintext=JSONObject().put("username",credentials.username).put("token",credentials.token).toString().toByteArray(Charsets.UTF_8)
                val cipher=Cipher.getInstance("AES/GCM/NoPadding")
                val encrypted=try{cipher.init(Cipher.ENCRYPT_MODE,keys.key(true));cipher.updateAAD(aad(canonical));cipher.doFinal(plaintext)}finally{plaintext.fill(0)}
                require(cipher.iv.size==12);val bytes=MAGIC+cipher.iv+encrypted;require(bytes.size<=MAX_BYTES)
                val temporary=File(folder,"credential-${UUID.randomUUID()}.next");require(temporary.createNewFile())
                try{FileOutputStream(temporary).use{it.write(bytes);it.fd.sync()};replace(temporary,target)}finally{require(temporary.delete() || !temporary.exists())}
            }
            session[canonical]=credentials
        }catch(_:Exception){throw GitCredentialFailure("credential_save")}
    }
    fun load(url:String):GitCredentials?=synchronized(lock) {
        val canonical=GitRemoteAddress.canonical(url);session[canonical]?.let{return@synchronized it}
        val source=location(canonical);if(!source.exists())return@synchronized null
        try {
            require(source.isFile && source.length() in (MAGIC.size+12+16).toLong()..MAX_BYTES.toLong())
            val bytes=source.readBytes();require(bytes.copyOfRange(0,MAGIC.size).contentEquals(MAGIC))
            val iv=bytes.copyOfRange(MAGIC.size,MAGIC.size+12);val encrypted=bytes.copyOfRange(MAGIC.size+12,bytes.size)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,keys.key(false),GCMParameterSpec(128,iv));cipher.updateAAD(aad(canonical))
            val plaintext=cipher.doFinal(encrypted)
            try {val json=JSONObject(plaintext.toString(Charsets.UTF_8));GitCredentials(json.getString("username"),json.getString("token")).also{session[canonical]=it}}
            finally{plaintext.fill(0)}
        }catch(_:Exception){throw GitCredentialFailure("credential_unreadable")}
    }
    fun clear(url:String)=synchronized(lock) {
        val canonical=GitRemoteAddress.canonical(url)
        try{delete(location(canonical));session.remove(canonical);Unit}catch(_:Exception){throw GitCredentialFailure("credential_clear")}
    }
    fun clearAll()=synchronized(lock) {
        try {
            folder.listFiles()?.filter{Regex("[a-f0-9]{64}\\.sealed").matches(it.name)}?.forEach{require(it.absoluteFile==it.canonicalFile);delete(it)}
            session.clear();keys.clear()
        }catch(_:Exception){throw GitCredentialFailure("credential_clear")}
    }
    fun forgetSession()=synchronized(lock){session.clear()}
    companion object {private val locks=ConcurrentHashMap<String,Any>();private val MAGIC=byteArrayOf(67,80,67,82,1);private const val MAX_BYTES=16384}
}
