plugins {
    id("ascon.android.library")
    id("ascon.android.compose")
}

android {
    namespace = "com.ascon.feature.series"
}

dependencies {
    implementation(project(":core"))
}
