package com.ascon.feature.library.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.LibraryRepository
import com.ascon.core.model.ReadingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel(library: LibraryRepository, initialFilter: ReadingStatus) : ViewModel() {
    private val filter = MutableStateFlow(initialFilter)

    val state: StateFlow<LibraryUiState> =
        combine(library.series, filter, ::libraryUiState)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                LibraryUiState(filter = initialFilter)
            )

    fun selectFilter(status: ReadingStatus) {
        filter.value = status
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
