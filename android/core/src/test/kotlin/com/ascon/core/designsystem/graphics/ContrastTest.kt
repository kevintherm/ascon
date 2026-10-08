package com.ascon.core.designsystem.graphics

import androidx.compose.ui.graphics.Color
import com.ascon.core.designsystem.theme.AsconColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {
    @Test
    fun `black on white is 21 to 1`() {
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
    }

    @Test
    fun `muted text passes on white and ground as tokens claim`() {
        assertTrue(contrastRatio(AsconColors.TextMuted, AsconColors.Surface) >= 4.5f)
        assertTrue(contrastRatio(AsconColors.TextMuted, AsconColors.Ground) >= 4.5f)
    }

    @Test
    fun `ensureContrast darkens a light color until white text passes`() {
        val light = Color(0xFFE9A27A)
        val fixed = ensureContrast(light)
        assertTrue(contrastRatio(fixed, Color.White) >= 4.5f)
    }

    @Test
    fun `ensureContrast keeps a color that already passes`() {
        val dark = Color(0xFF24132F)
        assertEquals(dark, ensureContrast(dark))
    }
}
