package com.ascon.engine.adblock

import android.content.Context
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
class Adblock(private val context: Context) : RequestFilter {
    @Volatile
    private var engine: AdblockEngine? = null

    /** Builds or loads the engine. Call once, off the main thread. */
    fun start(lists: List<String> = bundledLists()) {
        try {
            startEngine(lists)
        } catch (e: UnsatisfiedLinkError) {
            // No engine for this device's ABI. Browsing works, unblocked.
            Log.e(TAG, "Engine not loaded", e)
        }
    }

    private fun startEngine(lists: List<String>) {
        val dir = File(context.noBackupFilesDir, "adblock").apply { mkdirs() }
        val snapshot = File(dir, "engine-${hashOf(lists)}.dat")
        engine = snapshot.takeIf { it.exists() }?.let(::load) ?: build(lists, snapshot)
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

    /** The filter lists shipped in the app's assets under `adblock/`. */
    private fun bundledLists(): List<String> {
        val assets = context.assets
        return assets.list(ASSET_DIR).orEmpty().sorted().map { name ->
            assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
        }
    }

    private companion object {
        const val TAG = "Adblock"
        const val ASSET_DIR = "adblock"

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
