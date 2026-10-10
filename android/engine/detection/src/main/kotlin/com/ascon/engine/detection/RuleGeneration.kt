package com.ascon.engine.detection

import com.ascon.core.data.CachedRule
import com.ascon.core.data.RuleStore
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * AI detection, the last step of the lookup order. A site the backend has no rule for,
 * where heuristics find chapters, gives two snapshots of different chapters; the backend
 * writes a rule from them and checks it. The rule is kept like a looked-up one, so the
 * next page uses it, and the backend shares it with everyone.
 *
 * It runs only while [accountToken] gives a token. Each site is tried at most once per
 * run of the app, so a site the model can't read doesn't spend quota on every page.
 */
class RuleGeneration(
    private val store: RuleStore,
    private val backend: GenerationBackend,
    private val verifier: RuleVerifier,
    private val accountToken: () -> String?,
    private val clock: () -> Instant = Instant::now,
    private val maxPolls: Int = MAX_POLLS
) {
    private val lock = Mutex()
    private val samples = mutableMapOf<String, List<Sample>>()
    private val tried = mutableSetOf<String>()
    private var outOfQuotaUntil: Instant? = null

    private class Sample(val chapter: BigDecimal, val snapshot: PageSnapshot)

    /** Whether a snapshot of a chapter page on [host] would be used. */
    suspend fun wants(host: String): Boolean {
        val domain = host.lowercase()
        if (accountToken() == null || lock.withLock { domain in tried || outOfQuota() }) return false
        // Only a site the backend was asked about and had no rule for.
        val kept = store.rule(domain)
        return kept != null && kept.json == null
    }

    /**
     * Keeps [snapshot] of chapter [chapter] on [host]. With a snapshot of another chapter
     * already kept, asks the backend for a rule and waits for it, which takes about a
     * minute. Returns true when a new rule was kept for [host].
     */
    suspend fun offer(host: String, fingerprint: String?, chapter: BigDecimal, snapshot: PageSnapshot): Boolean {
        val domain = host.lowercase()
        val ready = lock.withLock { collect(domain, chapter, snapshot) } ?: return false
        return try {
            generate(domain, fingerprint, ready).also { lock.withLock { samples.remove(domain) } }
        } catch (_: IOException) {
            // Unreachable or refused: the next chapter page tries again with the samples kept.
            lock.withLock { tried -= domain }
            false
        }
    }

    /** Keeps the sample, and returns the samples to send once there are enough. Call it under [lock]. */
    private fun collect(domain: String, chapter: BigDecimal, snapshot: PageSnapshot): List<PageSnapshot>? {
        if (domain in tried || outOfQuota()) return null
        val kept = samples[domain].orEmpty().filter { it.chapter.compareTo(chapter) != 0 }
        val now = (kept + Sample(chapter, snapshot)).takeLast(SAMPLES)
        samples[domain] = now
        if (now.size == SAMPLES) tried += domain
        return now.takeIf { it.size == SAMPLES }?.map { it.snapshot }
    }

    private suspend fun generate(domain: String, fingerprint: String?, snapshots: List<PageSnapshot>): Boolean {
        var state = backend.request(domain, fingerprint, snapshots)
        var polls = 0
        while (state is CandidateState.Pending && polls < maxPolls) {
            delay(state.retryAfterSeconds.coerceIn(1, MAX_POLL_SECONDS) * MILLIS)
            state = backend.candidate(state.id)
            polls++
        }
        return when (state) {
            is CandidateState.Accepted -> keep(domain, fingerprint, state.rule)
            is CandidateState.OutOfQuota -> {
                lock.withLock { outOfQuotaUntil = state.resetsAt ?: clock().plusSeconds(DAY_SECONDS) }
                lock.withLock { tried -= domain }
                false
            }
            // The kept answer is out of date, so the next page looks the rule up again.
            CandidateState.RuleExists -> {
                store.saveRule(CachedRule(domain, null, 0, Instant.EPOCH, fingerprint))
                false
            }
            is CandidateState.Pending, is CandidateState.Rejected -> false
        }
    }

    private suspend fun keep(domain: String, fingerprint: String?, signed: SignedRule): Boolean {
        val rule = verifier.verify(signed) ?: return false
        val version = rule["version"]?.jsonPrimitive?.intOrNull ?: 0
        store.saveRule(CachedRule(domain, rule.toString(), version, clock(), fingerprint))
        return true
    }

    private fun outOfQuota(): Boolean = outOfQuotaUntil?.let { clock() < it } ?: false

    private companion object {
        const val SAMPLES = 2

        /** About five minutes at the backend's usual pace, past its own generation timeout. */
        const val MAX_POLLS = 60
        const val MAX_POLL_SECONDS = 30
        const val MILLIS = 1000L
        const val DAY_SECONDS = 86_400L
    }
}
