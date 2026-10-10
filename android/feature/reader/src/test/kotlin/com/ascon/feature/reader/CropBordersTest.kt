package com.ascon.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class CropBordersTest {
    private val white = 0xFFFFFFFF.toInt()
    private val ink = 0xFF101010.toInt()

    /** A [width] by [height] white image with ink from [left], [top] to [right], [bottom], exclusive. */
    private fun bounds(width: Int, height: Int, ink: CropBounds, vertical: Boolean, noise: Int = 0): CropBounds {
        val pixels = Array(height) { y ->
            IntArray(width) { x ->
                val inside = x in ink.left until ink.right && y in ink.top until ink.bottom
                if (inside) this.ink else white - noise
            }
        }
        return cropBounds(width, height, vertical, column = { x ->
            IntArray(height) { pixels[it][x] }
        }, row = { pixels[it] })
    }

    @Test
    fun `paged modes crop all four plain margins`() {
        assertEquals(CropBounds(10, 5, 90, 150), bounds(100, 160, CropBounds(10, 5, 90, 150), vertical = true))
    }

    @Test
    fun `long strip crops only the sides`() {
        assertEquals(CropBounds(10, 0, 90, 160), bounds(100, 160, CropBounds(10, 5, 90, 150), vertical = false))
    }

    @Test
    fun `slight noise in the margin still counts as margin`() {
        assertEquals(
            CropBounds(10, 5, 90, 150),
            bounds(100, 160, CropBounds(10, 5, 90, 150), vertical = true, noise = 0x0A0A0A)
        )
    }

    @Test
    fun `a page with no margins or almost no content stays whole`() {
        assertEquals(CropBounds(0, 0, 100, 160), bounds(100, 160, CropBounds(0, 0, 100, 160), vertical = true))
        assertEquals(CropBounds(0, 0, 100, 160), bounds(100, 160, CropBounds(45, 70, 55, 80), vertical = true))
        assertEquals(CropBounds(0, 0, 100, 160), bounds(100, 160, CropBounds(0, 0, 0, 0), vertical = true))
    }
}
