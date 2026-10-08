package com.ascon.feature.library.shelf

import com.ascon.core.model.Cover
import com.ascon.core.model.ReadingStatus
import com.ascon.core.model.Series
import com.ascon.core.model.toChapterLabel
import java.time.Instant

data class LibraryUiState(
    val loading: Boolean = true,
    val filter: ReadingStatus = ReadingStatus.Reading,
    val statusCounts: List<Pair<ReadingStatus, Int>> = emptyList(),
    val items: List<LibraryItem> = emptyList(),
    /** True when the library has no series at all, not just none under [filter]. */
    val libraryEmpty: Boolean = false
)

data class LibraryItem(
    val seriesId: String,
    val title: String,
    val cover: Cover,
    /** Chapter in progress, or null when not started. */
    val currentChapter: String?,
    val latestChapter: String?,
    /** How far through the known chapters, from 0 to 1. */
    val progress: Float,
    val newCount: Int
)

/** Series under [filter], most recently read first, then by title. */
fun libraryUiState(series: List<Series>, filter: ReadingStatus): LibraryUiState = LibraryUiState(
    loading = false,
    filter = filter,
    statusCounts = ReadingStatus.entries.map { status -> status to series.count { it.status == status } },
    items = series
        .filter { it.status == filter }
        .sortedWith(compareByDescending<Series> { it.lastReadAt ?: Instant.MIN }.thenBy { it.title })
        .map { it.toItem() },
    libraryEmpty = series.isEmpty()
)

private fun Series.toItem(): LibraryItem {
    val latest = latestChapter?.number
    val current = progress?.chapter
    return LibraryItem(
        seriesId = id,
        title = title,
        cover = cover,
        currentChapter = current?.toChapterLabel(),
        latestChapter = latest?.toChapterLabel(),
        progress = if (current == null ||
            latest == null ||
            latest.signum() == 0
        ) {
            0f
        } else {
            current.toFloat() / latest.toFloat()
        },
        newCount = newChapterCount
    )
}
