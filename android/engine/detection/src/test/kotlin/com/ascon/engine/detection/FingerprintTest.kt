package com.ascon.engine.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintTest {
    private val theme = listOf("generator:madara") +
        (1..120).map { "class:wp-manga-$it" } +
        (1..80).map { "tag:div>tag-$it" }

    @Test
    fun `a fingerprint is 16 hex digits and the same for the same features in any order`() {
        val print = Fingerprint.of(theme)!!
        assertTrue(Regex("^[0-9a-f]{16}$").matches(print))
        assertEquals(print, Fingerprint.of(theme.reversed() + theme.take(10)))
        assertEquals("af63de4c8601eda4", Fingerprint.of(listOf("a", "b", "c")))
    }

    @Test
    fun `a mirror with one change stays within the backend's distance, another theme doesn't`() {
        // Each changed feature can flip a few bits, so the backend's limit of 4 allows little.
        val mirror = theme.drop(1) + "class:mirror-logo"
        val other = (1..200).map { "class:other-theme-$it" }
        assertTrue(Fingerprint.distance(Fingerprint.of(theme)!!, Fingerprint.of(mirror)!!) <= 4)
        assertTrue(Fingerprint.distance(Fingerprint.of(theme)!!, Fingerprint.of(other)!!) > 4)
    }

    @Test
    fun `a page with no features has no fingerprint`() {
        assertNull(Fingerprint.of(emptyList()))
    }
}
