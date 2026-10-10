package com.ascon.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeLeftTest {
    @Test
    fun `remaining images times the median pace, rounded up`() {
        // Median 10 seconds; 30 images is 5 minutes, 31 is just over.
        assertEquals(5, minutesLeft(listOf(4f, 10f, 90f), remaining = 30))
        assertEquals(6, minutesLeft(listOf(4f, 10f, 90f), remaining = 31))
        assertEquals(1, minutesLeft(listOf(8f, 12f), remaining = 1))
    }

    @Test
    fun `nothing left or nothing learned shows no time`() {
        assertNull(minutesLeft(listOf(10f), remaining = 0))
        assertNull(minutesLeft(emptyList(), remaining = 12))
    }

    @Test
    fun `skims and pauses don't count`() {
        assertFalse(countsTowardPace(0.2f))
        assertTrue(countsTowardPace(6f))
        assertFalse(countsTowardPace(600f))
    }
}
