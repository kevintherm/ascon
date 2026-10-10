package com.ascon.core.data

import com.ascon.core.model.Chapter
import com.ascon.core.model.ChapterLink
import com.ascon.core.model.Cover
import com.ascon.core.model.ReadingProgress
import com.ascon.core.model.ReadingStatus
import com.ascon.core.model.Series
import com.ascon.core.model.Source
import java.math.BigDecimal
import java.time.Instant

// How the library changes as the user reads. Every LibraryRepository applies these, so
// the fake and Room agree.

/** Makes [sourceId] the source of progress, if the series has that source. */
internal fun Series.withProgressSource(sourceId: String): Series {
    val current = progress
    return if (current == null || source(sourceId) == null) this else copy(progress = current.copy(sourceId = sourceId))
}

/** See [LibraryRepository.recordPageRead]. */
internal fun Series.onPage(chapter: BigDecimal, page: Int, pageCount: Int): Series {
    val current = progress
    if (current == null || current.chapter.compareTo(chapter) != 0) return this
    val finished = page >= pageCount
    return copy(
        progress = current.copy(page = page, pageCount = pageCount),
        chapters = if (finished) {
            chapters.map { if (it.number.compareTo(chapter) == 0) it.copy(read = true, isNew = false) else it }
        } else {
            chapters
        }
    )
}

/** See [LibraryRepository.recordChapterOpened]. */
internal fun Series.opened(chapter: BigDecimal, at: Instant): Series {
    val current = progress
    if (current != null && chapter < current.chapter) return copy(lastReadAt = at)
    val sameChapter = current != null && current.chapter.compareTo(chapter) == 0
    return copy(
        status = if (status == ReadingStatus.Plan) ReadingStatus.Reading else status,
        chapters = withChapter(chapter).map { if (it.number < chapter) it.copy(read = true, isNew = false) else it },
        // A new chapter starts at page 0 until the reader or the page reports one.
        progress = if (sameChapter) {
            current
        } else {
            ReadingProgress(chapter, page = 0, pageCount = 0, sourceId = current?.sourceId ?: sources.first().id)
        },
        lastReadAt = at
    )
}

/** The chapter list with [number] in it. A chapter the user opened on a site exists, even if no check found it yet. */
private fun Series.withChapter(number: BigDecimal): List<Chapter> =
    if (chapters.any { it.number.compareTo(number) == 0 }) {
        chapters
    } else {
        (chapters + Chapter(number, publishedOn = null, read = false)).sortedBy { it.number }
    }

/**
 * The series with [host] as a source whose chapter range takes in [chapter], and [url] as
 * the chapter page last opened there.
 */
internal fun Series.withSite(host: String, chapter: BigDecimal, url: String): Series {
    val link = ChapterLink(url, chapter)
    val known = source(host)
        ?: return copy(sources = sources + Source(host, host, official = false, chapter, chapter, link))
    val widened = known.copy(
        firstChapter = known.firstChapter.min(chapter),
        lastChapter = known.lastChapter.max(chapter),
        lastOpened = link
    )
    return copy(sources = sources.map { if (it.id == host) widened else it })
}

/** A series first seen on [host] at [chapter], page [url], with nothing read yet. */
internal fun newSeries(id: String, title: String, host: String, chapter: BigDecimal, url: String): Series = Series(
    id = id,
    title = title,
    altTitles = emptyList(),
    cover = placeholderCover(title),
    status = ReadingStatus.Reading,
    linkedToAniList = false,
    sources = emptyList(),
    chapters = emptyList(),
    progress = null,
    lastReadAt = null
).withSite(host, chapter, url)

// Until covers load, a series gets one of these, picked by its title so it stays the same.
private val PlaceholderCovers = listOf(
    Cover.Placeholder(0xFF9FB6D8, 0xFF3C4F7A, 0xFF1C2235),
    Cover.Placeholder(0xFFD9D2B0, 0xFF8C7A4A, 0xFF2E2618),
    Cover.Placeholder(0xFFB9D3C2, 0xFF4E7A66, 0xFF182A22),
    Cover.Placeholder(0xFFE4C1A1, 0xFFA0613A, 0xFF2E1A10),
    Cover.Placeholder(0xFFCFE0E8, 0xFF5D8597, 0xFF1A2930)
)

private fun placeholderCover(title: String): Cover =
    PlaceholderCovers[Math.floorMod(title.hashCode(), PlaceholderCovers.size)]
