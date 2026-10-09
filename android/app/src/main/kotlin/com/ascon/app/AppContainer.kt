package com.ascon.app

import android.content.Context
import android.content.pm.ApplicationInfo
import com.ascon.core.data.AccountRepository
import com.ascon.core.data.LibraryRepository
import com.ascon.core.data.ProtectionSettingsRepository
import com.ascon.core.data.SettingsRepository
import com.ascon.core.data.datastore.DataStoreProtectionSettings
import com.ascon.core.data.fake.FakeAccountRepository
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.data.fake.FakeSettingsRepository
import com.ascon.core.data.room.AsconDatabase
import com.ascon.core.data.room.RoomLibraryRepository
import com.ascon.core.model.AccountState
import com.ascon.engine.adblock.Adblock
import com.ascon.engine.adblock.CosmeticFilter
import com.ascon.engine.adblock.FilterListUpdateWorker
import com.ascon.engine.detection.DetectionHost
import com.ascon.engine.detection.InMemoryRuleCache
import com.ascon.engine.detection.RuleLookup
import com.ascon.feature.browser.web.NavigationGuard
import com.ascon.feature.browser.web.OkHttpSiteKey
import com.ascon.feature.browser.web.WebViewPool
import com.ascon.feature.reader.ReaderImages
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Manual dependency injection: one instance of each repository for the whole app.
 * General settings and the account are fakes until the backend client and sign-in exist.
 */
class AppContainer(context: Context, val clock: Clock = Clock.systemDefaultZone()) {
    private val app = context.applicationContext

    private val debuggable = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    // Debug builds start with the sample library, so every screen has something to show.
    val library: LibraryRepository = RoomLibraryRepository(
        AsconDatabase.open(app),
        seed = if (debuggable) RoomLibraryRepository.Seed(FakeLibrary.series(clock), FakeLibrary.sites) else null
    )
    val settings: SettingsRepository = FakeSettingsRepository()

    /** Work that outlives every screen, such as saving settings and rebuilding the ad blocker. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val protection: ProtectionSettingsRepository = DataStoreProtectionSettings.open(app, background)
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

    /**
     * Blocks nothing until its engine has loaded, which starts here off the main thread,
     * and rebuilds it in the background when the user turns a filter list on or off.
     */
    val adblock = Adblock(app).also { adblock ->
        background.launch {
            adblock.start(protection.load().disabledFilterLists)
            protection.settings.map { it.disabledFilterLists }.distinctUntilChanged().collect { disabled ->
                if (disabled != adblock.disabled) adblock.start(disabled)
            }
        }
        FilterListUpdateWorker.schedule(app)
    }

    private val guard = NavigationGuard(OkHttpSiteKey, adblock)

    private val cosmetics by lazy {
        CosmeticFilter(adblock, CosmeticFilter.loadScript(app), MainScope()) { page ->
            guard.filtersPage(page, protection.settings.value)
        }
    }

    val webViews = WebViewPool(
        app,
        detection = { detection },
        adblock = adblock,
        cosmetics = { cosmetics },
        guard = guard,
        protection = { protection.settings.value }
    )

    val pageImages = ReaderImages(app)
}
