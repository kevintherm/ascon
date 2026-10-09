package com.ascon.core.data.fake

import com.ascon.core.data.LibraryRepository
import com.ascon.core.model.Chapter
import com.ascon.core.model.ReadingProgress
import com.ascon.core.model.ReadingStatus
import com.ascon.core.model.Series
import com.ascon.core.model.Site
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** An in-memory library seeded with [FakeLibrary]. Changes last until the process dies. */
class FakeLibraryRepository(
    initial: List<Series> = FakeLibrary.series(Clock.systemDefaultZone()),
    sites: List<Site> = FakeLibrary.sites
) : LibraryRepository {
    private val state = MutableStateFlow(initial)

    override val series: StateFlow<List<Series>> = state
    override val sites: Flow<List<Site>> = MutableStateFlow(sites)

    override fun series(id: String): Flow<Series?> = state.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun selectSource(seriesId: String, sourceId: String) {
        state.update { all ->
            all.map { series ->
                val progress = series.progress
                if (series.id != seriesId || progress == null || series.source(sourceId) == null) {
                    series
                } else {
                    series.copy(progress = progress.copy(sourceId = sourceId))
                }
            }
        }
    }

    override suspend fun recordChapterOpened(seriesId: String, chapter: BigDecimal, at: Instant) {
        state.update { all -> all.map { if (it.id == seriesId) it.opened(chapter, at) else it } }
    }

    override suspend fun recordPageRead(seriesId: String, chapter: BigDecimal, page: Int, pageCount: Int, at: Instant) {
        state.update { all ->
            all.map {
                if (it.id ==
                    seriesId
                ) {
                    it.opened(chapter, at).onPage(chapter, page, pageCount)
                } else {
                    it
                }
            }
        }
    }
}

private fun Series.onPage(chapter: BigDecimal, page: Int, pageCount: Int): Series {
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

private fun Series.opened(chapter: BigDecimal, at: Instant): Series {
    val current = progress
    if (current != null && chapter < current.chapter) return copy(lastReadAt = at)
    val sameChapter = current != null && current.chapter.compareTo(chapter) == 0
    return copy(
        status = if (status == ReadingStatus.Plan) ReadingStatus.Reading else status,
        chapters = withChapter(chapter).map { if (it.number < chapter) it.copy(read = true, isNew = false) else it },
        // Pages are unknown until the reader exists; a new chapter starts at page 0.
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
