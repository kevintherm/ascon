plugins {
    id("ascon.android.library")
    id("ascon.android.compose")
}

android {
    namespace = "com.ascon.core"
}

dependencies {
    // Fake repositories expose Flows until Room lands.
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
