package com.codepocket.ide

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class GitRemoteSecurityTest {
    @get:Rule val temp=TemporaryFolder()
    private val url="https://github.com/fixture/project.git"
    private val secret="fixture-only-not-a-real-pat-12345"
    private val head="a".repeat(40)
    private class Keys:GitCredentialKeys {
        var value:SecretKey?=null;var creates=0;var unavailable=false
        override fun key(create:Boolean):SecretKey {
            if(unavailable)error("sensitive provider diagnostic must not escape")
            if(value==null && create){creates++;value=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey()}
            return checkNotNull(value)
        }
        override fun clear(){value=null}
    }
    private fun replace(from:File,to:File){from.copyTo(to,true);check(from.delete())}
    private fun vault(folder:File,keys:Keys)=GitCredentialVault(folder,keys,::replace)
    private fun request()=JSONObject().put("action","push").put("expected","b".repeat(64)).put("url",url).put("branch","main")
        .put("expected_head",head).put("expected_remote",JSONObject.NULL).put("username","fixture").put("token",secret)
    private fun root()=File(temp.newFolder(UUID.randomUUID().toString()),"work").apply{check(mkdir())}
    @Test fun nativeHttpsIdentityMatchesTransportUnicodeIdnIpv6AndDefaultPortRules() {
        val cases=mapOf("https://GitHub.COM:443/owner/repo.git/" to "https://github.com/owner/repo.git",
            "https://git.example:8443/team/项目.git" to "https://git.example:8443/team/%E9%A1%B9%E7%9B%AE.git",
            "https://bücher.example/a+b" to "https://xn--bcher-kva.example/a%2Bb",
            "https://[2001:0DB8:0:0:0:0:0:1]:443/repo/" to "https://[2001:db8::1]/repo",
            "https://[::ffff:127.0.0.1]/x" to "https://[::ffff:7f00:1]/x","https://example.com" to "https://example.com/")
        cases.forEach{(input,expected)->assertEquals(expected,GitRemoteAddress.canonical(input));assertEquals(expected,GitRemoteAddress.canonical(expected))}
    }
    @Test fun invalidCredentialOrTraversalUrlsFailWithoutEchoingTheirInput() {
        for(value in listOf("http://github.com/a","https://secret-token@github.com/a","https://github.com/a?token=secret-token",
            "https://github.com/a#secret-token","https://github.com/%2e%2e/x","https://github.com/a%2fb","https://github.com/a\\b","https://github.com/%xx")) {
            val error=assertThrows(IllegalArgumentException::class.java){GitRemoteAddress.canonical(value)}
            assertFalse(error.toString().contains("secret-token"))
        }
    }
    @Test fun defaultSessionCredentialsNeverReachDiskOrCreateAKeyAndAreRedacted() {
        val folder=temp.newFolder("credentials");val keys=Keys();val store=vault(folder,keys);val value=GitCredentials("fixture",secret)
        store.save(url,value);assertEquals(secret,store.load(url)!!.token);assertEquals(0,keys.creates);assertEquals(emptyList<String>(),folder.list()!!.toList())
        assertFalse(value.toString().contains(secret));store.forgetSession();assertNull(store.load(url))
    }
    @Test fun rememberedCredentialsRoundTripEncryptedWithFreshIvAndCanonicalRepositoryScope() {
        val folder=temp.newFolder("credentials");val keys=Keys();val store=vault(folder,keys);val value=GitCredentials("fixture",secret)
        store.save("https://GITHUB.com:443/fixture/project.git/",value,true)
        val file=folder.listFiles()!!.single();val first=file.readBytes();assertFalse(first.toString(Charsets.ISO_8859_1).contains(secret))
        assertEquals(secret,vault(folder,keys).load(url)!!.token);assertEquals(1,keys.creates)
        store.save(url,value,true);assertFalse(first.contentEquals(file.readBytes()));assertNull(store.load("https://github.com/fixture/other.git"))
        store.save(url,value,false);assertFalse(file.exists());assertEquals(secret,store.load(url)!!.token);assertNull(vault(folder,keys).load(url))
    }
    @Test fun copiedCiphertextAndTamperingCannotBeDecryptedForAnotherRepository() {
        val folder=temp.newFolder("credentials");val keys=Keys();vault(folder,keys).save(url,GitCredentials("fixture",secret),true)
        val source=folder.listFiles()!!.single();val other="https://github.com/fixture/other.git"
        source.copyTo(File(folder,GitWorkspaceStore.digest(other.toByteArray())+".sealed"))
        assertEquals("credential_unreadable",assertThrows(GitCredentialFailure::class.java){vault(folder,keys).load(other)}.code)
        val bytes=source.readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();source.writeBytes(bytes)
        assertThrows(GitCredentialFailure::class.java){vault(folder,keys).load(url)};assertTrue(source.exists())
    }
    @Test fun unavailableKeysAndFailedAtomicWriteNeverFallBackToPlaintextOrSessionSuccess() {
        val folder=temp.newFolder("credentials");val keys=Keys().apply{unavailable=true};val store=vault(folder,keys)
        val failure=assertThrows(GitCredentialFailure::class.java){store.save(url,GitCredentials("fixture",secret),true)}
        assertEquals("credential_save",failure.code);assertFalse(failure.toString().contains("sensitive"));assertNull(store.load(url));assertTrue(folder.list()!!.isEmpty())
        keys.unavailable=false
        val denied=GitCredentialVault(folder,keys){_,_->throw java.io.IOException("disk refused $secret")}
        assertThrows(GitCredentialFailure::class.java){denied.save(url,GitCredentials("fixture",secret),true)}
        assertTrue(folder.list()!!.isEmpty());assertNull(denied.load(url))
    }
    @Test fun missingKeyDoesNotGenerateAReplacementAndExplicitClearRemovesAllRememberedSecrets() {
        val folder=temp.newFolder("credentials");val keys=Keys();val store=vault(folder,keys)
        store.save(url,GitCredentials("fixture",secret),true);keys.value=null
        assertThrows(GitCredentialFailure::class.java){vault(folder,keys).load(url)};assertEquals(1,keys.creates)
        store.clearAll();assertTrue(folder.list()!!.isEmpty());assertNull(store.load(url));assertNull(keys.value)
    }
    @Test fun malformedCredentialsAreRejectedBeforeAnythingCanBeSaved() {
        for(user in listOf("","has space","user:password","bad\nuser"))assertThrows(IllegalArgumentException::class.java){GitCredentials(user,secret)}
        for(token in listOf("","has space","bad\nsecret","\uD800"))assertThrows(Exception::class.java){GitCredentials("fixture",token)}
    }
    @Test fun pushIntentExistsBeforeTransportAndNeverContainsUsernameOrToken() {
        val folder=temp.newFolder("receipts");val ledger=GitPushLedger(folder,::replace);val work=root();var called=false
        val client=GitRemoteClient(object:SemanticTransport {
            override fun analyze(payload:String):String? {
                called=true;val packet=JSONObject(payload);val receipt=ledger.get(packet.getString("intent"));assertEquals("pending",receipt.state)
                assertEquals(head,receipt.head);assertEquals(work.parentFile!!.name,receipt.job);return null
            }
            override fun close(){}
        })
        val (_,receipt)=client.push(work,request(),"workspace",ledger)
        assertTrue(called);assertEquals("unknown",receipt.state);assertEquals(head,GitPushLedger(folder,::replace).get(receipt.id).head)
        folder.listFiles()!!.filter{it.extension=="json"}.forEach{assertFalse(it.readText().contains(secret));assertFalse(it.readText().contains("username"))}
    }
    @Test fun receiptClaimBindsJobSnapshotRemoteBranchAndCommitAndCannotRunTwice() {
        val ledger=GitPushLedger(temp.newFolder("receipts"),::replace);val work=root();val req=request();val receipt=ledger.begin("workspace",work,req)
        assertThrows(IllegalArgumentException::class.java){ledger.claim(receipt.id,root(),req)}
        val changed=JSONObject(req.toString()).put("expected_head","c".repeat(40))
        assertThrows(IllegalArgumentException::class.java){ledger.claim(receipt.id,work,changed)}
        val missing=JSONObject(req.toString()).apply{remove("expected_remote")}
        assertThrows(IllegalArgumentException::class.java){ledger.claim(receipt.id,work,missing)}
        assertEquals("started",ledger.claim(receipt.id,work,req).state)
        assertThrows(IllegalArgumentException::class.java){ledger.claim(receipt.id,work,req)}
    }
    @Test fun lateUnknownCannotOverwriteAConfirmedPushAndCleanupPreservesUncertainRecords() {
        val folder=temp.newFolder("receipts");val a=GitPushLedger(folder,::replace);val b=GitPushLedger(folder,::replace);val work=root()
        val receipt=a.begin("workspace",work,request());a.claim(receipt.id,work,request());a.finish(receipt.id,"pushed")
        assertEquals("pushed",b.finish(receipt.id,"unknown","push_unknown").state)
        val unknown=b.begin("workspace",work,request());b.finish(unknown.id,"unknown","push_unknown")
        a.clearConfirmed();assertEquals(listOf(unknown.id),a.forWorkspace("workspace").map{it.id});assertFalse(File(folder,"${receipt.id}.json").exists())
    }
    @Test fun concurrentLedgerWritersRetainTheConfirmedResult() {
        val folder=temp.newFolder("receipts");val ledger=GitPushLedger(folder,::replace);val work=root();val intent=ledger.begin("workspace",work,request())
        ledger.claim(intent.id,work,request());val pool=Executors.newFixedThreadPool(2);val start=CountDownLatch(1)
        try {
            val calls=listOf("pushed","unknown").map{state->pool.submit{check(start.await(5,TimeUnit.SECONDS));GitPushLedger(folder,::replace).finish(intent.id,state)}}
            start.countDown();calls.forEach{it.get(10,TimeUnit.SECONDS)};assertEquals("pushed",ledger.get(intent.id).state)
        }finally{start.countDown();pool.shutdownNow()}
    }
    @Test fun malformedRemoteRepliesAndSecretEchoAreNotReturnedToTheUi() {
        val bad=GitRemoteProtocol.failure("backend").let{JSONObject(it).put("error",secret).toString()}
        assertEquals("invalid_response",GitRemoteProtocol.decode(bad).error)
        assertEquals("backend",JSONObject(GitRemoteProtocol.checked(bad,request())).getString("error"))
        assertEquals("invalid_response",GitRemoteProtocol.decode("{\"ok\":false,\"error\":\"network\",\"token\":\"hidden\"}").error)
    }
    @Test fun receiptWritesWaitForAnIndependentJvmHoldingTheFilesystemLock() {
        val folder=temp.newFolder("receipts");val ledger=GitPushLedger(folder,::replace);val work=root();val intent=ledger.begin("workspace",work,request())
        val repo=generateSequence(File(checkNotNull(System.getProperty("user.dir")))){it.parentFile}
            .first{File(it,"verification/git-remote-service-2026-11-02/GitPushLedgerLockHolder.java").isFile}
        val windows=System.getProperty("os.name").orEmpty().startsWith("Windows",true)
        val java=File(System.getProperty("java.home"),if(windows)"bin/java.exe" else "bin/java")
        val errors=temp.newFile("lock-holder-errors.log")
        val child=ProcessBuilder(java.path,"--source","17",File(repo,"verification/git-remote-service-2026-11-02/GitPushLedgerLockHolder.java").path,
            File(folder,"ledger.lock").path).redirectError(errors).start()
        val workers=Executors.newFixedThreadPool(2)
        try {
            val ready=workers.submit<String>{child.inputStream.bufferedReader().readLine()}
            assertEquals(errors.readText(),"locked",ready.get(15,TimeUnit.SECONDS))
            val write=workers.submit<GitPushReceipt>{GitPushLedger(folder,::replace).finish(intent.id,"pushed")}
            assertThrows(java.util.concurrent.TimeoutException::class.java){write.get(150,TimeUnit.MILLISECONDS)}
            child.outputStream.write(1);child.outputStream.flush()
            assertTrue(child.waitFor(10,TimeUnit.SECONDS));assertEquals(errors.readText(),0,child.exitValue())
            assertEquals("pushed",write.get(10,TimeUnit.SECONDS).state)
        }finally {
            runCatching{child.outputStream.write(1);child.outputStream.flush()}
            if(child.isAlive){child.destroyForcibly();child.waitFor(2,TimeUnit.SECONDS)}
            workers.shutdownNow()
        }
    }
}
