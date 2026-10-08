package com.ascon.core.designsystem.graphics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** WCAG adds this to both luminances to model screen flare. */
private const val WCAG_FLARE = 0.05f

/** WCAG contrast ratio between two opaque colors, from 1 to 21. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + WCAG_FLARE) / (darker + WCAG_FLARE)
}

/** Scales the RGB channels toward black. [factor] 1 keeps the color, 0 gives black. */
fun Color.darken(factor: Float): Color = Color(
    red = red * factor,
    green = green * factor,
    blue = blue * factor,
    alpha = alpha
)

private const val DARKEN_STEP = 0.92f
private const val MAX_STEPS = 40

/** Darkens [background] until [text] on it reaches [minimum] contrast. */
fun ensureContrast(background: Color, text: Color = Color.White, minimum: Float = 4.5f): Color {
    var color = background
    repeat(MAX_STEPS) {
        if (contrastRatio(color, text) >= minimum) return color
        color = color.darken(DARKEN_STEP)
    }
    return color
}
