package com.ascon.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.ascon.core.designsystem.component.CoverArt
import com.ascon.core.designsystem.component.Dot
import com.ascon.core.designsystem.component.PillColors
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.toChapterLabel
import com.ascon.feature.browser.web.BlockedKind

private val CardShape = RoundedCornerShape(AsconRadius.Sheet)
private val CardPadding = 14.dp

/** Buttons sit 14 inside a radius-28 card, so their radius is 14. */
private val CardButtonShape = RoundedCornerShape(AsconRadius.nested(AsconRadius.Sheet, CardPadding))

private val CardButtonHeight = 48.dp

/** A swipe down this far puts the card away early. */
private val SwipeAway = 40.dp

private val CardTitle = AsconType.SectionTitle.copy(fontSize = 16.sp)

/** How long the card stays before it goes. A series not saved yet gets longer. */
private const val COUNTDOWN_MS = 5_000f
private const val COUNTDOWN_UNSAVED_MS = 8_000f

/**
 * The card that says what Ascon detected on the page, per BrowserV2Detected. A line
 * along its bottom counts down, then [onDock] puts it away into the toolbar's Reader
 * button. Touching the card pauses the countdown; the chevron or a swipe down puts it
 * away at once.
 */
@Composable
internal fun DetectionCardView(
    card: DetectionCard,
    readerAvailable: Boolean,
    onOpenReader: () -> Unit,
    onOpenSeries: (String) -> Unit,
    onDock: () -> Unit
) {
    var remaining by remember(card.url, card.chapter) { mutableFloatStateOf(1f) }
    var touched by remember { mutableStateOf(false) }
    val duration = if (card.saved) COUNTDOWN_MS else COUNTDOWN_UNSAVED_MS
    LaunchedEffect(card.url, card.chapter, touched) {
        if (touched) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (remaining > 0f) {
            val now = withFrameMillis { it }
            remaining -= (now - last) / duration
            last = now
        }
        onDock()
    }
    Box(
        Modifier
            .fillMaxWidth()
            .dropShadow(
                CardShape,
                Shadow(radius = 40.dp, color = AsconColors.ShadowCover, offset = DpOffset(0.dp, 16.dp))
            )
            .clip(CardShape)
            .background(AsconColors.Surface)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        touched = event.changes.any { it.pressed }
                    }
                }
            }
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (dragged > SwipeAway.toPx()) onDock() }
                ) { _, amount -> dragged += amount }
            }
    ) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CardHeader(card, onDock)
            val seriesId = card.seriesId
            when {
                readerAvailable -> CardButton(
                    stringResource(R.string.detection_open_reader),
                    PillColors.Primary,
                    Modifier.fillMaxWidth(),
                    onOpenReader
                )
                seriesId != null -> CardButton(
                    stringResource(R.string.detection_open_series),
                    PillColors.Primary,
                    Modifier.fillMaxWidth()
                ) { onOpenSeries(seriesId) }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(AsconColors.SurfaceSunken)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(remaining.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(AsconColors.Ink)
            )
        }
    }
}

@Composable
private fun CardHeader(card: DetectionCard, onDock: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        card.cover?.let {
            CoverArt(it, RoundedCornerShape(AsconRadius.CoverThumb), Modifier.size(44.dp, 62.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(AsconColors.Accent)
                val eyebrow = if (card.seriesId !=
                    null
                ) {
                    R.string.detection_eyebrow_library
                } else {
                    R.string.detection_eyebrow
                }
                Text(
                    stringResource(eyebrow).uppercase(),
                    style = AsconType.Eyebrow.copy(letterSpacing = AsconType.Eyebrow.letterSpacing * 2 / 3),
                    color = AsconColors.TextMuted
                )
            }
            Text(
                card.title ?: stringResource(R.string.detection_unknown_title),
                style = CardTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(chapterLine(card), style = AsconType.Meta, color = AsconColors.TextMuted)
        }
        val dock = stringResource(R.string.detection_dock)
        Box(
            Modifier
                .align(Alignment.Top)
                .size(36.dp)
                .clip(CircleShape)
                .background(AsconColors.Ground)
                .clickable(role = Role.Button, onClick = onDock)
                .semantics { contentDescription = dock },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                AsconIcons.ChevronDown,
                contentDescription = null,
                tint = AsconColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun chapterLine(card: DetectionCard): String {
    val chapter = card.chapter?.toChapterLabel() ?: return stringResource(R.string.detection_no_chapter)
    val page = card.page
    val pageCount = card.pageCount
    return if (page != null && pageCount != null) {
        val line = if (card.saved) R.string.detection_chapter_page_saved else R.string.detection_chapter_page
        stringResource(line, chapter, page, pageCount)
    } else if (card.saved) {
        stringResource(R.string.detection_chapter_saved, chapter)
    } else {
        stringResource(R.string.detection_chapter_not_in_library, chapter)
    }
}

@Composable
private fun CardButton(text: String, colors: PillColors, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(CardButtonHeight)
            .clip(CardButtonShape)
            .background(colors.container)
            .then(colors.border?.let { Modifier.border(it, CardButtonShape) } ?: Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = if (colors == PillColors.Primary) {
                AsconType.Button
            } else {
                AsconType.Button.copy(
                    fontWeight = AsconType.ButtonSecondary.fontWeight
                )
            },
            color = colors.content,
            maxLines = 1
        )
    }
}

/** A glass pill over the page's bottom, for a few seconds. */
@Composable
internal fun NoticePill(text: String) {
    val shape = CircleShape
    Text(
        text,
        style = AsconType.ButtonSecondary,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(shape)
            .background(AsconColors.Glass)
            .border(1.dp, AsconColors.GlassBorder, shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
internal fun noticeText(notice: Notice): String = when (notice.kind) {
    BlockedKind.Redirect -> stringResource(R.string.blocked_redirect, notice.host)
    BlockedKind.OtherApp -> stringResource(R.string.blocked_other_app)
    BlockedKind.Popup -> stringResource(R.string.blocked_popup)
    BlockedKind.AppDownload -> stringResource(R.string.blocked_app_download)
    BlockedKind.Download -> stringResource(R.string.blocked_download)
    BlockedKind.Ad -> stringResource(R.string.blocked_ad, notice.host)
}
