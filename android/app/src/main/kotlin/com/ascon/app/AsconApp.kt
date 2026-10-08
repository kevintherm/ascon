package com.ascon.app

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.ascon.core.designsystem.component.FloatingNavBar
import com.ascon.core.designsystem.component.FloatingNavBarClearance
import com.ascon.core.designsystem.component.NavItem
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.feature.browser.BrowseScreen
import com.ascon.feature.library.home.HomeActions
import com.ascon.feature.library.home.HomeRoute
import com.ascon.feature.library.home.HomeViewModel
import com.ascon.feature.library.shelf.LibraryActions
import com.ascon.feature.library.shelf.LibraryRoute
import com.ascon.feature.library.shelf.LibraryViewModel
import com.ascon.feature.series.SeriesActions
import com.ascon.feature.series.SeriesRoute
import com.ascon.feature.series.SeriesViewModel
import com.ascon.feature.settings.SettingsActions
import com.ascon.feature.settings.SettingsRoute
import com.ascon.feature.settings.SettingsViewModel

/** Extra room under scrolling content so its end clears the floating nav. */
private val NavBarGap = 32.dp

/** The whole app: one back stack, the screens, and the floating nav over the tabs. */
@Composable
fun AsconApp(container: AppContainer) {
    val backStack = rememberNavBackStack(Route.Home)
    val bottomPadding = FloatingNavBarClearance + NavBarGap +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val openTab: (Route.TopLevel) -> Unit = { backStack.openTab(it) }
    val openSeries: (String) -> Unit = { backStack.push(Route.Series(it)) }

    AsconTheme {
        Box(Modifier.fillMaxSize()) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.pop() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                entryProvider = entryProvider {
                    entry<Route.Home> {
                        HomeRoute(
                            viewModel = viewModel { HomeViewModel(container.library, container.accounts) },
                            actions = HomeActions(
                                onSearch = { openTab(Route.Browse) },
                                onProfile = { openTab(Route.Settings) },
                                onOpenSeries = openSeries,
                                onSeeAll = { openTab(Route.Library()) },
                                onStatus = { openTab(Route.Library(it)) },
                                onOpenSite = { openTab(Route.Browse) },
                                onAddSite = { openTab(Route.Browse) }
                            ),
                            bottomPadding = bottomPadding
                        )
                    }
                    entry<Route.Library> { key ->
                        LibraryRoute(
                            viewModel = viewModel { LibraryViewModel(container.library, key.status) },
                            actions = LibraryActions(
                                onOpenSeries = openSeries,
                                onOpenSite = { openTab(Route.Browse) }
                            ),
                            bottomPadding = bottomPadding
                        )
                    }
                    entry<Route.Browse> { BrowseScreen(bottomPadding) }
                    entry<Route.Settings> {
                        SettingsRoute(
                            viewModel = viewModel {
                                SettingsViewModel(container.settings, container.accounts, container.clock)
                            },
                            actions = SettingsActions(),
                            bottomPadding = bottomPadding
                        )
                    }
                    entry<Route.Series> { key ->
                        SeriesRoute(
                            viewModel = viewModel { SeriesViewModel(container.library, key.id, container.clock) },
                            actions = SeriesActions(onBack = { backStack.pop() })
                        )
                    }
                }
            )
            NavBar(
                backStack = backStack,
                onSelect = { openTab(it.route()) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun NavBar(backStack: List<NavKey>, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val items = listOf(
        NavItem(AsconIcons.Home, stringResource(R.string.nav_home)),
        NavItem(AsconIcons.Library, stringResource(R.string.nav_library)),
        NavItem(AsconIcons.Browse, stringResource(R.string.nav_browse)),
        NavItem(AsconIcons.Settings, stringResource(R.string.nav_settings))
    )
    val tabs = remember { Tab.entries }
    AnimatedVisibility(
        visible = backStack.showsNavBar(),
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        FloatingNavBar(
            items = items,
            selectedIndex = tabs.indexOf(backStack.currentTab()),
            onSelect = { onSelect(tabs[it]) },
            modifier = Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 20.dp)
        )
    }
}
