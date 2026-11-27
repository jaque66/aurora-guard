plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "pl.aurora.guard"
    compileSdk = 34
    defaultConfig {
        applicationId = "pl.aurora.guard"
        minSdk = 26
        targetSdk = 34
        versionCode = 23
        versionName = "2.7.1"
    }
    // Podpis wydania: klucz i hasla pochodza ze zmiennych srodowiskowych (sekrety CI). Bez nich (lokalnie, forki) uzywany jest domyslny klucz debug.
    val ksFile = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        if (ksFile != null) {
            create("guard") {
                storeFile = file(ksFile); storePassword = System.getenv("KEYSTORE_PASSWORD"); keyAlias = System.getenv("KEY_ALIAS"); keyPassword = System.getenv("KEY_PASSWORD"); storeType = "pkcs12"
            }
        }
    }
    buildTypes {
        getByName("debug") { if (ksFile != null) signingConfig = signingConfigs.getByName("guard") }
        release { isMinifyEnabled = false }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core-ktx:1.13.1")
}
