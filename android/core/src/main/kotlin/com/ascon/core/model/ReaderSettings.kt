package com.ascon.core.model

enum class ReadingMode { LongStrip, LeftToRight, RightToLeft }

/** Width fills the screen's width; Screen fits each page inside the screen. */
enum class PageFit { Width, Screen }

/** Space between pages in long strip. Auto joins slices of one strip and spaces the rest. */
enum class PageGap { Auto, None, Small }

enum class ReaderBackground { Black, Gray, White }

/** How the reader shows pages, for all series or saved for one. */
data class ReaderSettings(
    val mode: ReadingMode = ReadingMode.LongStrip,
    val fit: PageFit = PageFit.Width,
    val gap: PageGap = PageGap.Auto,
    val background: ReaderBackground = ReaderBackground.Black,
    /** Trims plain margins from each page: the sides in long strip, all four edges in paged modes. */
    val cropBorders: Boolean = false,
    val keepScreenOn: Boolean = false,
    val volumeKeys: Boolean = false,
    /** Shades the tap zones while the bars show. */
    val showTapZones: Boolean = false
)
