plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.codepocket.ide"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.codepocket.ide"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"

        // Mobile phones/tablets: keeps the APK reasonably small.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = false }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            providers.gradleProperty("codepocket.testRepository").orNull?.let { repository ->
                it.systemProperty("robolectric.dependency.repo.url", repository)
            }
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.13"
        providers.gradleProperty("codepocket.buildPython").orNull?.let { buildPython(it) }
        // Embedded CPython standard library; no pip requirements.
    }
}

dependencies {
    implementation(project(":editor-sora"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(project(":workspace-core"))
    implementation(project(":editor-surface"))
    implementation(project(":editor-assist"))
    implementation(project(":terminal-view"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

// Compile only against the Kotlin 1.9 ABI used by our sources. Gradle aligns the Sora
// runtime to 2.1.21; keep that runtime intact, without suppressing metadata validation.
configurations.matching { it.name.endsWith("CompileClasspath") }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib"))
            useVersion("1.9.25")
    }
}
