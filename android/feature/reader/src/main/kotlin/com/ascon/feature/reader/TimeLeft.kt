package com.ascon.feature.reader

import kotlin.math.ceil

/** Time on one image outside this range is a skim or a pause, and doesn't count toward the pace. */
internal const val MIN_IMAGE_SECONDS = 0.5f
internal const val MAX_IMAGE_SECONDS = 120f

/** The time left shows once this many images are read in this chapter. */
internal const val IMAGES_BEFORE_TIME_LEFT = 3

/** True when [seconds] on one image says something about the reader's pace. */
internal fun countsTowardPace(seconds: Float): Boolean = seconds in MIN_IMAGE_SECONDS..MAX_IMAGE_SECONDS

/** Minutes for [remaining] images at the median of [samples], rounded up. Null when nothing is left or known. */
internal fun minutesLeft(samples: List<Float>, remaining: Int): Int? {
    if (remaining <= 0 || samples.isEmpty()) return null
    val sorted = samples.sorted()
    val middle = sorted.size / 2
    val median = if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    return ceil(remaining * median / SECONDS_PER_MINUTE).toInt().coerceAtLeast(1)
}

private const val SECONDS_PER_MINUTE = 60f
