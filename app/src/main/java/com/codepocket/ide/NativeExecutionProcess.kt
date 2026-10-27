package com.codepocket.ide

import java.io.Closeable
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

internal data class ExecutionExit(val code: Int?, val signal: Int?) {
    val successful get() = code == 0 && signal == null
    companion object {
        fun decode(status: Int): ExecutionExit? = when {
            status == -1 -> null
            status in 0..255 -> ExecutionExit(status, null)
            status in 257..511 -> ExecutionExit(null, status - 256)
            else -> throw java.io.IOException("C++ exit status unavailable")
        }
    }
}
internal interface ExecutionProcess : Closeable {
    val stdout: InputStream
    val stderr: InputStream
    fun poll(): ExecutionExit?
}
internal interface TerminalExecutionProcess : ExecutionProcess {
    val faults: InputStream get() = java.io.ByteArrayInputStream(ByteArray(0))
    fun faultMapsAndResume(): String? = null
    fun write(bytes: ByteArray, offset: Int, length: Int): Int
    fun eof()
    fun resize(rows: Int, columns: Int)
}
internal object NativeExecutionBridge {
    private val loaded by lazy { System.loadLibrary("codepocket_execute"); true }
    fun launch(arguments: List<String>, directory: String, environment: List<String>, terminal: IntArray?, cpu: Int, memoryMb: Int): Long {
        check(loaded)
        return spawn(arguments.map { it.toByteArray(Charsets.UTF_8) }.toTypedArray(),directory.toByteArray(Charsets.UTF_8),
            environment.map { it.toByteArray(Charsets.UTF_8) }.toTypedArray(),terminal,cpu,memoryMb)
    }
    private external fun spawn(arguments: Array<ByteArray>, directory: ByteArray, environment: Array<ByteArray>, terminal: IntArray?, cpu: Int, memoryMb: Int): Long
    external fun status(handle: Long): Int
    external fun read(handle: Long, channel: Int, limit: Int): ByteArray?
    external fun stop(handle: Long)
    external fun write(handle: Long, bytes: ByteArray, offset: Int, length: Int): Int
    external fun eof(handle: Long)
    external fun resize(handle: Long, rows: Int, columns: Int)
    external fun faultMapsAndResume(handle: Long): ByteArray?
}
internal class NativeExecutionProcess(arguments: List<String>, directory: String, environment: List<String>,
    terminal: IntArray? = null, cpu: Int = 30, memoryMb: Int = 1536) : TerminalExecutionProcess {
    private val handle=NativeExecutionBridge.launch(arguments,directory,environment,terminal,cpu,memoryMb)
    private val closed=AtomicBoolean()
    private inner class Reader(private val channel: Int):InputStream() {
        private var buffer=ByteArray(0);private var offset=0
        override fun read():Int { val bytes=ByteArray(1);return if(read(bytes,0,1)<0)-1 else bytes[0].toInt()and 255 }
        override fun read(bytes:ByteArray,start:Int,length:Int):Int {
            require(start>=0 && length>=0 && start<=bytes.size-length)
            if(length==0)return 0
            while(offset==buffer.size) {
                if(closed.get())return -1
                if(Thread.currentThread().isInterrupted)throw InterruptedException()
                buffer=NativeExecutionBridge.read(handle,channel,minOf(65536,length))?:return -1;offset=0
                if(buffer.isNotEmpty())break
            }
            val count=minOf(length,buffer.size-offset);buffer.copyInto(bytes,start,offset,offset+count);offset+=count;return count
        }
        override fun close()=this@NativeExecutionProcess.close()
    }
    override val stdout:InputStream=Reader(1)
    override val stderr:InputStream=Reader(2)
    override val faults:InputStream=Reader(3)
    override fun faultMapsAndResume()=NativeExecutionBridge.faultMapsAndResume(handle)?.toString(Charsets.UTF_8)
    override fun poll():ExecutionExit?=ExecutionExit.decode(NativeExecutionBridge.status(handle))
    override fun write(bytes:ByteArray,offset:Int,length:Int)=if(closed.get())-1 else NativeExecutionBridge.write(handle,bytes,offset,length)
    override fun eof(){check(!closed.get());NativeExecutionBridge.eof(handle)}
    override fun resize(rows:Int,columns:Int){if(!closed.get())NativeExecutionBridge.resize(handle,rows,columns)}
    override fun close(){if(closed.compareAndSet(false,true))NativeExecutionBridge.stop(handle)}
}
