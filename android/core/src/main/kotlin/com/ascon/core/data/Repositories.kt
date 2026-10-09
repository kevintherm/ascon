package com.ascon.core.data

import com.ascon.core.model.AccountState
import com.ascon.core.model.Series
import com.ascon.core.model.Settings
import com.ascon.core.model.SettingsSummary
import com.ascon.core.model.Site
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** The library on this device. Room backs it later; for now [fake.FakeLibraryRepository]. */
interface LibraryRepository {
    val series: Flow<List<Series>>
    val sites: Flow<List<Site>>

    fun series(id: String): Flow<Series?>

    /** Makes [sourceId] the source the user reads [seriesId] from. */
    suspend fun selectSource(seriesId: String, sourceId: String)

    /**
     * Records that the user opened [chapter] of [seriesId]. Earlier chapters count as read
     * and progress moves to [chapter]. Opening an older chapter again never moves
     * progress back.
     */
    suspend fun recordChapterOpened(seriesId: String, chapter: BigDecimal, at: Instant)

    /**
     * Records that page [page] of [pageCount], counted from 1, is on screen in the
     * reader. Opens the chapter first, as [recordChapterOpened] does. Reaching the
     * last page marks the chapter read.
     */
    suspend fun recordPageRead(seriesId: String, chapter: BigDecimal, page: Int, pageCount: Int, at: Instant)
}

interface SettingsRepository {
    val settings: Flow<Settings>
    val summary: Flow<SettingsSummary>

    suspend fun update(transform: (Settings) -> Settings)
}

interface AccountRepository {
    val account: Flow<AccountState>
}
