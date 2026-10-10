package com.ascon.core.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Where the user is with a series. Chips and filters follow this order. */
enum class ReadingStatus { Reading, Plan, Paused, Done }

/**
 * One series, however many sites carry it. Sources link it to sites; progress is kept
 * by chapter number, not by source, so switching sources keeps the place.
 */
data class Series(
    val id: String,
    val title: String,
    val altTitles: List<String>,
    val cover: Cover,
    val status: ReadingStatus,
    val linkedToAniList: Boolean,
    val sources: List<Source>,
    /** Every known chapter, oldest first. */
    val chapters: List<Chapter>,
    val progress: ReadingProgress?,
    val lastReadAt: Instant?
) {
    val latestChapter: Chapter? get() = chapters.lastOrNull()

    /** Unread chapters after the one in progress, oldest first. */
    val upNext: List<Chapter>
        get() = progress?.let { p -> chapters.filter { !it.read && it.number > p.chapter } } ?: emptyList()

    /** Chapters that arrived since the user last looked. */
    val newChapterCount: Int get() = chapters.count { it.isNew && !it.read }

    fun source(id: String): Source? = sources.firstOrNull { it.id == id }

    /**
     * The address of chapter [number], made from a chapter page the user opened. Sources
     * known to have the chapter come first, [sourceId] before the others; then [sourceId]
     * anyway, since a site may have chapters no one has seen yet. Null when no source has
     * an address the number can be swapped into.
     */
    fun chapterUrl(number: BigDecimal, sourceId: String?): String? {
        val (having, lacking) = sources.partition { number in it.firstChapter..it.lastChapter }
        val tried = having.sortedByDescending { it.id == sourceId } + lacking.filter { it.id == sourceId }
        return tried.firstNotNullOfOrNull { it.chapterUrl(number) }
    }
}

/** A site that carries a series. */
data class Source(
    val id: String,
    val siteName: String,
    val official: Boolean,
    val firstChapter: BigDecimal,
    val lastChapter: BigDecimal,
    /** The latest chapter page opened on this site, for finding its other chapters. */
    val lastOpened: ChapterLink? = null
) {
    fun chapterUrl(number: BigDecimal): String? = lastOpened?.let { chapterUrl(it.url, it.chapter, number) }
}

/** A chapter page's address and the chapter it shows. */
data class ChapterLink(val url: String, val chapter: BigDecimal)

data class Chapter(
    val number: BigDecimal,
    val publishedOn: LocalDate?,
    val read: Boolean,
    /** Set when the chapter arrived since the user last looked. */
    val isNew: Boolean = false,
    val downloaded: Boolean = false,
    /** Source the chapter was read on, when known. */
    val readOnSourceId: String? = null
)

data class ReadingProgress(val chapter: BigDecimal, val page: Int, val pageCount: Int, val sourceId: String) {
    val fraction: Float get() = if (pageCount <= 0) 0f else page.toFloat() / pageCount
}

/**
 * A cover image. Until real covers load, fake data uses abstract gradients like the
 * mockups do, given as ARGB colors from top to bottom.
 */
sealed interface Cover {
    data class Placeholder(val top: Long, val middle: Long, val bottom: Long) : Cover
}

/** Chapter numbers print without trailing zeros: 12, 10.5. */
fun BigDecimal.toChapterLabel(): String = stripTrailingZeros().toPlainString()
