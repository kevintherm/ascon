package com.ascon.core.data.fake

import com.ascon.core.data.AccountRepository
import com.ascon.core.data.SettingsRepository
import com.ascon.core.model.AccountState
import com.ascon.core.model.Settings
import com.ascon.core.model.SettingsSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: Settings = Settings(), summary: SettingsSummary = FakeLibrary.settingsSummary) :
    SettingsRepository {
    private val state = MutableStateFlow(initial)

    override val settings: Flow<Settings> = state
    override val summary: Flow<SettingsSummary> = MutableStateFlow(summary)

    override suspend fun update(transform: (Settings) -> Settings) {
        state.update(transform)
    }
}

class FakeAccountRepository(initial: AccountState) : AccountRepository {
    override val account: Flow<AccountState> = MutableStateFlow(initial)
}
