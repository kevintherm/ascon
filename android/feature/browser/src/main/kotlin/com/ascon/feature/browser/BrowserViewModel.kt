package com.ascon.feature.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.LibraryRepository
import com.ascon.core.model.Cover
import com.ascon.core.model.ReaderChapter
import com.ascon.core.model.Series
import com.ascon.core.model.matchSeries
import com.ascon.engine.detection.Detection
import com.ascon.engine.detection.withoutFragment
import com.ascon.feature.browser.web.BlockedKind
import com.ascon.feature.browser.web.BrowserEvents
import com.ascon.feature.browser.web.LoadErrorKind
import com.ascon.feature.browser.web.displayHost
import java.math.BigDecimal
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowserUiState(
    val url: String,
    /** 0 to 100 while loading. */
    val progress: Int = 0,
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val error: LoadError? = null,
    val card: DetectionCard? = null,
    val notice: Notice? = null,
    /** A chapter with pages, waiting for the reader to open. See [BrowserViewModel.readerOpened]. */
    val reader: ReaderChapter? = null,
    /** The page is a chapter the reader cannot take, so it is read as the site shows it. */
    val readerUnavailable: Boolean = false
) {
    val host: String get() = displayHost(url)
}

data class LoadError(val host: String, val kind: LoadErrorKind)

/** What the detection card shows for the chapter on screen. */
data class DetectionCard(
    val url: String,
    val title: String?,
    val chapter: BigDecimal?,
    /** Set when the chapter belongs to a series in the library. */
    val seriesId: String?,
    val cover: Cover?,
    /** True once progress for this chapter is recorded. */
    val saved: Boolean,
    /** The page on screen, counted from 1, when the chapter is read as the site shows it. */
    val page: Int? = null,
    val pageCount: Int? = null
)

/** A short message about something the browser blocked. [id] tells repeats apart. */
data class Notice(val kind: BlockedKind, val host: String, val id: Long)

/**
 * State for one browser tab. The WebView lives in a [web.BrowserSession]; this class
 * only sees its events, so it is tested without one.
 *
 * When detection finds a chapter of a series in the library, progress is recorded once
 * per page. Matching is by exact title key for now. A chapter with pages opens the
 * reader once per page, so coming back from the reader, or going back to a page the
 * reader showed, shows the site.
 */
class BrowserViewModel(
    private val library: LibraryRepository,
    private val clock: Clock,
    private val saved: SavedStateHandle,
    initialUrl: String
) : ViewModel(),
    BrowserEvents {
    private val _state = MutableStateFlow(BrowserUiState(url = saved[KEY_URL] ?: initialUrl))
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private var recordedUrl: String? = null
    private val readerOpenedFor = mutableSetOf<String>()
    private val bannerDismissedFor = mutableSetOf<String>()

    /** The chapter on screen, kept when its card is hidden so its page is still saved. */
    private var chapterCard: DetectionCard? = null

    /** A page on screen reported before its chapter's card exists, applied once it does. */
    private var earlyPosition: Pair<String, Detection.ReadingPosition>? = null
    private var hiddenUrl: String? = null
    private var noticeCount = 0L

    override fun onPageStarted(url: String) {
        moveTo(url)
        _state.update { it.copy(loading = true, progress = 0, error = null) }
    }

    override fun onPageFinished(url: String) {
        _state.update { it.copy(loading = false, progress = 100) }
    }

    override fun onProgress(percent: Int) {
        _state.update { it.copy(progress = percent) }
    }

    override fun onHistoryChanged(url: String, canGoBack: Boolean, canGoForward: Boolean) {
        moveTo(url)
        _state.update { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    override fun onLoadError(url: String, kind: LoadErrorKind) {
        _state.update {
            it.copy(loading = false, error = LoadError(displayHost(url.ifEmpty { it.url }), kind), card = null)
        }
    }

    override fun onBlocked(kind: BlockedKind, url: String) {
        _state.update { it.copy(notice = Notice(kind, displayHost(url), ++noticeCount)) }
    }

    override fun onDetection(detection: Detection) {
        val page = detection.url.withoutFragment()
        if (page != state.value.url.withoutFragment()) return
        when {
            detection is Detection.ReadingPosition -> onPosition(page, detection)
            page == hiddenUrl -> Unit
            detection is Detection.ChapterPage -> onChapter(page, detection)
            else -> _state.update { it.copy(card = null, readerUnavailable = false) }
        }
    }

    private fun onChapter(page: String, detection: Detection.ChapterPage) {
        viewModelScope.launch {
            val chapter = detection.chapter
            val series = seriesOf(page, detection.title, chapter)
            val record = series != null && chapter != null && recordedUrl != page
            if (record) {
                recordedUrl = page
                library.recordChapterOpened(series.id, chapter, clock.instant())
            }
            val card = DetectionCard(
                url = page,
                title = series?.title ?: detection.title,
                chapter = chapter,
                seriesId = series?.id,
                cover = series?.cover,
                saved = series != null && chapter != null
            )
            // add() is false for a page the reader already showed, such as one reached with back.
            val reader = if (detection.images.isNotEmpty() && readerOpenedFor.add(page)) {
                ReaderChapter(
                    page,
                    card.title,
                    chapter,
                    series?.id,
                    detection.images,
                    detection.next,
                    detection.previous
                )
            } else {
                null
            }
            // The user may have moved on while the library was read.
            val unavailable = detection.images.isEmpty() && page !in bannerDismissedFor
            _state.update {
                if (it.url.withoutFragment() == page) {
                    // A repeat result keeps the page the card already shows.
                    val kept = chapterCard?.takeIf { old -> old.url == page && old.chapter == card.chapter }
                    chapterCard = card.copy(page = kept?.page, pageCount = kept?.pageCount)
                    it.copy(
                        card = chapterCard,
                        reader = reader ?: it.reader,
                        readerUnavailable = unavailable
                    )
                } else {
                    it
                }
            }
            earlyPosition?.takeIf { (url, _) -> url == page }?.let { (_, position) ->
                earlyPosition = null
                onPosition(page, position)
            }
        }
    }

    /**
     * The library series a chapter belongs to. A numbered chapter adds its series and site
     * to the library; without a number there is nothing to save, so only a match counts.
     */
    private suspend fun seriesOf(page: String, title: String?, chapter: BigDecimal?): Series? = when {
        title == null -> null
        chapter == null -> matchSeries(library.series.first(), title)
        else -> library.seriesFor(title, displayHost(page), chapter)
    }

    /** Saves the page on screen of a chapter read as the site shows it, and shows it on the card. */
    private fun onPosition(page: String, position: Detection.ReadingPosition) {
        val old = chapterCard?.takeIf { it.url == page }
        if (old == null) {
            // The library may still be finding the series; the card shows it when ready.
            earlyPosition = page to position
            return
        }
        if (old.page == position.page && old.pageCount == position.pageCount) return
        val card = old.copy(page = position.page, pageCount = position.pageCount)
        chapterCard = card
        _state.update { if (it.card?.url == page) it.copy(card = card) else it }
        val seriesId = card.seriesId
        val chapter = card.chapter
        if (seriesId != null && chapter != null) {
            viewModelScope.launch {
                library.recordPageRead(seriesId, chapter, position.page, position.pageCount, clock.instant())
            }
        }
    }

    fun dismissReaderUnavailable() {
        bannerDismissedFor += state.value.url.withoutFragment()
        _state.update { it.copy(readerUnavailable = false) }
    }

    fun readerOpened() {
        _state.update { it.copy(reader = null) }
    }

    fun hideCard() {
        hiddenUrl = state.value.card?.url
        _state.update { it.copy(card = null) }
    }

    fun dismissNotice(id: Long) {
        _state.update { if (it.notice?.id == id) it.copy(notice = null) else it }
    }

    /** Clears an error before the page is loaded again. */
    fun retry() {
        _state.update { it.copy(error = null) }
    }

    private fun moveTo(url: String) {
        saved[KEY_URL] = url
        _state.update {
            val samePage = it.url.withoutFragment() == url.withoutFragment()
            it.copy(
                url = url,
                card = if (samePage) it.card else null,
                reader = if (samePage) it.reader else null,
                readerUnavailable = samePage && it.readerUnavailable
            )
        }
    }

    private companion object {
        const val KEY_URL = "url"
    }
}
