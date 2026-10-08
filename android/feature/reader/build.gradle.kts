plugins {
    id("ascon.android.library")
    id("ascon.android.compose")
}

android {
    namespace = "com.ascon.feature.reader"
}

dependencies {
    implementation(project(":core"))
}
