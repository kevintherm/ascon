package com.ascon.core.data

import java.time.Instant

/** A site's detection rule as the app last heard it from the backend. */
data class CachedRule(
    val domain: String,
    /** The rule's JSON, already verified, or null when the backend had no rule for the site. */
    val json: String?,
    val version: Int,
    val fetchedAt: Instant,
    /**
     * The structure fingerprint the backend was last asked with, or null if it was asked by
     * domain only. Refreshing asks with it again, so a rule borrowed from a mirror stays.
     */
    val fingerprint: String? = null
)

/** How one version of a site's rule did on this device since the counts were last sent. */
data class RuleHealthCount(
    val domain: String,
    val version: Int,
    val successes: Int,
    val emptyResults: Int,
    val backwardJumps: Int
)

/** Rules from the backend and their health counts, kept on the device. */
interface RuleStore {
    suspend fun rule(domain: String): CachedRule?

    suspend fun saveRule(rule: CachedRule)

    /** Adds [count] to the counts kept for its rule version. */
    suspend fun addHealth(count: RuleHealthCount)

    suspend fun health(): List<RuleHealthCount>

    /** Takes [sent] away from the kept counts, once the backend has them. */
    suspend fun removeHealth(sent: List<RuleHealthCount>)
}

/** The anonymous token the backend gave this device. */
interface DeviceTokenStore {
    suspend fun token(): String?

    suspend fun saveToken(token: String?)
}
