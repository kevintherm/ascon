package com.ascon.engine.adblock

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.ascon.engine.adblock.rust.AdblockEngine
import com.ascon.engine.adblock.rust.AdblockException
import java.io.File
import java.security.MessageDigest

/**
 * The ad blocker. Parsing filter lists takes seconds, so the engine is built once and
 * saved as a snapshot, which later starts load in a fraction of that. A snapshot is
 * named after a hash of the lists it holds, so changed lists build a new one.
 *
 * Until [start] finishes, nothing is blocked.
 */
class Adblock(private val context: Context, val lists: FilterListStore = FilterListStore(context)) : RequestFilter {
    @Volatile
    private var engine: AdblockEngine? = null

    /**
     * Builds or loads the engine from the enabled lists. Call off the main thread at start,
     * and again after lists change. The old engine keeps working until the new one is ready.
     */
    @Synchronized
    fun start(lists: List<String> = this.lists.enabledTexts()) {
        try {
            startEngine(lists)
        } catch (e: UnsatisfiedLinkError) {
            // No engine for this device's ABI. Browsing works, unblocked.
            Log.e(TAG, "Engine not loaded", e)
        }
    }

    /** Turns a list on or off and rebuilds the engine. Call off the main thread. */
    fun setEnabled(list: FilterList, enabled: Boolean) {
        lists.setEnabled(list, enabled)
        start()
    }

    private fun startEngine(lists: List<String>) {
        val dir = File(context.noBackupFilesDir, "adblock/engine").apply { mkdirs() }
        val snapshot = File(dir, "engine-${hashOf(lists)}.dat")
        val started = SystemClock.elapsedRealtime()
        val loaded = snapshot.takeIf { it.exists() }?.let(::load)
        engine = loaded ?: build(lists, snapshot)
        Log.i(
            TAG,
            "Engine ${if (loaded != null) "loaded" else "built"} in ${SystemClock.elapsedRealtime() - started} ms"
        )
        dir.listFiles()?.filter { it != snapshot }?.forEach { it.delete() }
    }

    override fun shouldBlock(url: String, sourceUrl: String, type: RequestType): Boolean =
        engine?.shouldBlock(url, sourceUrl, type.filterName) ?: false

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
