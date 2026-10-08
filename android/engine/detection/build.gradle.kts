plugins {
    id("ascon.android.library")
}

android {
    namespace = "com.ascon.engine.detection"
}

dependencies {
    implementation(project(":core"))
}
