package com.ascon.core.designsystem

import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.theme.AsconRadius
import org.junit.Assert.assertEquals
import org.junit.Test

/** The worked examples from design/tokens.md. */
class RadiusTest {
    @Test
    fun `nested radius matches the token table`() {
        assertEquals(18.dp, AsconRadius.nested(28.dp, 10.dp)) // bottom nav item
        assertEquals(14.dp, AsconRadius.nested(28.dp, 14.dp)) // detection card
        assertEquals(12.dp, AsconRadius.nested(16.dp, 4.dp)) // switch knob
        assertEquals(12.dp, AsconRadius.nested(20.dp, 8.dp)) // tab card
    }

    @Test
    fun `nested radius never drops below 4`() {
        assertEquals(4.dp, AsconRadius.nested(12.dp, 10.dp))
    }
}
