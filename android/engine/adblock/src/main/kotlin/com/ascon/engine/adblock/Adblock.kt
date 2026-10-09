package com.ascon.engine.adblock

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.ascon.engine.adblock.rust.AdblockEngine
import com.ascon.engine.adblock.rust.AdblockException
import com.ascon.engine.adblock.rust.PageCosmetics
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The ad blocker. Parsing filter lists takes seconds, so each engine is built once and
 * saved as a snapshot, which later starts load in a fraction of that. A snapshot is
 * named after a hash of the lists it holds, so changed lists build a new one.
 *
 * There is one engine per [BlockCategory]: EasyList and the Ascon list for ads,
 * EasyPrivacy for trackers. A request is checked against ads first, so each blocked
 * request counts once. The lists are split, not copied, so memory stays about the same.
 *
 * Until the first [start] finishes, checks off the main thread wait for it, briefly, so
 * pages opened at launch are filtered too. The main thread never waits.
 */
class Adblock(private val context: Context, val lists: FilterListStore = FilterListStore(context)) : RequestFilter {
    /** Swapped as a whole, so a check never sees one new engine and one old. */
    private class Engines(val byCategory: Map<BlockCategory, AdblockEngine>) {
        val all: Collection<AdblockEngine> get() = byCategory.values
    }

    @Volatile
    private var engines: Engines? = null
    private val firstStart = CountDownLatch(1)

    /** Ids of the filter lists the user turned off. */
    @Volatile
    var disabled: Set<String> = emptySet()
        private set

    /**
     * Builds or loads the engines from all lists but the [disabled] ones. Call off the
     * main thread at start, and again after lists change. The old engines keep working
     * until the new ones are ready.
     */
    @Synchronized
    fun start(disabled: Set<String> = this.disabled) {
        this.disabled = disabled
        try {
            startEngines(disabled)
        } catch (e: UnsatisfiedLinkError) {
            // No engine for this device's ABI. Browsing works, unblocked.
            Log.e(TAG, "Engine not loaded", e)
        } finally {
            firstStart.countDown()
        }
    }

    private fun startEngines(disabled: Set<String>) {
        val dir = File(context.noBackupFilesDir, "adblock/engine").apply { mkdirs() }
        val started = SystemClock.elapsedRealtime()
        val snapshots = mutableListOf<File>()
        val built = BlockCategory.entries.mapNotNull { category ->
            val texts = lists.enabledTexts(category, disabled).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val snapshot = File(dir, "engine-${category.name.lowercase()}-${hashOf(texts)}.dat")
            snapshots += snapshot
            category to (snapshot.takeIf { it.exists() }?.let(::load) ?: build(texts, snapshot))
        }.toMap()
        engines = Engines(built)
        Log.i(TAG, "Engines ${built.keys} ready in ${SystemClock.elapsedRealtime() - started} ms")
        dir.listFiles()?.filter { it !in snapshots }?.forEach { it.delete() }
    }

    override fun shouldBlock(url: String, sourceUrl: String, type: RequestType): Boolean =
        blockedAs(url, sourceUrl, type) != null

    override fun blockedAs(url: String, sourceUrl: String, type: RequestType): BlockCategory? {
        val current = ready() ?: return null
        // Entries follow BlockCategory's order, so ads are checked first.
        return current.byCategory.entries.firstOrNull { (_, engine) ->
            engine.shouldBlock(url, sourceUrl, type.filterName)
        }?.key
    }

    /** The page's own hide rules from every engine, or null until the engines have loaded. */
    internal fun pageCosmetics(url: String): PageCosmetics? {
        val parts = ready()?.all?.map { it.pageCosmetics(url) }?.takeIf { it.isNotEmpty() } ?: return null
        return PageCosmetics(
            hideSelectors = parts.flatMap { it.hideSelectors },
            exceptions = parts.flatMap { it.exceptions }.distinct(),
            generichide = parts.any { it.generichide }
        )
    }

    /** Selectors from generic rules that name any of these classes or ids, less [exceptions]. */
    internal fun hiddenSelectors(classes: List<String>, ids: List<String>, exceptions: List<String>): List<String>? =
        ready()?.all?.flatMap { it.hiddenSelectors(classes, ids, exceptions) }

    private fun ready(): Engines? {
        val current = engines
        if (current != null || Looper.myLooper() == Looper.getMainLooper()) return current
        firstStart.await(STARTUP_WAIT_MS, TimeUnit.MILLISECONDS)
        return engines
    }

    private fun load(snapshot: File): AdblockEngine? = try {
        AdblockEngine.fromSnapshot(snapshot.readBytes())
    } catch (e: AdblockException) {
        // Written by another engine version. Build again from the lists.
        Log.w(TAG, "Snapshot not loaded", e)
        null
    }

    private fun build(lists: List<String>, snapshot: File): AdblockEngine {
        val built = AdblockEngine.fromLists(lists)
        val temporary = File(snapshot.path + ".tmp")
        temporary.writeBytes(built.snapshot())
        temporary.renameTo(snapshot)
        return built
    }

    private companion object {
        const val TAG = "Adblock"

        /** The longest a request at launch waits for the engine. Building it takes about half a second. */
        const val STARTUP_WAIT_MS = 3_000L

        /** Enough of the hash to tell list versions apart. */
        const val HASH_BYTES = 8

        fun hashOf(lists: List<String>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            lists.forEach {
                digest.update(it.toByteArray())
                digest.update(0)
            }
            return digest.digest().take(HASH_BYTES).joinToString("") { "%02x".format(it) }
        }
    }
}
