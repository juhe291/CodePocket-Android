plugins { id("com.android.library") }
android {
    namespace = "com.termux.terminal"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "27.2.12479018"
    defaultConfig {
        minSdk = 24
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { ndkBuild { arguments += listOf("APP_SUPPORT_FLEXIBLE_PAGE_SIZES=true")
            if (System.getProperty("os.name").startsWith("Windows")) arguments += listOf("HOST_OS=windows", "HOST_ARCH=x86_64") } }
    }
    externalNativeBuild { ndkBuild { path = file("src/main/jni/Android.mk") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { testImplementation("junit:junit:4.13.2") }
