import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.codepocket.ide"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.codepocket.ide"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "0.2.0"

        // Mobile phones/tablets: keeps the APK reasonably small.
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild { ndkBuild {
            arguments += listOf("APP_PLATFORM=android-24", "APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true")
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true))
                arguments += listOf("HOST_OS=windows", "HOST_ARCH=x86_64")
        } }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { viewBinding = false }
    externalNativeBuild { ndkBuild { path = file("src/main/jni/Android.mk") } }
    sourceSets.getByName("main") {
        jniLibs.srcDir(rootProject.file(".tools/cpp-runtime/jni"))
        assets.srcDir(rootProject.file(".tools/cpp-runtime/assets"))
    }
    packaging {
        jniLibs.useLegacyPackaging = true
        // Preserve the original Python/PTY ELF bytes as well as the pinned analysis closure.
        // Configuring an app-level NDK would otherwise re-strip the previously packaged libs.
        jniLibs.keepDebugSymbols += setOf("**/*.so")
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            providers.gradleProperty("codepocket.testRepository").orNull?.let { repository ->
                it.systemProperty("robolectric.dependency.repo.url", repository)
            }
        }
    }
}

// No implicit network during Gradle. The explicit preparation script checks every pinned input.
val checkCppRuntime by tasks.registering {
    doLast {
        val prepared = rootProject.file(".tools/cpp-runtime/prepared.json")
        check(prepared.isFile && rootProject.file(".tools/cpp-runtime/assets/cpp-runtime/manifest.json").isFile) {
            "Prepare C++ inputs first: python scripts/prepare_cpp_runtime.py --fetch --fetch-host"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(file("cpp-runtime-lock.json").readBytes())
            .joinToString("") { "%02x".format(it) }
        check(prepared.readText().contains("\"lock_sha256\": \"$digest\"")) { "C++ lock changed: rerun scripts/prepare_cpp_runtime.py" }
    }
}
tasks.named("preBuild").configure { dependsOn(checkCppRuntime) }

chaquopy {
    defaultConfig {
        version = "3.13"
        // Jedi reads its bundled stubs and Python sources by filesystem path.
        extractPackages("jedi", "parso")
        providers.gradleProperty("codepocket.buildPython").orNull?.let { buildPython(it) }
        pip {
            install("-r", "requirements.txt")
            providers.gradleProperty("codepocket.semanticWheels").orNull?.let { wheels ->
                options("--no-index", "--find-links", wheels)
            }
        }
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
