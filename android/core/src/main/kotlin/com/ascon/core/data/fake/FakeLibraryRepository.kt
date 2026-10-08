package com.ascon.core.data.fake

import com.ascon.core.data.LibraryRepository
import com.ascon.core.model.Series
import com.ascon.core.model.Site
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** An in-memory library seeded with [FakeLibrary]. Changes last until the process dies. */
class FakeLibraryRepository(
    initial: List<Series> = FakeLibrary.series(Clock.systemDefaultZone()),
    sites: List<Site> = FakeLibrary.sites
) : LibraryRepository {
    private val state = MutableStateFlow(initial)

    override val series: StateFlow<List<Series>> = state
    override val sites: Flow<List<Site>> = MutableStateFlow(sites)

    override fun series(id: String): Flow<Series?> = state.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun selectSource(seriesId: String, sourceId: String) {
        state.update { all ->
            all.map { series ->
                val progress = series.progress
                if (series.id != seriesId || progress == null || series.source(sourceId) == null) {
                    series
                } else {
                    series.copy(progress = progress.copy(sourceId = sourceId))
                }
            }
        }
    }
}
