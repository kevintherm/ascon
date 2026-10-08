package com.ascon.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.LibraryRepository
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SeriesViewModel(
    private val library: LibraryRepository,
    private val seriesId: String,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {
    private val newestFirst = MutableStateFlow(true)

    val state: StateFlow<SeriesUiState> =
        combine(library.series(seriesId), newestFirst) { series, newest ->
            seriesUiState(series, newest, LocalDate.now(clock))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SeriesUiState())

    fun toggleOrder() {
        newestFirst.update { !it }
    }

    fun selectSource(sourceId: String) {
        viewModelScope.launch { library.selectSource(seriesId, sourceId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
