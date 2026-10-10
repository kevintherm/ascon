package com.ascon.app

/**
 * Debug builds talk to a backend on the development machine, reached through
 * `adb reverse tcp:8080 tcp:8080`, and trust the development signing key from
 * contracts/fixtures/signed-rule.json. See the README for starting the backend.
 */
internal object BackendConfig {
    val BASE_URL: String? = "http://127.0.0.1:8080/v1/"

    val publicKeys: Map<String, String> = mapOf("dev" to "Ib8+0ckDUaWc1Xc/aOR6A4vuSQwdY2iGGpond7918+0=")
}
