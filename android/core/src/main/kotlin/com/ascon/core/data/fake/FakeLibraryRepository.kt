package com.ascon.core.data.fake

import com.ascon.core.data.LibraryRepository
import com.ascon.core.data.joinedSeries
import com.ascon.core.data.onPage
import com.ascon.core.data.opened
import com.ascon.core.data.pulled
import com.ascon.core.data.stamped
import com.ascon.core.data.syncRecords
import com.ascon.core.data.withProgressSource
import com.ascon.core.model.Series
import com.ascon.core.model.SeriesMetadata
import com.ascon.core.model.Site
import com.ascon.core.model.SyncRecord
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
    sites: List<Site> = FakeLibrary.sites,
    private val clock: Clock = Clock.systemUTC()
) : LibraryRepository {
    private val state = MutableStateFlow(initial)

    override val series: StateFlow<List<Series>> = state
    override val sites: Flow<List<Site>> = MutableStateFlow(sites)

    override fun series(id: String): Flow<Series?> = state.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun seriesFor(
        title: String,
        host: String,
        chapter: BigDecimal,
        url: String,
        link: SeriesMetadata?
    ): Series {
        lateinit var found: Series
        state.updateAndGet { all ->
            val (joined, match) = joinedSeries(all, title, host, chapter, url, link, ::newId)
            found = joined.stamped(match, clock.instant())
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

    override suspend fun syncRecords(since: Instant?): List<SyncRecord> = state.value.flatMap { it.syncRecords(since) }

    override suspend fun applyPulled(records: List<SyncRecord>) {
        state.update { all ->
            val changed = pulled(all, records, ::newId).associateBy { it.id }
            all.map { changed[it.id] ?: it } + changed.values.filter { new -> all.none { it.id == new.id } }
        }
    }

    private fun change(seriesId: String, transform: (Series) -> Series) {
        val now = clock.instant()
        state.update { all -> all.map { if (it.id == seriesId) transform(it).stamped(it, now) else it } }
    }

    private fun newId() = UUID.randomUUID().toString()
}
