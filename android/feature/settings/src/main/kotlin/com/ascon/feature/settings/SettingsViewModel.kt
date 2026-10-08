package com.ascon.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.AccountRepository
import com.ascon.core.data.SettingsRepository
import com.ascon.core.model.AccountState
import com.ascon.core.model.Settings
import com.ascon.core.model.SettingsSummary
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val account: AccountState = AccountState.SignedOut,
    val settings: Settings = Settings(),
    val summary: SettingsSummary? = null,
    /** Sync times are shown relative to this moment. */
    val now: Instant = Instant.EPOCH
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    accounts: AccountRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {
    val state: StateFlow<SettingsUiState> =
        combine(settings.settings, settings.summary, accounts.account) { values, summary, account ->
            SettingsUiState(
                loading = false,
                account = account,
                settings = values,
                summary = summary,
                now = clock.instant()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun setBlockAds(enabled: Boolean) = update { it.copy(blockAds = enabled) }

    fun setBlockPopups(enabled: Boolean) = update { it.copy(blockPopups = enabled) }

    fun setKeepScreenOn(enabled: Boolean) = update { it.copy(keepScreenOn = enabled) }

    private fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { settings.update(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
