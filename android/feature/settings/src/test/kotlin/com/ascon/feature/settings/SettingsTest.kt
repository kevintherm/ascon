package com.ascon.feature.settings

import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.data.AccountBackend
import com.ascon.core.data.AccountRefused
import com.ascon.core.data.GoogleCredential
import com.ascon.core.data.GoogleUnreachable
import com.ascon.core.data.NoGoogleAccount
import com.ascon.core.data.SignInCancelled
import com.ascon.core.data.fake.FakeAccountRepository
import com.ascon.core.data.fake.FakeProtectionSettings
import com.ascon.core.data.fake.FakeReaderSettings
import com.ascon.core.data.fake.FakeSettingsRepository
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.AccountSession
import com.ascon.core.model.AccountState
import com.ascon.core.model.Quota
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `toggles write through to settings`() = runTest {
        val protection = FakeProtectionSettings()
        val reader = FakeReaderSettings()
        val vm = SettingsViewModel(
            FakeSettingsRepository(),
            protection,
            reader,
            FakeAccountRepository(AccountState.SignedOut)
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        assertTrue(vm.state.value.protection.adblockEnabled)
        vm.setBlockAds(false)
        vm.setBlockPopups(false)
        vm.setKeepScreenOn(true)
        assertFalse(vm.state.value.protection.adblockEnabled)
        assertFalse(protection.settings.value.blockPopups)
        assertTrue(reader.allSeries.first().keepScreenOn)
        assertEquals(AccountState.SignedOut, vm.state.value.account)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SignInTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val context = RuntimeEnvironment.getApplication()
    private val accounts = FakeAccountRepository()
    private var picked: () -> GoogleCredential = { GoogleCredential("google-jwt", "Kevin", "kevin@example.com") }
    private val backend = FakeAccountBackend()

    private fun viewModel(withBackend: Boolean = true) = SettingsViewModel(
        FakeSettingsRepository(),
        FakeProtectionSettings(),
        FakeReaderSettings(),
        accounts,
        backend = backend.takeIf { withBackend },
        google = { picked() }
    )

    private fun TestScope.collected(vm: SettingsViewModel) =
        vm.also { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.state.collect {} } }

    @Test
    fun `signing in keeps the backend's token with Google's name`() = runTest {
        val vm = collected(viewModel())
        vm.signIn(context)

        assertEquals(listOf("google-jwt"), backend.idTokens)
        assertEquals("acc-1", accounts.token.value)
        assertEquals(AccountState.SignedIn("Kevin", "kevin@example.com", premium = false), vm.state.value.account)
        assertEquals(SignInStatus.Idle, vm.state.value.signIn)
    }

    @Test
    fun `each failure says why, and backing out says nothing`() = runTest {
        val vm = collected(viewModel())
        val cases = listOf<Pair<() -> GoogleCredential, SignInStatus>>(
            { throw SignInCancelled() } to SignInStatus.Idle,
            { throw NoGoogleAccount() } to SignInStatus.Failed(SignInFailure.NoGoogleAccount),
            { throw GoogleUnreachable() } to SignInStatus.Failed(SignInFailure.GoogleUnreachable)
        )
        for ((pick, status) in cases) {
            picked = pick
            vm.signIn(context)
            assertEquals(status, vm.state.value.signIn)
        }
        assertTrue(backend.idTokens.isEmpty())

        picked = { GoogleCredential("forged", null, "x@example.com") }
        vm.signIn(context)
        assertEquals(SignInStatus.Failed(SignInFailure.Refused), vm.state.value.signIn)
        backend.offline = true
        vm.signIn(context)
        assertEquals(SignInStatus.Failed(SignInFailure.ServerUnreachable), vm.state.value.signIn)
        assertEquals(AccountState.SignedOut, vm.state.value.account)

        val noBackend = collected(viewModel(withBackend = false))
        noBackend.signIn(context)
        assertEquals(SignInStatus.Failed(SignInFailure.Unavailable), noBackend.state.value.signIn)
    }

    @Test
    fun `the quota loads for the sheet, and a refused token signs out`() = runTest {
        val vm = collected(viewModel())
        vm.signIn(context)
        vm.loadQuota()
        assertEquals(QuotaState.Loaded(backend.quota), vm.state.value.quota)

        backend.revoked = true
        vm.loadQuota()
        assertEquals(AccountState.SignedOut, vm.state.value.account)
    }

    @Test
    fun `signing out works offline and ends the token when it can`() = runTest {
        val vm = collected(viewModel())
        vm.signIn(context)
        vm.signOut()
        assertEquals(AccountState.SignedOut, vm.state.value.account)
        assertEquals(listOf("acc-1"), backend.signedOut)

        vm.signIn(context)
        backend.offline = true
        vm.signOut()
        assertEquals(AccountState.SignedOut, vm.state.value.account)
        assertNull(accounts.token.value)
    }
}

private class FakeAccountBackend : AccountBackend {
    val idTokens = mutableListOf<String>()
    val signedOut = mutableListOf<String>()
    var offline = false
    var revoked = false
    val quota = Quota(10, 7, Instant.parse("2026-11-01T00:00:00Z"), premium = false)

    override suspend fun signIn(idToken: String): AccountSession {
        if (offline) throw IOException("offline")
        if (idToken == "forged") throw AccountRefused()
        idTokens += idToken
        return AccountSession("acc-1", "", null, premium = false)
    }

    override suspend fun signOut(token: String) {
        if (offline) throw IOException("offline")
        signedOut += token
    }

    override suspend fun quota(token: String): Quota {
        if (revoked) throw AccountRefused()
        return quota
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class Screenshots {
    @Test
    fun settings() = captureRoboImage("src/test/screenshots/settings.png") {
        AsconTheme { SettingsScreen(previewSettingsState(), SettingsActions(), 120.dp) }
    }

    @Test
    fun settingsSignedOut() = captureRoboImage("src/test/screenshots/settings_signed_out.png") {
        AsconTheme { SettingsScreen(previewSettingsState(signedIn = false), SettingsActions(), 120.dp) }
    }

    @Test
    fun settingsSigningIn() = captureRoboImage("src/test/screenshots/settings_signing_in.png") {
        AsconTheme {
            SettingsScreen(
                previewSettingsState(signedIn = false, signIn = SignInStatus.SigningIn),
                SettingsActions(),
                120.dp
            )
        }
    }

    @Test
    fun settingsSignInFailed() = captureRoboImage("src/test/screenshots/settings_sign_in_failed.png") {
        val failed = SignInStatus.Failed(SignInFailure.GoogleUnreachable)
        AsconTheme {
            SettingsScreen(previewSettingsState(signedIn = false, signIn = failed), SettingsActions(), 120.dp)
        }
    }

    @Test
    fun accountSheetFree() = captureRoboImage("src/test/screenshots/account_sheet_free.png") {
        AsconTheme { SettingsScreen(previewSettingsState(), SettingsActions(), 120.dp, AccountOverlay.Sheet) }
    }

    @Test
    fun accountSheetPremium() = captureRoboImage("src/test/screenshots/account_sheet_premium.png") {
        AsconTheme {
            SettingsScreen(previewSettingsState(premium = true), SettingsActions(), 120.dp, AccountOverlay.Sheet)
        }
    }

    @Test
    fun signOutDialog() = captureRoboImage("src/test/screenshots/sign_out_dialog.png") {
        AsconTheme { SettingsScreen(previewSettingsState(), SettingsActions(), 120.dp, AccountOverlay.SignOut) }
    }
}
