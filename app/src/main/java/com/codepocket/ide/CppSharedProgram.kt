package com.codepocket.ide

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Checks our actual linker output before the installed runner can dlopen it. */
internal object CppSharedProgram {
    fun validate(file: File) {
        require(file.canonicalFile == file.absoluteFile && file.isFile && file.length() in 64..(64L * 1024 * 1024))
        val bytes = file.readBytes(); val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun range(offset: Long, size: Long): Int {
            require(offset >= 0 && size >= 0 && offset <= bytes.size.toLong() - size)
            return offset.toInt()
        }
        fun u16(at: Int) = data.getShort(at).toInt() and 65535
        fun u32(at: Int) = data.getInt(at).toLong() and 0xffffffffL
        require(bytes.take(6) == listOf<Byte>(127, 69, 76, 70, 2, 1)) { "本地编译产物不是 ELF64 小端程序" }
        require(u16(16) == 3 && u16(18) == 183 && u32(20) == 1L) { "本地编译产物不是 arm64 共享程序" }
        require(u16(52) == 64 && u16(54) == 56)
        val ph = data.getLong(32); val count = u16(56); require(count in 1..128); range(ph, count * 56L)
        var loads = 0; var stack = false
        repeat(count) { index ->
            val at = (ph + index * 56).toInt(); val type = u32(at); val flags = u32(at + 4)
            val offset = data.getLong(at + 8); val address = data.getLong(at + 16)
            val size = data.getLong(at + 32); val memory = data.getLong(at + 40); val alignment = data.getLong(at + 48)
            range(offset, size)
            require(type != 3L) { "共享程序不应包含可执行解释器" }
            if (type == 1L) {
                loads++; require(memory >= size && alignment >= 16384 && alignment and (alignment - 1) == 0L)
                require(offset % alignment == address % alignment && flags and 3L != 3L) { "共享程序存在不安全或不兼容的加载段" }
            }
            if (type == 0x6474e551L) { stack = true; require(flags and 1L == 0L) }
            if (type == 2L) {
                require(size % 16 == 0L)
                var cursor = offset
                while (cursor < offset + size) {
                    val tag = data.getLong(cursor.toInt()); val value = data.getLong(cursor.toInt() + 8)
                    if (tag == 0L) break
                    require(tag != 22L && !(tag == 30L && value and 4L != 0L)) { "共享程序包含文本重定位" }
                    cursor += 16
                }
            }
        }
        require(loads > 0 && stack)
        val sh = data.getLong(40); val sections = u16(60)
        require(u16(58) == 64 && sections in 1..4096); range(sh, sections * 64L)
        var main = false
        repeat(sections) { index ->
            val at = (sh + index * 64).toInt()
            if (u32(at + 4) != 11L) return@repeat
            val symbols = data.getLong(at + 24); val size = data.getLong(at + 32); val link = u32(at + 40)
            require(data.getLong(at + 56) == 24L && size % 24 == 0L && link < sections)
            range(symbols, size)
            val strings = (sh + link * 64).toInt(); require(u32(strings + 4) == 3L)
            val text = data.getLong(strings + 24); val length = data.getLong(strings + 32); range(text, length)
            var cursor = symbols
            while (cursor < symbols + size) {
                val symbol = cursor.toInt(); val name = u32(symbol)
                require(name < length)
                val offset = (text + name).toInt()
                if (name + 5 <= length && bytes.copyOfRange(offset, offset + 5).contentEquals(byteArrayOf(109, 97, 105, 110, 0))) {
                    val info = bytes[symbol + 4].toInt() and 255
                    if (info and 15 == 2 && info shr 4 in 1..2 && u16(symbol + 6) != 0) main = true
                }
                cursor += 24
            }
        }
        require(main) { "程序缺少已定义的 main 入口" }
    }
}
