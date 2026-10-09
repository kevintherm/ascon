package com.ascon.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.AccountRepository
import com.ascon.core.data.ProtectionSettingsRepository
import com.ascon.core.data.SettingsRepository
import com.ascon.core.model.AccountState
import com.ascon.core.model.ProtectionSettings
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
    val protection: ProtectionSettings = ProtectionSettings(),
    val summary: SettingsSummary? = null,
    /** Sync times are shown relative to this moment. */
    val now: Instant = Instant.EPOCH
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val protection: ProtectionSettingsRepository,
    accounts: AccountRepository,
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {
    val state: StateFlow<SettingsUiState> =
        combine(
            settings.settings,
            protection.settings,
            settings.summary,
            accounts.account
        ) { values, protection, summary, account ->
            SettingsUiState(
                loading = false,
                account = account,
                settings = values,
                protection = protection,
                summary = summary,
                now = clock.instant()
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SettingsUiState())

    fun setBlockAds(enabled: Boolean) = updateProtection { it.copy(adblockEnabled = enabled) }

    fun setBlockPopups(enabled: Boolean) = updateProtection { it.copy(blockPopups = enabled) }

    fun setKeepScreenOn(enabled: Boolean) = update { it.copy(keepScreenOn = enabled) }

    private fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { settings.update(transform) }
    }

    private fun updateProtection(transform: (ProtectionSettings) -> ProtectionSettings) {
        viewModelScope.launch { protection.update(transform) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
