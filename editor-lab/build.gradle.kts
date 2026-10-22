plugins { id("com.android.application") }

// Optional internal harness. The production adapter lives in :editor-sora.
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
    sourceSets.getByName("test").resources.srcDir("../editor-sora/src/main/assets")
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            providers.gradleProperty("codepocket.testRepository").orNull?.let { repository ->
                it.systemProperty("robolectric.dependency.repo.url", repository)
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":editor-sora"))
    implementation(project(":workspace-core"))
    implementation(project(":editor-surface"))
    implementation(project(":editor-assist"))
    implementation("io.github.rosemoe:editor:0.23.7")
    implementation("io.github.rosemoe:language-textmate:0.23.7")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
