plugins {
    id("ascon.android.feature")
}

android {
    namespace = "com.ascon.feature.reader"
}

dependencies {
    // Coil decodes page images at screen width and caches them; the OkHttp fetcher lets
    // the reader send the WebView's cookies and User-Agent with each image request.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
}
