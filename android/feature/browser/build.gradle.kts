plugins {
    id("ascon.android.feature")
}

android {
    namespace = "com.ascon.feature.browser"
}

dependencies {
    implementation(project(":engine:detection"))
    implementation(project(":engine:adblock"))
    // The public suffix list, to tell one site from another in the navigation guard.
    // The reader fetches images with it in step 6.
    implementation(libs.okhttp)
    // BackHandler, so back walks the page history before it closes the browser.
    implementation(libs.androidx.activity.compose)
}
