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
        jniLibs.srcDir(layout.buildDirectory.dir("cpp-runner/jni"))
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

// AGP does not package BUILD_EXECUTABLE outputs. Build the installed PIE explicitly
// and stage it with the lib*.so name required by Android native-library extraction.
val prepareCppRunner by tasks.registering {
    inputs.files("src/main/jni/cpp_runner.c", "src/main/jni/cpp_fault.h", "src/main/jni/Android.mk")
    val staged = layout.buildDirectory.file("cpp-runner/jni/arm64-v8a/libcodepocket_cpp_runner.so")
    outputs.file(staged)
    doLast {
        val ndk = File(androidComponents.sdkComponents.sdkDirectory.get().asFile, "ndk/${android.ndkVersion}")
        val directory = layout.buildDirectory.dir("cpp-runner").get().asFile
        val windows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        val arguments = listOf("NDK_PROJECT_PATH=null", "APP_BUILD_SCRIPT=${file("src/main/jni/Android.mk")}",
            "NDK_OUT=${File(directory, "obj")}", "NDK_LIBS_OUT=${File(directory, "libs")}",
            "APP_ABI=arm64-v8a", "APP_PLATFORM=android-24", "APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true", "codepocket_cpp_runner") +
            if (windows) listOf("HOST_OS=windows", "HOST_ARCH=x86_64") else emptyList()
        exec {
            commandLine(if (windows) listOf("cmd", "/c", File(ndk, "ndk-build.cmd").path) + arguments
                else listOf(File(ndk, "ndk-build").path) + arguments)
        }
        val built = File(directory, "obj/local/arm64-v8a/codepocket_cpp_runner")
        check(built.isFile)
        staged.get().asFile.apply { parentFile.mkdirs(); built.copyTo(this, overwrite = true) }
    }
}
tasks.named("preBuild").configure { dependsOn(prepareCppRunner) }

chaquopy {
    defaultConfig {
        version = "3.13"
        // Jedi reads its bundled stubs and Python sources by filesystem path.
        extractPackages("jedi", "parso", "autopep8", "pycodestyle", "dulwich", "urllib3")
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
