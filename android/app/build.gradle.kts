plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "kr.ieumpay.pos"
    compileSdk = 35
    defaultConfig { applicationId = "kr.ieumpay.pos.demo"; minSdk = 26; targetSdk = 35; versionCode = 3; versionName = "0.3.1-demo" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
}
