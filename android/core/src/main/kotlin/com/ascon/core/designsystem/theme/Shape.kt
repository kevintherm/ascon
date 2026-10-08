package com.ascon.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii and spacing from design/tokens.md. */
object AsconRadius {
    val Sheet = 28.dp
    val Card = 20.dp
    val SourceCard = 18.dp
    val Tile = 16.dp
    val Chip = 12.dp
    val CoverLarge = 12.dp
    val CoverThumb = 8.dp

    /** Below this the nesting math gives curves too small to read as intentional. */
    val Smallest = 4.dp

    /**
     * Outer radius = inner radius + gap, so the inner radius is the outer minus the gap.
     * The gap is the real distance from the inner element's edge to the container's edge.
     */
    fun nested(outer: Dp, gap: Dp): Dp = (outer - gap).coerceAtLeast(Smallest)
}

object AsconSpacing {
    val Gutter = 16.dp
    val SectionGap = 18.dp
    val MinTouchTarget = 44.dp
}
