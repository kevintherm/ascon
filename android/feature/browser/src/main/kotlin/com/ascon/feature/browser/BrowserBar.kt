package com.ascon.feature.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.toChapterLabel

// The toolbar from BrowserV2Docked: 64 tall, 8 by 12 padding, items 44 tall with radius 12.
private val ToolbarPadding = 12.dp
internal val ItemSize = 44.dp
private val ItemRadius = 12.dp
private val IconWidth = 40.dp
private val ToolbarBorder = Color(0x0FFFFFFF)
private val ShieldFill = Color(0x1AFFFFFF)

/** How long the toolbar takes to slide out of view or back. */
internal const val SLIDE_MS = 200

/** The Reader button grows by this much when the detection card goes into it. */
private const val PULSE_SCALE = 1.12f

/**
 * The toolbar docked at the top: Back, the address box, the Reader button on a chapter
 * page, and the menu. On a chapter read as the site shows it, the Reader button becomes
 * the page being tracked and a notice row sits under the toolbar on the first load.
 * While a page loads, a line runs along the bottom edge.
 */
@Composable
internal fun BrowserToolbar(
    state: BrowserUiState,
    editing: Boolean,
    commands: ToolbarCommands,
    pulse: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth().background(AsconColors.Ink)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = ToolbarPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (editing) {
                ToolbarIcon(AsconIcons.Close, stringResource(R.string.browser_cancel_edit), commands.onCancelEdit)
                AddressEditor(state.url, commands.onSubmit, Modifier.weight(1f))
            } else {
                ToolbarIcon(AsconIcons.Back, stringResource(R.string.browser_back), commands.onBack)
                AddressBox(state, commands.onEdit, commands.onShield, Modifier.weight(1f))
                ReaderSlot(state, commands.onOpenReader, pulse)
                ToolbarIcon(AsconIcons.More, stringResource(R.string.browser_menu), commands.onMore)
            }
        }
        AnimatedVisibility(
            visible = state.readerUnavailable && state.error == null && !editing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ReaderUnavailableRow(commands.onDismissReaderUnavailable)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ToolbarBorder)) {
            LoadLine(state)
        }
    }
}

/** What the toolbar asks of the screen. */
internal class ToolbarCommands(
    val onBack: () -> Unit,
    val onEdit: () -> Unit,
    val onCancelEdit: () -> Unit,
    val onSubmit: (String) -> Unit,
    val onShield: () -> Unit,
    val onOpenReader: () -> Unit,
    val onMore: () -> Unit,
    val onDismissReaderUnavailable: () -> Unit
)

/** A 2 px accent line along the toolbar's bottom edge while the page loads. */
@Composable
private fun LoadLine(state: BrowserUiState) {
    val progress by animateFloatAsState(if (state.loading) state.progress / 100f else 1f, label = "load")
    val alpha by animateFloatAsState(if (state.loading) 1f else 0f, label = "loadLine")
    Box(
        Modifier
            .fillMaxWidth(progress)
            .height(2.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(AsconColors.Accent)
    )
}

@Composable
private fun ToolbarIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .width(IconWidth)
            .height(ItemSize)
            .clip(RoundedCornerShape(ItemRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** The shield with the page's blocked count, and the host, which a tap turns into an address field. */
@Composable
private fun AddressBox(state: BrowserUiState, onEdit: () -> Unit, onShield: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.browser_address, state.host)
    Row(
        modifier
            .height(ItemSize)
            .clip(RoundedCornerShape(ItemRadius))
            .background(AsconColors.Ink2)
            .clickable(role = Role.Button, onClick = onEdit)
            .semantics { contentDescription = label }
            .padding(start = 11.dp, end = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShieldCount(state.blocked.total, onShield)
        Text(
            state.host,
            style = AsconType.Button.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** What protection stopped on this page. A tap opens the protection sheet. */
@Composable
private fun ShieldCount(count: Int, onClick: () -> Unit) {
    val label = pluralStringResource(R.plurals.browser_blocked, count, count)
    Row(
        Modifier
            .height(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(ShieldFill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(AsconIcons.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
        Text(count.toString(), style = AsconType.CaptionStrong.copy(fontWeight = FontWeight.Bold), color = Color.White)
    }
}

/**
 * The Reader button on a chapter the reader can take. It pulses once each time [pulse]
 * changes, as the detection card goes into it. On a chapter read as the site shows it,
 * a neutral chip with the page being tracked stands in its place.
 */
@Composable
private fun ReaderSlot(state: BrowserUiState, onOpenReader: () -> Unit, pulse: Int) {
    val card = state.card
    val page = card?.page
    when {
        state.readerChapter != null -> {
            val scale = remember { Animatable(1f) }
            LaunchedEffect(pulse) {
                if (pulse > 0) {
                    scale.animateTo(PULSE_SCALE, tween(SLIDE_MS))
                    scale.animateTo(1f, tween(SLIDE_MS))
                }
            }
            ToolbarChip(
                AsconIcons.Reader,
                stringResource(R.string.browser_reader),
                AsconColors.Accent,
                AsconType.ButtonSmall.copy(fontWeight = FontWeight.Bold),
                Modifier
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .clickable(role = Role.Button, onClick = onOpenReader)
            )
        }
        card != null && page != null -> {
            val label = card.chapter?.let {
                stringResource(R.string.browser_tracking, it.toChapterLabel(), page)
            } ?: stringResource(R.string.browser_tracking_page, page)
            ToolbarChip(
                AsconIcons.Check,
                stringResource(R.string.browser_tracking_short, page),
                AsconColors.Ink2,
                AsconType.CaptionStrong.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                Modifier.semantics(mergeDescendants = true) { contentDescription = label }
            )
        }
    }
}

@Composable
private fun ToolbarChip(icon: ImageVector, text: String, fill: Color, style: TextStyle, modifier: Modifier = Modifier) {
    Row(
        Modifier
            .height(ItemSize)
            .then(modifier)
            .clip(RoundedCornerShape(ItemRadius))
            .background(fill)
            .padding(start = 10.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(text, style = style, color = Color.White, maxLines = 1)
    }
}

/** Under the toolbar on the first load of a chapter read as the site shows it, per BrowserV2Fallback. */
@Composable
private fun ReaderUnavailableRow(onDismiss: () -> Unit) {
    Row(
        Modifier
            .padding(start = ToolbarPadding, end = ToolbarPadding, bottom = 10.dp)
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AsconColors.OnDarkFill)
            .padding(start = 14.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(AsconIcons.ReaderOff, contentDescription = null, tint = NoticeIcon, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.reader_unavailable),
            style = AsconType.Meta.copy(lineHeight = 18.sp),
            color = NoticeText,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp)
        )
        val dismiss = stringResource(R.string.reader_unavailable_dismiss)
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = dismiss },
            contentAlignment = Alignment.Center
        ) {
            Icon(AsconIcons.Close, contentDescription = null, tint = NoticeIcon, modifier = Modifier.size(16.dp))
        }
    }
}

private val NoticeIcon = Color(0xBFFFFFFF)
private val NoticeText = Color(0xD9FFFFFF)

/**
 * Along the top edge while the toolbar is out of view, on a chapter page: how far
 * through the chapter the page on screen is, in the brand gradient. It takes no touches.
 */
@Composable
internal fun ReadingLine(card: DetectionCard?, modifier: Modifier = Modifier) {
    val page = card?.page ?: return
    val count = card.pageCount?.takeIf { it > 0 } ?: return
    Box(
        modifier
            .fillMaxWidth(page.toFloat() / count)
            .height(2.dp)
            .clip(RoundedCornerShape(topEnd = 1.dp, bottomEnd = 1.dp))
            .background(AsconColors.BrandGradient)
    )
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
            textStyle = AsconType.Button.copy(fontWeight = FontWeight.SemiBold, color = Color.White),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit(value.text) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
        )
    }
}
