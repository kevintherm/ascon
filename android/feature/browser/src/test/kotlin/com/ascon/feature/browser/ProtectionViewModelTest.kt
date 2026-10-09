package com.ascon.feature.browser

import com.ascon.core.data.fake.FakeProtectionSettings
import com.ascon.core.model.ProtectionSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProtectionViewModelTest {
    private val settings = FakeProtectionSettings()
    private lateinit var vm: ProtectionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = ProtectionViewModel(settings, sites = { host -> host.split('.').takeLast(2).joinToString(".") })
        vm.onPage("https://www.mangafire.to/read/aztec/chapter-12")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the sheet speaks of the page's site`() {
        assertEquals("mangafire.to", vm.state.value.site)
        assertFalse(vm.state.value.trusted)
    }

    @Test
    fun `a switch saves the setting, then asks for a reload`() {
        vm.setAdblock(false)
        assertFalse(settings.settings.value.adblockEnabled)
        assertTrue(vm.state.value.reload)
        vm.reloaded()
        assertFalse(vm.state.value.reload)

        vm.setBlockPopups(false)
        assertEquals(ProtectionSettings(adblockEnabled = false, blockPopups = false), settings.settings.value)
    }

    @Test
    fun `trusting is per site`() {
        vm.setTrusted(true)
        assertEquals(setOf("mangafire.to"), settings.settings.value.trustedSites)
        assertTrue(vm.state.value.trusted)
        vm.onPage("https://other.example/")
        assertFalse(vm.state.value.trusted)
    }

    @Test
    fun `site looks broken trusts the site with an undo`() {
        vm.turnOffForSite()
        assertTrue(vm.state.value.trusted)
        assertTrue(vm.state.value.reload)
        val undo = vm.state.value.undo
        assertEquals("mangafire.to", undo?.site)

        vm.reloaded()
        vm.undoTrust()
        assertFalse(vm.state.value.trusted)
        assertTrue(vm.state.value.reload)
        assertNull(vm.state.value.undo)
    }

    @Test
    fun `the undo goes away once shown`() {
        vm.turnOffForSite()
        val id = vm.state.value.undo!!.id
        vm.undoShown(id + 1)
        assertEquals(id, vm.state.value.undo?.id)
        vm.undoShown(id)
        assertNull(vm.state.value.undo)
    }
}
