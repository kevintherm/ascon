package com.ascon.core.designsystem.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class CssGradientTest {
    private val size = Size(200f, 100f)

    private fun assertNear(expected: Offset, actual: Offset) {
        assertEquals(expected.x, actual.x, 0.01f)
        assertEquals(expected.y, actual.y, 0.01f)
    }

    @Test
    fun `180 degrees runs top to bottom`() {
        val (start, end) = cssGradientLine(180f, size)
        assertNear(Offset(100f, 0f), start)
        assertNear(Offset(100f, 100f), end)
    }

    @Test
    fun `90 degrees runs left to right`() {
        val (start, end) = cssGradientLine(90f, size)
        assertNear(Offset(0f, 50f), start)
        assertNear(Offset(200f, 50f), end)
    }

    @Test
    fun `diagonal line reaches past the corners like CSS`() {
        // CSS: length = |w sin a| + |h cos a|, centered. At 135deg on 200x100 that is 212.13.
        val (start, end) = cssGradientLine(135f, size)
        val half = (200f * 0.70710677f + 100f * 0.70710677f) / 2f
        assertNear(Offset(100f - half * 0.70710677f, 50f - half * 0.70710677f), start)
        assertNear(Offset(100f + half * 0.70710677f, 50f + half * 0.70710677f), end)
    }
}
