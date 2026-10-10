package com.ascon.core.data.room

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.model.ChapterLink
import com.ascon.core.model.Series
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomLibraryRepositoryTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val seed = RoomLibraryRepository.Seed(FakeLibrary.series(clock), FakeLibrary.sites)
    private val opened = mutableListOf<AsconDatabase>()
    private val aztec = "aztec-turning-of-heaven"

    private fun repo(seed: RoomLibraryRepository.Seed? = this.seed): RoomLibraryRepository {
        val db = AsconDatabase.open(context, "test.db").also { opened += it }
        var n = 0
        return RoomLibraryRepository(db, seed, newId = { "new-${++n}" })
    }

    private fun closeAll() = opened.forEach { it.close() }

    @After
    fun tearDown() {
        closeAll()
        context.deleteDatabase("test.db")
    }

    private suspend fun RoomLibraryRepository.aztec(): Series = series(aztec).first()!!

    @Test
    fun `the seed comes back exactly as it went in`() = runBlocking {
        assertEquals(seed.series.sortedBy { it.id }, repo().series.first().sortedBy { it.id })
        assertEquals(seed.sites, repo().sites.first())
    }

    @Test
    fun `progress survives reopening the database`() = runBlocking {
        repo().recordPageRead(aztec, BigDecimal(13), page = 5, pageCount = 40, at = clock.instant(), pageOffset = 0.62f)
        closeAll()

        val progress = repo(seed = null).aztec().progress!!
        assertEquals(BigDecimal(13), progress.chapter)
        assertEquals(5, progress.page)
        assertEquals(40, progress.pageCount)
        assertEquals(0.62f, progress.pageOffset)
    }

    @Test
    fun `the seed fills an empty library once`() = runBlocking {
        repo().recordPageRead(aztec, BigDecimal(14), page = 3, pageCount = 20, at = clock.instant())
        closeAll()
        assertEquals(BigDecimal(14), repo().aztec().progress?.chapter)
    }

    @Test
    fun `without a seed the library starts empty`() = runBlocking {
        assertEquals(emptyList<Series>(), repo(seed = null).series.first())
        assertEquals(emptyList<Any>(), repo(seed = null).sites.first())
    }

    @Test
    fun `opening, reading and switching sources follow the library rules`() = runBlocking {
        val repo = repo()
        repo.recordChapterOpened(aztec, BigDecimal(14), clock.instant())
        assertEquals(BigDecimal(12), repo.aztec().progress?.chapter)

        repo.recordPageRead(aztec, BigDecimal(14), page = 20, pageCount = 20, at = clock.instant())
        assertEquals((1..14).toList(), repo.aztec().chapters.filter { it.read }.map { it.number.toInt() })

        repo.selectSource(aztec, "mangafire")
        assertEquals("mangafire", repo.aztec().progress?.sourceId)
    }

    @Test
    fun `12 and 12_0 are the same chapter`() = runBlocking {
        val repo = repo()
        repo.recordChapterOpened(aztec, BigDecimal("12.0"), clock.instant())
        assertEquals(34, repo.aztec().progress?.page)
        assertEquals(14, repo.aztec().chapters.size)
    }

    @Test
    fun `an unknown title becomes a new series read on the site`() = runBlocking {
        val repo = repo(seed = null)
        val found = repo.seriesFor(
            "Moonlit Ferry",
            "asurascans.com",
            BigDecimal(203),
            "https://asurascans.com/ferry/203"
        )
        assertEquals("new-1", found.id)
        assertEquals(listOf("asurascans.com"), found.sources.map { it.id })
        assertEquals(BigDecimal(203), found.sources.single().lastChapter)

        repo.recordChapterOpened(found.id, BigDecimal(203), clock.instant())
        val again = repo.seriesFor(
            "moonlit ferry!",
            "asurascans.com",
            BigDecimal(204),
            "https://asurascans.com/ferry/204"
        )
        assertEquals("new-1", again.id)
        assertEquals(BigDecimal(204), again.sources.single().lastChapter)
        assertEquals(1, repo.series.first().size)
        assertEquals("asurascans.com", repo.series(found.id).first()?.progress?.sourceId)
    }

    @Test
    fun `the chapter page last opened on a site is kept, so its chapters can open again`() = runBlocking {
        val repo = repo(seed = null)
        val found = repo.seriesFor(
            "Moonlit Ferry",
            "asurascans.com",
            BigDecimal(203),
            "https://asurascans.com/ferry/203"
        )
        repo.seriesFor("Moonlit Ferry", "asurascans.com", BigDecimal(204), "https://asurascans.com/ferry/204")
        closeAll()

        val saved = repo(seed = null).series(found.id).first()!!
        assertEquals(
            ChapterLink("https://asurascans.com/ferry/204", BigDecimal(204)),
            saved.sources.single().lastOpened
        )
        assertEquals("https://asurascans.com/ferry/150", saved.chapterUrl(BigDecimal(150), "asurascans.com"))
    }

    @Test
    fun `each chapter keeps the page it was opened at, for sites with ids in their addresses`() = runBlocking {
        val repo = repo(seed = null)
        val found = repo.seriesFor("Lantern Code", "ids.example", BigDecimal(2), "https://ids.example/series/41/bbbb")
        repo.seriesFor("Lantern Code", "ids.example", BigDecimal.ONE, "https://ids.example/series/41/aaaa")
        closeAll()

        val saved = repo(seed = null).series(found.id).first()!!
        assertEquals("https://ids.example/series/41/bbbb", saved.chapterUrl(BigDecimal(2), "ids.example"))
        assertEquals("https://ids.example/series/41/aaaa", saved.chapterUrl(BigDecimal.ONE, "ids.example"))
        assertEquals(null, saved.chapterUrl(BigDecimal(3), "ids.example"))
    }

    @Test
    fun `a known series read on a new site gains it as a source`() = runBlocking {
        val repo = repo()
        val found = repo.seriesFor(
            "Aztec Turning of Heaven",
            "komiku.org",
            BigDecimal(12),
            "https://komiku.org/aztec-12/"
        )
        assertEquals(aztec, found.id)
        assertEquals(listOf("mangaplus", "mangafire", "komiku.org"), repo.aztec().sources.map { it.id })
        assertNull(repo.series("new-1").first())
        assertNotNull(repo.aztec().progress)
    }
}
