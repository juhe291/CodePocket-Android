plugins { id("com.android.library"); id("org.jetbrains.kotlin.android") }

// Existing project/SAF storage, shared without copying or weakening the IDE's safety checks.
android {
    namespace = "com.codepocket.workspace"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig { minSdk = 24 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
