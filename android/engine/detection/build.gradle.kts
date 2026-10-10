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
    // Rule lookups and health counts go to the Ascon backend.
    implementation(libs.okhttp)
    // Ed25519 to verify signed rules; Android has it built in only from API 33.
    implementation(libs.bouncycastle.bcprov)
    // Sends rule health counts in a daily batch.
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
