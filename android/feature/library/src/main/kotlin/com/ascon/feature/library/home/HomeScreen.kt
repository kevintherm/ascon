package com.ascon.feature.library.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.designsystem.component.Chip
import com.ascon.core.designsystem.component.CoverArt
import com.ascon.core.designsystem.component.CoverTint
import com.ascon.core.designsystem.component.CoverTintBackground
import com.ascon.core.designsystem.component.Dot
import com.ascon.core.designsystem.component.GroupedCard
import com.ascon.core.designsystem.component.PillButton
import com.ascon.core.designsystem.component.PillColors
import com.ascon.core.designsystem.component.ProgressTrack
import com.ascon.core.designsystem.component.RowDivider
import com.ascon.core.designsystem.component.SectionHeader
import com.ascon.core.designsystem.component.StatusBarIcons
import com.ascon.core.designsystem.component.StatusBarScrim
import com.ascon.core.designsystem.component.chapterLabel
import com.ascon.core.designsystem.component.coverTint
import com.ascon.core.designsystem.component.dashedBorder
import com.ascon.core.designsystem.component.statusChipLabel
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.AccountState
import com.ascon.core.model.ReadingStatus
import com.ascon.core.model.Site
import com.ascon.feature.library.R
import java.time.Clock

/** What the home screen can ask the app to do. Navigation is wired in the app module. */
@Immutable
data class HomeActions(
    val onSearch: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onOpenSeries: (String) -> Unit = {},
    val onSeeAll: () -> Unit = {},
    val onStatus: (ReadingStatus) -> Unit = {},
    val onOpenSite: (Site) -> Unit = {},
    val onAddSite: () -> Unit = {}
)

@Composable
fun HomeRoute(viewModel: HomeViewModel, actions: HomeActions, bottomPadding: Dp) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, actions, bottomPadding)
}

/** How far the content sheet slides up over the header. */
private val SheetOverlap = 26.dp

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, bottomPadding: Dp) {
    val scroll = rememberScrollState()
    var headerHeight by remember { mutableIntStateOf(0) }
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)
    val pastHeader by remember {
        derivedStateOf { headerHeight > 0 && scroll.value >= headerHeight - statusBarHeight }
    }
    StatusBarIcons(darkIcons = pastHeader)

    Box(Modifier.fillMaxSize().background(AsconColors.Ground)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Header(state, actions, Modifier.onSizeChanged { headerHeight = it.height })
            Content(
                state = state,
                actions = actions,
                modifier = Modifier
                    .offset(y = -SheetOverlap)
                    .clip(RoundedCornerShape(topStart = AsconRadius.Sheet, topEnd = AsconRadius.Sheet))
                    .background(AsconColors.Ground)
                    .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = bottomPadding - SheetOverlap)
            )
        }
        StatusBarScrim(visible = pastHeader)
    }
}

@Composable
private fun Header(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val hero = state.continueReading
    val tint = remember(hero?.cover) { hero?.cover?.let(::coverTint) ?: CoverTint.Fallback }
    CoverTintBackground(tint, modifier.fillMaxWidth()) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 38.dp + SheetOverlap)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchField(actions.onSearch, Modifier.weight(1f))
                ProfileButton(state.profileInitial, actions.onProfile)
            }
            Spacer(Modifier.height(30.dp))
            if (hero != null) {
                Hero(hero, onResume = { actions.onOpenSeries(hero.seriesId) })
            } else if (!state.loading) {
                Welcome()
            }
        }
    }
}

@Composable
private fun SearchField(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.13f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(AsconIcons.Search, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.home_search_hint),
            style = AsconType.Body,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1
        )
    }
}

private val AvatarGradient = Brush.linearGradient(listOf(Color(0xFFF2D5C4), Color(0xFFC98F7A)))
private val AvatarInk = Color(0xFF3A1A20)

@Composable
private fun ProfileButton(initial: String?, onClick: () -> Unit) {
    val label = stringResource(R.string.home_profile)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (initial != null) {
                    Modifier.background(AvatarGradient)
                } else {
                    Modifier.background(Color.White.copy(alpha = 0.13f))
                }
            )
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        if (initial != null) {
            Text(initial, style = AsconType.ButtonLarge, color = AvatarInk)
        } else {
            Icon(AsconIcons.Person, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Hero(hero: ContinueReading, onResume: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.home_continue_reading).uppercase(),
            style = AsconType.Eyebrow,
            color = Color.White.copy(alpha = 0.72f)
        )
        Text(
            hero.title,
            style = AsconType.HeaderTitle,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            stringResource(R.string.home_continue_meta, hero.chapter, hero.page, hero.pageCount, hero.sourceName),
            style = AsconType.Meta,
            color = Color.White.copy(alpha = 0.78f)
        )
        ProgressTrack(hero.fraction, Modifier.padding(top = 6.dp), track = Color.White.copy(alpha = 0.22f))
        PillButton(
            text = stringResource(R.string.home_resume),
            onClick = onResume,
            colors = PillColors.Surface,
            height = 44.dp,
            textStyle = AsconType.Button,
            icon = AsconIcons.Play,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun Welcome() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.home_welcome_title), style = AsconType.HeaderTitle, color = Color.White)
        Text(
            stringResource(R.string.home_welcome_body),
            style = AsconType.Meta,
            color = Color.White.copy(alpha = 0.78f)
        )
    }
}

@Composable
private fun Content(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (state.statusCounts.any { (_, count) -> count > 0 }) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.statusCounts.forEach { (status, count) ->
                    Chip(statusChipLabel(status, count), selected = false, onClick = { actions.onStatus(status) })
                }
            }
        }
        if (state.newChapters.isNotEmpty()) {
            Section(
                stringResource(R.string.home_new_chapters),
                stringResource(R.string.home_see_all),
                actions.onSeeAll
            ) {
                GroupedCard {
                    state.newChapters.forEachIndexed { index, item ->
                        if (index > 0) RowDivider(inset = NewChapterInset)
                        NewChapterRow(
                            item = item,
                            first = index == 0,
                            last = index == state.newChapters.lastIndex,
                            onClick = { actions.onOpenSeries(item.seriesId) }
                        )
                    }
                }
            }
        }
        if (state.sites.isNotEmpty()) {
            Section(stringResource(R.string.home_your_sites)) {
                SiteGrid(state.sites, actions.onOpenSite, actions.onAddSite)
            }
        }
    }
}

@Composable
private fun Section(title: String, action: String? = null, onAction: () -> Unit = {}, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(title, action = action, onAction = onAction)
        content()
    }
}

/** Side padding of a new-chapter row, from the mockup's card padding. */
private val NewChapterInset = 14.dp

/** The card's 4 of top and bottom padding moves into its first and last rows, so their press reaches the edge. */
private val CardEdgePadding = 4.dp

@Composable
private fun NewChapterRow(item: NewChapterItem, first: Boolean, last: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = NewChapterInset,
                end = NewChapterInset,
                top = 12.dp + if (first) CardEdgePadding else 0.dp,
                bottom = 12.dp + if (last) CardEdgePadding else 0.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArt(item.cover, RoundedCornerShape(AsconRadius.CoverThumb), Modifier.size(46.dp, 64.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = AsconType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.hasNew) Dot(AsconColors.Accent)
                Text(
                    "${chapterLabel(item.firstChapter, item.lastChapter)} · ${detailText(item.detail)}",
                    style = AsconType.Meta,
                    color = AsconColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        PillButton(
            text = stringResource(R.string.home_read_chapter, item.firstChapter),
            onClick = onClick,
            colors = PillColors.Outline,
            height = 40.dp,
            textStyle = AsconType.ButtonSecondary
        )
    }
}

@Composable
private fun detailText(detail: NewChapterDetail): String = when (detail) {
    NewChapterDetail.Downloaded -> stringResource(R.string.home_downloaded)
    is NewChapterDetail.Sources -> pluralStringResource(R.plurals.home_sources, detail.count, detail.count)
    is NewChapterDetail.OneSource -> detail.name
}

private const val SITE_COLUMNS = 4

@Composable
private fun SiteGrid(sites: List<Site>, onOpen: (Site) -> Unit, onAdd: () -> Unit) {
    val cells: List<Site?> = sites + null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(SITE_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { site ->
                    val cell = Modifier.weight(1f)
                    if (site == null) {
                        AddSiteTile(onAdd, cell)
                    } else {
                        SiteTile(site, highlighted = site == sites.first(), onClick = { onOpen(site) }, modifier = cell)
                    }
                }
                repeat(SITE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private val TileShape = RoundedCornerShape(AsconRadius.Tile)

/** A site tile. The most recently visited site gets the ink monogram. */
@Composable
private fun SiteTile(site: Site, highlighted: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(72.dp)
            .clip(TileShape)
            .background(AsconColors.Surface)
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (highlighted) AsconColors.Ink else AsconColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            Text(site.monogram, style = AsconType.Badge, color = if (highlighted) Color.White else AsconColors.Ink)
        }
        Text(
            site.name,
            style = AsconType.CaptionStrong,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun AddSiteTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.home_add_site)
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(TileShape)
            .dashedBorder(1.5.dp, AsconColors.BorderDashed, AsconRadius.Tile, dash = 5.dp, gap = 4.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(AsconIcons.Plus, null, tint = AsconColors.TextMuted, modifier = Modifier.size(22.dp))
    }
}

/** Preview and screenshot state built from the fake library. */
internal fun previewHomeState(signedIn: Boolean = true, clock: Clock = Clock.systemDefaultZone()) = homeUiState(
    series = FakeLibrary.series(clock),
    sites = FakeLibrary.sites,
    account = if (signedIn) {
        AccountState.SignedIn(
            "Kevin",
            premium = false,
            lastSyncedAt = null
        )
    } else {
        AccountState.SignedOut
    }
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomePreview() {
    AsconTheme { HomeScreen(previewHomeState(), HomeActions(), bottomPadding = 120.dp) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeEmptyPreview() {
    AsconTheme {
        HomeScreen(
            homeUiState(emptyList(), emptyList(), AccountState.SignedOut),
            HomeActions(),
            bottomPadding = 120.dp
        )
    }
}
