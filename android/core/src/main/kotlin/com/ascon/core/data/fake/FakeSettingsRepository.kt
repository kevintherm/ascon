package com.ascon.core.data.fake

import com.ascon.core.data.AccountRepository
import com.ascon.core.data.SettingsRepository
import com.ascon.core.model.AccountSession
import com.ascon.core.model.AccountState
import com.ascon.core.model.SettingsSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository(summary: SettingsSummary = FakeLibrary.settingsSummary) : SettingsRepository {
    override val summary: Flow<SettingsSummary> = MutableStateFlow(summary)
}

class FakeAccountRepository(initial: AccountState = AccountState.SignedOut, token: String? = null) : AccountRepository {
    override val account = MutableStateFlow(initial)
    override val token = MutableStateFlow(token ?: if (initial is AccountState.SignedIn) "fake-token" else null)

    override suspend fun signedIn(session: AccountSession) {
        token.value = session.token
        account.value = AccountState.SignedIn(session.displayName, session.email, session.premium)
    }

    override suspend fun signedOut() {
        token.value = null
        account.value = AccountState.SignedOut
    }
}
