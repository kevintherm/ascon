package com.ascon.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ascon.core.designsystem.component.FloatingNavBar
import com.ascon.core.designsystem.component.FloatingNavBarClearance
import com.ascon.core.designsystem.component.NavItem
import com.ascon.core.designsystem.component.Snackbar
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.ReadingStatus
import com.ascon.feature.browser.BrowseRoute
import com.ascon.feature.browser.BrowseViewModel
import com.ascon.feature.browser.BrowserActions
import com.ascon.feature.browser.BrowserRoute
import com.ascon.feature.browser.BrowserViewModel
import com.ascon.feature.browser.ProtectionViewModel
import com.ascon.feature.browser.web.BrowserSessionHolder
import com.ascon.feature.library.home.HomeActions
import com.ascon.feature.library.home.HomeRoute
import com.ascon.feature.library.home.HomeViewModel
import com.ascon.feature.library.shelf.LibraryActions
import com.ascon.feature.library.shelf.LibraryRoute
import com.ascon.feature.library.shelf.LibraryViewModel
import com.ascon.feature.reader.ReaderActions
import com.ascon.feature.reader.ReaderRoute
import com.ascon.feature.reader.ReaderViewModel
import com.ascon.feature.series.SeriesActions
import com.ascon.feature.series.SeriesRoute
import com.ascon.feature.series.SeriesViewModel
import com.ascon.feature.settings.SettingsActions
import com.ascon.feature.settings.SettingsRoute
import com.ascon.feature.settings.SettingsViewModel
import kotlinx.coroutines.delay

/** Extra room under scrolling content so its end clears the floating nav. */
private val NavBarGap = 32.dp

/**
 * The whole app. Tabs live in [TabHost], always composed at the bottom. The back stack
 * holds [Route.Root] plus detail screens, which [NavDisplay] slides in over the tabs.
 * The floating nav sits on top and hides while a detail screen is open. [startUrl], when
 * set, opens a browser tab over the tabs at launch.
 */
@Composable
fun AsconApp(container: AppContainer, startUrl: String? = null) {
    val backStack = rememberNavBackStack(Route.Root)
    LaunchedEffect(startUrl) { startUrl?.let { backStack.push(Route.Browser(it)) } }
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val cover = remember { CoverState() }
    val tabsShown = backStack.showsTabs()

    // A tab's sheet or dialog covers the nav bar, so the bar steps aside while one is open.
    var tabOverlay by remember { mutableStateOf(false) }
    val bottomPadding = FloatingNavBarClearance + NavBarGap +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Tab view models live as long as the activity, like the tabs themselves.
    val home = viewModel { HomeViewModel(container.library, container.accounts) }
    val library = viewModel { LibraryViewModel(container.library, ReadingStatus.Reading) }
    val settings =
        viewModel {
            SettingsViewModel(
                container.settings,
                container.protection,
                container.reader,
                container.accounts,
                container.clock,
                container.accountBackend,
                container.google
            )
        }
    val browse = viewModel { BrowseViewModel(container.library) }
    // The browser is single-tab and outlives its screen: closing it keeps the page loaded,
    // and the Browse nav item returns to it.
    val browser = viewModel {
        BrowserViewModel(
            container.library,
            container.reader,
            container.clock,
            createSavedStateHandle(),
            startUrl.orEmpty()
        )
    }
    val protection = viewModel { ProtectionViewModel(container.protection) }
    val browserSession = viewModel { BrowserSessionHolder(container.webViews) }.session

    val closed = rememberClosedBrowser {
        browserSession.clear()
        browser.sessionEnded()
    }

    val openSeries: (String) -> Unit = { backStack.push(Route.Series(it)) }
    val openUrl: (String) -> Unit = {
        closed.discard()
        backStack.push(Route.Browser(it))
    }
    // A chapter picked in the reader, for the browser tab under it to load.
    var browserLoad by rememberSaveable { mutableStateOf<String?>(null) }
    // A chapter from the library opens in the browser, which finds its pages for the reader.
    val openChapter: (String) -> Unit = { url ->
        if (!backStack.popToBrowser()) openUrl(url)
        browserLoad = url
    }
    val openLibrary: (ReadingStatus) -> Unit = { status ->
        library.selectFilter(status)
        tab = Tab.Library
    }

    // Back from a tab other than Home goes to Home. Detail screens handle back in NavDisplay.
    BackHandler(enabled = tabsShown && tab.back() != null) { tab.back()?.let { tab = it } }

    AsconTheme {
        Box(Modifier.fillMaxSize()) {
            TabHost(selected = tab, visible = tabsShown, modifier = Modifier.coveredByDetail(cover)) { shown ->
                when (shown) {
                    Tab.Home -> HomeRoute(
                        viewModel = home,
                        actions = HomeActions(
                            onSearch = { tab = Tab.Browse },
                            onProfile = { tab = Tab.Settings },
                            onOpenSeries = openSeries,
                            onOpenChapter = openChapter,
                            onSeeAll = { openLibrary(ReadingStatus.Reading) },
                            onStatus = openLibrary,
                            onOpenSite = { site -> openUrl("https://${site.domain}/") },
                            onAddSite = { tab = Tab.Browse }
                        ),
                        bottomPadding = bottomPadding
                    )
                    Tab.Library -> LibraryRoute(
                        viewModel = library,
                        actions = LibraryActions(onOpenSeries = openSeries, onOpenSite = { tab = Tab.Browse }),
                        bottomPadding = bottomPadding
                    )
                    Tab.Browse -> BrowseRoute(browse, onOpen = openUrl, bottomPadding = bottomPadding)
                    Tab.Settings -> SettingsRoute(
                        viewModel = settings,
                        actions = SettingsActions(onOverlay = { tabOverlay = it }),
                        bottomPadding = bottomPadding
                    )
                }
            }
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.pop() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                transitionSpec = pushTransition,
                popTransitionSpec = popTransition,
                predictivePopTransitionSpec = predictivePopTransition,
                entryProvider = entryProvider {
                    entry<Route.Root> { RootEntry(cover) }
                    entry<Route.Series> { key ->
                        SeriesRoute(
                            viewModel = viewModel { SeriesViewModel(container.library, key.id, container.clock) },
                            actions = SeriesActions(onBack = { backStack.pop() }, onOpenChapter = openChapter)
                        )
                    }
                    entry<Route.Browser> { key ->
                        BrowserRoute(
                            viewModel = browser,
                            protectionViewModel = protection,
                            session = browserSession,
                            openUrl = key.url,
                            actions = BrowserActions(
                                onClose = { backStack.pop() },
                                onEndSession = {
                                    closed.url = browser.state.value.url
                                    backStack.pop()
                                },
                                onOpenSeries = openSeries,
                                onOpenReader = { backStack.push(Route.Reader.of(it)) },
                                onUrlLoaded = { browserLoad = null }
                            ),
                            loadUrl = browserLoad
                        )
                    }
                    entry<Route.Reader> { key ->
                        ReaderRoute(
                            viewModel = viewModel {
                                ReaderViewModel(
                                    container.library,
                                    container.clock,
                                    key.toChapter(),
                                    container.reader,
                                    container.readingPace
                                )
                            },
                            images = container.pageImages,
                            actions = ReaderActions(
                                onBack = { backStack.pop() },
                                onOpenChapter = { url ->
                                    browserLoad = url
                                    backStack.pop()
                                },
                                onOpenSeries = openSeries
                            )
                        )
                    }
                }
            )
            NavBar(
                visible = tabsShown,
                covered = tabOverlay,
                selected = tab,
                onSelect = {
                    if (it == Tab.Browse && !browserSession.isEmpty && closed.url == null) {
                        backStack.push(Route.Browser(browser.state.value.url))
                    } else {
                        tab = it
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            ClosedSnackbar(
                visible = closed.url != null,
                above = if (tabsShown) FloatingNavBarClearance else 0.dp,
                onUndo = {
                    closed.url?.let { url ->
                        closed.url = null
                        backStack.push(Route.Browser(url))
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/** How long Browser closed · Undo stays up before the page is discarded. */
private const val UNDO_CLOSE_MS = 5_000L

/**
 * The page Close left, while Undo can still bring it back. Close leaves the browser at
 * once, and [discard] runs only once Undo has had its 5 s, or when another page opens.
 */
private class ClosedBrowser(private val onDiscard: () -> Unit) {
    var url by mutableStateOf<String?>(null)

    fun discard() {
        if (url != null) {
            url = null
            onDiscard()
        }
    }
}

@Composable
private fun rememberClosedBrowser(onDiscard: () -> Unit): ClosedBrowser {
    val closed = remember { ClosedBrowser(onDiscard) }
    LaunchedEffect(closed.url) {
        if (closed.url != null) {
            delay(UNDO_CLOSE_MS)
            closed.discard()
        }
    }
    return closed
}

/** Browser closed · Undo, over the screen that opened the browser and above the nav. */
@Composable
private fun ClosedSnackbar(visible: Boolean, above: Dp, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = fadeOut()
    ) {
        Snackbar(
            text = stringResource(R.string.browser_closed),
            action = stringResource(R.string.browser_closed_undo),
            onAction = onUndo,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(bottom = above + 16.dp)
        )
    }
}

@Composable
private fun NavBar(
    visible: Boolean,
    covered: Boolean,
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        NavItem(AsconIcons.Home, stringResource(R.string.nav_home)),
        NavItem(AsconIcons.Library, stringResource(R.string.nav_library)),
        NavItem(AsconIcons.Browse, stringResource(R.string.nav_browse)),
        NavItem(AsconIcons.Settings, stringResource(R.string.nav_settings))
    )
    AnimatedVisibility(
        visible = visible && !covered,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        FloatingNavBar(
            items = items,
            selectedIndex = selected.ordinal,
            onSelect = { onSelect(Tab.entries[it]) },
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
        )
    }
}
