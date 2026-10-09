package com.ascon.feature.reader

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** The gap between pages that are separate pages, per notes.md. */
internal val SmallPageGap = 6.dp

/** A page this much taller than wide is a slice of a long strip. */
private const val SLICE_RATIO = 1.6f

/**
 * The Auto page gap between two consecutive images in long strip. Slices of one strip,
 * the same width with either one at least 1.6 times as tall as wide, join without a gap,
 * so the strip reads as one image. Other pages get [SmallPageGap]. Either counts, so the
 * short last slice of a strip still joins. Until both have loaded there is no gap, so
 * strips, the common case, do not jump as they load.
 */
internal fun autoPageGap(above: IntSize?, below: IntSize?): Dp = when {
    above == null || below == null -> 0.dp
    above.width == below.width && (above.isSlice() || below.isSlice()) -> 0.dp
    else -> SmallPageGap
}

private fun IntSize.isSlice(): Boolean = width > 0 && height >= width * SLICE_RATIO
