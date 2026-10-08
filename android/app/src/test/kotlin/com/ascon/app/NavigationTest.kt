package com.ascon.app

import androidx.navigation3.runtime.NavKey
import com.ascon.core.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
    private fun stack(vararg routes: NavKey) = mutableListOf(*routes)

    @Test
    fun `a tab sits directly on home`() {
        val s = stack(Route.Home, Route.Series("a"))
        s.openTab(Route.Settings)
        assertEquals(listOf(Route.Home, Route.Settings), s)
    }

    @Test
    fun `opening home clears everything above it`() {
        val s = stack(Route.Home, Route.Library(), Route.Series("a"))
        s.openTab(Route.Home)
        assertEquals(listOf<NavKey>(Route.Home), s)
    }

    @Test
    fun `opening the current tab does nothing`() {
        val s = stack(Route.Home, Route.Library(ReadingStatus.Paused))
        s.openTab(Route.Library(ReadingStatus.Paused))
        assertEquals(listOf(Route.Home, Route.Library(ReadingStatus.Paused)), s)
    }

    @Test
    fun `back from a tab lands on home, back from home leaves`() {
        val s = stack(Route.Home, Route.Browse)
        assertTrue(s.pop())
        assertEquals(listOf<NavKey>(Route.Home), s)
        assertFalse(s.pop())
    }

    @Test
    fun `the nav bar shows on tabs and hides on details`() {
        assertTrue(stack(Route.Home, Route.Library()).showsNavBar())
        assertFalse(stack(Route.Home, Route.Series("a")).showsNavBar())
    }

    @Test
    fun `the current tab is the nearest tab under a detail screen`() {
        assertEquals(Tab.Library, stack(Route.Home, Route.Library(), Route.Series("a")).currentTab())
        assertEquals(Tab.Home, stack(Route.Home, Route.Series("a")).currentTab())
    }

    @Test
    fun `pushing the same detail twice keeps one copy`() {
        val s = stack(Route.Home)
        s.push(Route.Series("a"))
        s.push(Route.Series("a"))
        assertEquals(listOf(Route.Home, Route.Series("a")), s)
    }
}
