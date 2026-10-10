package com.ascon.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomTest {
    private val width = 1000f

    @Test
    fun `zooming keeps the point under the fingers in place`() {
        val zoom = Zoom().zoomBy(2f, focusX = 300f, width = width)
        assertEquals(2f, zoom.scale)
        // The point at 300 was 300 into the page and is now 600 into it, still at 300 on screen.
        assertEquals(300f, zoom.pan)
    }

    @Test
    fun `zoom stays between 1x and 4x`() {
        assertEquals(4f, Zoom().zoomBy(10f, 500f, width).scale)
        assertEquals(1f, Zoom(2f, 500f).zoomBy(0.1f, 500f, width).scale)
        assertEquals(0f, Zoom(2f, 500f).zoomBy(0.1f, 500f, width).pan)
    }

    @Test
    fun `panning stops at the page edges`() {
        val zoom = Zoom(2f, 500f)
        assertEquals(1000f, zoom.panBy(-800f, width).pan)
        assertEquals(0f, zoom.panBy(800f, width).pan)
        assertEquals(400f, zoom.panBy(100f, width).pan)
    }

    @Test
    fun `double tap zooms 2x at the point and again resets`() {
        val zoomed = Zoom().toggled(focusX = 900f, width = width)
        assertEquals(Zoom(2f, 900f), zoomed)
        assertEquals(Zoom(), zoomed.toggled(focusX = 100f, width = width))
    }

    @Test
    fun `side thirds scroll and the middle toggles the bars`() {
        assertEquals(TapZone.Back, tapZone(x = 100f, width = 900f))
        assertEquals(TapZone.Middle, tapZone(x = 450f, width = 900f))
        assertEquals(TapZone.Forward, tapZone(x = 800f, width = 900f))
    }
}
