package com.ascon.feature.browser

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ascon.core.designsystem.component.StatusBarIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.model.ReaderChapter
import com.ascon.engine.detection.withoutFragment
import com.ascon.feature.browser.web.BrowserSession
import com.ascon.feature.browser.web.addressToUrl
import kotlinx.coroutines.delay

/** How long Undo stays up after "Site looks broken?" turned protection off. */
private const val UNDO_MS = 5000L

/** How long a notice about something blocked stays up. */
private const val NOTICE_MS = 3000L

data class BrowserActions(
    /** Back to Ascon: leaves the browser and keeps the page loaded for the Browse tab. */
    val onClose: () -> Unit = {},
    /** Close: leaves the browser and ends the session, so its page and history are discarded. */
    val onEndSession: () -> Unit = {},
    val onOpenSeries: (String) -> Unit = {},
    val onOpenReader: (ReaderChapter) -> Unit = {},
    /** Called once [BrowserRoute]'s `loadUrl` is loaded, so the app can clear it. */
    val onUrlLoaded: () -> Unit = {}
)

/**
 * The browser screen over the one browser tab. [openUrl] is the page this screen was
 * opened for: it loads once, unless the tab already shows it, as when the Browse tab
 * returns to it. [loadUrl], when set, is loaded too, for example the next chapter picked
 * in the reader or a chapter opened from the library. It loads even when the tab shows it,
 * and opens the reader again if it finds pages.
 */
@Composable
fun BrowserRoute(
    viewModel: BrowserViewModel,
    protectionViewModel: ProtectionViewModel,
    session: BrowserSession,
    actions: BrowserActions,
    openUrl: String,
    loadUrl: String? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val protection by protectionViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Events keep flowing while a series page covers the browser, so state stays current.
    SideEffect { session.events = viewModel }
    var opened by rememberSaveable(openUrl) { mutableStateOf(false) }
    LaunchedEffect(openUrl) {
        if (!opened) {
            opened = true
            val shown = !session.isEmpty && openUrl.withoutFragment() == state.url.withoutFragment()
            if (!shown && openUrl != loadUrl) session.load(openUrl)
        }
    }
    LaunchedEffect(loadUrl) {
        loadUrl?.let {
            viewModel.allowReader(it)
            session.load(it)
            actions.onUrlLoaded()
        }
    }
    LaunchedEffect(state.url) { protectionViewModel.onPage(state.url) }
    // A change from the protection sheet loads the page again, so hidden elements follow it.
    LaunchedEffect(protection.reload) {
        if (protection.reload) {
            protectionViewModel.reloaded()
            session.reload()
        }
    }
    LaunchedEffect(state.reader) {
        state.reader?.let {
            viewModel.readerOpened()
            actions.onOpenReader(it)
        }
    }

    BrowserScreen(
        state = state,
        protection = protection,
        commands = BrowserCommands(
            onBack = session::goBack,
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
            onDockCard = viewModel::dockCard,
            onNoticeShown = viewModel::dismissNotice,
            onDismissReaderUnavailable = viewModel::dismissReaderUnavailable,
            onOpenReader = viewModel::openReader,
            onOpenElsewhere = {
                val view = Intent(Intent.ACTION_VIEW, Uri.parse(state.url)).addCategory(Intent.CATEGORY_BROWSABLE)
                context.startActivity(Intent.createChooser(view, null))
            },
            onCloseBrowser = actions.onClose,
            onEndSession = actions.onEndSession,
            onSetAdblock = protectionViewModel::setAdblock,
            onSetBlockPopups = protectionViewModel::setBlockPopups,
            onSetTrusted = protectionViewModel::setTrusted,
            onTurnOffProtection = protectionViewModel::turnOffForSite,
            onUndoTrust = protectionViewModel::undoTrust,
            onUndoShown = protectionViewModel::undoShown
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
    /** Goes back in the page's history. On the first page the screen asks to close instead. */
    val onBack: () -> Unit = {},
    val onForward: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onLoad: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onOpenSeries: (String) -> Unit = {},
    val onDockCard: () -> Unit = {},
    val onNoticeShown: (Long) -> Unit = {},
    val onDismissReaderUnavailable: () -> Unit = {},
    val onOpenReader: () -> Unit = {},
    /** Hands the page to another browser app. */
    val onOpenElsewhere: () -> Unit = {},
    /** Leaves the browser. The page stays loaded for the Browse tab to return to. */
    val onCloseBrowser: () -> Unit = {},
    /** Leaves the browser and discards the page and its history. */
    val onEndSession: () -> Unit = {},
    val onSetAdblock: (Boolean) -> Unit = {},
    val onSetBlockPopups: (Boolean) -> Unit = {},
    val onSetTrusted: (Boolean) -> Unit = {},
    /** "Site looks broken?" confirmed: trusts the site and reloads, with Undo. */
    val onTurnOffProtection: () -> Unit = {},
    val onUndoTrust: () -> Unit = {},
    val onUndoShown: (Long) -> Unit = {}
)

/**
 * The browser: the page, the toolbar docked under it, and the detection card and
 * notices just above the toolbar. Scrolling down slides the toolbar away, and the page
 * takes its place once the slide ends. [page] draws the WebView; previews and tests
 * pass a stand-in.
 */
@Composable
fun BrowserScreen(
    state: BrowserUiState,
    commands: BrowserCommands,
    protection: ProtectionUiState = ProtectionUiState(),
    page: @Composable (Modifier) -> Unit
) {
    StatusBarIcons(darkIcons = false)
    var editing by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var shieldOpen by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    val back = { if (state.canGoBack) commands.onBack() else confirmClose = true }

    BackHandler { if (editing) editing = false else back() }
    protection.undo?.let { undo ->
        LaunchedEffect(undo.id) {
            delay(UNDO_MS)
            commands.onUndoShown(undo.id)
        }
    }
    state.notice?.let { notice ->
        LaunchedEffect(notice.id) {
            delay(NOTICE_MS)
            commands.onNoticeShown(notice.id)
        }
    }
    // The Reader button pulses once where the card went.
    var pulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.cardDocked) { if (state.cardDocked && state.card != null) pulse++ }

    val hidden = state.toolbarHidden && !editing
    val slide = remember { Animatable(0f) }
    var pageUnder by remember { mutableStateOf(false) }
    LaunchedEffect(hidden) {
        // The page resizes once, after the toolbar is gone or before it comes back, never mid-slide.
        if (hidden) {
            slide.animateTo(1f, tween(SLIDE_MS))
            pageUnder = true
        } else {
            pageUnder = false
            slide.animateTo(0f, tween(SLIDE_MS))
        }
    }
    var toolbarHeight by remember { mutableIntStateOf(0) }

    Column(
        Modifier
            .fillMaxSize()
            .background(AsconColors.BrowserGround)
            .imePadding()
    ) {
        Box(Modifier.fillMaxWidth().background(AsconColors.Ink).statusBarsPadding())
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            // Only the page's bottom edge moves, so what is at the top never jumps.
            val density = LocalDensity.current
            val bottom = if (pageUnder) {
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            } else {
                with(density) { toolbarHeight.toDp() }
            }
            Box(Modifier.fillMaxSize().padding(bottom = bottom)) {
                page(Modifier.fillMaxSize())
                state.error?.let { LoadErrorPage(it, commands.onRetry, Modifier.fillMaxSize()) }
            }
            BrowserToolbar(
                state = state,
                editing = editing,
                commands = ToolbarCommands(
                    onBack = back,
                    onEdit = { editing = true },
                    onCancelEdit = { editing = false },
                    onSubmit = { text ->
                        editing = false
                        addressToUrl(text)?.let(commands.onLoad)
                    },
                    onShield = { shieldOpen = true },
                    onOpenReader = commands.onOpenReader,
                    onMore = { menuOpen = true },
                    onDismissReaderUnavailable = commands.onDismissReaderUnavailable
                ),
                pulse = pulse,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { toolbarHeight = it.height }
                    .graphicsLayer { translationY = slide.value * size.height }
            )
            if (pageUnder) ReadingLine(state.card)
            // The card and notices sit 12 above the toolbar, and follow it as it slides.
            val above = with(density) { (toolbarHeight * (1 - slide.value)).toDp() }
            BottomOverlays(
                state,
                commands,
                protection,
                editing,
                Modifier.align(Alignment.BottomCenter).padding(bottom = above)
            )
        }
    }
    BrowserMenu(
        visible = menuOpen,
        state = state,
        commands = commands,
        onDismiss = { menuOpen = false },
        onProtection = {
            menuOpen = false
            shieldOpen = true
        }
    )
    ProtectionSheet(
        visible = shieldOpen,
        blocked = state.blocked,
        protection = protection,
        commands = commands,
        onDismiss = { shieldOpen = false }
    )
    CloseSheet(
        visible = confirmClose,
        onDismiss = { confirmClose = false },
        onClose = {
            confirmClose = false
            commands.onEndSession()
        }
    )
}

/** The notices and the detection card, the only things over the page, above the toolbar. */
@Composable
private fun BottomOverlays(
    state: BrowserUiState,
    commands: BrowserCommands,
    protection: ProtectionUiState,
    editing: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
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
            visible = protection.undo != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut()
        ) {
            protection.undo?.let {
                UndoPill(stringResource(R.string.protection_off_for, it.site), commands.onUndoTrust)
            }
        }
        AnimatedVisibility(
            visible = state.card != null && !state.cardDocked && !editing,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            state.card?.let {
                DetectionCardView(
                    it,
                    state.readerChapter != null,
                    commands.onOpenReader,
                    commands.onOpenSeries,
                    commands.onDockCard
                )
            }
        }
    }
}
