package com.ascon.core.data.fake

import com.ascon.core.data.LibraryRepository
import com.ascon.core.data.newSeries
import com.ascon.core.data.onPage
import com.ascon.core.data.opened
import com.ascon.core.data.withProgressSource
import com.ascon.core.data.withSite
import com.ascon.core.model.Series
import com.ascon.core.model.Site
import com.ascon.core.model.matchSeries
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

/** An in-memory library seeded with [FakeLibrary]. Changes last until the process dies. */
class FakeLibraryRepository(
    initial: List<Series> = FakeLibrary.series(Clock.systemDefaultZone()),
    sites: List<Site> = FakeLibrary.sites
) : LibraryRepository {
    private val state = MutableStateFlow(initial)

    override val series: StateFlow<List<Series>> = state
    override val sites: Flow<List<Site>> = MutableStateFlow(sites)

    override fun series(id: String): Flow<Series?> = state.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun seriesFor(title: String, host: String, chapter: BigDecimal, url: String): Series {
        lateinit var found: Series
        state.updateAndGet { all ->
            val match = matchSeries(all, title)
            found = match?.withSite(host, chapter, url)
                ?: newSeries(UUID.randomUUID().toString(), title, host, chapter, url)
            if (match == null) all + found else all.map { if (it.id == found.id) found else it }
        }
        return found
    }

    override suspend fun selectSource(seriesId: String, sourceId: String) = change(seriesId) {
        it.withProgressSource(sourceId)
    }

    override suspend fun recordChapterOpened(seriesId: String, chapter: BigDecimal, at: Instant) = change(seriesId) {
        it.opened(chapter, at)
    }

    override suspend fun recordPageRead(
        seriesId: String,
        chapter: BigDecimal,
        page: Int,
        pageCount: Int,
        at: Instant,
        pageOffset: Float
    ) = change(seriesId) { it.opened(chapter, at).onPage(chapter, page, pageCount, pageOffset) }

    private fun change(seriesId: String, transform: (Series) -> Series) {
        state.update { all -> all.map { if (it.id == seriesId) transform(it) else it } }
    }
}
