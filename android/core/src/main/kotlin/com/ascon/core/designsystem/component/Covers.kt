package com.ascon.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.graphics.Glow
import com.ascon.core.designsystem.graphics.Screentone
import com.ascon.core.designsystem.graphics.ScreentoneFade
import com.ascon.core.designsystem.graphics.cssLinearGradient
import com.ascon.core.designsystem.graphics.darken
import com.ascon.core.designsystem.graphics.ensureContrast
import com.ascon.core.model.Cover

private const val COVER_ANGLE = 170f
private const val COVER_MIDDLE_STOP = 0.55f

/** A cover image clipped to [shape]. Placeholders draw the gradient and a light tone. */
@Composable
fun CoverArt(cover: Cover, shape: RoundedCornerShape, modifier: Modifier = Modifier, toneAlpha: Float = 0.12f) {
    Box(modifier.clip(shape)) {
        when (cover) {
            is Cover.Placeholder -> {
                val colors = remember(cover) { cover.colors() }
                Box(
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            drawRect(
                                cssLinearGradient(
                                    COVER_ANGLE,
                                    size,
                                    0f to colors[0],
                                    COVER_MIDDLE_STOP to colors[1],
                                    1f to colors[2]
                                )
                            )
                        }
                )
                Screentone(
                    Color.Black.copy(alpha = toneAlpha),
                    Modifier.matchParentSize(),
                    dotRadius = 1.dp,
                    pitch = 5.dp
                )
            }
        }
    }
}

private fun Cover.Placeholder.colors() = listOf(Color(top), Color(middle), Color(bottom))

/**
 * The two header colors and the glow derived from a cover, per the cover tint recipe in
 * design/tokens.md. Color A is darkened until white text on it passes 4.5:1.
 */
@Immutable
data class CoverTint(val start: Color, val end: Color, val glow: List<Color>) {
    companion object {
        /** Used when no swatches can be extracted. */
        val Fallback = CoverTint(Color(0xFF7A1E2C), Color(0xFF24132F), listOf(Color(0xFF7A1E2C), Color(0xFF24132F)))
    }
}

private const val START_DARKEN = 0.68f
private const val END_DARKEN = 0.84f

/**
 * Real covers will use the AndroidX Palette dark muted and dark vibrant swatches. For
 * placeholder gradients the middle and bottom stops play those roles.
 */
fun coverTint(cover: Cover): CoverTint = when (cover) {
    is Cover.Placeholder -> {
        val colors = cover.colors()
        CoverTint(
            start = ensureContrast(colors[1].darken(START_DARKEN)),
            end = colors[2].darken(END_DARKEN),
            glow = colors
        )
    }
}

/**
 * Where the glow sits and how the tone fades in a [CoverTintBackground]. The defaults
 * are the home header; the series header mirrors them.
 */
data class CoverTintLayout(
    val gradientAngle: Float = 155f,
    val glowAlignment: Alignment = Alignment.TopEnd,
    /** Positive x pushes the glow right, past the edge for a TopEnd glow. */
    val glowOffset: DpOffset = DpOffset(60.dp, 40.dp),
    val glowSize: DpOffset = DpOffset(260.dp, 300.dp),
    val glowBlur: Dp = 42.dp,
    val glowOpacity: Float = 0.55f,
    val toneAlpha: Float = 0.20f,
    val toneFade: ScreentoneFade = ScreentoneFade.out(angleDegrees = 215f, end = 0.62f)
) {
    companion object {
        val Home = CoverTintLayout()
        val Series = CoverTintLayout(
            gradientAngle = 160f,
            glowAlignment = Alignment.TopStart,
            glowOffset = DpOffset((-40).dp, 30.dp),
            glowSize = DpOffset(300.dp, 320.dp),
            glowBlur = 48.dp,
            glowOpacity = 0.5f,
            toneAlpha = 0.18f,
            toneFade = ScreentoneFade.into(angleDegrees = 145f, start = 0.30f)
        )
    }
}

/**
 * A series-driven header background: a gradient from the cover's colors, a blurred glow
 * partly off-screen, and the screentone fading across. Never the raw cover image.
 * Text on top is always white.
 */
@Composable
fun CoverTintBackground(
    tint: CoverTint,
    modifier: Modifier = Modifier,
    layout: CoverTintLayout = CoverTintLayout.Home,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clipToBounds()
            .drawBehind { drawRect(cssLinearGradient(layout.gradientAngle, size, listOf(tint.start, tint.end))) }
    ) {
        // The glow box grows by the spread on every side; shift it back so the blob itself
        // lands where the layout puts it.
        val spread = layout.glowBlur * 2
        val shiftX = if (layout.glowAlignment == Alignment.TopEnd) spread else -spread
        Glow(
            colors = tint.glow,
            blurRadius = layout.glowBlur,
            opacity = layout.glowOpacity,
            spread = spread,
            // matchParentSize keeps the glow out of the header's measured size; the unbounded
            // wrap lets it hang past the edges.
            modifier = Modifier
                .matchParentSize()
                .wrapContentSize(layout.glowAlignment, unbounded = true)
                .offset(x = layout.glowOffset.x + shiftX, y = layout.glowOffset.y - spread)
                .requiredSize(layout.glowSize.x + spread * 2, layout.glowSize.y + spread * 2)
        )
        Screentone(Color.White.copy(alpha = layout.toneAlpha), Modifier.matchParentSize(), fade = layout.toneFade)
        content()
    }
}
