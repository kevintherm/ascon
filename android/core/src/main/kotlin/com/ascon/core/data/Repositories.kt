package com.ascon.core.data

import android.content.Context
import com.ascon.core.model.AccountSession
import com.ascon.core.model.AccountState
import com.ascon.core.model.ProtectionSettings
import com.ascon.core.model.Quota
import com.ascon.core.model.ReaderSettings
import com.ascon.core.model.Series
import com.ascon.core.model.SeriesMetadata
import com.ascon.core.model.SettingsSummary
import com.ascon.core.model.Site
import com.ascon.core.model.SyncPage
import com.ascon.core.model.SyncPosition
import com.ascon.core.model.SyncRecord
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
     * The series [title] belongs to, matched by title, or else by the AniList or MangaUpdates
     * series [link] names. A title the library does not have becomes a new series, linked to
     * [link] when given. Either way [host] becomes one of its sources, with a chapter range
     * that takes in [chapter], and the chapter page [url] is kept so the library can open
     * its chapters again.
     */
    suspend fun seriesFor(
        title: String,
        host: String,
        chapter: BigDecimal,
        url: String,
        link: SeriesMetadata? = null
    ): Series

    /** Makes [sourceId] the source the user reads [seriesId] from. */
    suspend fun selectSource(seriesId: String, sourceId: String)

    /**
     * Records that the user opened [chapter] of [seriesId]. Progress moves to it only while
     * the chapter in progress wasn't read into, as LibraryChanges.kt says.
     */
    suspend fun recordChapterOpened(seriesId: String, chapter: BigDecimal, at: Instant)

    /**
     * Records that page [page] of [pageCount], counted from 1, is on screen in the
     * reader. Opens the chapter first, as [recordChapterOpened] does. Reaching the
     * last page marks the chapter read. [pageOffset] is how far down the page the top of
     * the screen is, as [ReadingProgress.pageOffset] says.
     */
    suspend fun recordPageRead(
        seriesId: String,
        chapter: BigDecimal,
        page: Int,
        pageCount: Int,
        at: Instant,
        pageOffset: Float = 0f
    )

    /** What to push for sync: the parts changed after [since], or everything when it is null. */
    suspend fun syncRecords(since: Instant?): List<SyncRecord>

    /** Applies [records] pulled from the server, keeping parts this phone changed later. */
    suspend fun applyPulled(records: List<SyncRecord>)
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

    /**
     * Whether the reader opens by itself on chapters of [site], a host such as
     * `mangafire.to`, as the user chose for that site. Null until the user chooses, and
     * then the reader opens by itself only on a chapter with a next or previous chapter.
     */
    fun autoOpen(site: String): Flow<Boolean?>

    suspend fun setAutoOpen(site: String, on: Boolean)
}

/** One key per site, however its host is written: case and "www." don't matter. */
fun siteKey(host: String): String = host.lowercase().removePrefix("www.")

/** How long this user looks at each image, learned across chapters for the time left. */
interface ReadingPaceRepository {
    /** Recent seconds spent on an image, oldest first. */
    val secondsPerImage: Flow<List<Float>>

    suspend fun recordSecondsPerImage(seconds: Float)
}

/** The signed-in account, kept on the device. */
interface AccountRepository {
    val account: Flow<AccountState>

    /** The backend's account token, or null while signed out. Backend calls read it each time. */
    val token: StateFlow<String?>

    suspend fun signedIn(session: AccountSession)

    /** Forgets the account on this device, and where sync stood. The library stays. */
    suspend fun signedOut()

    /** Where sync stands for the account signed in now. */
    suspend fun syncPosition(): SyncPosition

    /** Saves [position], unless the account signed in now is no longer [token]'s. */
    suspend fun synced(token: String, position: SyncPosition)
}

/** The sync calls of the Ascon backend, contracts/openapi.yaml. */
interface SyncBackend {
    /** Throws [AccountRefused] when [token] no longer works and [java.io.IOException] on other failures. */
    suspend fun push(token: String, records: List<SyncRecord>)

    /** Throws [SyncCursorExpired] when [cursor] is too old, else as [push] does. */
    suspend fun pull(token: String, cursor: String?): SyncPage
}

/** The pull cursor is too old; pull everything again. */
class SyncCursorExpired : java.io.IOException("sync cursor expired")

/** The account calls of the Ascon backend, contracts/openapi.yaml. */
interface AccountBackend {
    /**
     * Signs in with a Google ID token and returns the session without a name, which the
     * backend doesn't know. Throws [AccountRefused] when the backend refuses the ID token
     * and [java.io.IOException] when it can't be reached.
     */
    suspend fun signIn(idToken: String): AccountSession

    /** Ends [token] on the backend. Throws [java.io.IOException] when it can't be reached. */
    suspend fun signOut(token: String)

    /** Throws [AccountRefused] when [token] no longer works, as after signing out elsewhere. */
    suspend fun quota(token: String): Quota
}

/** The backend refused a Google ID token or an account token. */
class AccountRefused : Exception()

/** Asks the user to pick a Google account, through Android's Credential Manager. */
fun interface GoogleAccounts {
    /**
     * Needs an activity [context] to show the account picker. Throws [SignInCancelled] when
     * the user backs out, [NoGoogleAccount] when the phone has none, and
     * [GoogleUnreachable] for anything else, such as no connection.
     */
    suspend fun pick(context: Context): GoogleCredential
}

/** A Google ID token and the profile it came with. [email] is the account's address. */
data class GoogleCredential(val idToken: String, val displayName: String?, val email: String)

class SignInCancelled : Exception()

class NoGoogleAccount : Exception()

class GoogleUnreachable(cause: Throwable? = null) : Exception(cause)

/** The backend's series search over AniList and MangaUpdates, with the device token. */
interface MetadataSearch {
    /** Candidates for [query], best first. Throws [java.io.IOException] when the call fails. */
    suspend fun search(query: String): List<SeriesMetadata>
}
