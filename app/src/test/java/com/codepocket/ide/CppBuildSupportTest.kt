package com.codepocket.ide

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject

class CppBuildSupportTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun actualPreparedSupportMatchesAllFourPinnedFiles() {
        val repository = CppFixtures.root
        val spec = JSONObject(File(repository, ".tools/cpp-runtime/assets/cpp-runtime/manifest.json").readText()).getJSONObject("build_support")
        val archive = File(repository, ".tools/cpp-runtime/assets/${spec.getString("asset")}")
        assertEquals(spec.getString("sha256"), CppHeaderArchive.sha256(archive.inputStream()))
        val destination = temp.newFolder(); CppBuildSupport.extract(archive.inputStream(), destination, spec.getInt("entries"), spec.getLong("uncompressed_bytes"))
        val lock = JSONObject(File(repository, "app/cpp-runtime-lock.json").readText()).getJSONArray("build_support")
        repeat(lock.length()) { index ->
            val item = lock.getJSONObject(index); val file = File(destination, item.getString("name"))
            assertEquals(item.getLong("bytes"), file.length()); assertEquals(item.getString("sha256"), CppHeaderArchive.sha256(file.inputStream()))
        }
    }
    @Test fun unknownTraversalAndOversizedArchivesCannotWriteOutsideOwnedDirectory() {
        for (name in listOf("../escaped.o", "/crtbegin_so.o", "crtbegin_so.o/child", "program.so")) {
            val bytes = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(name)); zip.write(1); zip.closeEntry()
            } }.toByteArray()
            assertThrows(IllegalArgumentException::class.java) { CppBuildSupport.extract(ByteArrayInputStream(bytes), temp.newFolder(), 4, 1) }
        }
    }
}
