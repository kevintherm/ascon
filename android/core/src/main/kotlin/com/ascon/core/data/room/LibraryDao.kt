package com.ascon.core.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ascon.core.data.newSeries
import com.ascon.core.data.pulled
import com.ascon.core.data.stamped
import com.ascon.core.data.syncRecords
import com.ascon.core.data.withSite
import com.ascon.core.model.Series
import com.ascon.core.model.SyncRecord
import com.ascon.core.model.matchSeries
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.Flow

// Room needs one declared function per query, so a DAO grows past the usual limit.
@Suppress("TooManyFunctions")
@Dao
internal abstract class LibraryDao {
    @Transaction
    @Query("SELECT * FROM series")
    abstract fun observeAll(): Flow<List<SeriesRecord>>

    @Transaction
    @Query("SELECT * FROM series WHERE id = :id")
    abstract fun observe(id: String): Flow<SeriesRecord?>

    @Query("SELECT * FROM site ORDER BY position")
    abstract fun observeSites(): Flow<List<SiteEntity>>

    @Transaction
    @Query("SELECT * FROM series")
    protected abstract suspend fun all(): List<SeriesRecord>

    @Transaction
    @Query("SELECT * FROM series WHERE id = :id")
    protected abstract suspend fun one(id: String): SeriesRecord?

    @Query("SELECT COUNT(*) FROM series")
    protected abstract suspend fun seriesCount(): Int

    @Upsert
    protected abstract suspend fun upsert(series: SeriesEntity)

    @Upsert
    protected abstract suspend fun upsertSources(sources: List<SourceEntity>)

    @Upsert
    protected abstract suspend fun upsertChapters(chapters: List<ChapterEntity>)

    @Upsert
    protected abstract suspend fun upsertProgress(progress: ProgressEntity)

    @Query("DELETE FROM progress WHERE series_id = :seriesId")
    protected abstract suspend fun deleteProgress(seriesId: String)

    @Insert
    protected abstract suspend fun insertSites(sites: List<SiteEntity>)

    /** Writes [series] as a whole. Sources and chapters are only ever added, never removed. */
    @Transaction
    open suspend fun save(series: Series) {
        val record = series.toRecord()
        upsert(record.series)
        upsertSources(record.sources)
        upsertChapters(record.chapters)
        record.progress?.let { upsertProgress(it) } ?: deleteProgress(series.id)
    }

    /**
     * Reads, changes and writes one series as one transaction, so changes never interleave.
     * The parts that changed are stamped with [now] for sync.
     */
    @Transaction
    open suspend fun change(id: String, now: Instant, transform: (Series) -> Series) {
        one(id)?.toModel()?.let { save(transform(it).stamped(it, now)) }
    }

    /** See [com.ascon.core.data.LibraryRepository.syncRecords]. */
    @Transaction
    open suspend fun syncRecords(since: Instant?): List<SyncRecord> = all().flatMap { it.toModel().syncRecords(since) }

    /** See [com.ascon.core.data.LibraryRepository.applyPulled]. */
    @Transaction
    open suspend fun applyPulled(records: List<SyncRecord>, newId: () -> String) {
        pulled(all().map { it.toModel() }, records, newId).forEach { save(it) }
    }

    /** See [com.ascon.core.data.LibraryRepository.seriesFor]. */
    @Transaction
    open suspend fun seriesFor(
        title: String,
        host: String,
        chapter: BigDecimal,
        url: String,
        now: Instant,
        newId: () -> String
    ): Series {
        val match = matchSeries(all().map { it.toModel() }, title)
        val found = (match?.withSite(host, chapter, url) ?: newSeries(newId(), title, host, chapter, url))
            .stamped(match, now)
        save(found)
        return found
    }

    /** Fills an empty library with [series] and [sites]. */
    @Transaction
    open suspend fun seedIfEmpty(series: List<Series>, sites: List<SiteEntity>) {
        if (seriesCount() > 0) return
        series.forEach { save(it) }
        insertSites(sites)
    }
}
