package com.codepocket.ide

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.*
import org.junit.Assert.*
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/** Actual bounded HTTP streams on a local test server; production requests still use fixed HTTPS. */
class AppUpdateHttpTest {
    private lateinit var server:HttpServer
    private val executor=Executors.newCachedThreadPool()
    private val requests=AtomicInteger()
    @Volatile private var handler:(HttpExchange)->Unit={reply(it,200,AppUpdateFixtures.release().toString().toByteArray())}
    private var target=""
    private fun reply(exchange:HttpExchange,status:Int,bytes:ByteArray){exchange.sendResponseHeaders(status,bytes.size.toLong());exchange.responseBody.use{it.write(bytes)}}
    @Before fun start(){server=HttpServer.create(InetSocketAddress("127.0.0.1",0),0);server.executor=executor
        server.createContext("/"){exchange->requests.incrementAndGet();try{handler(exchange)}finally{exchange.close()}};server.start();target="http://127.0.0.1:${server.address.port}/latest"}
    @After fun stop(){server.stop(0);executor.shutdownNow()}
    private fun source()=GitHubAppUpdateSource(AppUpdateFixtures.repository){assertEquals(AppUpdateFixtures.repository.endpoint,it.toString());URL(target).openConnection(Proxy.NO_PROXY) as HttpURLConnection}
    @Test fun anActualRequestIsUnauthenticatedAndHasNoWorkspaceOrTrackingData(){
        handler={exchange->assertEquals("GET",exchange.requestMethod);assertNull(exchange.requestHeaders.getFirst("Authorization"))
            assertNull(exchange.requestURI.rawQuery);assertEquals("application/vnd.github+json",exchange.requestHeaders.getFirst("Accept"))
            assertEquals("CodePocket-Android-update-check",exchange.requestHeaders.getFirst("User-Agent"));reply(exchange,200,AppUpdateFixtures.release().toString().toByteArray())}
        assertEquals(AppUpdateKind.AVAILABLE,source().check(AppUpdateFixtures.current).kind);assertEquals(1,requests.get())
    }
    @Test fun noReleaseAndApiLimitsAreDifferentFromBeingCurrent(){
        for((status,kind,failure) in listOf(Triple(404,AppUpdateKind.NO_RELEASE,null),Triple(403,AppUpdateKind.FAILED,AppUpdateFailure.RATE_LIMIT),Triple(429,AppUpdateKind.FAILED,AppUpdateFailure.RATE_LIMIT))){
            handler={reply(it,status,"{}".toByteArray())};val result=source().check(AppUpdateFixtures.current);assertEquals(kind,result.kind);assertEquals(failure,result.failure)
        };assertEquals(3,requests.get())
    }
    @Test fun redirectsAreNotFollowedEvenToTheSameLocalServer(){
        handler={exchange->exchange.responseHeaders.add("Location",target.replace("latest","redirected"));reply(exchange,302,"redirect".toByteArray())}
        val result=source().check(AppUpdateFixtures.current);assertEquals(AppUpdateFailure.RESPONSE,result.failure);assertEquals(1,requests.get())
    }
    @Test fun aChunkedResponseCannotExceedTheBodyLimit(){
        handler={exchange->exchange.sendResponseHeaders(200,0);runCatching{exchange.responseBody.use{it.write(ByteArray(262145){32})}}}
        assertEquals(AppUpdateFailure.TOO_LARGE,source().check(AppUpdateFixtures.current).failure)
    }
    @Test fun declaredLargeResponsesAreRejectedBeforeReadingTheirBody(){
        handler={exchange->exchange.sendResponseHeaders(200,524288);runCatching{exchange.responseBody.close()}}
        assertEquals(AppUpdateFailure.TOO_LARGE,source().check(AppUpdateFixtures.current).failure)
    }
    @Test fun malformedJsonAndInvalidUtf8DoNotProduceLatestVersionSuccess(){
        for(bytes in listOf("not JSON".toByteArray(),byteArrayOf(0xc3.toByte(),0x28))){handler={reply(it,200,bytes)}
            val result=source().check(AppUpdateFixtures.current);assertEquals(AppUpdateKind.FAILED,result.kind);assertEquals(AppUpdateFailure.RESPONSE,result.failure)}
    }
    @Test fun timeoutAndNetworkMessagesAreTypedWithoutEchoingSensitiveDiagnostics(){
        val timeout=GitHubAppUpdateSource(AppUpdateFixtures.repository){throw SocketTimeoutException("fixture private diagnostic")}
        assertEquals(AppUpdateFailure.TIMEOUT,timeout.check(AppUpdateFixtures.current).failure)
        val offline=GitHubAppUpdateSource(AppUpdateFixtures.repository){throw java.io.IOException("not-a-real-secret")}.check(AppUpdateFixtures.current)
        assertEquals(AppUpdateFailure.NETWORK,offline.failure);assertFalse(offline.toString().contains("not-a-real-secret"))
        val cancelled=source();cancelled.cancel();assertThrows(CancellationException::class.java){cancelled.check(AppUpdateFixtures.current)};assertEquals(0,requests.get())
    }
    @Test fun cancellingAnActiveStreamReleasesItsRequestAndProducesNoSuccess(){
        val ready=CountDownLatch(1);val release=CountDownLatch(1)
        handler={exchange->exchange.sendResponseHeaders(200,0);runCatching{exchange.responseBody.use{it.write('{'.code);it.flush();ready.countDown();release.await(8,TimeUnit.SECONDS);it.write('}'.code)}}}
        val client=source();val worker=Executors.newSingleThreadExecutor();val task=worker.submit<AppUpdateResult>{client.check(AppUpdateFixtures.current)}
        try {assertTrue(ready.await(5,TimeUnit.SECONDS));val before=System.nanoTime();client.cancel()
            assertTrue("Cancellation must not block the caller",System.nanoTime()-before<1_500_000_000L);release.countDown()
            val error=assertThrows(ExecutionException::class.java){task.get(6,TimeUnit.SECONDS)};assertTrue(error.cause is CancellationException)
        }finally{release.countDown();task.cancel(true);worker.shutdownNow()}
    }
}
