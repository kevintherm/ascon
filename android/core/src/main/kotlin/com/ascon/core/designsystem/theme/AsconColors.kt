package com.ascon.core.designsystem.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Color tokens from design/tokens.md. Names match the token names there. */
object AsconColors {
    // Light surfaces
    val Ground = Color(0xFFF3F4F6)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFE6E8EC)
    val SurfaceSunken = Color(0xFFECEEF1)
    val Border = Color(0xFFD5D8DE)
    val BorderDashed = Color(0xFFC9CDD4)

    /** Lines between rows in a group on the ground, such as in sheets. */
    val DividerOnGround = Color(0xFFE3E5EA)

    // Ink and text
    val Ink = Color(0xFF15161A)
    val Ink2 = Color(0xFF26282E)
    val TextMuted = Color(0xFF5B5F69)

    /** Chevrons and read-state icons only, never body text. */
    val TextSubtle = Color(0xFF8A8E97)

    // Dark surfaces, reader and browser
    val ReaderGround = Color(0xFF0E0F12)
    val DarkCard = Color(0xFF1B1C21)
    val BrowserGround = Color(0xFF1B1C20)
    val Glass = Color(0xC714151A)
    val GlassBorder = Color(0x14FFFFFF)
    val OnDarkMuted = Color(0xB8FFFFFF)
    val OnDarkFill = Color(0x14FFFFFF)

    // Accent
    val Accent = Color(0xFFD9472B)

    /** Only as the start of the brand gradient. */
    val Accent2 = Color(0xFFF5A54A)

    /** Sync status dot only. */
    val Success = Color(0xFF2E9E5B)

    /** Sheet scrim. */
    val Scrim = Color(0x8C0E0F12)

    /** Floating nav and bars. */
    val ShadowFloating = Color(0x4715161A)

    /** Hero covers. */
    val ShadowCover = Color(0x59000000)

    /**
     * accent2 → accent, left to right. Only where something fills up or costs money:
     * reading progress, the page scrubber, import progress and the Plus upsell.
     */
    val BrandGradient: Brush = Brush.horizontalGradient(listOf(Accent2, Accent))
}
