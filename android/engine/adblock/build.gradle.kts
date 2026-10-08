plugins {
    id("ascon.android.library")
}

android {
    namespace = "com.ascon.engine.adblock"
}

dependencies {
    implementation(project(":core"))
}
