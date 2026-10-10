package com.ascon.core.data.fake

import com.ascon.core.model.ReadingStatus
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeLibraryRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `selecting a source moves progress to it`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        repo.selectSource("aztec-turning-of-heaven", "mangafire")
        assertEquals("mangafire", repo.series("aztec-turning-of-heaven").first()?.progress?.sourceId)
    }

    @Test
    fun `an unknown source is ignored`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        repo.selectSource("aztec-turning-of-heaven", "nowhere")
        assertEquals("mangaplus", repo.series("aztec-turning-of-heaven").first()?.progress?.sourceId)
    }

    @Test
    fun `up next is the unread chapters after the one in progress`() {
        val aztec = FakeLibrary.series(clock).first { it.id == "aztec-turning-of-heaven" }
        assertEquals(listOf(13, 14), aztec.upNext.map { it.number.toInt() })
        assertEquals(2, aztec.newChapterCount)
    }

    @Test
    fun `opening a chapter moves progress and reads the ones before it`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        val at = clock.instant().plusSeconds(60)
        repo.recordChapterOpened("aztec-turning-of-heaven", BigDecimal(14), at)

        val aztec = repo.series("aztec-turning-of-heaven").first()!!
        assertEquals(BigDecimal(14), aztec.progress?.chapter)
        assertEquals(0, aztec.progress?.page)
        assertEquals("mangaplus", aztec.progress?.sourceId)
        assertEquals((1..13).toList(), aztec.chapters.filter { it.read }.map { it.number.toInt() })
        assertEquals(at, aztec.lastReadAt)
    }

    @Test
    fun `opening an older chapter keeps progress`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        repo.recordChapterOpened("aztec-turning-of-heaven", BigDecimal(3), clock.instant())
        assertEquals(BigDecimal(12), repo.series("aztec-turning-of-heaven").first()?.progress?.chapter)
    }

    @Test
    fun `opening the chapter in progress keeps the page`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        repo.recordChapterOpened("aztec-turning-of-heaven", BigDecimal("12.0"), clock.instant())
        assertEquals(34, repo.series("aztec-turning-of-heaven").first()?.progress?.page)
    }

    @Test
    fun `opening a planned series starts reading it`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        repo.recordChapterOpened("tidewater-saga", BigDecimal.ONE, clock.instant())
        val saga = repo.series("tidewater-saga").first()!!
        assertEquals(ReadingStatus.Reading, saga.status)
        assertEquals(BigDecimal.ONE, saga.progress?.chapter)
    }

    @Test
    fun `opening a chapter the library did not know adds it`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        repo.recordChapterOpened("aztec-turning-of-heaven", BigDecimal(15), clock.instant())
        val aztec = repo.series("aztec-turning-of-heaven").first()!!
        assertEquals((1..15).toList(), aztec.chapters.map { it.number.toInt() })
        assertEquals(BigDecimal(15), aztec.latestChapter?.number)
        assertEquals(false, aztec.latestChapter?.read)
    }

    @Test
    fun `reading a page saves the page and the page count`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        repo.recordPageRead(
            "aztec-turning-of-heaven",
            BigDecimal(13),
            page = 5,
            pageCount = 40,
            at = clock.instant(),
            pageOffset = 0.25f
        )
        val progress = repo.series("aztec-turning-of-heaven").first()?.progress
        assertEquals(BigDecimal(13), progress?.chapter)
        assertEquals(5, progress?.page)
        assertEquals(40, progress?.pageCount)
        assertEquals(0.25f, progress?.pageOffset)
    }

    @Test
    fun `reading the last page marks the chapter read`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        repo.recordPageRead("aztec-turning-of-heaven", BigDecimal(13), page = 40, pageCount = 40, at = clock.instant())
        val aztec = repo.series("aztec-turning-of-heaven").first()!!
        assertEquals(true, aztec.chapters.first { it.number.toInt() == 13 }.read)
    }

    @Test
    fun `reading a page of an older chapter keeps progress`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        repo.recordPageRead("aztec-turning-of-heaven", BigDecimal(3), page = 2, pageCount = 20, at = clock.instant())
        val progress = repo.series("aztec-turning-of-heaven").first()?.progress
        assertEquals(BigDecimal(12), progress?.chapter)
        assertEquals(34, progress?.page)
    }
}
