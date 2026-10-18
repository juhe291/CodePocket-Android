plugins { id("com.android.application") }

// Java-only compatibility experiment. Never a dependency of :app or part of its APK.
android {
    namespace = "com.codepocket.editorlab"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.codepocket.ide.editorlab"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-experiment"
    }
    sourceSets.getByName("test").resources.srcDir("src/main/assets")
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
    testImplementation("junit:junit:4.13.2")
}
