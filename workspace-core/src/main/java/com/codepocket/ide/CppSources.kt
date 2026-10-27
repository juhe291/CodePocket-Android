package com.codepocket.ide

/** Configuration/build scripts are intentionally not imported into the trusted analysis snapshot. */
object CppSources {
    private val extensions = setOf("c", "cc", "cpp", "cxx", "h", "hh", "hpp", "hxx", "inc", "inl")
    fun isSource(path: String) = path.substringAfterLast('.', "").lowercase() in extensions
}
