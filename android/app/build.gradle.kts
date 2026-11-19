plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pc5.tvlauncher"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.pc5.tvlauncher"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.6.0-native"
    }
    // Fixed key so every CI build can update the previous install (adb install -r)
    signingConfigs {
        create("pc5") {
            storeFile = rootProject.file("pc5-tv.keystore")
            storePassword = "pc5launcher"
            keyAlias = "pc5"
            keyPassword = "pc5launcher"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("pc5") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("pc5")
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.13.1")
}
