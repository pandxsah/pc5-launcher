plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// The PC5 web UI (app/web) is copied unchanged into the APK, so the theme is pixel-identical.
val copyWebUi by tasks.registering(Sync::class) {
    from(rootProject.file("../app/web"))
    into(layout.buildDirectory.dir("generated/pc5assets/web"))
}

android {
    namespace = "com.pc5.tvlauncher"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.pc5.tvlauncher"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.5.1-tv"
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
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/pc5assets"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

tasks.named("preBuild") { dependsOn(copyWebUi) }

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
