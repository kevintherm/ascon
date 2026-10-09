package com.ascon.feature.browser

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.toChapterLabel

// Browser bar from design/tokens.md: 68 tall, radius 34, 10 side padding, 48 items.
internal val BarHeight = 68.dp
private val BarRadius = 34.dp
private val BarPadding = 10.dp
internal val BarSide = 12.dp
internal val BarBottom = 18.dp
internal val ItemSize = 48.dp

/** The items are 48 tall in a 68 bar, so the gap is 10 and their radius is 24. */
private val ItemRadius = AsconRadius.nested(BarRadius, BarPadding)

/** Space under the page for the bar, so the end of a page is never hidden behind it. */
internal val BarClearance = BarHeight + BarBottom + 8.dp

@Composable
internal fun BrowserBar(
    state: BrowserUiState,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onReload: () -> Unit,
    onMore: () -> Unit,
    collapsed: Boolean = false,
    onExpand: () -> Unit = {}
) {
    // Only the height animates, per notes.md. The ends stay round: radius 34 at 68, 18 at 36.
    val height by animateDpAsState(if (collapsed) StripHeight else BarHeight, tween(COLLAPSE_MS), label = "bar")
    val shape = RoundedCornerShape(percent = 50)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (collapsed) {
                    Modifier
                } else {
                    Modifier.dropShadow(
                        shape,
                        Shadow(radius = 30.dp, color = BarShadow, offset = DpOffset(0.dp, 12.dp))
                    )
                }
            )
            .clip(shape)
            .background(if (collapsed) AsconColors.Glass else AsconColors.Ink)
            .then(if (collapsed) Modifier.border(1.dp, AsconColors.GlassBorder, shape) else Modifier)
    ) {
        if (collapsed) {
            CollapsedStrip(state, onExpand)
        } else {
            BarItems(state, editing, onEditingChange, onBack, onSubmit, onReload, onMore)
        }
    }
}

@Composable
private fun BarItems(
    state: BrowserUiState,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
    onReload: () -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = BarPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (editing) {
            BarIcon(AsconIcons.Close, stringResource(R.string.browser_cancel_edit)) { onEditingChange(false) }
            AddressEditor(state.url, onSubmit, Modifier.weight(1f))
        } else {
            BarIcon(
                AsconIcons.Back,
                stringResource(if (state.canGoBack) R.string.browser_back else R.string.browser_close),
                onBack
            )
            AddressPill(
                state,
                onEdit = { onEditingChange(true) },
                onReload = onReload,
                modifier = Modifier.weight(1f)
            )
            BarIcon(AsconIcons.More, stringResource(R.string.browser_menu), onMore)
        }
    }
}

private val BarShadow = Color(0x66000000)

/** The collapsed bar, per BrowserV2Scrolling. */
internal val StripHeight = 36.dp
internal const val COLLAPSE_MS = 200

/** Space under the page for the collapsed strip. */
internal val StripClearance = StripHeight + BarBottom + 8.dp

/** The bar shrunk while scrolling: shield count, host and chapter. A tap expands it. */
@Composable
private fun CollapsedStrip(state: BrowserUiState, onExpand: () -> Unit) {
    val label = stringResource(R.string.browser_show_controls)
    Box(
        Modifier
            .fillMaxSize()
            .clickable(role = Role.Button, onClick = onExpand)
            .semantics { contentDescription = label }
            .padding(start = 6.dp, end = 14.dp)
    ) {
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(StripChipFill)
                .padding(horizontal = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(AsconIcons.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
            Text(
                state.blocked.toString(),
                style = AsconType.CaptionStrong.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
        Text(
            state.host,
            style = AsconType.MetaStrong,
            color = StripHost,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center)
        )
        state.card?.chapter?.let {
            Text(
                stringResource(R.string.browser_menu_chapter_short, it.toChapterLabel()),
                style = AsconType.ButtonSmall,
                color = Color.White,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

private val StripChipFill = Color(0x1FFFFFFF)
private val StripHost = Color(0xD9FFFFFF)

@Composable
private fun BarIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(ItemSize)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/**
 * The address pill in `ink2`: the shield with the page's blocked count, the host, which
 * a tap turns into an address field, and reload. While a page loads, a lighter fill
 * grows across the pill from the left.
 */
@Composable
private fun AddressPill(
    state: BrowserUiState,
    onEdit: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(if (state.loading) state.progress / 100f else 1f, label = "load")
    val fillAlpha by animateFloatAsState(if (state.loading) 1f else 0f, label = "loadFill")
    val label = stringResource(R.string.browser_address, state.host)
    val reloadLabel = stringResource(R.string.browser_reload)
    Box(
        modifier
            .height(ItemSize)
            .clip(RoundedCornerShape(ItemRadius))
            .background(AsconColors.Ink2),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .graphicsLayer { alpha = fillAlpha }
                .background(AsconColors.OnDarkFill)
        )
        Row(
            Modifier.padding(start = 11.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShieldCount(state.blocked)
            Text(
                state.host,
                style = AsconType.Button.copy(fontWeight = AsconType.ButtonSecondary.fontWeight),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .height(ItemSize)
                    .clickable(role = Role.Button, onClick = onEdit)
                    .semantics { contentDescription = label }
                    .wrapContentHeight(Alignment.CenterVertically)
            )
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onReload)
                    .semantics { contentDescription = reloadLabel },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    AsconIcons.Reload,
                    contentDescription = null,
                    tint = AsconColors.OnDarkMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Requests the ad blocker stopped on this page. The protection sheet will open from here. */
@Composable
private fun ShieldCount(count: Int) {
    val label = pluralStringResource(R.plurals.browser_blocked, count, count)
    Row(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(AsconColors.OnDarkFill)
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(AsconIcons.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        Text(count.toString(), style = AsconType.CaptionStrong.copy(fontWeight = FontWeight.Bold), color = Color.White)
    }
}

@Composable
private fun AddressEditor(url: String, onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    var value by remember { mutableStateOf(TextFieldValue(url, selection = TextRange(0, url.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(
        modifier
            .height(ItemSize)
            .clip(RoundedCornerShape(ItemRadius))
            .background(AsconColors.Ink2)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = AsconType.Button.copy(fontWeight = AsconType.ButtonSecondary.fontWeight, color = Color.White),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit(value.text) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
        )
    }
}
