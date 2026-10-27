package com.codepocket.ide

import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONTokener
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class CppRuntimeFault(val signal: Int, val addresses: List<Long>, val maps: String) {
    companion object {
        fun decode(bytes: ByteArray, maps: String): CppRuntimeFault? {
            if(bytes.size!=528 || maps.length>262144)return null
            val record=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if(record.int!=0x43504631)return null
            val signal=record.int;val count=record.int
            if(signal !in setOf(4,5,6,7,8,11,24,25) || count !in 1..64 || record.int!=0)return null
            return CppRuntimeFault(signal,List(count){record.long}.filter{it>0},maps)
        }
    }
}

/** Translate mapped file offsets through actual executable ELF segments. In particular,
 * a 16 KiB mapping's file offset is not necessarily its ELF virtual address. */
internal object CppFaultLocation {
    private data class Load(val offset: Long,val address: Long,val size: Long)
    private val mapping=Regex("^([0-9a-f]+)-([0-9a-f]+)\\s+([rwxps-]{4})\\s+([0-9a-f]+)\\s+\\S+\\s+\\d+\\s+(.+)$")
    fun offsets(program: File, fault: CppRuntimeFault): List<Long> {
        if(!program.isFile || program.length() !in 64..64L*1024*1024)return emptyList()
        val image=ByteBuffer.wrap(program.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        if(image.int!=0x464c457f || image.get(4).toInt()!=2 || image.get(5).toInt()!=1 || image.getShort(18).toInt()!=183)return emptyList()
        val phoff=image.getLong(32);val phsize=image.getShort(54).toInt()and 65535;val count=image.getShort(56).toInt()and 65535
        if(phoff<64 || phsize!=56 || count !in 1..100 || phoff+phsize.toLong()*count>image.capacity())return emptyList()
        val segments=(0 until count).mapNotNull { index ->
            val at=(phoff+index*56).toInt()
            if(image.getInt(at)!=1 || image.getInt(at+4)and 1==0)null else {
                val offset=image.getLong(at+8);val address=image.getLong(at+16);val size=image.getLong(at+32)
                if(offset<0 || address<0 || size<=0 || offset>image.capacity()-size)null else Load(offset,address,size)
            }
        }
        val expected=program.canonicalPath
        val ranges=fault.maps.lineSequence().mapNotNull { line ->
            val parts=mapping.matchEntire(line)?.groupValues?:return@mapNotNull null
            if(!parts[3].contains('x') || parts[5]!=expected)return@mapNotNull null
            val start=parts[1].toLongOrNull(16)?:return@mapNotNull null
            val end=parts[2].toLongOrNull(16)?:return@mapNotNull null
            val offset=parts[4].toLongOrNull(16)?:return@mapNotNull null
            if(start<0 || end<=start || offset<0)return@mapNotNull null
            Triple(start,end,offset)
        }.toList()
        return fault.addresses.mapNotNull { pc ->
            val range=ranges.firstOrNull{pc>=it.first && pc<it.second}?:return@mapNotNull null
            val offset=pc-range.first+range.third
            val segment=segments.firstOrNull{offset>=it.offset && offset-it.offset<it.size}?:return@mapNotNull null
            segment.address+offset-segment.offset
        }.distinct()
    }
    fun arguments(tool: File, program: File, addresses: List<Long>): List<String> {
        require(addresses.size in 1..64 && addresses.all{it>=0})
        return listOf(tool.path,"--obj=${program.path}","--output-style=JSON","--inlining=true","--demangle","--no-debuginfod")+
            addresses.map{"0x"+it.toString(16)}
    }
    fun diagnostic(snapshot: CppRunSnapshot, output: String): CppBuildError? {
        if(output.length>65536 || !snapshot.isCurrent())return null
        for(line in output.lineSequence().filter{it.isNotBlank()}) {
            // LLVM versions emit one JSON object per address or a JSON array for
            // command-line addresses. Accept the actual tool's two documented forms.
            val json=runCatching{JSONTokener(line).nextValue()}.getOrNull()
            val records=when(json){is JSONObject->listOf(json);is JSONArray->(0 until minOf(64,json.length())).mapNotNull{json.optJSONObject(it)};else->emptyList()}
            for(record in records) {
              val frames=record.optJSONArray("Symbol")?:continue
              for(index in 0 until minOf(64,frames.length())) {
                val frame=frames.optJSONObject(index)?:continue
                val path=frame.optString("FileName")
                val relative=if(path in snapshot.texts)path else runCatching {
                    val named=File(path)
                    val file=(if(named.isAbsolute)named else File(snapshot.source,path)).canonicalFile
                    require(file.path.startsWith(snapshot.source.path+File.separator))
                    file.path.removePrefix(snapshot.source.path+File.separator).replace(File.separatorChar,'/')
                }.getOrNull()?:continue
                val text=snapshot.texts[relative]?:continue
                val number=frame.optInt("Line",0)
                if(number in 1..text.count{it=='\n'}+1)return CppBuildError(relative,number,DocumentTree.digest(text))
              }
            }
        }
        return null
    }
}
