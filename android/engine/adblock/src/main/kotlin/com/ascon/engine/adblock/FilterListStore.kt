package com.ascon.engine.adblock

import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * Where filter lists live on the device: the copies shipped in the assets, newer
 * downloads beside them in app storage, and which lists the user turned on.
 *
 * Asset files under `adblock/` that no [FilterList] names are extra lists, always on.
 * Debug builds add one for the Maestro flows.
 */
class FilterListStore(private val context: Context) {
    private val dir = File(context.noBackupFilesDir, "adblock/lists").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("adblock", Context.MODE_PRIVATE)

    fun isEnabled(list: FilterList): Boolean = prefs.getBoolean(enabledKey(list), list.enabledByDefault)

    fun setEnabled(list: FilterList, enabled: Boolean) = prefs.edit { putBoolean(enabledKey(list), enabled) }

    /** The newest copy of [list], downloaded or shipped. */
    fun text(list: FilterList): String? {
        val shipped = asset("${list.id}.txt")
        val downloaded = File(dir, "${list.id}.txt").takeIf { it.exists() }?.readText()
        return if (shipped == null) downloaded else FilterLists.newer(shipped, downloaded)
    }

    fun save(list: FilterList, text: String) {
        val temporary = File(dir, "${list.id}.tmp")
        temporary.writeText(text)
        temporary.renameTo(File(dir, "${list.id}.txt"))
    }

    /** The text of every list the engine should use. */
    fun enabledTexts(): List<String> {
        val known = FilterList.All.map { "${it.id}.txt" }.toSet()
        val modules = FilterList.All.filter(::isEnabled).mapNotNull(::text)
        val extras = context.assets.list(ASSET_DIR).orEmpty().filter { it !in known }.sorted().mapNotNull(::asset)
        return modules + extras
    }

    private fun asset(name: String): String? = try {
        context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
    } catch (_: java.io.FileNotFoundException) {
        null
    }

    private fun enabledKey(list: FilterList) = "enabled.${list.id}"

    private companion object {
        const val ASSET_DIR = "adblock"
    }
}
