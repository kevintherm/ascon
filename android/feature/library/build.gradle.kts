plugins {
    id("ascon.android.library")
    id("ascon.android.compose")
}

android {
    namespace = "com.ascon.feature.library"
}

dependencies {
    implementation(project(":core"))
}
