package com.ascon.core.data.room

import com.ascon.core.data.LibraryRepository
import com.ascon.core.data.onPage
import com.ascon.core.data.opened
import com.ascon.core.data.withProgressSource
import com.ascon.core.model.Series
import com.ascon.core.model.Site
import com.ascon.core.model.SyncRecord
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The library kept in Room. [seed] fills an empty library before its first use; debug
 * builds pass the fake library so every screen has data.
 */
class RoomLibraryRepository(
    database: AsconDatabase,
    private val seed: Seed? = null,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: Clock = Clock.systemUTC()
) : LibraryRepository {
    /** Series and sites to start an empty library with. */
    class Seed(val series: List<Series>, val sites: List<Site>)

    private val dao = database.library()
    private val seeding = Mutex()

    @Volatile private var seeded = seed == null

    override val series: Flow<List<Series>> = ready(dao.observeAll().map { all -> all.map { it.toModel() } })
    override val sites: Flow<List<Site>> = ready(dao.observeSites().map { all -> all.map { it.toModel() } })

    override fun series(id: String): Flow<Series?> = ready(dao.observe(id).map { it?.toModel() })

    override suspend fun seriesFor(title: String, host: String, chapter: BigDecimal, url: String): Series {
        ensureSeeded()
        return dao.seriesFor(title, host, chapter, url, clock.instant(), newId)
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

    private suspend fun change(seriesId: String, transform: (Series) -> Series) {
        ensureSeeded()
        dao.change(seriesId, clock.instant(), transform)
    }

    override suspend fun syncRecords(since: Instant?): List<SyncRecord> {
        ensureSeeded()
        return dao.syncRecords(since)
    }

    override suspend fun applyPulled(records: List<SyncRecord>) {
        ensureSeeded()
        dao.applyPulled(records, newId)
    }

    private fun <T> ready(flow: Flow<T>): Flow<T> = flow {
        ensureSeeded()
        emitAll(flow)
    }

    private suspend fun ensureSeeded() {
        if (seeded) return
        seeding.withLock {
            if (seeded) return
            val seed = checkNotNull(seed)
            dao.seedIfEmpty(
                seed.series,
                seed.sites.mapIndexed { position, site -> SiteEntity(site.domain, site.name, site.monogram, position) }
            )
            seeded = true
        }
    }
}
