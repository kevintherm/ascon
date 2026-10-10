package com.ascon.core.data.fake

import com.ascon.core.data.ReaderSettingsRepository
import com.ascon.core.data.siteKey
import com.ascon.core.model.ReaderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Reader settings in memory, for previews and tests. */
class FakeReaderSettings(allSeries: ReaderSettings = ReaderSettings()) : ReaderSettingsRepository {
    private val all = MutableStateFlow(allSeries)
    private val series = MutableStateFlow(emptyMap<String, ReaderSettings>())

    override val allSeries: Flow<ReaderSettings> = all

    override fun forSeries(seriesId: String): Flow<ReaderSettings?> = series.map { it[seriesId] }.distinctUntilChanged()

    override suspend fun updateAllSeries(transform: (ReaderSettings) -> ReaderSettings) {
        all.update(transform)
    }

    override suspend fun updateSeries(seriesId: String, transform: (ReaderSettings) -> ReaderSettings) {
        series.update { it + (seriesId to transform(it[seriesId] ?: all.value)) }
    }

    override suspend fun clearSeries(seriesId: String) {
        series.update { it - seriesId }
    }

    private val chosen = MutableStateFlow(emptyMap<String, Boolean>())

    override fun autoOpen(site: String): Flow<Boolean?> = chosen.map { it[siteKey(site)] }.distinctUntilChanged()

    override suspend fun setAutoOpen(site: String, on: Boolean) {
        chosen.update { it + (siteKey(site) to on) }
    }
}
