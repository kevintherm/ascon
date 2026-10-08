plugins {
    alias(libs.plugins.android.application)
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

    testImplementation(libs.junit)
}
