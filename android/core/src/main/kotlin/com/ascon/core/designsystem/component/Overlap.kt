package com.ascon.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

/**
 * Slides the element up by [amount] over whatever sits above it, and gives the space back
 * so the elements below do not move. Used for content sheets that overlap a header,
 * including inside lazy lists where a plain offset would leave a gap.
 */
fun Modifier.overlapAbove(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) {
        placeable.place(0, -shift)
    }
}
