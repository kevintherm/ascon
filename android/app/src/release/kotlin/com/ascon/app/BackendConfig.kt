package com.ascon.app

/**
 * The backend isn't deployed yet, so release builds use only the rules on the device and
 * the built-in ones. Set the server's address and its public signing key here when it is.
 */
internal object BackendConfig {
    val BASE_URL: String? = null

    /** Sign-in isn't built, so release builds have no AI detection. */
    val accountToken: String? = null

    val publicKeys: Map<String, String> = emptyMap()
}
