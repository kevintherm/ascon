package com.ascon.feature.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.LibraryRepository
import com.ascon.core.data.MetadataSearch
import com.ascon.core.data.ReaderSettingsRepository
import com.ascon.core.model.Cover
import com.ascon.core.model.ReaderChapter
import com.ascon.core.model.ReadingProgress
import com.ascon.core.model.Series
import com.ascon.core.model.SeriesMetadata
import com.ascon.core.model.autoLinkMatch
import com.ascon.core.model.matchSeries
import com.ascon.engine.adblock.BlockCategory
import com.ascon.engine.detection.Detection
import com.ascon.engine.detection.withoutFragment
import com.ascon.feature.browser.web.BlockedKind
import com.ascon.feature.browser.web.BrowserEvents
import com.ascon.feature.browser.web.LoadErrorKind
import com.ascon.feature.browser.web.displayHost
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    val readerUnavailable: Boolean = false,
    /** The chapter on this page, with its pages, for opening the reader again. */
    val readerChapter: ReaderChapter? = null,
    /** What protection stopped on this page. */
    val blocked: BlockedCounts = BlockedCounts(),
    /** The card has gone, into the toolbar's tracking chip. It shows again when the chapter changes. */
    val cardDocked: Boolean = false,
    /** Scrolling down slid the toolbar out of view. Scrolling up, or the top or end of the page, brings it back. */
    val toolbarHidden: Boolean = false,
    /**
     * A chapter read as the site shows it, waiting to be scrolled to its saved page.
     * See [BrowserViewModel.resumeScrolled].
     */
    val resumeScroll: ResumeScroll? = null,
    /** The page's own title, for Browse's Open page card when nothing was detected. */
    val title: String? = null
) {
    val host: String get() = displayHost(url)
}

/** What protection stopped on one page load, as the protection sheet counts it. */
data class BlockedCounts(val ads: Int = 0, val trackers: Int = 0, val redirects: Int = 0) {
    val total: Int get() = ads + trackers + redirects

    fun plus(category: BlockCategory): BlockedCounts = when (category) {
        BlockCategory.Ad -> copy(ads = ads + 1)
        BlockCategory.Tracker -> copy(trackers = trackers + 1)
    }
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
 * reader showed, shows the site. It doesn't open by itself on a site where the user
 * turned that off in [readerSettings].
 */
class BrowserViewModel(
    private val library: LibraryRepository,
    private val readerSettings: ReaderSettingsRepository,
    private val clock: Clock,
    private val saved: SavedStateHandle,
    initialUrl: String,
    /** The backend's series search, or null when this build has no backend. */
    private val metadata: MetadataSearch? = null
) : ViewModel(),
    BrowserEvents {
    private val _state = MutableStateFlow(BrowserUiState(url = saved[KEY_URL] ?: initialUrl))
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private var recordedUrl: String? = null
    private val readerOpenedFor = mutableSetOf<String>()
    private val bannerDismissedFor = mutableSetOf<String>()

    /** Pages already scrolled to their saved page, so going back to one keeps where it was. */
    private val resumedFor = mutableSetOf<String>()

    /** The chapter on screen, kept when its card is hidden so its page is still saved. */
    private var chapterCard: DetectionCard? = null

    /** A page on screen reported before its chapter's card exists, applied once it does. */
    private var earlyPosition: Pair<String, Detection.ReadingPosition>? = null
    private var lastPosition: Detection.ReadingPosition? = null

    /**
     * A page that started loading and has no chapter result yet. Reloading the same page
     * keeps its card, but the new document reports its top before it can scroll back.
     */
    private var loadedSinceChapter: String? = null
    private var scrollY = 0

    /**
     * Where the scroll last turned: the highest point while the toolbar shows, the lowest
     * while it is hidden. Only 24 dp past it, either way, changes the toolbar, so a slow
     * drag that wobbles by a few pixels never toggles it.
     */
    private var turnedAt = 0

    /** Where the page was scrolled to when the card appeared. One screen past it docks the card. */
    private var cardScrollStart = 0
    private var noticeCount = 0L

    override fun onPageStarted(url: String) {
        moveTo(url)
        loadedSinceChapter = url.withoutFragment()
        _state.update { it.copy(loading = true, progress = 0, error = null) }
    }

    override fun onPageFinished(url: String) {
        _state.update { it.copy(loading = false, progress = 100) }
    }

    override fun onProgress(percent: Int) {
        _state.update { it.copy(progress = percent) }
    }

    override fun onTitle(title: String) {
        // Before a page names itself, WebView reports its address as the title.
        val named = title.trim().takeUnless { it.isEmpty() || it.contains("://") }
        _state.update { it.copy(title = named) }
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
        _state.update {
            // Pages the tab was kept from count as redirects stopped. Downloads are not redirects.
            val redirect = kind != BlockedKind.Download && kind != BlockedKind.AppDownload
            val blocked = if (redirect) it.blocked.copy(redirects = it.blocked.redirects + 1) else it.blocked
            it.copy(notice = Notice(kind, displayHost(url), ++noticeCount), blocked = blocked)
        }
    }

    override fun onDetection(detection: Detection) {
        val page = detection.url.withoutFragment()
        if (page != state.value.url.withoutFragment()) return
        when {
            // Until a resume scroll is sent, the page reports its top, not where the user was.
            detection is Detection.ReadingPosition -> if (state.value.resumeScroll?.url !=
                page
            ) {
                onPosition(page, detection)
            }
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
            val available = detection.images.takeIf { it.isNotEmpty() }?.let { images ->
                val start = series?.progress?.let { resumePage(it, chapter, images.size) } ?: 1
                ReaderChapter(page, card.title, chapter, series?.id, images, detection.next, detection.previous, start)
            }
            // add() is false for a page the reader already showed, such as one reached with back.
            val reader = available?.takeIf { opensByItself(page, detection) && readerOpenedFor.add(page) }
            val unavailable = detection.images.isEmpty() && page !in bannerDismissedFor
            showChapter(page, card, reader, available, unavailable)
            if (reader == null) series?.progress?.let { requestResume(page, it, chapter) }
            replayPosition(page)
        }
    }

    /**
     * Whether the reader opens by itself on [page], as the user chose for the site. Without
     * that choice, a chapter with no next or previous stays on the site, whose own buttons
     * are the way on, and the reader waits to be asked for.
     */
    private suspend fun opensByItself(page: String, detection: Detection.ChapterPage): Boolean =
        readerSettings.autoOpen(displayHost(page)).first() ?: (detection.next != null || detection.previous != null)

    /** Saves the position [page] reported before its chapter was found, now that the card can show it. */
    private fun replayPosition(page: String) {
        if (loadedSinceChapter == page) loadedSinceChapter = null
        earlyPosition?.takeIf { (url, _) -> url == page }?.let { (_, position) ->
            earlyPosition = null
            onPosition(page, position)
        }
    }

    /** Shows the card for [page], unless the user moved on while the library was read. */
    private fun showChapter(
        page: String,
        card: DetectionCard,
        reader: ReaderChapter?,
        available: ReaderChapter?,
        unavailable: Boolean
    ) {
        _state.update {
            if (it.url.withoutFragment() != page) return@update it
            // A repeat result keeps the page the card already shows, and stays docked.
            val kept = chapterCard?.takeIf { old -> old.url == page && old.chapter == card.chapter }
            chapterCard = card.copy(page = kept?.page, pageCount = kept?.pageCount)
            val sameChapter = it.card?.url == page && it.card.chapter == card.chapter
            if (!sameChapter) cardScrollStart = scrollY
            it.copy(
                card = chapterCard,
                cardDocked = sameChapter && it.cardDocked,
                reader = reader ?: it.reader,
                readerChapter = available ?: it.readerChapter,
                readerUnavailable = unavailable
            )
        }
    }

    /**
     * The library series a chapter belongs to. A numbered chapter adds its series and site
     * to the library; without a number there is nothing to save, so only a match counts.
     */
    private suspend fun seriesOf(page: String, title: String?, chapter: BigDecimal?): Series? = when {
        title == null -> null
        chapter == null -> matchSeries(library.series.first(), title)
        else -> library.seriesFor(title, displayHost(page), chapter, page, linkFor(title))
    }

    /**
     * The AniList or MangaUpdates series [title] links to by itself, per [autoLinkMatch]. A
     * title the library already has needs no search. Until the matching sheet is built, a
     * title with no exact match, or a failed search, still joins the library unlinked.
     */
    private suspend fun linkFor(title: String): SeriesMetadata? {
        val search = metadata?.takeIf { matchSeries(library.series.first(), title) == null } ?: return null
        val results = try {
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) { search.search(title) }
        } catch (_: IOException) {
            null
        }
        return results?.let { autoLinkMatch(title, it) }
    }

    /** Asks for [page] to scroll to its saved page, once per page, when [progress] is partway through it. */
    private fun requestResume(page: String, progress: ReadingProgress, chapter: BigDecimal?) {
        val resume = resumeScroll(page, progress, chapter)?.takeIf { resumedFor.add(page) } ?: return
        // The page reports its top before it scrolls; that must not replace the saved page.
        earlyPosition = null
        _state.update { if (it.url.withoutFragment() == page) it.copy(resumeScroll = resume) else it }
    }

    /** The scroll in [BrowserUiState.resumeScroll] was sent to the page. */
    fun resumeScrolled() {
        _state.update { it.copy(resumeScroll = null) }
    }

    /** Saves the page on screen of a chapter read as the site shows it, and shows it on the card. */
    private fun onPosition(page: String, position: Detection.ReadingPosition) {
        val old = chapterCard?.takeIf { it.url == page && loadedSinceChapter != page }
        if (old == null) {
            // The library may still be finding the series; the card shows it when ready.
            earlyPosition = page to position
            return
        }
        // A place already saved isn't saved again, so the site behind the reader, sending
        // the same place again, can't undo the reader's progress.
        if (position == lastPosition) return
        lastPosition = position
        val card = old.copy(page = position.page, pageCount = position.pageCount)
        if (card != old) {
            chapterCard = card
            _state.update { if (it.card?.url == page) it.copy(card = card) else it }
        }
        val seriesId = card.seriesId
        val chapter = card.chapter
        if (seriesId != null && chapter != null) {
            viewModelScope.launch {
                library.recordPageRead(
                    seriesId,
                    chapter,
                    position.page,
                    position.pageCount,
                    clock.instant(),
                    position.offset
                )
            }
        }
    }

    /**
     * Lets the reader open by itself on [url] again, or the page scroll to its saved page,
     * for a page the user asked for rather than went back to.
     */
    fun allowReader(url: String) {
        readerOpenedFor -= url.withoutFragment()
        resumedFor -= url.withoutFragment()
    }

    fun dismissReaderUnavailable() {
        bannerDismissedFor += state.value.url.withoutFragment()
        _state.update { it.copy(readerUnavailable = false) }
    }

    /**
     * Opens the reader again on this page's chapter, from the menu, the card or the toolbar,
     * at the page on screen. The site may count its pages differently, so it is scaled.
     */
    fun openReader() {
        _state.update {
            val chapter = it.readerChapter ?: return@update it
            val card = it.card
            val page = card?.page
            val count = card?.pageCount
            val start = if (page != null && count != null && count > 0) {
                ((page - 1) * chapter.pages.size / count + 1).coerceIn(1, chapter.pages.size)
            } else {
                1
            }
            it.copy(reader = chapter.copy(startPage = start))
        }
    }

    /** Called off the main thread for each request the ad blocker stops on [pageUrl]. */
    override fun onRequestBlocked(pageUrl: String, category: BlockCategory) {
        _state.update {
            if (it.url.withoutFragment() ==
                pageUrl.withoutFragment()
            ) {
                it.copy(blocked = it.blocked.plus(category))
            } else {
                it
            }
        }
    }

    fun readerOpened() {
        _state.update { it.copy(reader = null) }
    }

    /** Puts the card away into the Reader button: after its countdown, from its chevron, or by scrolling. */
    fun dockCard() {
        _state.update { if (it.card != null) it.copy(cardDocked = true) else it }
    }

    /**
     * Hides the toolbar after 24 dp down and shows it after 24 dp up, or at the top or end.
     * Near the end it stays: hiding it would grow the page to its end and show it again.
     */
    override fun onScrolled(scrollY: Int, viewportHeight: Int, toEnd: Int) {
        this.scrollY = scrollY
        val hidden = state.value.toolbarHidden
        val change = if (hidden) {
            turnedAt = maxOf(turnedAt, scrollY)
            toEnd == 0 || scrollY <= 0 || turnedAt - scrollY > TURN_AFTER_DP
        } else {
            turnedAt = minOf(turnedAt, scrollY)
            toEnd > KEEP_NEAR_END_DP && scrollY - turnedAt > TURN_AFTER_DP
        }
        if (change) {
            turnedAt = scrollY
            _state.update { it.copy(toolbarHidden = !hidden) }
        }
        val card = state.value.card
        if (card != null && !state.value.cardDocked && scrollY - cardScrollStart >= viewportHeight) dockCard()
    }

    fun dismissNotice(id: Long) {
        _state.update { if (it.notice?.id == id) it.copy(notice = null) else it }
    }

    /** The session ended with Close: the next page starts a new one, as on a fresh launch. */
    fun sessionEnded() {
        recordedUrl = null
        readerOpenedFor.clear()
        bannerDismissedFor.clear()
        resumedFor.clear()
        chapterCard = null
        earlyPosition = null
        scrollY = 0
        turnedAt = 0
        saved[KEY_URL] = ""
        _state.value = BrowserUiState(url = "")
    }

    /** Clears an error before the page is loaded again. */
    fun retry() {
        _state.update { it.copy(error = null) }
    }

    private fun moveTo(url: String) {
        saved[KEY_URL] = url
        if (url.withoutFragment() != state.value.url.withoutFragment()) {
            scrollY = 0
            turnedAt = 0
        }
        _state.update {
            val samePage = it.url.withoutFragment() == url.withoutFragment()
            it.copy(
                url = url,
                card = if (samePage) it.card else null,
                cardDocked = samePage && it.cardDocked,
                toolbarHidden = samePage && it.toolbarHidden,
                reader = if (samePage) it.reader else null,
                readerChapter = if (samePage) it.readerChapter else null,
                blocked = if (samePage) it.blocked else BlockedCounts(),
                readerUnavailable = samePage && it.readerUnavailable,
                resumeScroll = if (samePage) it.resumeScroll else null,
                title = if (samePage) it.title else null
            )
        }
    }

    private companion object {
        const val KEY_URL = "url"

        /** How long a detected chapter waits for the series search before joining unlinked. */
        const val SEARCH_TIMEOUT_MS = 4_000L

        /** How far the page scrolls past a turn before the toolbar hides or shows, per notes.md. */
        const val TURN_AFTER_DP = 24

        /** More than the toolbar's height, so hiding it near the end can't reach the end. */
        const val KEEP_NEAR_END_DP = 120
    }
}
