package com.ascon.core.designsystem.graphics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Start and end of a CSS `linear-gradient(<angle>deg, …)` drawn over [size].
 *
 * CSS measures the angle clockwise from "to top", and stretches the gradient line so
 * the corners get exactly the first and last colors. The mockups use CSS angles, so
 * matching the math keeps headers looking like the approved screens.
 */
fun cssGradientLine(angleDegrees: Float, size: Size): Pair<Offset, Offset> {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
    val center = size.center
    return Offset(center.x - dx * half, center.y - dy * half) to Offset(center.x + dx * half, center.y + dy * half)
}

/** A CSS-style linear gradient. [stops] pairs a position from 0 to 1 with a color. */
fun cssLinearGradient(angleDegrees: Float, size: Size, vararg stops: Pair<Float, Color>): Brush {
    val (start, end) = cssGradientLine(angleDegrees, size)
    return Brush.linearGradient(colorStops = stops, start = start, end = end)
}

/** Evenly spaced CSS-style linear gradient. */
fun cssLinearGradient(angleDegrees: Float, size: Size, colors: List<Color>): Brush {
    val (start, end) = cssGradientLine(angleDegrees, size)
    return Brush.linearGradient(colors = colors, start = start, end = end)
}
