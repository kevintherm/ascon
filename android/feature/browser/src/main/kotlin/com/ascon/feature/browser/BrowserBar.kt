package com.ascon.feature.browser

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ascon.core.designsystem.component.RowDivider
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconType

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
    onMore: () -> Unit
) {
    val shape = RoundedCornerShape(BarRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .dropShadow(shape, Shadow(radius = 30.dp, color = BarShadow, offset = DpOffset(0.dp, 12.dp)))
            .clip(shape)
            .background(AsconColors.Ink)
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
            AddressPill(state, onClick = { onEditingChange(true) }, modifier = Modifier.weight(1f))
            BarIcon(AsconIcons.More, stringResource(R.string.browser_more), onMore)
        }
    }
}

private val BarShadow = Color(0x66000000)

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
 * The address in `ink2` with the host only. While a page loads, a lighter fill grows
 * across the pill from the left.
 */
@Composable
private fun AddressPill(state: BrowserUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(if (state.loading) state.progress / 100f else 1f, label = "load")
    val fillAlpha by animateFloatAsState(if (state.loading) 1f else 0f, label = "loadFill")
    val label = stringResource(R.string.browser_address, state.host)
    Box(
        modifier
            .height(ItemSize)
            .clip(RoundedCornerShape(ItemRadius))
            .background(AsconColors.Ink2)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .graphicsLayer { alpha = fillAlpha }
                .background(AsconColors.OnDarkFill)
        )
        Text(
            state.host,
            style = AsconType.Button.copy(fontWeight = AsconType.ButtonSecondary.fontWeight),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
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

/** Forward, reload and share, in a card above the bar's right end. */
@Composable
internal fun OverflowMenu(state: BrowserUiState, onDismiss: () -> Unit, commands: BrowserCommands) {
    val shape = RoundedCornerShape(AsconRadius.Card)
    Popup(
        alignment = Alignment.BottomEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(end = BarSide, bottom = BarBottom + BarHeight + 8.dp)
                .widthIn(min = 200.dp)
                .dropShadow(
                    shape,
                    Shadow(radius = 30.dp, color = AsconColors.ShadowFloating, offset = DpOffset(0.dp, 12.dp))
                )
                .clip(shape)
                .background(AsconColors.Surface)
        ) {
            fun run(action: () -> Unit): () -> Unit = {
                onDismiss()
                action()
            }
            MenuRow(
                AsconIcons.Forward,
                stringResource(R.string.browser_forward),
                state.canGoForward,
                run(commands.onForward)
            )
            RowDivider()
            MenuRow(AsconIcons.Reload, stringResource(R.string.browser_reload), true, run(commands.onReload))
            RowDivider()
            MenuRow(AsconIcons.Share, stringResource(R.string.browser_share), true, run(commands.onShare))
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) AsconColors.Ink else AsconColors.TextSubtle
    Row(
        Modifier
            .widthIn(min = 200.dp)
            .height(52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(text, style = AsconType.RowTitleRead, color = color)
    }
}
