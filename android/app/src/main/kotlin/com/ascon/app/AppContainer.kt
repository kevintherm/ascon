package com.ascon.app

import android.content.Context
import com.ascon.core.data.AccountRepository
import com.ascon.core.data.LibraryRepository
import com.ascon.core.data.SettingsRepository
import com.ascon.core.data.fake.FakeAccountRepository
import com.ascon.core.data.fake.FakeLibraryRepository
import com.ascon.core.data.fake.FakeSettingsRepository
import com.ascon.core.model.AccountState
import com.ascon.engine.detection.DetectionHost
import com.ascon.engine.detection.InMemoryRuleCache
import com.ascon.engine.detection.RuleLookup
import com.ascon.feature.browser.web.WebViewPool
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.MainScope

/**
 * Manual dependency injection: one instance of each repository for the whole app.
 * Fakes until Room, the backend client and sign-in exist.
 */
class AppContainer(context: Context, val clock: Clock = Clock.systemDefaultZone()) {
    private val app = context.applicationContext

    val library: LibraryRepository = FakeLibraryRepository()
    val settings: SettingsRepository = FakeSettingsRepository()
    val accounts: AccountRepository = FakeAccountRepository(
        AccountState.SignedIn(
            displayName = "Kevin",
            premium = false,
            lastSyncedAt = clock.instant().minus(Duration.ofMinutes(2))
        )
    )

    /** Rules found for single domains. In memory until Room and the backend client exist. */
    private val rules = InMemoryRuleCache()

    private val detection by lazy {
        DetectionHost(
            script = DetectionHost.loadScript(app),
            rules = RuleLookup(rules, DetectionHost.loadBuiltInRules(app)),
            scope = MainScope()
        )
    }

    val webViews = WebViewPool(app, detection = { detection })
}
