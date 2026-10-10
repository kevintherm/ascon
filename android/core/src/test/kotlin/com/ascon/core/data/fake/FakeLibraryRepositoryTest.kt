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
    fun `opening a later chapter keeps the place until it is read into`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        val at = clock.instant().plusSeconds(60)
        repo.recordChapterOpened("aztec-turning-of-heaven", BigDecimal(14), at)
        repo.recordPageRead("aztec-turning-of-heaven", BigDecimal(14), page = 1, pageCount = 20, at = at)

        val opened = repo.series("aztec-turning-of-heaven").first()!!
        assertEquals(BigDecimal(12), opened.progress?.chapter)
        assertEquals(34, opened.progress?.page)
        assertEquals(at, opened.lastReadAt)

        repo.recordPageRead("aztec-turning-of-heaven", BigDecimal(14), page = 2, pageCount = 20, at = at)
        val aztec = repo.series("aztec-turning-of-heaven").first()!!
        assertEquals(BigDecimal(14), aztec.progress?.chapter)
        assertEquals(2, aztec.progress?.page)
        assertEquals("mangaplus", aztec.progress?.sourceId)
        assertEquals((1..13).toList(), aztec.chapters.filter { it.read }.map { it.number.toInt() })
    }

    @Test
    fun `a chapter opened by mistake gives way to the chapter read`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        val saga = "tidewater-saga"
        repo.recordPageRead(saga, BigDecimal(150), page = 1, pageCount = 30, at = clock.instant())
        assertEquals(BigDecimal(150), repo.series(saga).first()?.progress?.chapter)

        repo.recordChapterOpened(saga, BigDecimal.ONE, clock.instant())
        repo.recordPageRead(saga, BigDecimal.ONE, page = 12, pageCount = 25, at = clock.instant())

        val series = repo.series(saga).first()!!
        assertEquals(BigDecimal.ONE, series.progress?.chapter)
        assertEquals(12, series.progress?.page)
        assertEquals(emptyList<Int>(), series.chapters.filter { it.read }.map { it.number.toInt() })
    }

    @Test
    fun `a chapter skipped to and read into reads the ones before it`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        val saga = "tidewater-saga"
        repo.recordChapterOpened(saga, BigDecimal(3), clock.instant())
        repo.recordPageRead(saga, BigDecimal(3), page = 4, pageCount = 25, at = clock.instant())
        repo.recordChapterOpened(saga, BigDecimal.ONE, clock.instant())

        val series = repo.series(saga).first()!!
        assertEquals(BigDecimal(3), series.progress?.chapter)
        assertEquals(listOf(1, 2), series.chapters.filter { it.read }.map { it.number.toInt() })
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
    fun `reading into an older chapter moves the place back and keeps read marks`() = runBlocking {
        val repo = FakeLibraryRepository(FakeLibrary.series(clock))
        repo.recordPageRead("aztec-turning-of-heaven", BigDecimal(3), page = 2, pageCount = 20, at = clock.instant())
        val aztec = repo.series("aztec-turning-of-heaven").first()!!
        assertEquals(BigDecimal(3), aztec.progress?.chapter)
        assertEquals(2, aztec.progress?.page)
        assertEquals((1..11).toList(), aztec.chapters.filter { it.read }.map { it.number.toInt() })
    }

    @Test
    fun `a chapter read into by mistake gives way to the one read next`() = runBlocking {
        val repo = FakeLibraryRepository(emptyList(), emptyList())
        val saga = repo.seriesFor("IRL Quest", "site.example", BigDecimal(211), "https://site.example/quest/211").id
        repo.recordPageRead(saga, BigDecimal(211), page = 15, pageCount = 40, at = clock.instant())
        for (chapter in 1..3) {
            repo.recordPageRead(saga, BigDecimal(chapter), page = 25, pageCount = 25, at = clock.instant())
        }
        val series = repo.series(saga).first()!!
        assertEquals(BigDecimal(3), series.progress?.chapter)
        assertEquals(listOf(1, 2, 3), series.chapters.filter { it.read }.map { it.number.toInt() })
    }
}
