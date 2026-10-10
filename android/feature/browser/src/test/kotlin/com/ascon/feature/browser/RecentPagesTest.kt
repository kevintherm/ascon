package com.ascon.feature.browser

import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.model.Chapter
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPagesTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneOffset.UTC)
    private val library = FakeLibrary.series(clock)

    private fun opened(number: Int, url: String?, hoursAgo: Long?, source: String? = null) = Chapter(
        number = BigDecimal(number),
        publishedOn = null,
        read = false,
        openedUrl = url,
        openedOnSourceId = source,
        updatedAt = hoursAgo?.let { clock.instant().minusSeconds(it * 3600) }
    )

    @Test
    fun `the newest opened chapter pages come first, named by the user's sites`() {
        val a = library[0].copy(
            chapters = listOf(
                opened(1, "https://mangadex.org/chapter/a-1", hoursAgo = 30, source = "mangadex.org"),
                opened(2, "https://unknown.example/a/2", hoursAgo = 1),
                // Never opened, or opened before sync stamps existed: not listed.
                opened(3, null, hoursAgo = 0),
                opened(4, "https://mangadex.org/chapter/a-4", hoursAgo = null)
            )
        )
        val pages = recentPages(listOf(a), FakeLibrary.sites)

        assertEquals(listOf(BigDecimal(2), BigDecimal(1)), pages.map { it.chapter })
        assertEquals(listOf("unknown.example", "MangaDex"), pages.map { it.site })
        assertEquals(listOf("UN", "MD"), pages.map { it.monogram })
        assertEquals(a.title, pages.first().title)
    }

    @Test
    fun `only the last five are listed`() {
        val a = library[0].copy(chapters = (1..8).map { opened(it, "https://x.example/c/$it", hoursAgo = 10L - it) })
        assertEquals((8 downTo 4).map(::BigDecimal), recentPages(listOf(a), emptyList()).map { it.chapter })
    }
}
