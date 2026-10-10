package com.ascon.core.data

import com.ascon.core.data.fake.FakeLibraryRepository
import com.ascon.core.model.ReadingStatus
import com.ascon.core.model.Stamped
import com.ascon.core.model.SyncRecord
import com.ascon.core.model.progressSyncId
import com.ascon.core.model.seriesSyncId
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySyncTest {
    private val start = Instant.parse("2026-10-10T08:00:00Z")

    private fun phone(at: Instant = start) =
        FakeLibraryRepository(emptyList(), emptyList(), Clock.fixed(at, ZoneOffset.UTC))

    /** A phone that found Moonlit Ferry on asura and read chapter [chapter] to page 5 of 20. */
    private suspend fun FakeLibraryRepository.readFerry(chapter: Int, title: String = "Moonlit Ferry"): String {
        val url = "https://asurascans.com/ferry/chapter-$chapter"
        val series = seriesFor(title, "asurascans.com", BigDecimal(chapter), url)
        recordPageRead(series.id, BigDecimal(chapter), page = 5, pageCount = 20, at = start, pageOffset = 0.5f)
        return series.id
    }

    @Test
    fun `a new phone gets the series, its source and the place`() = runBlocking {
        val a = phone()
        a.readFerry(12)
        val b = phone()
        b.applyPulled(a.syncRecords(since = null))

        val ferry = b.series.first().single()
        assertEquals("Moonlit Ferry", ferry.title)
        assertEquals(seriesSyncId("Moonlit Ferry"), ferry.syncId)
        assertEquals(listOf("asurascans.com"), ferry.sources.map { it.id })
        assertEquals("https://asurascans.com/ferry/chapter-12", ferry.chapterUrl(BigDecimal(12), "asurascans.com"))
        val place = ferry.progress!!
        assertEquals(BigDecimal(12), place.chapter)
        assertEquals(5, place.page)
        assertEquals(20, place.pageCount)
        assertEquals(0.5f, place.pageOffset)
        assertEquals("asurascans.com", place.sourceId)
        assertEquals(listOf(12), ferry.chapters.map { it.number.toInt() })
        assertEquals(ReadingStatus.Reading, ferry.status)
    }

    @Test
    fun `the same series found on two phones becomes one`() = runBlocking {
        val a = phone()
        a.readFerry(12)
        val b = phone(start.plusSeconds(60))
        val own = b.readFerry(3, title = "moonlit ferry!")
        b.applyPulled(a.syncRecords(since = null))

        val ferry = b.series.first().single()
        assertEquals(own, ferry.id)
        // b's place is newer, so it stays.
        assertEquals(BigDecimal(3), ferry.progress?.chapter)
    }

    @Test
    fun `a newer place from another phone replaces the older one`() = runBlocking {
        val a = phone()
        a.readFerry(3)
        val b = phone(start.plusSeconds(60))
        b.readFerry(12)
        a.applyPulled(b.syncRecords(since = null))
        assertEquals(BigDecimal(12), a.series.first().single().progress?.chapter)
    }

    @Test
    fun `only parts changed after the last push are sent`() = runBlocking {
        val a = phone()
        a.readFerry(12)
        assertEquals(emptyList<SyncRecord>(), a.syncRecords(since = start))
        assertEquals(4, a.syncRecords(since = start.minusMillis(1)).size)
    }

    @Test
    fun `a record with only some fields finds its series by its own id`() = runBlocking {
        val a = phone()
        a.readFerry(12)
        val id = progressSyncId(seriesSyncId("Moonlit Ferry"))
        val later = start.plusSeconds(30)
        val chapter = Stamped(BigDecimal(13), later)
        a.applyPulled(listOf(SyncRecord.Progress(id, null, chapter, Stamped(2, later), null, null, null)))

        val place = a.series.first().single().progress!!
        assertEquals(BigDecimal(13), place.chapter)
        assertEquals(2, place.page)
        assertEquals(20, place.pageCount)
    }

    @Test
    fun `records for a series the phone never heard of are skipped`() = runBlocking {
        val a = phone()
        val id = progressSyncId(seriesSyncId("Unknown"))
        a.applyPulled(listOf(SyncRecord.Progress(id, null, Stamped(BigDecimal.ONE, start), null, null, null, null)))
        assertTrue(a.series.first().isEmpty())
        assertNull(a.series.first().firstOrNull())
    }
}
