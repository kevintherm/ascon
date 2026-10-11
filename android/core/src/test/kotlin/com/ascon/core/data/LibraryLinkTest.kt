package com.ascon.core.data

import com.ascon.core.data.fake.FakeLibraryRepository
import com.ascon.core.model.SeriesMetadata
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryLinkTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneOffset.UTC)
    private fun library() = FakeLibraryRepository(emptyList(), emptyList(), clock)

    private val solo = SeriesMetadata(
        ref = "anilist:105398",
        title = "Na Honjaman Level Up",
        altTitles = listOf("Solo Leveling", "나 혼자만 레벨업"),
        format = "manhwa",
        otherRef = "mangaupdates:15180124327"
    )

    private suspend fun FakeLibraryRepository.read(title: String, host: String, link: SeriesMetadata? = null) =
        seriesFor(title, host, BigDecimal.ONE, "https://$host/solo/chapter-1", link)

    @Test
    fun `a linked new series keeps the site's title and learns the others`() = runBlocking {
        val series = library().read("Solo Leveling", "asura.example", solo)

        assertEquals("Solo Leveling", series.title)
        assertEquals(listOf("Na Honjaman Level Up", "나 혼자만 레벨업"), series.altTitles)
        assertEquals(105398L, series.aniListId)
        assertEquals(15180124327L, series.mangaUpdatesId)
        assertTrue(series.linkedToAniList)
    }

    @Test
    fun `another title of a linked series joins it and is kept for next time`() = runBlocking {
        val library = library()
        val first = library.read("Solo Leveling", "asura.example", solo)
        // A site calling it something AniList doesn't list, linked to the same entry.
        val second = library.read("I Level Up Alone", "other.example", solo)

        assertEquals(first.id, second.id)
        assertEquals(listOf("asura.example", "other.example"), second.sources.map { it.id })
        // The next chapter from that site matches by title, with no search.
        val third = library.read("i level up alone", "other.example")
        assertEquals(first.id, third.id)
        assertEquals(1, library.series.first().size)
    }

    @Test
    fun `a title already in the library ignores the link`() = runBlocking {
        val library = library()
        val unlinked = library.read("Solo Leveling", "asura.example")
        val again = library.read("Solo Leveling", "asura.example", solo.copy(ref = "anilist:1", otherRef = null))

        assertEquals(unlinked.id, again.id)
        assertNull(again.aniListId)
        assertFalse(again.linkedToAniList)
    }
}
