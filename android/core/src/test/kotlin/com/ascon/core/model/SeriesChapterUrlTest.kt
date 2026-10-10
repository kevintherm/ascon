package com.ascon.core.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesChapterUrlTest {
    private fun source(id: String, last: Int, link: ChapterLink?) =
        Source(id, id, official = false, BigDecimal.ONE, BigDecimal(last), link)

    private fun series(vararg sources: Source) = Series(
        id = "aztec",
        title = "Aztec",
        altTitles = emptyList(),
        cover = Cover.Placeholder(0, 0, 0),
        status = ReadingStatus.Reading,
        linkedToAniList = false,
        sources = sources.toList(),
        chapters = emptyList(),
        progress = null,
        lastReadAt = null
    )

    private val one = source("one.example", 12, ChapterLink("https://one.example/aztec/chapter-12", BigDecimal(12)))
    private val two = source("two.example", 15, ChapterLink("https://two.example/read/aztec/14", BigDecimal(14)))

    @Test
    fun `uses the source asked for`() {
        assertEquals("https://two.example/read/aztec/9", series(one, two).chapterUrl(BigDecimal(9), "two.example"))
    }

    @Test
    fun `falls back to another source that has the chapter`() {
        val unlinked = source("three.example", 20, link = null)
        assertEquals(
            "https://one.example/aztec/chapter-3",
            series(unlinked, one).chapterUrl(BigDecimal(3), "three.example")
        )
        assertEquals("https://two.example/read/aztec/15", series(one, two).chapterUrl(BigDecimal(15), "one.example"))
    }

    @Test
    fun `a chapter opened at an id address opens there, before a swapped number`() {
        val flame = source("ids.example", 2, ChapterLink("https://ids.example/series/57/aaaa", BigDecimal.ONE))
        val opened =
            Chapter(
                BigDecimal(2),
                null,
                read = false,
                openedUrl = "https://ids.example/series/57/bbbb",
                openedOnSourceId = "ids.example"
            )
        val withIds = series(flame, two).copy(chapters = listOf(opened))
        assertEquals("https://ids.example/series/57/bbbb", withIds.chapterUrl(BigDecimal(2), "ids.example"))
        // Asked for another source, a swapped number there comes first.
        assertEquals("https://two.example/read/aztec/2", withIds.chapterUrl(BigDecimal(2), "two.example"))
        assertEquals(
            "https://ids.example/series/57/bbbb",
            series(flame).copy(chapters = listOf(opened)).chapterUrl(BigDecimal(2), null)
        )
    }

    @Test
    fun `gives nothing when no source has a page to start from`() {
        assertNull(series(source("three.example", 20, link = null)).chapterUrl(BigDecimal(3), null))
    }
}
