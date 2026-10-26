package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AnalysisDiskCacheTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun byteLimitRemovesOldestCacheEntriesAndKeepsSourceOutsideTheCache() {
        val root = temp.newFolder(); val source = File(root, "source.py").apply { writeText("keep source") }
        val cache = File(root, "cache/v1").apply { mkdirs() }
        val files = (1..5).map { index -> File(cache, "m$index.pkl").apply { writeBytes(ByteArray(20)); setLastModified(index * 1000L) } }
        AnalysisDiskCache.trim(cache.parentFile, maxBytes = 45, maxFiles = 100)
        assertEquals(listOf("m4.pkl", "m5.pkl"), cache.listFiles()!!.map { it.name }.sorted())
        assertEquals("keep source", source.readText()); assertEquals(40, files.filter { it.exists() }.sumOf { it.length() }.toInt())
    }
    @Test fun fileLimitIsIndependentAndRepeatedTrimmingIsIdempotent() {
        val root = temp.newFolder(); repeat(20) { File(root, "m$it").writeText("x") }
        AnalysisDiskCache.trim(root, maxBytes = 10000, maxFiles = 3)
        val names = root.listFiles()!!.map { it.name }.sorted(); assertEquals(3, names.size)
        AnalysisDiskCache.trim(root, maxBytes = 10000, maxFiles = 3); assertEquals(names, root.listFiles()!!.map { it.name }.sorted())
        AnalysisDiskCache.trim(root, maxBytes = 0, maxFiles = 0); assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun defaultLimitsAndNonexistentCacheDoNotCreateOrDeleteUserDirectories() {
        val root = temp.newFolder(); val missing = File(root, "not-created")
        AnalysisDiskCache.trim(missing); assertFalse(missing.exists())
        assertEquals(16L * 1024 * 1024, AnalysisDiskCache.MAX_BYTES); assertEquals(256, AnalysisDiskCache.MAX_FILES)
        assertThrows(IllegalArgumentException::class.java) { AnalysisDiskCache.trim(root, -1) }
    }
}
