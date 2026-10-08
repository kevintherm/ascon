package com.ascon.core.designsystem.graphics

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The screentone overlay: a grid of dots that fills its bounds. Place it over content.
 *
 * Dots are drawn once into a single tile and repeated with a shader, so a full-width
 * header costs one rectangle per frame. [fade] is an optional CSS-style mask: the dots
 * keep the alpha of the fade at each point, so the tone can disappear across the area.
 */
@Composable
fun Screentone(
    color: Color,
    modifier: Modifier = Modifier,
    dotRadius: Dp = 1.1.dp,
    pitch: Dp = 7.dp,
    fade: ScreentoneFade? = null
) {
    Spacer(modifier.fillMaxSize().screentone(color, dotRadius, pitch, fade))
}

private fun Modifier.screentone(color: Color, dotRadius: Dp, pitch: Dp, fade: ScreentoneFade?): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithCache {
        val tile = pitch.toPx().roundToInt().coerceAtLeast(2)
        val bitmap = ImageBitmap(tile, tile)
        Canvas(bitmap).drawCircle(
            center = Offset(tile / 2f, tile / 2f),
            radius = dotRadius.toPx(),
            paint = Paint().apply {
                this.color = color
                isAntiAlias = true
            }
        )
        val dots = ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
        val mask: Brush? = fade?.let { cssLinearGradient(it.angleDegrees, size, *it.stops.toTypedArray()) }
        onDrawBehind {
            drawRect(dots)
            if (mask != null) drawRect(mask, blendMode = BlendMode.DstIn)
        }
    }

/** A CSS-style mask: an angle and stops whose colors carry only alpha. */
data class ScreentoneFade(val angleDegrees: Float, val stops: List<Pair<Float, Color>>) {
    companion object {
        /** Opaque at the start of the line, gone by [end]. */
        fun out(angleDegrees: Float, end: Float) =
            ScreentoneFade(angleDegrees, listOf(0f to Color.Black, end to Color.Transparent))

        /** Gone until [start], opaque at the end of the line. */
        fun into(angleDegrees: Float, start: Float) =
            ScreentoneFade(angleDegrees, listOf(start to Color.Transparent, 1f to Color.Black))
    }
}
