package com.ascon.feature.browser

import com.ascon.core.model.ReadingProgress
import java.math.BigDecimal

// Where a chapter the user comes back to picks up, in the reader or on the site.

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

/** Page [page] of [pageCount], to bring on screen on the chapter at [url]. */
data class ResumeScroll(val url: String, val page: Int, val pageCount: Int)

/**
 * Where the chapter at [url], read as the site shows it, should scroll to when [progress]
 * is partway through its [chapter]. Null when it isn't, or when it would stay at the top.
 */
internal fun resumeScroll(url: String, progress: ReadingProgress, chapter: BigDecimal?): ResumeScroll? {
    val partway = chapter != null &&
        progress.chapter.compareTo(chapter) == 0 &&
        progress.page in 2 until progress.pageCount
    return if (partway) ResumeScroll(url, progress.page, progress.pageCount) else null
}
