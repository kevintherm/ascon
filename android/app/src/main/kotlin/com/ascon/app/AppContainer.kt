package com.ascon.app

import com.ascon.core.data.AccountRepository
import com.ascon.core.data.LibraryRepository
import com.ascon.core.data.SettingsRepository
import com.ascon.core.data.fake.FakeAccountRepository
import com.ascon.core.data.fake.FakeLibraryRepository
import com.ascon.core.data.fake.FakeSettingsRepository
import com.ascon.core.model.AccountState
import java.time.Clock
import java.time.Duration

/**
 * Manual dependency injection: one instance of each repository for the whole app.
 * Fakes until Room, the backend client and sign-in exist.
 */
class AppContainer(val clock: Clock = Clock.systemDefaultZone()) {
    val library: LibraryRepository = FakeLibraryRepository()
    val settings: SettingsRepository = FakeSettingsRepository()
    val accounts: AccountRepository = FakeAccountRepository(
        AccountState.SignedIn(
            displayName = "Kevin",
            premium = false,
            lastSyncedAt = clock.instant().minus(Duration.ofMinutes(2))
        )
    )
}
