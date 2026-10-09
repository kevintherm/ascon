package com.ascon.feature.reader

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PageGapTest {
    private val slice = IntSize(800, 2000)
    private val page = IntSize(800, 1150)

    @Test
    fun `slices of one strip join`() {
        assertEquals(0.dp, autoPageGap(slice, slice))
        // The last slice of a strip is often short.
        assertEquals(0.dp, autoPageGap(slice, IntSize(800, 600)))
    }

    @Test
    fun `separate pages get a small gap`() {
        assertEquals(SmallPageGap, autoPageGap(page, page))
        assertEquals(SmallPageGap, autoPageGap(slice, IntSize(720, 2000)))
    }

    @Test
    fun `no gap until both pages have loaded`() {
        assertEquals(0.dp, autoPageGap(page, null))
        assertEquals(0.dp, autoPageGap(null, page))
    }
}
