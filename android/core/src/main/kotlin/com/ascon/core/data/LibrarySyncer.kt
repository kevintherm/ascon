package com.ascon.core.data

import com.ascon.core.model.SyncPosition
import java.io.IOException
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Where the latest sync stands, for the account sheet. */
enum class SyncStatus { Idle, Syncing, Failed }

/**
 * Syncs the library with the signed-in account: pushes what changed since the last push,
 * then pulls what other phones changed, per AGENTS.md. One sync runs at a time.
 */
class LibrarySyncer(
    private val library: LibraryRepository,
    private val accounts: AccountRepository,
    private val backend: SyncBackend,
    private val clock: Clock = Clock.systemUTC()
) {
    private val running = Mutex()
    private val _status = MutableStateFlow(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /**
     * Syncs in [scope] for as long as it lives: when an account signs in, which includes the
     * app starting signed in, and once the library has been still for [quiet] after a change.
     */
    @OptIn(FlowPreview::class)
    fun runIn(scope: CoroutineScope, quiet: Duration = 5.seconds) {
        scope.launch { accounts.token.filterNotNull().distinctUntilChanged().collect { sync() } }
        scope.launch { library.series.drop(1).debounce(quiet).collect { sync() } }
    }

    /** Syncs once if signed in. A token the backend refuses signs the phone out. */
    suspend fun sync() {
        val token = accounts.token.value ?: return
        running.withLock {
            _status.value = SyncStatus.Syncing
            _status.value = try {
                syncAs(token)
                SyncStatus.Idle
            } catch (_: AccountRefused) {
                accounts.signedOut()
                SyncStatus.Idle
            } catch (_: IOException) {
                SyncStatus.Failed
            }
        }
    }

    private suspend fun syncAs(token: String) {
        val position = accounts.syncPosition()
        // Read before reading what to push, so a change made meanwhile goes next time.
        val pushedAt = clock.instant()
        library.syncRecords(position.pushedAt).chunked(PUSH_LIMIT).forEach { backend.push(token, it) }
        val cursor = try {
            pullAll(token, position.cursor)
        } catch (_: SyncCursorExpired) {
            pullAll(token, null)
        }
        accounts.synced(token, SyncPosition(cursor, pushedAt, syncedAt = clock.instant()))
    }

    private suspend fun pullAll(token: String, from: String?): String? {
        var cursor = from
        do {
            val page = backend.pull(token, cursor)
            library.applyPulled(page.records)
            cursor = page.cursor
        } while (page.hasMore)
        return cursor
    }

    private companion object {
        /** The most changes the backend takes in one push. */
        const val PUSH_LIMIT = 1000
    }
}
