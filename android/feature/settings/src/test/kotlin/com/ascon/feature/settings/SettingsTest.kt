package com.ascon.feature.settings

import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.data.fake.FakeAccountRepository
import com.ascon.core.data.fake.FakeProtectionSettings
import com.ascon.core.data.fake.FakeSettingsRepository
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.AccountState
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
        val vm = SettingsViewModel(FakeSettingsRepository(), protection, FakeAccountRepository(AccountState.SignedOut))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }

        assertTrue(vm.state.value.protection.adblockEnabled)
        vm.setBlockAds(false)
        vm.setBlockPopups(false)
        vm.setKeepScreenOn(true)
        assertFalse(vm.state.value.protection.adblockEnabled)
        assertFalse(protection.settings.value.blockPopups)
        assertTrue(vm.state.value.settings.keepScreenOn)
        assertEquals(AccountState.SignedOut, vm.state.value.account)
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
}
