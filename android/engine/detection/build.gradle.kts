plugins {
    id("ascon.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ascon.engine.detection"
}

dependencies {
    implementation(project(":core"))
    // addDocumentStartJavaScript and addWebMessageListener: the injection bridge.
    api(libs.androidx.webkit)
    // Bridge messages and rules are JSON.
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
