package com.ascon.core.model

import java.math.BigDecimal

/**
 * A chapter the browser found pages for, handed to the reader. [pages] are image URLs
 * in reading order; [next] and [previous] are chapter page URLs on the site.
 */
data class ReaderChapter(
    val url: String,
    val title: String?,
    val chapter: BigDecimal?,
    /** Set when the chapter belongs to a series in the library, so progress is saved. */
    val seriesId: String?,
    val pages: List<String>,
    val next: String?,
    val previous: String?
)
