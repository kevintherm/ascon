package com.ascon.engine.adblock

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterListsTest {
    private fun list(version: Long?, rules: Int = 50) = buildString {
        appendLine("[Adblock Plus 2.0]")
        version?.let { appendLine("! Version: $it") }
        appendLine("! Title: Test")
        repeat(rules) { appendLine("||ads$it.example^") }
    }

    @Test
    fun `the version comes from the list header`() {
        assertEquals(202610091213L, FilterLists.versionOf(list(202610091213)))
        assertNull(FilterLists.versionOf(list(null)))
    }

    @Test
    fun `the newer copy wins, and a copy without a version never replaces one with`() {
        assertEquals(list(2), FilterLists.newer(list(1), list(2)))
        assertEquals(list(2), FilterLists.newer(list(2), list(1)))
        assertEquals(list(1), FilterLists.newer(list(1), list(null)))
        assertEquals(list(1), FilterLists.newer(list(1), null))
    }

    @Test
    fun `a download must look like a filter list`() {
        assertTrue(FilterLists.isFilterList(list(1)))
        assertFalse(FilterLists.isFilterList("<html><body>Rate limited</body></html>"))
        assertFalse(FilterLists.isFilterList("[Adblock Plus 2.0]\n! Version: 3\n"))
    }

    @Test
    fun `the modules are EasyList, EasyPrivacy and the Ascon list, all on by default`() {
        assertEquals(listOf("easylist", "easyprivacy", "ascon"), FilterList.All.map { it.id })
        assertTrue(FilterList.All.all { it.enabledByDefault })
        assertNull(FilterList.All.first { it.id == "ascon" }.updateUrl)
    }

    @Test
    fun `an update saves newer valid downloads and reports whether anything changed`() = runTest {
        val current = mutableMapOf("easylist" to list(1), "easyprivacy" to list(5))
        val served = mapOf(
            FilterList.EasyList.updateUrl to list(2),
            FilterList.EasyPrivacy.updateUrl to list(4)
        )
        val updater = FilterListUpdater(
            current = { current[it.id] },
            fetch = { served[it] },
            save = { list, text -> current[list.id] = text }
        )

        assertTrue(updater.update(listOf(FilterList.EasyList, FilterList.EasyPrivacy, FilterList.Ascon)))
        assertEquals(list(2), current["easylist"])
        assertEquals(list(5), current["easyprivacy"])
        assertFalse(updater.update(listOf(FilterList.EasyList, FilterList.EasyPrivacy)))
    }

    @Test
    fun `a failed or broken download changes nothing`() = runTest {
        val current = mutableMapOf("easylist" to list(1))
        val updater = FilterListUpdater(
            current = { current[it.id] },
            fetch = { url -> if (url == FilterList.EasyList.updateUrl) "Service unavailable" else null },
            save = { list, text -> current[list.id] = text }
        )
        assertFalse(updater.update(listOf(FilterList.EasyList, FilterList.EasyPrivacy)))
        assertEquals(list(1), current["easylist"])
    }
}
