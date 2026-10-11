package com.ascon.core.data

import com.ascon.core.model.Chapter
import com.ascon.core.model.ChapterLink
import com.ascon.core.model.Cover
import com.ascon.core.model.ReadingProgress
import com.ascon.core.model.ReadingStatus
import com.ascon.core.model.Series
import com.ascon.core.model.SeriesMetadata
import com.ascon.core.model.Source
import com.ascon.core.model.matchSeries
import com.ascon.core.model.titleKey
import java.math.BigDecimal
import java.time.Instant

// How the library changes as the user reads. Every LibraryRepository applies these, so
// the fake and Room agree.

/** Makes [sourceId] the source of progress, if the series has that source. */
internal fun Series.withProgressSource(sourceId: String): Series {
    val current = progress
    return if (current == null || source(sourceId) == null) this else copy(progress = current.copy(sourceId = sourceId))
}

/**
 * The page a chapter must be read to before it counts as read into, so a chapter opened by
 * mistake and left on its first page doesn't take the series' place.
 */
private const val PAGES_TO_COUNT = 2

/**
 * Whether the user read into the chapter in progress, past its first page or to its end.
 * Until then the place is only where they last looked, and the next chapter read takes it.
 */
private val Series.readIntoProgress: Boolean
    get() = progress?.let { p ->
        p.page >= PAGES_TO_COUNT ||
            chapters.any { it.number.compareTo(p.chapter) == 0 && it.read }
    }
        ?: false

/** See [LibraryRepository.recordPageRead]. */
internal fun Series.onPage(chapter: BigDecimal, page: Int, pageCount: Int, pageOffset: Float = 0f): Series {
    val current = progress
    val finished = page >= pageCount
    val counts = page >= PAGES_TO_COUNT || finished
    val same = current != null && current.chapter.compareTo(chapter) == 0
    // The place follows the chapter read into last, back as well as forward, so a chapter
    // read by mistake gives way to the one read next.
    val moves = current != null && !same && counts
    // Reading into a chapter, here or by moving the place to it, reads the ones before it.
    val readsBefore = moves || (same && counts && !readIntoProgress)
    val place = (if (same || moves) current else null) ?: return this
    return copy(
        progress = place.copy(
            chapter = chapter,
            page = page,
            pageCount = pageCount,
            pageOffset = pageOffset.coerceIn(0f, 1f)
        ),
        chapters = chapters.markedRead(chapter, before = readsBefore, it = finished)
    )
}

/** The chapters with those before [chapter] read if [before], and [chapter] itself if [it]. */
internal fun List<Chapter>.markedRead(chapter: BigDecimal, before: Boolean, it: Boolean): List<Chapter> = map { c ->
    val read = (before && c.number < chapter) || (it && c.number.compareTo(chapter) == 0)
    if (read) c.copy(read = true, isNew = false) else c
}

/**
 * See [LibraryRepository.recordChapterOpened]. Opening alone moves the place only when the
 * user hadn't read into the chapter in progress; otherwise reading moves it, in [onPage].
 */
internal fun Series.opened(chapter: BigDecimal, at: Instant): Series {
    val current = progress
    val sameChapter = current != null && current.chapter.compareTo(chapter) == 0
    return copy(
        status = if (status == ReadingStatus.Plan) ReadingStatus.Reading else status,
        chapters = withChapter(chapter),
        // A new chapter starts at page 0 until the reader or the page reports one.
        progress = if (sameChapter || readIntoProgress) {
            current
        } else {
            ReadingProgress(chapter, page = 0, pageCount = 0, sourceId = current?.sourceId ?: sources.first().id)
        },
        lastReadAt = at
    )
}

/** The chapter list with [number] in it. A chapter the user opened on a site exists, even if no check found it yet. */
internal fun Series.withChapter(number: BigDecimal): List<Chapter> =
    if (chapters.any { it.number.compareTo(number) == 0 }) {
        chapters
    } else {
        (chapters + Chapter(number, publishedOn = null, read = false)).sortedBy { it.number }
    }

/**
 * The series with [host] as a source whose chapter range takes in [chapter], and [url] as
 * the chapter page last opened there and the page [chapter] was opened at.
 */
internal fun Series.withSite(host: String, chapter: BigDecimal, url: String): Series {
    val addressed = copy(
        chapters = withChapter(chapter).map {
            if (it.number.compareTo(chapter) == 0) it.copy(openedUrl = url, openedOnSourceId = host) else it
        }
    )
    return addressed.withSource(host, chapter, url)
}

private fun Series.withSource(host: String, chapter: BigDecimal, url: String): Series {
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
/**
 * The series a detected chapter of [title] joins in [library], with [host] as a source:
 * the series with that title, else the series [link] names on AniList or MangaUpdates,
 * which keeps [title] as an alternate title, else a new series linked to [link]. Returns
 * the series and the one it was before, null when it is new.
 */
internal fun joinedSeries(
    library: List<Series>,
    title: String,
    host: String,
    chapter: BigDecimal,
    url: String,
    link: SeriesMetadata?,
    newId: () -> String
): Pair<Series, Series?> {
    val match = matchSeries(library, title) ?: link?.let { l -> library.firstOrNull { it.isLinkedTo(l) } }
    val joined = match?.withAltTitle(title)?.withSite(host, chapter, url)
        ?: newSeries(newId(), title, host, chapter, url).let { if (link != null) it.linkedTo(link) else it }
    return joined to match
}

private fun Series.isLinkedTo(link: SeriesMetadata): Boolean = (aniListId != null && aniListId == link.aniListId) ||
    (mangaUpdatesId != null && mangaUpdatesId == link.mangaUpdatesId)

/** This series linked to [link], keeping its titles as alternate titles. */
internal fun Series.linkedTo(link: SeriesMetadata): Series = link.titles.fold(this) { s, t -> s.withAltTitle(t) }.copy(
    aniListId = link.aniListId,
    mangaUpdatesId = link.mangaUpdatesId,
    linkedToAniList = link.aniListId != null
)

/** This series with [title] as an alternate title, unless it already has a title with that key. */
internal fun Series.withAltTitle(title: String): Series {
    val key = titleKey(title)
    val known = (listOf(this.title) + altTitles).any { titleKey(it) == key }
    return if (key.isEmpty() || known) this else copy(altTitles = altTitles + title.trim())
}

internal fun newSeries(id: String, title: String, host: String, chapter: BigDecimal, url: String): Series =
    newSeries(id, title).withSite(host, chapter, url)

/** A series titled [title] with no sources and nothing read yet. */
internal fun newSeries(id: String, title: String): Series = Series(
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
)

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
