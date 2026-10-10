package com.ascon.core.data.room

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.data.CachedRule
import com.ascon.core.data.RuleHealthCount
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomRuleStoreTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = AsconDatabase.open(context, "rules.db")
    private val store = RoomRuleStore(db)
    private val at = Instant.parse("2026-10-10T12:00:00Z")

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase("rules.db")
    }

    @Test
    fun `a site's rule, or the backend having none, is kept and replaced`() = runBlocking {
        assertNull(store.rule("a.example"))
        store.saveRule(CachedRule("a.example", json = null, version = 0, fetchedAt = at))
        assertEquals(CachedRule("a.example", null, 0, at), store.rule("a.example"))

        val found =
            CachedRule("a.example", """{"version":2}""", 2, at.plusSeconds(60), fingerprint = "00ff00ff00ff00ff")
        store.saveRule(found)
        assertEquals(found, store.rule("a.example"))
    }

    @Test
    fun `health counts add up per rule version until sent`() = runBlocking {
        store.addHealth(RuleHealthCount("a.example", 2, successes = 1, emptyResults = 0, backwardJumps = 0))
        store.addHealth(RuleHealthCount("a.example", 2, successes = 2, emptyResults = 1, backwardJumps = 0))
        store.addHealth(RuleHealthCount("b.example", 1, successes = 0, emptyResults = 0, backwardJumps = 1))
        val counts = store.health().sortedBy { it.domain }
        assertEquals(
            listOf(RuleHealthCount("a.example", 2, 3, 1, 0), RuleHealthCount("b.example", 1, 0, 0, 1)),
            counts
        )

        // A count that arrives while the batch is being sent survives it.
        store.addHealth(RuleHealthCount("a.example", 2, successes = 1, emptyResults = 0, backwardJumps = 0))
        store.removeHealth(counts)
        assertEquals(listOf(RuleHealthCount("a.example", 2, 1, 0, 0)), store.health())
    }
}
