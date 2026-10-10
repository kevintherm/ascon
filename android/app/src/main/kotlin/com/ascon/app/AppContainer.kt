package com.ascon.app

import android.content.Context
import android.content.pm.ApplicationInfo
import com.ascon.core.data.AccountRepository
import com.ascon.core.data.LibraryRepository
import com.ascon.core.data.ProtectionSettingsRepository
import com.ascon.core.data.ReaderSettingsRepository
import com.ascon.core.data.ReadingPaceRepository
import com.ascon.core.data.RuleStore
import com.ascon.core.data.SettingsRepository
import com.ascon.core.data.datastore.DataStoreDeviceToken
import com.ascon.core.data.datastore.DataStoreProtectionSettings
import com.ascon.core.data.datastore.DataStoreReaderSettings
import com.ascon.core.data.fake.FakeAccountRepository
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.data.fake.FakeSettingsRepository
import com.ascon.core.data.room.AsconDatabase
import com.ascon.core.data.room.RoomLibraryRepository
import com.ascon.core.data.room.RoomRuleStore
import com.ascon.core.model.AccountState
import com.ascon.engine.adblock.Adblock
import com.ascon.engine.adblock.CosmeticFilter
import com.ascon.engine.adblock.FilterListUpdateWorker
import com.ascon.engine.detection.DetectionHost
import com.ascon.engine.detection.HttpRuleBackend
import com.ascon.engine.detection.RuleBackend
import com.ascon.engine.detection.RuleHealth
import com.ascon.engine.detection.RuleHealthWorker
import com.ascon.engine.detection.RuleLookup
import com.ascon.engine.detection.RuleVerifier
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Manual dependency injection: one instance of each repository for the whole app.
 * General settings and the account are fakes until sign-in exists.
 */
class AppContainer(context: Context, val clock: Clock = Clock.systemDefaultZone()) {
    private val app = context.applicationContext

    private val debuggable = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    // Debug builds start with the sample library, so every screen has something to show.
    private val database = AsconDatabase.open(app)

    val library: LibraryRepository = RoomLibraryRepository(
        database,
        seed = if (debuggable) RoomLibraryRepository.Seed(FakeLibrary.series(clock), FakeLibrary.sites) else null
    )
    val settings: SettingsRepository = FakeSettingsRepository()

    /** Work that outlives every screen, such as saving settings and rebuilding the ad blocker. */
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val protection: ProtectionSettingsRepository = DataStoreProtectionSettings.open(app, background)
    private val readerStore = DataStoreReaderSettings.open(app, background)
    val reader: ReaderSettingsRepository = readerStore
    val readingPace: ReadingPaceRepository = readerStore
    val accounts: AccountRepository = FakeAccountRepository(
        AccountState.SignedIn(
            displayName = "Kevin",
            premium = false,
            lastSyncedAt = clock.instant().minus(Duration.ofMinutes(2))
        )
    )

    /** Rules fetched for single sites, and how they did. */
    val ruleStore: RuleStore = RoomRuleStore(database)

    /** Null when this build has no backend to ask. */
    val ruleBackend: RuleBackend? = BackendConfig.BASE_URL?.let { url ->
        HttpRuleBackend(
            baseUrl = url.toHttpUrl(),
            client = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(BACKEND_TIMEOUT_SECONDS)).build(),
            tokens = DataStoreDeviceToken.open(app, background),
            appVersion = app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
        )
    }

    private val detection by lazy {
        DetectionHost(
            script = DetectionHost.loadScript(app),
            rules = RuleLookup(
                store = ruleStore,
                builtIn = DetectionHost.loadBuiltInRules(app),
                backend = ruleBackend,
                verifier = RuleVerifier(BackendConfig.publicKeys),
                scope = background
            ),
            scope = MainScope(),
            health = RuleHealth(ruleStore)
        ).also { RuleHealthWorker.schedule(app) }
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

    private companion object {
        const val BACKEND_TIMEOUT_SECONDS = 10L
    }
}
