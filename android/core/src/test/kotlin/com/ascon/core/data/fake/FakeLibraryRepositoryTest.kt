package com.ascon.core.data.fake

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
}
