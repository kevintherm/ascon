package com.ascon.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ascon.core.designsystem.component.StatusBarIcons
import com.ascon.core.designsystem.theme.AsconColors
import kotlinx.coroutines.launch

/** Where the reader sends the user. [onOpenChapter] gets a chapter page URL on the site. */
data class ReaderActions(val onBack: () -> Unit = {}, val onOpenChapter: (String) -> Unit = {})

@Composable
fun ReaderRoute(viewModel: ReaderViewModel, images: ReaderImages, actions: ReaderActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReaderScreen(
        state = state,
        commands = ReaderCommands(
            onBack = actions.onBack,
            onPrevious = { state.previous?.let(actions.onOpenChapter) },
            onNext = { state.next?.let(actions.onOpenChapter) },
            onPageShown = viewModel::onPageShown,
            onToggleBars = viewModel::toggleBars
        )
    ) { index, url, modifier ->
        PageImage(index, url, referer = state.url, images = images, modifier = modifier)
    }
}

/** What the screen asks of the view model and the app. */
data class ReaderCommands(
    val onBack: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPageShown: (Int) -> Unit = {},
    val onToggleBars: () -> Unit = {}
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
    page: @Composable (index: Int, url: String, modifier: Modifier) -> Unit
) {
    StatusBarIcons(darkIcons = false)
    // Opens at the page the chapter starts on, such as the one on screen in the browser.
    val list = rememberLazyListState(initialFirstVisibleItemIndex = state.page - 1)
    val scope = rememberCoroutineScope()
    LaunchedEffect(list) {
        snapshotFlow { list.pageOnScreen() }.collect { commands.onPageShown(it) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AsconColors.ReaderGround)
    ) {
        LazyColumn(
            state = list,
            modifier = Modifier
                .fillMaxSize()
                .testTag(PAGES_TAG)
                .pointerInput(Unit) { detectTapGestures { commands.onToggleBars() } }
        ) {
            itemsIndexed(state.pages, key = { index, _ -> index }) { index, url ->
                page(index, url, Modifier.fillMaxWidth())
            }
        }
        AnimatedVisibility(
            visible = state.barsVisible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 }
        ) {
            ReaderTopBar(
                state = state,
                onBack = commands.onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp)
            )
        }
        AnimatedVisibility(
            visible = state.barsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            ReaderPanel(
                state = state,
                onSeek = { index -> scope.launch { list.scrollToItem(index) } },
                onPrevious = commands.onPrevious,
                onNext = commands.onNext,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 18.dp)
            )
        }
    }
}

/** The page at the top of the screen, or the last page once the list cannot scroll further. */
private fun LazyListState.pageOnScreen(): Int {
    val count = layoutInfo.totalItemsCount
    return if (count > 0 && !canScrollForward) count - 1 else firstVisibleItemIndex
}
