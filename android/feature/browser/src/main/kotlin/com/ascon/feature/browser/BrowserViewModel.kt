package com.ascon.feature.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.LibraryRepository
import com.ascon.core.model.Cover
import com.ascon.core.model.ReaderChapter
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
    val reader: ReaderChapter? = null
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
    val saved: Boolean
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
        if (page != state.value.url.withoutFragment() || page == hiddenUrl) return
        if (detection !is Detection.ChapterPage) {
            _state.update { it.copy(card = null) }
            return
        }
        viewModelScope.launch {
            val series = detection.title?.let { matchSeries(library.series.first(), it) }
            val chapter = detection.chapter
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
            _state.update {
                if (it.url.withoutFragment() == page) it.copy(card = card, reader = reader ?: it.reader) else it
            }
        }
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
            it.copy(url = url, card = if (samePage) it.card else null, reader = if (samePage) it.reader else null)
        }
    }

    private companion object {
        const val KEY_URL = "url"
    }
}
