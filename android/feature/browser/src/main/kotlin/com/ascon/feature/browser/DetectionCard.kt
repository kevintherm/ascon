package com.ascon.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.ascon.core.designsystem.component.CoverArt
import com.ascon.core.designsystem.component.Dot
import com.ascon.core.designsystem.component.PillColors
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.toChapterLabel
import com.ascon.feature.browser.web.BlockedKind

private val CardShape = RoundedCornerShape(AsconRadius.Sheet)
private val CardPadding = 14.dp

/** Buttons sit 14 inside a radius-28 card, so their radius is 14. */
private val CardButtonShape = RoundedCornerShape(AsconRadius.nested(AsconRadius.Sheet, CardPadding))

private val CardTitle = AsconType.SectionTitle.copy(fontSize = 16.sp)

/** The card that says what Ascon detected on the page, per screen 03. */
@Composable
internal fun DetectionCardView(card: DetectionCard, onOpenSeries: (String) -> Unit, onHide: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .dropShadow(
                CardShape,
                Shadow(radius = 40.dp, color = AsconColors.ShadowCover, offset = DpOffset(0.dp, 16.dp))
            )
            .clip(CardShape)
            .background(AsconColors.Surface)
            .padding(CardPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            card.cover?.let {
                CoverArt(it, RoundedCornerShape(AsconRadius.CoverThumb), Modifier.size(44.dp, 62.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Dot(AsconColors.Accent)
                    Text(
                        stringResource(
                            if (card.seriesId !=
                                null
                            ) {
                                R.string.detection_eyebrow_library
                            } else {
                                R.string.detection_eyebrow
                            }
                        ).uppercase(),
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
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val seriesId = card.seriesId
            if (seriesId != null) {
                CardButton(
                    stringResource(R.string.detection_open_series),
                    PillColors.Primary,
                    Modifier.weight(1f)
                ) { onOpenSeries(seriesId) }
            }
            CardButton(
                stringResource(R.string.detection_hide),
                PillColors.Outline,
                if (seriesId == null) Modifier.weight(1f) else Modifier,
                onHide
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
            .height(ItemSize)
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

/** A glass pill above the bar, for a few seconds. */
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
