package com.ascon.feature.reader

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.abs

/** The part of an image left after cropping, as edges in pixels: [right] and [bottom] are exclusive. */
internal data class CropBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Channels within this of the margin color still count as margin, for JPEG noise. */
private const val TOLERANCE = 24

/** A line still counts as margin with this share of other pixels, such as dust or a page number's edge. */
private const val STRAY_SHARE = 0.005f

/** A crop never leaves less than this share of a side, so a blank page stays whole. */
private const val MIN_KEPT = 0.25f

private const val CHANNEL = 0xFF
private const val RED = 16
private const val GREEN = 8

/**
 * Where to crop an image whose margins are one plain color, taken from its top-left
 * pixel. [column] and [row] give the pixels of one column or row. Top and bottom are
 * cropped only when [vertical], so slices of a long strip keep joining.
 */
internal fun cropBounds(
    width: Int,
    height: Int,
    vertical: Boolean,
    column: (x: Int) -> IntArray,
    row: (y: Int) -> IntArray
): CropBounds {
    val whole = CropBounds(0, 0, width, height)
    val margin = row(0).firstOrNull()
    fun plain(line: IntArray) = line.count { margin == null || !near(it, margin) } <= line.size * STRAY_SHARE
    fun edge(range: IntProgression, line: (Int) -> IntArray) = range.firstOrNull { !plain(line(it)) }

    // A page that is all margin has no content edge, and stays whole.
    val left = edge(0 until width, column)
    if (left == null) return whole
    val right = (edge(width - 1 downTo 0, column) ?: (width - 1)) + 1
    val top = if (vertical) edge(0 until height, row) ?: 0 else 0
    val bottom = if (vertical) (edge(height - 1 downTo 0, row) ?: (height - 1)) + 1 else height
    val keepsWidth = right - left >= width * MIN_KEPT
    val keepsHeight = bottom - top >= height * MIN_KEPT
    return CropBounds(
        left = if (keepsWidth) left else 0,
        top = if (keepsHeight) top else 0,
        right = if (keepsWidth) right else width,
        bottom = if (keepsHeight) bottom else height
    )
}

private fun near(a: Int, b: Int): Boolean = abs((a shr RED and CHANNEL) - (b shr RED and CHANNEL)) <= TOLERANCE &&
    abs((a shr GREEN and CHANNEL) - (b shr GREEN and CHANNEL)) <= TOLERANCE &&
    abs((a and CHANNEL) - (b and CHANNEL)) <= TOLERANCE

/** Crops plain margins off a page as Coil decodes it. */
internal class CropBorders(private val vertical: Boolean) : Transformation() {
    override val cacheKey: String = "crop-borders-$vertical"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val width = input.width
        val height = input.height
        val bounds = cropBounds(
            width,
            height,
            vertical,
            column = { x -> IntArray(height).also { input.getPixels(it, 0, 1, x, 0, 1, height) } },
            row = { y -> IntArray(width).also { input.getPixels(it, 0, width, 0, y, width, 1) } }
        )
        if (bounds == CropBounds(0, 0, width, height)) return input
        return Bitmap.createBitmap(
            input,
            bounds.left,
            bounds.top,
            bounds.right - bounds.left,
            bounds.bottom - bounds.top
        )
    }
}
