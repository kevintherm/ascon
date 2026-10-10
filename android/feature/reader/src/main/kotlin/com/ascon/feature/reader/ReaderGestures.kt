package com.ascon.feature.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

/** Pinch zoom goes up to this. */
internal const val MAX_ZOOM = 4f

/** A double tap zooms this far. */
internal const val DOUBLE_TAP_ZOOM = 2f

/**
 * How far the pages are zoomed, and [pan], how far the zoomed pages' left edge sits left
 * of the screen's. Vertical position is the list's own scroll.
 */
internal data class Zoom(val scale: Float = 1f, val pan: Float = 0f, val panY: Float = 0f) {
    val zoomed: Boolean get() = scale > 1f

    /**
     * Scales by [factor], within 1x to 4x, keeping [focusX] on a [width] wide screen in
     * place. Paged modes pass [focusY] and [height] too; long strip scrolls instead.
     */
    fun zoomBy(factor: Float, focusX: Float, width: Float, focusY: Float = 0f, height: Float = 0f): Zoom {
        val next = (scale * factor).coerceIn(1f, MAX_ZOOM)
        val grown = next / scale
        return Zoom(next, (pan + focusX) * grown - focusX, (panY + focusY) * grown - focusY).clamped(width, height)
    }

    /** Follows a finger that moved [dx] sideways and, in paged modes, [dy] down. */
    fun panBy(dx: Float, width: Float, dy: Float = 0f, height: Float = 0f): Zoom =
        copy(pan = pan - dx, panY = panY - dy).clamped(width, height)

    /** A double tap: 2x at the point, or back to 1x. */
    fun toggled(focusX: Float, width: Float, focusY: Float = 0f, height: Float = 0f): Zoom =
        if (zoomed) Zoom() else zoomBy(DOUBLE_TAP_ZOOM / scale, focusX, width, focusY, height)

    private fun clamped(width: Float, height: Float) = copy(
        pan = pan.coerceIn(0f, width * (scale - 1)),
        panY = panY.coerceIn(0f, height * (scale - 1))
    )
}

internal enum class TapZone { Back, Middle, Forward }

/** The left third scrolls back, the right third forward, and the middle toggles the bars. */
internal fun tapZone(x: Float, width: Float): TapZone = when {
    x < width * SIDE_ZONE -> TapZone.Back
    x > width * (1 - SIDE_ZONE) -> TapZone.Forward
    else -> TapZone.Middle
}

/**
 * Pages a tap in [zone] turns in paged modes: -1, 0 or 1. Right to left mirrors the
 * sides, so the left third turns forward.
 */
internal fun pageTurn(zone: TapZone, rightToLeft: Boolean): Int {
    val forward = when (zone) {
        TapZone.Back -> -1
        TapZone.Middle -> 0
        TapZone.Forward -> 1
    }
    return if (rightToLeft) -forward else forward
}

/** Each side zone's share of the screen's width. */
private const val SIDE_ZONE = 1 / 3f

/**
 * Two fingers pinch and pan, and take the gesture from the list. One finger on zoomed
 * pages pans; it takes the gesture too when [ownsPan], or else the list scrolls with it.
 * Reads events before the list does.
 */
internal fun Modifier.pinchAndPan(
    zoomed: () -> Boolean,
    ownsPan: Boolean = false,
    onPan: (Offset) -> Unit,
    onPinch: (factor: Float, centroid: Offset, pan: Offset) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            when {
                pressed.size >= 2 -> {
                    onPinch(event.calculateZoom(), event.calculateCentroid(useCurrent = true), event.calculatePan())
                    event.changes.forEach { it.consume() }
                }
                pressed.size == 1 && zoomed() -> {
                    onPan(pressed.first().positionChange())
                    if (ownsPan) pressed.first().consume()
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
