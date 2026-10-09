plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    id("ascon.android.compose")
}

android {
    namespace = "com.ascon.app"
    compileSdk = AndroidConfig.COMPILE_SDK

    defaultConfig {
        applicationId = "com.ascon.app"
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        versionCode = 1
        versionName = "0.1.0"
        // The ABIs the Rust adblock engine is built for, see engine/adblock.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = AndroidConfig.JAVA_VERSION
        targetCompatibility = AndroidConfig.JAVA_VERSION
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":feature:library"))
    implementation(project(":feature:series"))
    implementation(project(":feature:browser"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:settings"))
    implementation(project(":engine:adblock"))
    implementation(project(":engine:detection"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Navigation 3: the back stack is a plain list the app owns, which keeps tab logic testable.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    // Routes are saved across process death through kotlinx.serialization.
    implementation(libs.kotlinx.serialization.core)

    testImplementation(libs.junit)
}
