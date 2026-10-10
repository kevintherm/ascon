package com.ascon.engine.detection

import com.ascon.core.data.CachedRule
import com.ascon.core.data.RuleHealthCount
import com.ascon.core.data.fake.FakeRuleStore
import java.io.File
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleLookupTest {
    private val builtIn = BuiltInRules.parse(File("src/main/assets/rules/builtin.json").readText())

    /** The glasslight rule, signed by the Go signer with the development key. */
    private val fixture = Json.parseToJsonElement(File("../../../contracts/fixtures/signed-rule.json").readText())
        as JsonObject
    private fun field(name: String) = fixture[name]!!.jsonPrimitive.content
    private val signed = SignedRule(field("payload"), field("signature"), field("keyId"))
    private val verifier = RuleVerifier(mapOf(field("keyId") to field("publicKey")))
    private val glasslight = verifier.verify(signed)!!

    private var now = Instant.parse("2026-10-10T12:00:00Z")
    private val store = FakeRuleStore()

    /** Answers lookups from [answers], and records each call. */
    private inner class FakeBackend : RuleBackend {
        val calls = mutableListOf<Pair<String, String?>>()
        var answer: suspend (String, String?) -> RuleAnswer = { _, _ -> RuleAnswer.Missing }

        override suspend fun lookup(domain: String, fingerprint: String?): RuleAnswer {
            calls += domain to fingerprint
            return answer(domain, fingerprint)
        }

        override suspend fun sendHealth(counts: List<RuleHealthCount>) = Unit
    }

    private val backend = FakeBackend()

    private fun TestScope.lookup(backend: RuleBackend? = this@RuleLookupTest.backend) =
        RuleLookup(store, builtIn, backend, verifier, backgroundScope, clock = { now })

    @Test
    fun `without the backend, the cached rule comes before the theme rules`() = runTest {
        store.rules["tidepool.example"] = CachedRule("tidepool.example", """{"domain":"tidepool.example"}""", 1, now)
        val lookup = lookup(backend = null)

        val candidates = lookup.candidatesFor("Tidepool.example")
        assertEquals(DetectionSource.Rule, candidates.first().via)
        assertEquals(builtIn, candidates.drop(1))
        assertEquals(builtIn, lookup.candidatesFor("other.example"))
    }

    @Test
    fun `a site seen for the first time asks the backend and keeps its verified rule`() = runTest {
        backend.answer = { _, _ -> RuleAnswer.Found(signed) }
        val lookup = lookup()

        assertEquals(listOf(RuleCandidate.forDomain(glasslight)) + builtIn, lookup.candidatesFor("glasslight.example"))
        assertEquals(listOf("glasslight.example" to null), backend.calls)
        assertEquals(1, store.rules["glasslight.example"]!!.version)

        // The next page uses the kept rule without asking.
        lookup.candidatesFor("glasslight.example")
        assertEquals(1, backend.calls.size)
    }

    @Test
    fun `a site the backend has no rule for isn't asked about again for a day`() = runTest {
        val lookup = lookup()
        assertEquals(builtIn, lookup.candidatesFor("plain.example"))
        now = now.plus(Duration.ofHours(23))
        assertEquals(builtIn, lookup.candidatesFor("plain.example"))
        assertEquals(1, backend.calls.size)

        now = now.plus(Duration.ofHours(2))
        lookup.candidatesFor("plain.example")
        runCurrent()
        assertEquals(2, backend.calls.size)
    }

    @Test
    fun `a rule that fails its signature is never used`() = runTest {
        backend.answer = { _, _ -> RuleAnswer.Found(signed.copy(keyId = "unknown")) }
        val lookup = lookup()

        assertEquals(builtIn, lookup.candidatesFor("glasslight.example"))
        assertNull(store.rules["glasslight.example"]!!.json)
    }

    @Test
    fun `a slow backend doesn't hold the page, and its answer is kept for the next one`() = runTest {
        val reply = CompletableDeferred<RuleAnswer>()
        backend.answer = { _, _ -> reply.await() }
        val lookup = lookup()

        assertEquals(builtIn, lookup.candidatesFor("glasslight.example"))
        reply.complete(RuleAnswer.Found(signed))
        runCurrent()
        assertEquals(RuleCandidate.forDomain(glasslight), lookup.candidatesFor("glasslight.example").first())
        assertEquals(1, backend.calls.size)
    }

    @Test
    fun `an unreachable backend is asked again on the next page`() = runTest {
        backend.answer = { _, _ -> throw IOException("offline") }
        val lookup = lookup()

        assertEquals(builtIn, lookup.candidatesFor("glasslight.example"))
        assertNull(store.rules["glasslight.example"])
        lookup.candidatesFor("glasslight.example")
        assertEquals(2, backend.calls.size)
    }

    @Test
    fun `a day-old rule is used at once and refreshed in the background`() = runTest {
        store.rules["glasslight.example"] = CachedRule("glasslight.example", """{"version":0}""", 0, now)
        now = now.plus(Duration.ofHours(25))
        backend.answer = { _, _ -> RuleAnswer.Found(signed) }
        val lookup = lookup()

        assertEquals("""{"version":0}""", lookup.candidatesFor("glasslight.example").first().rule.toString())
        runCurrent()
        assertEquals(1, store.rules["glasslight.example"]!!.version)
    }

    @Test
    fun `a mirror without its own rule borrows one by page structure, once`() = runTest {
        backend.answer = { _, fingerprint -> if (fingerprint == null) RuleAnswer.Missing else RuleAnswer.Found(signed) }
        val lookup = lookup()
        lookup.candidatesFor("mirror.example")

        val found = lookup.forStructure("mirror.example", "00ff00ff00ff00ff")
        assertEquals(listOf(RuleCandidate.forDomain(glasslight)) + builtIn, found)
        assertEquals("00ff00ff00ff00ff", store.rules["mirror.example"]!!.fingerprint)
        assertNull(lookup.forStructure("mirror.example", "00ff00ff00ff00ff"))

        // Refreshing asks with the same fingerprint, so the borrowed rule stays.
        now = now.plus(Duration.ofDays(2))
        lookup.candidatesFor("mirror.example")
        runCurrent()
        assertEquals("mirror.example" to "00ff00ff00ff00ff", backend.calls.last())
        assertTrue(store.rules["mirror.example"]!!.json != null)
    }

    @Test
    fun `a site with its own rule, or one not asked about yet, isn't asked by structure`() = runTest {
        backend.answer = { _, _ -> RuleAnswer.Found(signed) }
        val lookup = lookup()
        assertNull(lookup.forStructure("glasslight.example", "00ff00ff00ff00ff"))
        lookup.candidatesFor("glasslight.example")
        assertNull(lookup.forStructure("glasslight.example", "00ff00ff00ff00ff"))
        assertEquals(1, backend.calls.size)
    }
}
