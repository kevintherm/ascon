package com.ascon.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.LibraryRepository
import com.ascon.core.model.Cover
import com.ascon.core.model.ReaderChapter
import java.math.BigDecimal
import java.net.URI
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderUiState(
    /** The chapter page on the site. Page images are fetched with it as Referer. */
    val url: String,
    val title: String?,
    val chapter: BigDecimal?,
    /** The site the chapter is read on, without "www.". */
    val host: String,
    val pages: List<String>,
    /** The page on screen, counted from 1. */
    val page: Int = 1,
    val barsVisible: Boolean = true,
    /** Chapter page URLs on the site. */
    val next: String?,
    val previous: String?,
    /** Set when the series is in the library. */
    val seriesId: String? = null,
    val cover: Cover? = null,
    /** The next chapter's number, when the library knows it or it follows a whole number. */
    val nextChapter: BigDecimal? = null
) {
    val pageCount: Int get() = pages.size
}

/** One chapter in the native reader. Saves the page on screen when the series is in the library. */
class ReaderViewModel(
    private val library: LibraryRepository,
    private val clock: Clock,
    private val chapter: ReaderChapter
) : ViewModel() {
    private val _state = MutableStateFlow(
        ReaderUiState(
            url = chapter.url,
            title = chapter.title,
            chapter = chapter.chapter,
            host = hostOf(chapter.url),
            pages = chapter.pages,
            page = chapter.startPage.coerceIn(1, chapter.pages.size.coerceAtLeast(1)),
            next = chapter.next,
            previous = chapter.previous,
            seriesId = chapter.seriesId,
            nextChapter = chapter.chapter?.let { nextAfter(it, emptyList()) }
        )
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        val seriesId = chapter.seriesId
        val number = chapter.chapter
        if (seriesId != null) {
            viewModelScope.launch {
                library.series(seriesId).collect { series ->
                    _state.update {
                        it.copy(
                            cover = series?.cover,
                            nextChapter = number?.let { n ->
                                nextAfter(n, series?.chapters.orEmpty().map { c -> c.number })
                            }
                        )
                    }
                }
            }
        }
    }

    private var shownPage = 0

    /** [index] counts from 0, as the list does. */
    fun onPageShown(index: Int) {
        val page = index + 1
        if (page == shownPage) return
        shownPage = page
        _state.update { it.copy(page = page) }
        val seriesId = chapter.seriesId
        val number = chapter.chapter
        if (seriesId != null && number != null) {
            viewModelScope.launch {
                library.recordPageRead(seriesId, number, page, chapter.pages.size, clock.instant())
            }
        }
    }

    fun toggleBars() {
        _state.update { it.copy(barsVisible = !it.barsVisible) }
    }

    /** Shows the bars as the end of the chapter comes up, for the next chapter. A tap hides them again. */
    fun nearEnd() {
        _state.update { it.copy(barsVisible = true) }
    }

    private companion object {
        /** The first known chapter after [number], or the next whole number after a whole one. */
        fun nextAfter(number: BigDecimal, known: List<BigDecimal>): BigDecimal? =
            known.filter { it > number }.minOrNull()
                ?: number.takeIf { it.stripTrailingZeros().scale() <= 0 }?.add(BigDecimal.ONE)

        fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull().orEmpty().removePrefix("www.")
    }
}
