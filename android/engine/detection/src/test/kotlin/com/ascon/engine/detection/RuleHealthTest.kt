package com.ascon.engine.detection

import com.ascon.core.data.CachedRule
import com.ascon.core.data.RuleHealthCount
import com.ascon.core.data.fake.FakeRuleStore
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleHealthTest {
    private val store = FakeRuleStore().apply {
        rules["t.example"] = CachedRule("t.example", "{}", 3, Instant.EPOCH)
    }
    private val health = RuleHealth(store)

    private fun chapter(n: Int, images: Int = 2, source: DetectionSource = DetectionSource.Rule, next: Int? = n + 1) =
        Detection.ChapterPage(
            url = "https://t.example/c/$n",
            source = source,
            seriesSlug = "s",
            title = "Title",
            chapterLabel = "Chapter $n",
            chapter = BigDecimal(n),
            images = (1..images).map { "https://t.example/i/$it.jpg" },
            next = next?.let { "https://t.example/c/$it" },
            previous = null
        )

    private suspend fun counts() = store.health().single()

    @Test
    fun `each page counts once, as a success if the rule ever found it, once the next page comes`() = runTest {
        health.record("t.example", chapter(1, images = 0))
        health.record("t.example", chapter(1))
        health.record("t.example", chapter(2, images = 0))
        health.record("t.example", Detection.None("https://t.example/"))

        assertEquals(RuleHealthCount("t.example", 3, successes = 1, emptyResults = 1, backwardJumps = 0), counts())
    }

    @Test
    fun `following the rule's next link to a lower chapter is a backward jump`() = runTest {
        health.record("t.example", chapter(5, next = 4).copy(url = "https://t.example/c/5"))
        health.record("t.example", chapter(4).copy(url = "https://t.example/c/4"))
        health.record("t.example", chapter(9))
        // Opening a lower chapter some other way isn't the rule's fault.
        health.record("t.example", chapter(2))
        health.record("t.example", Detection.None("https://t.example/"))

        assertEquals(RuleHealthCount("t.example", 3, successes = 4, emptyResults = 0, backwardJumps = 1), counts())
    }

    @Test
    fun `only the site's own rule from the backend is counted`() = runTest {
        health.record("t.example", chapter(1, source = DetectionSource.BuiltIn))
        health.record("t.example", chapter(2, source = DetectionSource.Heuristic))
        health.record("t.example", Detection.None("https://t.example/"))
        health.record("u.example", chapter(1))
        health.record("u.example", Detection.None("https://u.example/"))

        assertEquals(emptyList<RuleHealthCount>(), store.health())
    }
}
