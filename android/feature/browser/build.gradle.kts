plugins {
    id("ascon.android.library")
    id("ascon.android.compose")
}

android {
    namespace = "com.ascon.feature.browser"
}

dependencies {
    implementation(project(":core"))
}
