package com.ascon.engine.detection

import com.ascon.core.data.CachedRule
import com.ascon.core.data.fake.FakeRuleStore
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleGenerationTest {
    /** The glasslight rule, signed by the Go signer with the development key. */
    private val fixture = Json.parseToJsonElement(File("../../../contracts/fixtures/signed-rule.json").readText())
        as JsonObject
    private fun field(name: String) = fixture[name]!!.jsonPrimitive.content
    private val signed = SignedRule(field("payload"), field("signature"), field("keyId"))
    private val verifier = RuleVerifier(mapOf(field("keyId") to field("publicKey")))

    private var now = Instant.parse("2026-10-10T12:00:00Z")
    private val store = FakeRuleStore()
    private var token: String? = "acc-token"
    private val site = "inkwell.example"

    private class FakeGenerationBackend : GenerationBackend {
        val requests = mutableListOf<Triple<String, String?, List<PageSnapshot>>>()
        val polls = mutableListOf<String>()
        val answers = ArrayDeque<CandidateState>()
        var fail = false

        override suspend fun request(
            domain: String,
            fingerprint: String?,
            samples: List<PageSnapshot>
        ): CandidateState {
            if (fail) throw IOException("offline")
            requests += Triple(domain, fingerprint, samples)
            return answers.removeFirst()
        }

        override suspend fun candidate(id: String): CandidateState {
            polls += id
            return answers.removeFirst()
        }
    }

    private val backend = FakeGenerationBackend()
    private val generation = RuleGeneration(store, backend, verifier, { token }, clock = { now })

    private fun page(n: Int) = PageSnapshot("https://$site/sea-glass/chapter-$n", "<p>$n</p>")

    private suspend fun offer(n: Int, host: String = site) =
        generation.offer(host, "00ff00ff00ff00ff", BigDecimal(n), page(n))

    /** The backend was asked and had no rule for the site. */
    private fun missing(domain: String = site) {
        store.rules[domain] = CachedRule(domain, null, 0, now)
    }

    @Test
    fun `only a site the backend had no rule for, with an account, wants snapshots`() = runTest {
        assertFalse("never looked up", generation.wants(site))
        missing()
        assertTrue(generation.wants("Inkwell.example"))
        token = null
        assertFalse("no account", generation.wants(site))
        token = "acc-token"
        store.rules[site] = CachedRule(site, """{"domain":"$site"}""", 1, now)
        assertFalse("has a rule", generation.wants(site))
    }

    @Test
    fun `two different chapters are sent together, and the accepted rule is kept`() = runTest {
        missing()
        backend.answers += CandidateState.Pending("c-1", retryAfterSeconds = 5)
        backend.answers += CandidateState.Pending("c-1", retryAfterSeconds = 5)
        backend.answers += CandidateState.Accepted(signed)

        assertFalse(offer(4))
        assertFalse("the same chapter again", offer(4))
        assertEquals(0, backend.requests.size)
        assertTrue(offer(5))

        assertEquals(listOf(Triple(site, "00ff00ff00ff00ff", listOf(page(4), page(5)))), backend.requests)
        assertEquals(listOf("c-1", "c-1"), backend.polls)
        assertEquals(10_000L, currentTime)
        val kept = store.rules[site]!!
        assertEquals(verifier.verify(signed).toString(), kept.json)
        assertEquals(now, kept.fetchedAt)
    }

    @Test
    fun `a rejected site is not tried again in this run`() = runTest {
        missing()
        backend.answers += CandidateState.Rejected("no title on sample 1")

        offer(1)
        assertFalse(offer(2))
        assertFalse(generation.wants(site))
        assertFalse(offer(3))
        assertFalse(offer(4))
        assertEquals(1, backend.requests.size)
        assertNull(store.rules[site]!!.json)
    }

    @Test
    fun `an unreachable backend lets a later chapter try again`() = runTest {
        missing()
        backend.fail = true
        offer(1)
        assertFalse(offer(2))

        backend.fail = false
        backend.answers += CandidateState.Accepted(signed)
        assertTrue(generation.wants(site))
        assertTrue(offer(3))
    }

    @Test
    fun `out of quota stops every site until the quota resets`() = runTest {
        missing()
        missing("other.example")
        backend.answers += CandidateState.OutOfQuota(Instant.parse("2026-11-01T00:00:00Z"))

        offer(1)
        offer(2)
        assertFalse(generation.wants("other.example"))
        assertFalse(generation.wants(site))

        now = Instant.parse("2026-11-01T00:00:00Z")
        assertTrue(generation.wants(site))
    }

    @Test
    fun `a rule made meanwhile marks the kept answer stale so it is looked up`() = runTest {
        missing()
        backend.answers += CandidateState.RuleExists

        offer(1)
        assertFalse(offer(2))
        assertEquals(Instant.EPOCH, store.rules[site]!!.fetchedAt)
    }

    @Test
    fun `a rule that fails its signature is not kept`() = runTest {
        missing()
        backend.answers += CandidateState.Accepted(signed.copy(signature = signed.signature.reversed()))

        offer(1)
        assertFalse(offer(2))
        assertNull(store.rules[site]!!.json)
    }
}
