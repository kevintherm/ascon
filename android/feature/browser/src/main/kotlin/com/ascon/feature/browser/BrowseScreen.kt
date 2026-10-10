package com.ascon.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ascon.core.data.LibraryRepository
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.designsystem.component.Eyebrow
import com.ascon.core.designsystem.component.StatusBarIcons
import com.ascon.core.designsystem.component.StatusBarScrim
import com.ascon.core.designsystem.component.dashedBorder
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.Site
import com.ascon.feature.browser.web.WebViewHealth
import com.ascon.feature.browser.web.addressToUrl
import com.ascon.feature.browser.web.webViewHealth
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class BrowseViewModel(library: LibraryRepository, val clock: Clock) : ViewModel() {
    val sites: StateFlow<List<Site>> = library.sites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recent: StateFlow<List<RecentPage>> = combine(library.series, library.sites, ::recentPages)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

@Composable
fun BrowseRoute(
    viewModel: BrowseViewModel,
    onOpen: (String) -> Unit,
    bottomPadding: Dp,
    openPage: OpenPage? = null,
    onReturnToPage: () -> Unit = {},
    onClosePage: () -> Unit = {}
) {
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val health = remember { if (inPreview) WebViewHealth.Ok else webViewHealth(context) }
    BrowseScreen(
        sites,
        health,
        onOpen,
        bottomPadding,
        openPage,
        onReturnToPage,
        onClosePage,
        recent,
        LocalDate.now(viewModel.clock),
        viewModel.clock.zone
    )
}

/**
 * The Browse tab: an address field, the page the browser kept, and the sites the user
 * reads on. Pages open in a browser tab.
 */
@Composable
fun BrowseScreen(
    sites: List<Site>,
    health: WebViewHealth,
    onOpen: (String) -> Unit,
    bottomPadding: Dp,
    openPage: OpenPage? = null,
    onReturnToPage: () -> Unit = {},
    onClosePage: () -> Unit = {},
    recent: List<RecentPage> = emptyList(),
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
) {
    StatusBarIcons(darkIcons = true)
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxSize().background(AsconColors.Ground)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                stringResource(R.string.browse_title),
                style = AsconType.ScreenTitle,
                modifier = Modifier.semantics { heading() }
            )
            val address = remember { FocusRequester() }
            AddressField(address, onSubmit = { addressToUrl(it)?.let(onOpen) })
            if (health != WebViewHealth.Ok) WebViewWarning(health)
            openPage?.let { OpenPageCard(it, onReturnToPage, onClosePage) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Eyebrow(stringResource(R.string.browse_your_sites))
                if (sites.isEmpty()) {
                    Text(
                        stringResource(R.string.browse_no_sites),
                        style = AsconType.Body,
                        color = AsconColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                SiteGrid(sites, onOpen = { onOpen("https://${it.domain}/") }, onAdd = address::requestFocus)
            }
            if (recent.isNotEmpty()) RecentlyVisited(recent, today, zone, onOpen)
        }
        StatusBarScrim(visible = scroll.value > 0)
    }
}

private val FieldHeight = 54.dp
private val MonogramSize = 26.dp

@Composable
private fun AddressField(requester: FocusRequester, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(FieldHeight)
            .clip(CircleShape)
            .background(AsconColors.Surface)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            AsconIcons.Search,
            contentDescription = null,
            tint = AsconColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = AsconType.Button.copy(
                fontWeight = AsconType.ButtonSecondary.fontWeight,
                color = AsconColors.Ink
            ),
            cursorBrush = SolidColor(AsconColors.Ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                focus.clearFocus()
                onSubmit(text)
                text = ""
            }),
            modifier = Modifier.fillMaxWidth().focusRequester(requester),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            stringResource(R.string.browse_address_hint),
                            style = AsconType.ButtonSecondary.copy(fontSize = AsconType.Button.fontSize),
                            color = AsconColors.TextMuted
                        )
                    }
                    field()
                }
            }
        )
    }
}

/** Sites per row in Your sites, per BrowseIdle. */
private const val SITE_COLUMNS = 4
private val TileShape = RoundedCornerShape(AsconRadius.Tile)

/** Your sites as tiles, four to a row, ending with a tile that adds a site by its address. */
@Composable
private fun SiteGrid(sites: List<Site>, onOpen: (Site) -> Unit, onAdd: () -> Unit) {
    val tiles: List<Site?> = sites + null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(SITE_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { site ->
                    val tile = Modifier.weight(1f).height(72.dp)
                    if (site == null) AddSiteTile(onAdd, tile) else SiteTile(site, { onOpen(site) }, tile)
                }
                repeat(SITE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SiteTile(site: Site, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(TileShape)
            .background(AsconColors.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(MonogramSize)
                .clip(RoundedCornerShape(8.dp))
                .background(AsconColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            Text(site.monogram, style = AsconType.Badge, color = AsconColors.Ink)
        }
        Text(
            site.name,
            style = AsconType.CaptionStrong,
            color = AsconColors.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AddSiteTile(onClick: () -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.browse_add_site)
    Box(
        modifier
            .clip(TileShape)
            .dashedBorder(1.5.dp, AsconColors.BorderDashed, AsconRadius.Tile, dash = 5.dp, gap = 4.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(AsconIcons.Plus, contentDescription = null, tint = AsconColors.TextMuted, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun WebViewWarning(health: WebViewHealth) {
    val (title, body) = when (health) {
        WebViewHealth.Outdated -> R.string.browse_webview_outdated_title to R.string.browse_webview_outdated_body
        WebViewHealth.NoDetection ->
            R.string.browse_webview_no_detection_title to
                R.string.browse_webview_no_detection_body
        else -> R.string.browse_webview_missing_title to R.string.browse_webview_missing_body
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AsconRadius.Card))
            .background(AsconColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(stringResource(title), style = AsconType.RowTitle)
        Text(stringResource(body), style = AsconType.Meta, color = AsconColors.TextMuted)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BrowsePreview() {
    AsconTheme {
        BrowseScreen(
            FakeLibrary.sites,
            WebViewHealth.Ok,
            onOpen = {},
            bottomPadding = 120.dp,
            openPage = BrowserUiState(url = PreviewCard.url, card = PreviewCard).openPage(),
            recent = PreviewRecent,
            today = PreviewToday
        )
    }
}
