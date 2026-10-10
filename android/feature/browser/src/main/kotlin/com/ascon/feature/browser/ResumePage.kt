package com.ascon.feature.browser

import com.ascon.core.model.ReadingProgress
import java.math.BigDecimal

/**
 * The page to reopen [chapter] at, when [progress] is partway through it, counted from 1.
 * The saved page may come from a count of the site's pages, so it is scaled to the
 * reader's [imageCount]. Null when the chapter isn't partly read.
 */
internal fun resumePage(progress: ReadingProgress, chapter: BigDecimal?, imageCount: Int): Int? {
    val partway = chapter != null &&
        progress.chapter.compareTo(chapter) == 0 &&
        progress.page in 1 until progress.pageCount
    return if (partway && imageCount > 0) {
        ((progress.page - 1) * imageCount / progress.pageCount + 1).coerceIn(1, imageCount)
    } else {
        null
    }
}
