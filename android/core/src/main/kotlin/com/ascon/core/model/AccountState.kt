package com.ascon.core.model

import java.time.Instant

/** Who is signed in, if anyone. Every on-device feature works without an account. */
sealed interface AccountState {
    data object SignedOut : AccountState

    /** [email] is the Google account's, kept on the device only; the backend stores neither it nor the name. */
    data class SignedIn(
        val displayName: String,
        val email: String? = null,
        val premium: Boolean,
        val lastSyncedAt: Instant? = null
    ) : AccountState
}

/** What the app keeps after signing in. [token] is the backend's account token. */
data class AccountSession(val token: String, val displayName: String, val email: String?, val premium: Boolean)

/** The account's AI detection allowance this month, as the backend's Quota. */
data class Quota(val limit: Int, val remaining: Int, val resetsAt: Instant, val premium: Boolean)
