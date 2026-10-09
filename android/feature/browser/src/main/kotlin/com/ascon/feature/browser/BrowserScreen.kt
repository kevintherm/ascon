package com.ascon.feature.browser

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ascon.core.designsystem.component.StatusBarIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.feature.browser.web.BrowserSession
import com.ascon.feature.browser.web.addressToUrl
import kotlinx.coroutines.delay

/** How long a notice about something blocked stays up. */
private const val NOTICE_MS = 3000L

data class BrowserActions(val onClose: () -> Unit = {}, val onOpenSeries: (String) -> Unit = {})

@Composable
fun BrowserRoute(viewModel: BrowserViewModel, session: BrowserSession, actions: BrowserActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Events keep flowing while a series page covers the browser, so state stays current.
    SideEffect { session.events = viewModel }
    LaunchedEffect(session) { if (session.isEmpty) session.load(state.url) }

    BrowserScreen(
        state = state,
        commands = BrowserCommands(
            onBack = { if (state.canGoBack) session.goBack() else actions.onClose() },
            onForward = session::goForward,
            onReload = session::reload,
            onLoad = session::load,
            onRetry = {
                viewModel.retry()
                session.load(state.url)
            },
            onShare = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, state.url)
                context.startActivity(Intent.createChooser(send, null))
            },
            onOpenSeries = actions.onOpenSeries,
            onHideCard = viewModel::hideCard,
            onNoticeShown = viewModel::dismissNotice
        )
    ) { modifier ->
        key(session.generation) {
            AndroidView(
                factory = { session.attach(it) },
                onRelease = session::detach,
                modifier = modifier
            )
        }
    }
}

/** What the screen asks of the WebView and the view model. */
data class BrowserCommands(
    val onBack: () -> Unit = {},
    val onForward: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onLoad: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onOpenSeries: (String) -> Unit = {},
    val onHideCard: () -> Unit = {},
    val onNoticeShown: (Long) -> Unit = {}
)

/**
 * The browser: the page, the ink bar at the bottom, and the detection card and notices
 * floating over the page. [page] draws the WebView; previews and tests pass a stand-in.
 */
@Composable
fun BrowserScreen(state: BrowserUiState, commands: BrowserCommands, page: @Composable (Modifier) -> Unit) {
    StatusBarIcons(darkIcons = false)
    var editing by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = editing || state.canGoBack) {
        if (editing) editing = false else commands.onBack()
    }
    state.notice?.let { notice ->
        LaunchedEffect(notice.id) {
            delay(NOTICE_MS)
            commands.onNoticeShown(notice.id)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(AsconColors.BrowserGround)
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .statusBarsPadding()
            ) {
                page(Modifier.fillMaxSize())
                state.error?.let { LoadErrorPage(it, commands.onRetry, Modifier.fillMaxSize()) }
            }
            Spacer(
                Modifier
                    .navigationBarsPadding()
                    .height(BarClearance)
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = BarSide, end = BarSide, bottom = BarBottom),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = state.notice != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut()
            ) {
                state.notice?.let { NoticePill(noticeText(it)) }
            }
            AnimatedVisibility(
                visible = state.card != null && !editing,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                state.card?.let { DetectionCardView(it, commands.onOpenSeries, commands.onHideCard) }
            }
            BrowserBar(
                state = state,
                editing = editing,
                onEditingChange = { editing = it },
                onBack = commands.onBack,
                onSubmit = { text ->
                    editing = false
                    addressToUrl(text)?.let(commands.onLoad)
                },
                onMore = { menuOpen = true }
            )
        }
        if (menuOpen) {
            OverflowMenu(
                state = state,
                onDismiss = { menuOpen = false },
                commands = commands
            )
        }
    }
}
