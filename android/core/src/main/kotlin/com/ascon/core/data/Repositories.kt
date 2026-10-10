package com.ascon.core.data

import com.ascon.core.model.AccountState
import com.ascon.core.model.ProtectionSettings
import com.ascon.core.model.ReaderSettings
import com.ascon.core.model.Series
import com.ascon.core.model.SettingsSummary
import com.ascon.core.model.Site
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The library on this device. Room backs it on the device; previews and tests use [fake.FakeLibraryRepository]. */
interface LibraryRepository {
    val series: Flow<List<Series>>
    val sites: Flow<List<Site>>

    fun series(id: String): Flow<Series?>

    /**
     * The series [title] belongs to, matched by title. A title the library does not have
     * becomes a new series. Either way [host] becomes one of its sources, with a chapter
     * range that takes in [chapter], and the chapter page [url] is kept so the library
     * can open its chapters again.
     */
    suspend fun seriesFor(title: String, host: String, chapter: BigDecimal, url: String): Series

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

/**
 * The protection settings, saved on the device. The browser reads [settings] on its
 * request threads, so it is a [StateFlow] holding the defaults until the saved values
 * are read, moments after start.
 */
interface ProtectionSettingsRepository {
    val settings: StateFlow<ProtectionSettings>

    /** The saved settings, waiting for them to be read if need be. */
    suspend fun load(): ProtectionSettings

    suspend fun update(transform: (ProtectionSettings) -> ProtectionSettings)
}

interface SettingsRepository {
    val summary: Flow<SettingsSummary>
}

/**
 * Reader settings for all series, and for each series given its own. A series without
 * its own reads with [allSeries].
 */
interface ReaderSettingsRepository {
    val allSeries: Flow<ReaderSettings>

    /** The series' own settings, or null while it follows [allSeries]. */
    fun forSeries(seriesId: String): Flow<ReaderSettings?>

    suspend fun updateAllSeries(transform: (ReaderSettings) -> ReaderSettings)

    /** Changes the series' own settings, starting from [allSeries] when it has none yet. */
    suspend fun updateSeries(seriesId: String, transform: (ReaderSettings) -> ReaderSettings)

    /** The series follows [allSeries] again. */
    suspend fun clearSeries(seriesId: String)
}

/** How long this user looks at each image, learned across chapters for the time left. */
interface ReadingPaceRepository {
    /** Recent seconds spent on an image, oldest first. */
    val secondsPerImage: Flow<List<Float>>

    suspend fun recordSecondsPerImage(seconds: Float)
}

interface AccountRepository {
    val account: Flow<AccountState>
}
