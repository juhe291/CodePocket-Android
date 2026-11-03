package com.codepocket.ide

import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor

/** Small test-only HTTP/1.1 fixture using java.base, including actual chunk framing. */
internal class UpdateFixtureHeaders {
    private val values=linkedMapOf<String,String>()
    fun add(name:String,value:String){values[name.lowercase()]=value}
    fun getFirst(name:String)=values[name.lowercase()]
    fun lines()=values.entries.joinToString(""){(k,v)->"$k: $v\r\n"}
}
internal class UpdateFixtureExchange(private val socket:Socket) {
    val requestHeaders=UpdateFixtureHeaders()
    val responseHeaders=UpdateFixtureHeaders()
    val requestMethod:String
    val requestURI:URI
    private val output=socket.getOutputStream()
    private var chunked=false
    private var finished=false
    init {
        socket.soTimeout=5000
        val reader=socket.getInputStream().bufferedReader(Charsets.US_ASCII)
        val request=checkNotNull(reader.readLine()).split(' ');requestMethod=request[0];requestURI=URI(request[1])
        while(true){val line=reader.readLine()?:break;if(line.isEmpty())break;val colon=line.indexOf(':');check(colon>0);requestHeaders.add(line.substring(0,colon),line.substring(colon+1).trim())}
    }
    fun sendResponseHeaders(status:Int,size:Long) {
        chunked=size==0L
        responseHeaders.add("Connection","close")
        if(chunked)responseHeaders.add("Transfer-Encoding","chunked") else responseHeaders.add("Content-Length",size.toString())
        output.write(("HTTP/1.1 $status Fixture\r\n"+responseHeaders.lines()+"\r\n").toByteArray(Charsets.US_ASCII));output.flush()
    }
    val responseBody=object:OutputStream() {
        override fun write(value:Int)=write(byteArrayOf(value.toByte()),0,1)
        override fun write(bytes:ByteArray,offset:Int,count:Int){
            if(count==0)return
            if(chunked)output.write((count.toString(16)+"\r\n").toByteArray(Charsets.US_ASCII))
            output.write(bytes,offset,count)
            if(chunked)output.write("\r\n".toByteArray(Charsets.US_ASCII))
        }
        override fun flush()=output.flush()
        override fun close(){if(!finished){finished=true;if(chunked)output.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII));output.flush();socket.close()}}
    }
    fun close(){runCatching{responseBody.close()};runCatching{socket.close()}}
}
internal class UpdateFixtureServer private constructor(private val server:ServerSocket) {
    lateinit var executor:Executor
    private lateinit var handler:(UpdateFixtureExchange)->Unit
    private val sockets=ConcurrentHashMap.newKeySet<Socket>()
    val address get()=server.localSocketAddress as InetSocketAddress
    fun createContext(path:String,callback:(UpdateFixtureExchange)->Unit){check(path=="/");handler=callback}
    fun start(){executor.execute{while(!server.isClosed){
        val socket=try{server.accept()}catch(_:java.net.SocketException){break};sockets.add(socket)
        executor.execute{try{val exchange=UpdateFixtureExchange(socket);try{handler(exchange)}finally{exchange.close()}}finally{sockets.remove(socket);runCatching{socket.close()}}}
    }}}
    fun stop(delay:Int){check(delay==0);server.close();sockets.forEach{runCatching{it.close()}};sockets.clear()}
    companion object {fun create(address:InetSocketAddress,backlog:Int)=UpdateFixtureServer(ServerSocket().apply{bind(address,backlog)})}
}
