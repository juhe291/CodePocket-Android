plugins { id("com.android.library") }

// Java facade: Sora/Kotlin 2 metadata never enters the main Kotlin 1.9 compiler classpath.
android {
    namespace = "com.codepocket.editor.sora"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig { minSdk = 24 }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":editor-surface"))
    implementation("io.github.rosemoe:editor:0.23.7")
    implementation("io.github.rosemoe:language-textmate:0.23.7")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
