package com.ascon.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ascon.core.designsystem.component.Snackbar
import com.ascon.core.designsystem.component.StatusBarIcons
import com.ascon.core.model.PageFit
import com.ascon.core.model.ReaderSettings
import com.ascon.core.model.toChapterLabel
import java.math.BigDecimal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Where the reader sends the user. [onOpenChapter] gets a chapter page URL on the site. */
data class ReaderActions(
    val onBack: () -> Unit = {},
    val onOpenChapter: (String) -> Unit = {},
    val onOpenSeries: (String) -> Unit = {}
)

@Composable
fun ReaderRoute(viewModel: ReaderViewModel, images: ReaderImages, actions: ReaderActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Each page's image size once it loads, for the gaps between pages.
    val sizes = remember(state.url) { mutableStateMapOf<Int, IntSize>() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(MESSAGE_MILLIS)
            message = null
        }
    }
    // Fetches the page's original file, then saves or shares it.
    fun withPage(index: Int, use: suspend (PageFile, name: String) -> String?) {
        scope.launch {
            val file = images.fetch(state.pages[index], referer = state.url)
            val name = listOfNotNull(state.title, state.chapter?.toChapterLabel(), "${index + 1}")
                .joinToString(" ") { it.replace(Regex("[^\\p{L}\\p{N}.]+"), "-") }
            message =
                if (file == null) resources.getString(R.string.reader_page_not_fetched, index + 1) else use(file, name)
        }
    }
    Box {
        ReaderScreen(
            state = state,
            pageSizes = sizes,
            commands = ReaderCommands(
                onBack = actions.onBack,
                onPrevious = { state.previous?.let(actions.onOpenChapter) },
                onNext = { state.next?.let(actions.onOpenChapter) },
                onPageShown = viewModel::onPageShown,
                onToggleBars = viewModel::toggleBars,
                onNearEnd = viewModel::nearEnd,
                onOpenSeries = actions.onOpenSeries,
                onOpenChapterNumber = { number ->
                    chapterUrl(state.url, state.chapter, number)?.let(actions.onOpenChapter)
                },
                onSettingsScope = viewModel::setSettingsScope,
                onChangeSettings = viewModel::updateSettings,
                onSavePage = { index ->
                    withPage(index) { file, name ->
                        if (canSavePages && savePage(context, file, name)) {
                            resources.getString(R.string.reader_page_saved, index + 1)
                        } else {
                            resources.getString(R.string.reader_page_not_fetched, index + 1)
                        }
                    }
                },
                onSharePage = { index ->
                    withPage(index) { file, name ->
                        sharePage(context, file, name)
                        null
                    }
                }
            )
        ) { index, url, modifier ->
            PageImage(
                index,
                url,
                referer = state.url,
                images = images,
                onSize = { sizes[index] = it },
                modifier = modifier,
                fitScreen = state.settings.fit == PageFit.Screen
            )
        }
        message?.let {
            Snackbar(
                it,
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            )
        }
    }
}

/** How long a saved or failed page message stays. */
private const val MESSAGE_MILLIS = 3000L

/** What the screen asks of the view model and the app. */
data class ReaderCommands(
    val onBack: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPageShown: (Int) -> Unit = {},
    val onToggleBars: () -> Unit = {},
    /** The end of the last page is less than half a screen away. */
    val onNearEnd: () -> Unit = {},
    val onOpenSeries: (String) -> Unit = {},
    /** A chapter picked in the chapters sheet. */
    val onOpenChapterNumber: (BigDecimal) -> Unit = {},
    val onSettingsScope: (SettingsScope) -> Unit = {},
    val onChangeSettings: ((ReaderSettings) -> ReaderSettings) -> Unit = {},
    /** Long press on a page, counted from 0. */
    val onSavePage: (Int) -> Unit = {},
    val onSharePage: (Int) -> Unit = {}
)

internal const val PAGES_TAG = "reader-pages"

/**
 * The native reader: pages in one long strip on the reader ground, with glass bars that a
 * tap shows or hides. [page] draws one page; previews and tests pass a stand-in.
 */
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    commands: ReaderCommands,
    pageSizes: Map<Int, IntSize> = emptyMap(),
    page: @Composable (index: Int, url: String, modifier: Modifier) -> Unit
) {
    StatusBarIcons(darkIcons = false)
    // Opens at the page the chapter starts on, such as the one on screen in the browser.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.page - 1)
    val scope = rememberCoroutineScope()
    LaunchedEffect(list) {
        snapshotFlow { list.pageOnScreen(state.pageCount) }.collect { commands.onPageShown(it) }
    }
    LaunchedEffect(list) {
        snapshotFlow { list.isNearEnd() }.distinctUntilChanged().filter { it }.collect { commands.onNearEnd() }
    }
    val settings = state.settings
    var settingsOpen by remember { mutableStateOf(false) }
    var chaptersOpen by remember { mutableStateOf(false) }
    var menuPage by remember { mutableStateOf<Int?>(null) }
    KeepScreenOn(settings.keepScreenOn)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Room after the last page, so the panel shown at the end never covers it.
    var panelHeight by remember { mutableIntStateOf(0) }
    val endSpace = with(LocalDensity.current) { panelHeight.toDp() }

    Box(
        Modifier
            .fillMaxSize()
            .background(settings.background.color())
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                // Volume keys scroll most of a screen, down or up, when the setting is on.
                val down = event.key == Key.VolumeDown
                if (!settings.volumeKeys || !(down || event.key == Key.VolumeUp)) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    val step = list.layoutInfo.viewportSize.height * TURN_SCROLL
                    scope.launch { list.animateScrollBy(if (down) step else -step) }
                }
                true
            }
    ) {
        ReaderPages(
            state = state,
            list = list,
            commands = commands,
            pageSizes = pageSizes,
            endSpace = endSpace,
            onLongPress = { menuPage = it },
            page = page
        )
        AnimatedVisibility(
            visible = state.barsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
        ) {
            ReaderTopBar(
                state = state,
                onBack = commands.onBack,
                onSettings = { settingsOpen = true },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp)
            )
        }
        AnimatedVisibility(
            visible = state.barsVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Kept while the panel hides, so the pages do not jump.
                .onSizeChanged { if (it.height > 0) panelHeight = it.height },
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            ReaderPanel(
                state = state,
                onSeek = { index -> scope.launch { list.scrollToItem(index) } },
                onPrevious = commands.onPrevious,
                onNext = commands.onNext,
                onChapters = { chaptersOpen = true },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 18.dp)
            )
        }
        ReaderChaptersSheet(
            visible = chaptersOpen,
            state = state,
            onOpen = { number ->
                chaptersOpen = false
                commands.onOpenChapterNumber(number)
            },
            onDismiss = { chaptersOpen = false }
        )
        PageMenu(
            page = menuPage,
            onSave = { commands.onSavePage(it) },
            onShare = { commands.onSharePage(it) },
            onDismiss = { menuPage = null }
        )
        ReaderSettingsSheet(
            visible = settingsOpen,
            state = state,
            commands = ReaderSettingsCommands(commands.onSettingsScope, commands.onChangeSettings),
            onDismiss = { settingsOpen = false }
        )
    }
}

/** True when the end of the last page is less than half the screen below the screen's bottom. */
private fun LazyListState.isNearEnd(): Boolean {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull()?.takeIf { it.index == info.totalItemsCount - 1 } ?: return false
    return last.offset + last.size - info.viewportEndOffset < info.viewportSize.height / 2
}

/**
 * The page at the top of the screen, or the last page once the list cannot scroll further.
 * The chapter end after the pages counts as the last page.
 */
private fun LazyListState.pageOnScreen(pageCount: Int): Int {
    val last = (pageCount - 1).coerceAtLeast(0)
    // Before the first layout the list has no items and cannot scroll either.
    val laidOut = layoutInfo.totalItemsCount > 0
    return if (laidOut && pageCount > 0 && !canScrollForward) last else firstVisibleItemIndex.coerceAtMost(last)
}

/** Keeps the screen on while the reader shows, when [on]. */
@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}
