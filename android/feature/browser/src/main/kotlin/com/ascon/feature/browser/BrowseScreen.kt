package com.ascon.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.ascon.core.designsystem.component.GroupedCard
import com.ascon.core.designsystem.component.RowDivider
import com.ascon.core.designsystem.component.RowInset
import com.ascon.core.designsystem.component.StatusBarIcons
import com.ascon.core.designsystem.component.StatusBarScrim
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.Site
import com.ascon.feature.browser.web.WebViewHealth
import com.ascon.feature.browser.web.addressToUrl
import com.ascon.feature.browser.web.webViewHealth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class BrowseViewModel(library: LibraryRepository) : ViewModel() {
    val sites: StateFlow<List<Site>> = library.sites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
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
    val context = LocalContext.current
    val inPreview = LocalInspectionMode.current
    val health = remember { if (inPreview) WebViewHealth.Ok else webViewHealth(context) }
    BrowseScreen(sites, health, onOpen, bottomPadding, openPage, onReturnToPage, onClosePage)
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
    onClosePage: () -> Unit = {}
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
            AddressField(onSubmit = { addressToUrl(it)?.let(onOpen) })
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
                } else {
                    GroupedCard {
                        sites.forEachIndexed { i, site ->
                            if (i > 0) RowDivider(start = RowInset + MonogramSize + 12.dp)
                            SiteRow(site) { onOpen("https://${site.domain}/") }
                        }
                    }
                }
            }
        }
        StatusBarScrim(visible = scroll.value > 0)
    }
}

private val FieldHeight = 54.dp
private val MonogramSize = 36.dp

@Composable
private fun AddressField(onSubmit: (String) -> Unit) {
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
            modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun SiteRow(site: Site, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = RowInset),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(MonogramSize)
                .clip(RoundedCornerShape(10.dp))
                .background(AsconColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            Text(site.monogram, style = AsconType.Badge, color = AsconColors.Ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(site.name, style = AsconType.RowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(site.domain, style = AsconType.Small, color = AsconColors.TextMuted, maxLines = 1)
        }
        Icon(
            AsconIcons.ChevronRight,
            contentDescription = null,
            tint = AsconColors.TextSubtle,
            modifier = Modifier.size(18.dp)
        )
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
            openPage = BrowserUiState(url = PreviewCard.url, card = PreviewCard).openPage()
        )
    }
}
