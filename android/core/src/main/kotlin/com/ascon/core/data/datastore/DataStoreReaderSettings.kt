package com.ascon.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ascon.core.data.ReaderSettingsRepository
import com.ascon.core.data.ReadingPaceRepository
import com.ascon.core.data.siteKey
import com.ascon.core.model.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [ReaderSettings] in a Preferences DataStore: one entry for all series and one for each
 * series with its own, each as `name=value` pairs. Make one per file, for the whole app.
 */
class DataStoreReaderSettings(private val store: DataStore<Preferences>) :
    ReaderSettingsRepository,
    ReadingPaceRepository {
    override val secondsPerImage: Flow<List<Float>> =
        store.data.map { decodePace(it[Pace]) }.distinctUntilChanged()

    override suspend fun recordSecondsPerImage(seconds: Float) {
        store.edit { it[Pace] = encodePace((decodePace(it[Pace]) + seconds).takeLast(PACE_SAMPLES)) }
    }

    override val allSeries: Flow<ReaderSettings> =
        store.data.map { decode(it[AllSeries]) ?: ReaderSettings() }.distinctUntilChanged()

    override fun forSeries(seriesId: String): Flow<ReaderSettings?> =
        store.data.map { decode(it[seriesKey(seriesId)]) }.distinctUntilChanged()

    override suspend fun updateAllSeries(transform: (ReaderSettings) -> ReaderSettings) {
        store.edit { it[AllSeries] = encode(transform(decode(it[AllSeries]) ?: ReaderSettings())) }
    }

    override suspend fun updateSeries(seriesId: String, transform: (ReaderSettings) -> ReaderSettings) {
        store.edit {
            val key = seriesKey(seriesId)
            val current = decode(it[key]) ?: decode(it[AllSeries]) ?: ReaderSettings()
            it[key] = encode(transform(current))
        }
    }

    override suspend fun clearSeries(seriesId: String) {
        store.edit { it.remove(seriesKey(seriesId)) }
    }

    override fun autoOpen(site: String): Flow<Boolean?> = store.data.map {
        when (siteKey(site)) {
            in it[ManualSites].orEmpty() -> false
            in it[AutoSites].orEmpty() -> true
            else -> null
        }
    }.distinctUntilChanged()

    override suspend fun setAutoOpen(site: String, on: Boolean) {
        val key = siteKey(site)
        store.edit {
            it[ManualSites] = if (on) it[ManualSites].orEmpty() - key else it[ManualSites].orEmpty() + key
            it[AutoSites] = if (on) it[AutoSites].orEmpty() + key else it[AutoSites].orEmpty() - key
        }
    }

    companion object {
        fun open(context: Context, scope: CoroutineScope) = DataStoreReaderSettings(
            PreferenceDataStoreFactory.create(scope = scope) {
                context.applicationContext.preferencesDataStoreFile("reader")
            }
        )

        private val AllSeries = stringPreferencesKey("all_series")

        private fun seriesKey(seriesId: String) = stringPreferencesKey("series:$seriesId")

        private val Pace = stringPreferencesKey("pace")

        /** Sites the reader doesn't open on by itself. */
        private val ManualSites = stringSetPreferencesKey("manual_reader_sites")
        private val AutoSites = stringSetPreferencesKey("auto_reader_sites")

        /** The reading pace keeps this many recent images. */
        private const val PACE_SAMPLES = 200

        internal fun encodePace(samples: List<Float>): String = samples.joinToString(",")

        internal fun decodePace(text: String?): List<Float> =
            text.orEmpty().split(',').mapNotNull { it.toFloatOrNull() }

        internal fun encode(settings: ReaderSettings): String = listOf(
            "mode" to settings.mode.name,
            "fit" to settings.fit.name,
            "gap" to settings.gap.name,
            "background" to settings.background.name,
            "crop_borders" to settings.cropBorders.toString(),
            "keep_screen_on" to settings.keepScreenOn.toString(),
            "volume_keys" to settings.volumeKeys.toString(),
            "show_tap_zones" to settings.showTapZones.toString()
        ).joinToString(";") { (name, value) -> "$name=$value" }

        /** Unknown or missing values take their defaults, so older and newer entries both read. */
        internal fun decode(text: String?): ReaderSettings? {
            if (text == null) return null
            val values = text.split(';').mapNotNull { pair ->
                pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap()
            val defaults = ReaderSettings()
            return ReaderSettings(
                mode = enumOf(values["mode"], defaults.mode),
                fit = enumOf(values["fit"], defaults.fit),
                gap = enumOf(values["gap"], defaults.gap),
                background = enumOf(values["background"], defaults.background),
                cropBorders = values["crop_borders"]?.toBooleanStrictOrNull() ?: defaults.cropBorders,
                keepScreenOn = values["keep_screen_on"]?.toBooleanStrictOrNull() ?: defaults.keepScreenOn,
                volumeKeys = values["volume_keys"]?.toBooleanStrictOrNull() ?: defaults.volumeKeys,
                showTapZones = values["show_tap_zones"]?.toBooleanStrictOrNull() ?: defaults.showTapZones
            )
        }

        private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}
