package com.ascon.feature.library.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.AccountRepository
import com.ascon.core.data.LibraryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(library: LibraryRepository, accounts: AccountRepository) : ViewModel() {
    val state: StateFlow<HomeUiState> =
        combine(library.series, library.sites, accounts.account, ::homeUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())
}

/** Keeps flows alive across a configuration change without holding them forever. */
internal const val STOP_TIMEOUT_MS = 5_000L
