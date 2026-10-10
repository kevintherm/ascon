package com.ascon.engine.detection

import com.ascon.core.data.CachedRule
import com.ascon.core.data.RuleStore
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Gives the rules a page on [host] may try, in lookup order. */
interface RuleSource {
    suspend fun candidatesFor(host: String): List<RuleCandidate>

    /**
     * New rules for a page on [host] whose structure has [fingerprint], when they borrow
     * another site's rule. Null when nothing changes.
     */
    suspend fun forStructure(host: String, fingerprint: String): List<RuleCandidate>? = null
}

/**
 * The lookup order from AGENTS.md, as far as it exists on the device: the rule kept for
 * the exact host, then the backend by domain and by structure fingerprint, then the
 * built-in theme rules. Heuristics run in the page when nothing matches.
 *
 * A site seen for the first time waits up to [wait] for the backend, which usually
 * answers while the page is still loading. A slower answer is kept for the next page.
 * Answers are kept for a day, a missing rule included, then refreshed in the background.
 * Without [backend], only kept rules and the built-in ones are used.
 */
class RuleLookup(
    private val store: RuleStore,
    private val builtIn: List<RuleCandidate>,
    private val backend: RuleBackend? = null,
    private val verifier: RuleVerifier? = null,
    private val scope: CoroutineScope,
    private val clock: () -> Instant = Instant::now,
    private val wait: Duration = FIRST_LOOKUP_WAIT
) : RuleSource {
    /** Lookups on their way, so pages loading together share one call per site. */
    private val fetching = ConcurrentHashMap<String, Deferred<CachedRule?>>()

    override suspend fun candidatesFor(host: String): List<RuleCandidate> {
        val domain = host.lowercase()
        var kept = store.rule(domain)
        if (backend != null && (kept == null || isStale(kept))) {
            val fetch = fetch(domain, kept?.fingerprint)
            if (kept == null) kept = withTimeoutOrNull(wait.toMillis()) { fetch.await() }
        }
        return candidates(kept)
    }

    override suspend fun forStructure(host: String, fingerprint: String): List<RuleCandidate>? {
        val domain = host.lowercase()
        val kept = store.rule(domain)
        // Only a site the backend had no rule for, and wasn't asked about by structure yet.
        val unasked = kept != null && kept.json == null && kept.fingerprint == null
        if (backend == null || !unasked) return null
        val found = fetch(domain, fingerprint).await()
        return if (found?.json != null) candidates(found) else null
    }

    private fun candidates(kept: CachedRule?): List<RuleCandidate> {
        val rule = kept?.json?.let { BridgeProtocol.json.parseToJsonElement(it) as? JsonObject }
        return listOfNotNull(rule?.let(RuleCandidate::forDomain)) + builtIn
    }

    private fun isStale(kept: CachedRule) = kept.fetchedAt.plus(MAX_AGE) <= clock()

    /** Asks the backend in [scope], so the answer is kept even if the page stops waiting. */
    private fun fetch(domain: String, fingerprint: String?): Deferred<CachedRule?> = fetching.computeIfAbsent(domain) {
        scope.async {
            try {
                ask(domain, fingerprint)
            } finally {
                fetching.remove(domain)
            }
        }
    }

    /** The kept answer, or null when the backend couldn't be reached. */
    private suspend fun ask(domain: String, fingerprint: String?): CachedRule? {
        val answer = try {
            backend!!.lookup(domain, fingerprint)
        } catch (_: IOException) {
            return null
        }
        // A rule that fails verification is treated as no rule, so it isn't asked for on every page.
        val rule = (answer as? RuleAnswer.Found)?.let { verifier?.verify(it.rule) }
        val version = rule?.get("version")?.jsonPrimitive?.intOrNull ?: 0
        return CachedRule(domain, rule?.toString(), version, clock(), fingerprint).also { store.saveRule(it) }
    }

    private companion object {
        /** As long as the backend's Cache-Control lets a rule be kept. */
        val MAX_AGE: Duration = Duration.ofDays(1)

        val FIRST_LOOKUP_WAIT: Duration = Duration.ofMillis(1500)
    }
}

/** One entry of assets/rules/builtin.json. */
@Serializable
private data class BuiltInRule(val name: String, val `when`: String, val rule: JsonObject)

object BuiltInRules {
    const val ASSET = "rules/builtin.json"

    fun parse(json: String): List<RuleCandidate> =
        BridgeProtocol.json.decodeFromString(ListSerializer(BuiltInRule.serializer()), json)
            .map { RuleCandidate.builtIn(it.rule, it.`when`) }
}
