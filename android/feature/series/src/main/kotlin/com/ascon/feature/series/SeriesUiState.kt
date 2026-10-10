package com.ascon.feature.series

import com.ascon.core.model.Chapter
import com.ascon.core.model.Cover
import com.ascon.core.model.ReadingProgress
import com.ascon.core.model.ReadingStatus
import com.ascon.core.model.Series
import com.ascon.core.model.toChapterLabel
import java.time.LocalDate

data class SeriesUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val header: SeriesHeader? = null,
    val primaryAction: PrimaryAction? = null,
    val sources: List<SourceCard> = emptyList(),
    val chapters: List<ChapterRow> = emptyList(),
    val newestFirst: Boolean = true,
    /** Dates in rows are shown relative to this day. */
    val today: LocalDate = LocalDate.MIN
)

data class SeriesHeader(
    val title: String,
    val altTitle: String?,
    val cover: Cover,
    val linkedToAniList: Boolean,
    val status: ReadingStatus
)

sealed interface PrimaryAction {
    data class Continue(val chapter: String, val page: Int, val pageCount: Int) : PrimaryAction

    data class Start(val chapter: String) : PrimaryAction

    /** Every chapter on this source is read, and [sourceName] already has [chapter]. */
    data class Ahead(val chapter: String, val sourceName: String) : PrimaryAction

    /** Every chapter Ascon knows of is read. Not a button: a status block with Reread. */
    data class CaughtUp(val latest: String) : PrimaryAction
}

data class SourceCard(
    val id: String,
    val name: String,
    val official: Boolean,
    val chapters: Pair<String, String>,
    val selected: Boolean
)

data class ChapterRow(val number: String, val state: ChapterState, val trailing: ChapterTrailing)

sealed interface ChapterState {
    data object New : ChapterState

    data object Unread : ChapterState

    data class InProgress(val page: Int, val pageCount: Int) : ChapterState {
        val fraction: Float get() = if (pageCount == 0) 0f else page.toFloat() / pageCount
    }

    data object Read : ChapterState
}

sealed interface ChapterTrailing {
    data class Date(val date: LocalDate) : ChapterTrailing

    data class ReadVia(val sourceName: String) : ChapterTrailing

    data object Downloaded : ChapterTrailing

    data object None : ChapterTrailing
}

fun seriesUiState(series: Series?, newestFirst: Boolean, today: LocalDate): SeriesUiState {
    if (series == null) return SeriesUiState(loading = false, notFound = true, today = today)
    val progress = series.progress
    val currentSource = progress?.sourceId ?: series.sources.firstOrNull()?.id
    val rows = series.chapters.map { it.toRow(series, progress) }
    return SeriesUiState(
        loading = false,
        header = SeriesHeader(
            title = series.title,
            altTitle = series.altTitles.firstOrNull(),
            cover = series.cover,
            linkedToAniList = series.linkedToAniList,
            status = series.status
        ),
        primaryAction = primaryAction(series, currentSource),
        sources = series.sources.map { source ->
            SourceCard(
                id = source.id,
                name = source.siteName,
                official = source.official,
                chapters = source.firstChapter.toChapterLabel() to source.lastChapter.toChapterLabel(),
                selected = source.id == currentSource
            )
        },
        chapters = if (newestFirst) rows.asReversed() else rows,
        newestFirst = newestFirst,
        today = today
    )
}

/**
 * The main button, per notes.md: continue the chapter in progress, start the next unread
 * one, point to the source that is ahead when this one has nothing more, or show that the
 * user is caught up.
 */
private fun primaryAction(series: Series, currentSource: String?): PrimaryAction? {
    val progress = series.progress
    val next = series.upNext.firstOrNull() ?: series.chapters.firstOrNull { !it.read }
    val here = currentSource?.let(series::source)
    val ahead = next?.let { n -> series.sources.firstOrNull { it.lastChapter >= n.number } }
    return when {
        progress != null && progress.page < progress.pageCount ->
            PrimaryAction.Continue(progress.chapter.toChapterLabel(), progress.page, progress.pageCount)
        next == null -> series.latestChapter?.let { PrimaryAction.CaughtUp(it.number.toChapterLabel()) }
        here != null && here.lastChapter < next.number && ahead != null ->
            PrimaryAction.Ahead(next.number.toChapterLabel(), ahead.siteName)
        else -> PrimaryAction.Start(next.number.toChapterLabel())
    }
}

private fun Chapter.toRow(series: Series, progress: ReadingProgress?): ChapterRow {
    val state = when {
        progress != null && progress.chapter.compareTo(number) == 0 && !read ->
            ChapterState.InProgress(progress.page, progress.pageCount)
        read -> ChapterState.Read
        isNew -> ChapterState.New
        else -> ChapterState.Unread
    }
    val readOn = readOnSourceId?.let(series::source)
    val date = publishedOn
    val trailing = when {
        state is ChapterState.InProgress -> ChapterTrailing.None
        downloaded -> ChapterTrailing.Downloaded
        read && readOn != null -> ChapterTrailing.ReadVia(readOn.siteName.substringBefore('.'))
        date != null -> ChapterTrailing.Date(date)
        else -> ChapterTrailing.None
    }
    return ChapterRow(number.toChapterLabel(), state, trailing)
}
