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
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.ReadingStatus
import com.ascon.feature.browser.BrowseRoute
import com.ascon.feature.browser.BrowseViewModel
import com.ascon.feature.browser.BrowserActions
import com.ascon.feature.browser.BrowserRoute
import com.ascon.feature.browser.BrowserViewModel
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
    val bottomPadding = FloatingNavBarClearance + NavBarGap +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Tab view models live as long as the activity, like the tabs themselves.
    val home = viewModel { HomeViewModel(container.library, container.accounts) }
    val library = viewModel { LibraryViewModel(container.library, ReadingStatus.Reading) }
    val settings = viewModel { SettingsViewModel(container.settings, container.accounts, container.clock) }
    val browse = viewModel { BrowseViewModel(container.library) }
    // The browser is single-tab and outlives its screen: closing it keeps the page loaded,
    // and the Browse nav item returns to it.
    val browser = viewModel {
        BrowserViewModel(container.library, container.clock, createSavedStateHandle(), startUrl.orEmpty())
    }
    val browserSession = viewModel { BrowserSessionHolder(container.webViews) }.session

    val openSeries: (String) -> Unit = { backStack.push(Route.Series(it)) }
    val openUrl: (String) -> Unit = { backStack.push(Route.Browser(it)) }
    // A chapter picked in the reader, for the browser tab under it to load.
    var browserLoad by rememberSaveable { mutableStateOf<String?>(null) }
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
                        actions = SettingsActions(),
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
                            actions = SeriesActions(onBack = { backStack.pop() })
                        )
                    }
                    entry<Route.Browser> { key ->
                        BrowserRoute(
                            viewModel = browser,
                            session = browserSession,
                            openUrl = key.url,
                            actions = BrowserActions(
                                onClose = { backStack.pop() },
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
                                ReaderViewModel(container.library, container.clock, key.toChapter())
                            },
                            images = container.pageImages,
                            actions = ReaderActions(
                                onBack = { backStack.pop() },
                                onOpenChapter = { url ->
                                    browserLoad = url
                                    backStack.pop()
                                }
                            )
                        )
                    }
                }
            )
            NavBar(
                visible = tabsShown,
                selected = tab,
                onSelect = {
                    if (it == Tab.Browse && !browserSession.isEmpty) {
                        backStack.push(Route.Browser(browser.state.value.url))
                    } else {
                        tab = it
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun NavBar(visible: Boolean, selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        NavItem(AsconIcons.Home, stringResource(R.string.nav_home)),
        NavItem(AsconIcons.Library, stringResource(R.string.nav_library)),
        NavItem(AsconIcons.Browse, stringResource(R.string.nav_browse)),
        NavItem(AsconIcons.Settings, stringResource(R.string.nav_settings))
    )
    AnimatedVisibility(
        visible = visible,
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
