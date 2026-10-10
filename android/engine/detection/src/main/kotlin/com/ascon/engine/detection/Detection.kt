package com.ascon.engine.detection

import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which step of the lookup order recognized the page. The names are bridge.js's. */
@Serializable
enum class DetectionSource {
    /** A rule made for this domain, cached on the device or from the server. */
    @SerialName("rule")
    Rule,

    /** A hand-written rule for a common manga site theme. */
    @SerialName("builtin")
    BuiltIn,

    /** JSON-LD, og:title and a chapter number in the URL. Least reliable. */
    @SerialName("heuristic")
    Heuristic
}

/** What the bridge found on a page. */
sealed interface Detection {
    val url: String

    data class ChapterPage(
        override val url: String,
        val source: DetectionSource,
        /** The series part of the URL, when the rule names one. */
        val seriesSlug: String?,
        val title: String?,
        val chapterLabel: String?,
        val chapter: BigDecimal?,
        val images: List<String>,
        val next: String?,
        val previous: String?
    ) : Detection

    data class SeriesPage(
        override val url: String,
        val source: DetectionSource,
        val seriesSlug: String?,
        val title: String?,
        val chapters: List<ChapterLink>
    ) : Detection

    /**
     * The page on screen, counted from 1, in a chapter read as the site shows it because
     * the reader could not take its pages. Sent after the chapter's [ChapterPage].
     */
    data class ReadingPosition(
        override val url: String,
        val page: Int,
        val pageCount: Int,
        /** How far down the page the top of the screen is, from 0 up to 1. */
        val offset: Float = 0f
    ) : Detection

    /** Neither a chapter nor a series page. */
    data class None(override val url: String) : Detection
}

data class ChapterLink(val url: String, val label: String?, val number: BigDecimal?)
