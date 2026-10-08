package com.ascon.core.designsystem.graphics

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val CORNER = 40.dp

/**
 * A soft blurred blob of color behind a cover-tinted header.
 *
 * The blob is drawn inset by [spread] inside the bounds, because blur output is clipped
 * to the layer: without the margin the blob shows hard edges. Size the glow to the blob
 * plus [spread] on every side.
 *
 * Real blur needs Android 12. Older versions get a radial gradient of the middle color,
 * which reads the same at this softness.
 */
@Composable
fun Glow(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    angleDegrees: Float = 200f,
    blurRadius: Dp = 42.dp,
    opacity: Float = 0.55f,
    spread: Dp = blurRadius * 2
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Box(
            modifier
                .alpha(opacity)
                .blur(blurRadius, BlurredEdgeTreatment.Unbounded)
                .drawBehind {
                    val inset = spread.toPx()
                    val blob = Size(size.width - inset * 2, size.height - inset * 2)
                    translate(inset, inset) {
                        drawRoundRect(
                            brush = cssLinearGradient(angleDegrees, blob, colors),
                            size = blob,
                            cornerRadius = CornerRadius(CORNER.toPx())
                        )
                    }
                }
        )
    } else {
        val middle = colors[colors.size / 2]
        Box(
            modifier.drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(middle.copy(alpha = opacity), Color.Transparent),
                        radius = size.maxDimension / 2f
                    )
                )
            }
        )
    }
}
