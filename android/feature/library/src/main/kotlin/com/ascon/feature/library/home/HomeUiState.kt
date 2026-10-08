package com.ascon.feature.library.home

import com.ascon.core.model.AccountState
import com.ascon.core.model.Cover
import com.ascon.core.model.ReadingStatus
import com.ascon.core.model.Series
import com.ascon.core.model.Site
import com.ascon.core.model.toChapterLabel
import java.time.Instant

data class HomeUiState(
    val loading: Boolean = true,
    val continueReading: ContinueReading? = null,
    val statusCounts: List<Pair<ReadingStatus, Int>> = emptyList(),
    val newChapters: List<NewChapterItem> = emptyList(),
    val sites: List<Site> = emptyList(),
    /** First letter of the signed-in name, or null when signed out. */
    val profileInitial: String? = null
)

data class ContinueReading(
    val seriesId: String,
    val title: String,
    val cover: Cover,
    val chapter: String,
    val page: Int,
    val pageCount: Int,
    val sourceName: String
) {
    val fraction: Float get() = if (pageCount == 0) 0f else page.toFloat() / pageCount
}

data class NewChapterItem(
    val seriesId: String,
    val title: String,
    val cover: Cover,
    val firstChapter: String,
    val lastChapter: String,
    /** Shows the accent dot. False when the next chapter is only ready, not new. */
    val hasNew: Boolean,
    val detail: NewChapterDetail
)

sealed interface NewChapterDetail {
    data object Downloaded : NewChapterDetail

    data class Sources(val count: Int) : NewChapterDetail

    data class OneSource(val name: String) : NewChapterDetail
}

/** How many rows the home screen shows before "See all". */
internal const val NEW_CHAPTER_ROWS = 3

/**
 * Builds the home screen from the library. The hero is the series read most recently;
 * new chapters lists other series whose next chapter is new or already downloaded.
 */
fun homeUiState(series: List<Series>, sites: List<Site>, account: AccountState): HomeUiState {
    val hero = series
        .filter { it.status == ReadingStatus.Reading && it.progress != null && it.lastReadAt != null }
        .maxByOrNull { it.lastReadAt ?: Instant.MIN }

    val newChapters = series
        .asSequence()
        .filter { it.id != hero?.id }
        .map { it to it.upNext }
        .filter { (_, up) -> up.isNotEmpty() && (up.first().isNew || up.first().downloaded) }
        .sortedByDescending { (_, up) -> up.mapNotNull { it.publishedOn }.maxOrNull() }
        .take(NEW_CHAPTER_ROWS)
        .map { (s, up) ->
            val next = up.first()
            NewChapterItem(
                seriesId = s.id,
                title = s.title,
                cover = s.cover,
                firstChapter = next.number.toChapterLabel(),
                lastChapter = up.last().number.toChapterLabel(),
                hasNew = up.any { it.isNew },
                detail = when {
                    next.downloaded -> NewChapterDetail.Downloaded
                    s.sources.size > 1 -> NewChapterDetail.Sources(s.sources.size)
                    else -> NewChapterDetail.OneSource(s.sources.first().siteName)
                }
            )
        }
        .toList()

    return HomeUiState(
        loading = false,
        continueReading = hero?.progress?.let { progress ->
            ContinueReading(
                seriesId = hero.id,
                title = hero.title,
                cover = hero.cover,
                chapter = progress.chapter.toChapterLabel(),
                page = progress.page,
                pageCount = progress.pageCount,
                sourceName = hero.source(progress.sourceId)?.siteName.orEmpty()
            )
        },
        statusCounts = ReadingStatus.entries.map { status -> status to series.count { it.status == status } },
        newChapters = newChapters,
        sites = sites,
        profileInitial = (account as? AccountState.SignedIn)?.displayName?.firstOrNull()?.uppercase()
    )
}
