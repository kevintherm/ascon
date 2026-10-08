package com.ascon.core.model

import java.time.Instant

/** Who is signed in, if anyone. Every on-device feature works without an account. */
sealed interface AccountState {
    data object SignedOut : AccountState

    data class SignedIn(val displayName: String, val premium: Boolean, val lastSyncedAt: Instant?) : AccountState
}
