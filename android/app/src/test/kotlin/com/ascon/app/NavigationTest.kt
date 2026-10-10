package com.ascon.app

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
    private fun stack(vararg routes: NavKey) = mutableListOf(*routes)

    @Test
    fun `back from a tab lands on home, back from home leaves`() {
        assertEquals(Tab.Home, Tab.Library.back())
        assertEquals(Tab.Home, Tab.Settings.back())
        assertNull(Tab.Home.back())
    }

    @Test
    fun `popping a detail returns to the tabs, popping the tabs does nothing`() {
        val s = stack(Route.Root, Route.Series("a"))
        assertTrue(s.pop())
        assertEquals(listOf<NavKey>(Route.Root), s)
        assertFalse(s.pop())
    }

    @Test
    fun `the tabs and nav show only when no detail covers them`() {
        assertTrue(stack(Route.Root).showsTabs())
        assertFalse(stack(Route.Root, Route.Series("a")).showsTabs())
    }

    @Test
    fun `pushing the same detail twice keeps one copy`() {
        val s = stack(Route.Root)
        s.push(Route.Series("a"))
        s.push(Route.Series("a"))
        assertEquals(listOf(Route.Root, Route.Series("a")), s)
    }

    @Test
    fun `a chapter goes back to the open browser instead of stacking another`() {
        val s = stack(Route.Root, Route.Browser("https://a.example/1"), Route.Series("a"))
        assertTrue(s.popToBrowser())
        assertEquals(listOf(Route.Root, Route.Browser("https://a.example/1")), s)
        assertFalse(stack(Route.Root, Route.Series("a")).popToBrowser())
    }
}

class SlideDirectionTest {
    @Test
    fun `direction follows the tab order and holds until the next switch`() {
        val direction = SlideDirection(Tab.Home)
        assertEquals(1, direction.after(Tab.Settings))
        // Recomposing with the same tab keeps the direction for the running slide.
        assertEquals(1, direction.after(Tab.Settings))
        assertEquals(-1, direction.after(Tab.Library))
        assertEquals(-1, direction.after(Tab.Library))
        assertEquals(1, direction.after(Tab.Browse))
        assertEquals(-1, direction.after(Tab.Home))
    }
}
