package com.ascon.feature.library.home

import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.model.AccountState
import com.ascon.core.model.ChapterLink
import com.ascon.core.model.ReadingStatus
import com.ascon.feature.library.FixedClock
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    private val series = FakeLibrary.series(FixedClock)

    @Test
    fun `hero is the series read most recently`() {
        val state = homeUiState(series, FakeLibrary.sites, AccountState.SignedOut)
        val hero = state.continueReading!!
        assertEquals("aztec-turning-of-heaven", hero.seriesId)
        assertEquals("12", hero.chapter)
        assertEquals("MANGA Plus", hero.sourceName)
        assertEquals(34f / 58f, hero.fraction, 0.0001f)
    }

    @Test
    fun `resume opens the chapter in progress once a page of it was seen`() {
        assertNull(homeUiState(series, emptyList(), AccountState.SignedOut).continueReading?.url)

        val linked = series.map { s ->
            if (s.id != "aztec-turning-of-heaven") {
                s
            } else {
                s.copy(
                    sources = s.sources.map {
                        it.copy(lastOpened = ChapterLink("https://plus.example/v/9", BigDecimal(9)))
                    }
                )
            }
        }
        assertEquals(
            "https://plus.example/v/12",
            homeUiState(linked, emptyList(), AccountState.SignedOut).continueReading?.url
        )
    }

    @Test
    fun `new chapters lists new or downloaded next chapters, newest first, without the hero`() {
        val rows = homeUiState(series, FakeLibrary.sites, AccountState.SignedOut).newChapters
        assertEquals(listOf("last-lighthouse-keeper", "salt-and-iron-kitchen", "paper-moth"), rows.map { it.seriesId })
        assertEquals(NewChapterDetail.Sources(2), rows[0].detail)
        assertEquals("112" to "113", rows[1].firstChapter to rows[1].lastChapter)
        assertEquals(NewChapterDetail.Downloaded, rows[2].detail)
        assertTrue(rows[0].hasNew && !rows[2].hasNew)
    }

    @Test
    fun `status counts follow chip order`() {
        val counts = homeUiState(series, emptyList(), AccountState.SignedOut).statusCounts
        assertEquals(ReadingStatus.entries.toList(), counts.map { it.first })
        assertEquals(series.size, counts.sumOf { it.second })
    }

    @Test
    fun `profile shows the initial only when signed in`() {
        val signedIn = AccountState.SignedIn("kevin", premium = false, lastSyncedAt = null)
        assertEquals("K", homeUiState(series, emptyList(), signedIn).profileInitial)
        assertNull(homeUiState(series, emptyList(), AccountState.SignedOut).profileInitial)
    }

    @Test
    fun `an empty library has no hero and no rows`() {
        val state = homeUiState(emptyList(), emptyList(), AccountState.SignedOut)
        assertNull(state.continueReading)
        assertTrue(state.newChapters.isEmpty())
    }
}
