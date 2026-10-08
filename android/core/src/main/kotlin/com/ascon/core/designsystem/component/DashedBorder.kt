package com.ascon.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** A dashed rounded border drawn inside the bounds, for "add" tiles and empty slots. */
fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp, dash: Dp, gap: Dp): Modifier = drawWithCache {
    val strokePx = width.toPx()
    val inset = strokePx / 2
    val stroke = Stroke(
        width = strokePx,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))
    )
    val r = (radius.toPx() - inset).coerceAtLeast(0f)
    onDrawBehind {
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokePx, size.height - strokePx),
            cornerRadius = CornerRadius(r),
            style = stroke
        )
    }
}
