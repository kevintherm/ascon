package com.ascon.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.LibraryRepository
import com.ascon.core.data.ReaderSettingsRepository
import com.ascon.core.data.ReadingPaceRepository
import com.ascon.core.model.Chapter
import com.ascon.core.model.Cover
import com.ascon.core.model.ReaderChapter
import com.ascon.core.model.ReaderSettings
import com.ascon.core.model.chapterOf
import java.math.BigDecimal
import java.net.URI
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
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
    /** The chapter the end button goes to, from the next link, else the library or the next whole number. */
    val nextChapter: BigDecimal? = null,
    /** The series' own settings, or the ones for all series. */
    val settings: ReaderSettings = ReaderSettings(),
    /** The series' chapters in the library, oldest first. Empty for a series outside it. */
    val chapters: List<Chapter> = emptyList(),
    /** Site names of the series' sources, by source id. */
    val siteNames: Map<String, String> = emptyMap(),
    /** Chapter dates are shown relative to this day. */
    val today: LocalDate? = null,
    /** Minutes to the chapter's end at this user's pace, once a few images are read. */
    val minutesLeft: Int? = null,
    /**
     * The reader opens by itself on this site's chapters, as the user chose for the site, or
     * else when this chapter has a next or previous chapter.
     */
    val autoOpen: Boolean = true,
    /** Where changes from the settings sheet go. Only all series without a series in the library. */
    val settingsScope: SettingsScope = if (seriesId != null) SettingsScope.Series else SettingsScope.AllSeries
) {
    val pageCount: Int get() = pages.size
}

enum class SettingsScope { Series, AllSeries }

/** One chapter in the native reader. Saves the page on screen when the series is in the library. */
class ReaderViewModel(
    private val library: LibraryRepository,
    private val clock: Clock,
    private val chapter: ReaderChapter,
    private val readerSettings: ReaderSettingsRepository,
    private val pace: ReadingPaceRepository
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
            nextChapter = nextChapter(emptyList())
        )
    )
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        val own = chapter.seriesId?.let(readerSettings::forSeries) ?: flowOf(null)
        viewModelScope.launch {
            combine(readerSettings.allSeries, own) { all, series -> series ?: all }.collect { settings ->
                _state.update { it.copy(settings = settings) }
            }
        }
    }

    init {
        val seriesId = chapter.seriesId
        if (seriesId != null) {
            viewModelScope.launch {
                library.series(seriesId).collect { series ->
                    _state.update {
                        it.copy(
                            chapters = series?.chapters.orEmpty(),
                            siteNames = series?.sources.orEmpty().associate { source -> source.id to source.siteName },
                            today = LocalDate.now(clock),
                            cover = series?.cover,
                            nextChapter = nextChapter(series?.chapters.orEmpty().map { c -> c.number })
                        )
                    }
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            readerSettings.autoOpen(state.value.host).collect { on ->
                _state.update { it.copy(autoOpen = on ?: (it.next != null || it.previous != null)) }
            }
        }
    }

    /** Turns opening by itself on or off for the whole site, not just this series. */
    fun setAutoOpen(on: Boolean) {
        viewModelScope.launch { readerSettings.setAutoOpen(state.value.host, on) }
    }

    /**
     * The chapter the end button goes to: the one the site's next link names, else the
     * next chapter after this one among [known].
     */
    private fun nextChapter(known: List<BigDecimal>): BigDecimal? {
        val number = chapter.chapter ?: return null
        return chapter.next?.let { chapterOf(it, chapter.url, number) } ?: nextAfter(number, known)
    }

    private var shownPage = 0
    private var shownAt = 0L
    private var paceSamples = emptyList<Float>()

    /** Images read in this chapter at a reading pace. */
    private var imagesRead = 0

    init {
        viewModelScope.launch {
            pace.secondsPerImage.collect { samples ->
                paceSamples = samples
                _state.update { it.withTimeLeft() }
            }
        }
    }

    /** [index] counts from 0, as the list does. */
    fun onPageShown(index: Int) {
        val page = index + 1
        if (page == shownPage) return
        val now = clock.millis()
        // One image forward, after a reading pace's worth of time on the last one.
        val seconds = (now - shownAt) / MILLIS_PER_SECOND
        if (shownPage > 0 && page == shownPage + 1 && countsTowardPace(seconds)) {
            imagesRead++
            viewModelScope.launch { pace.recordSecondsPerImage(seconds) }
        }
        shownPage = page
        shownAt = now
        _state.update { it.copy(page = page).withTimeLeft() }
        val seriesId = chapter.seriesId
        val number = chapter.chapter
        if (seriesId != null && number != null) {
            viewModelScope.launch {
                library.recordPageRead(seriesId, number, page, chapter.pages.size, clock.instant())
            }
        }
    }

    private fun ReaderUiState.withTimeLeft() = copy(
        minutesLeft = if (imagesRead >= IMAGES_BEFORE_TIME_LEFT) minutesLeft(paceSamples, pageCount - page) else null
    )

    fun toggleBars() {
        _state.update { it.copy(barsVisible = !it.barsVisible) }
    }

    /** Shows the bars as the end of the chapter comes up, for the next chapter. A tap hides them again. */
    fun nearEnd() {
        _state.update { it.copy(barsVisible = true) }
    }

    /**
     * This series saves changes for the series alone. All series makes the series follow
     * the settings for all series again, and saves changes there.
     */
    fun setSettingsScope(scope: SettingsScope) {
        val seriesId = chapter.seriesId ?: return
        _state.update { it.copy(settingsScope = scope) }
        if (scope == SettingsScope.AllSeries) viewModelScope.launch { readerSettings.clearSeries(seriesId) }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        val seriesId = chapter.seriesId
        viewModelScope.launch {
            if (seriesId != null && state.value.settingsScope == SettingsScope.Series) {
                readerSettings.updateSeries(seriesId, transform)
            } else {
                readerSettings.updateAllSeries(transform)
            }
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000f

        /** The first known chapter after [number], or the next whole number after a whole one. */
        fun nextAfter(number: BigDecimal, known: List<BigDecimal>): BigDecimal? =
            known.filter { it > number }.minOrNull()
                ?: number.takeIf { it.stripTrailingZeros().scale() <= 0 }?.add(BigDecimal.ONE)

        fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull().orEmpty().removePrefix("www.")
    }
}
