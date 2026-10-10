package com.ascon.core.data.fake

import com.ascon.core.data.AccountRepository
import com.ascon.core.data.SettingsRepository
import com.ascon.core.model.AccountState
import com.ascon.core.model.SettingsSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(summary: SettingsSummary = FakeLibrary.settingsSummary) : SettingsRepository {
    override val summary: Flow<SettingsSummary> = MutableStateFlow(summary)
}

class FakeAccountRepository(initial: AccountState) : AccountRepository {
    override val account: Flow<AccountState> = MutableStateFlow(initial)
}
