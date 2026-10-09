package com.ascon.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.component.CoverArt
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.toChapterLabel

// Sizes from BrowserV2Docked: the button nests 6 inside the chip, so 26 = 20 + 6.
private val ChipHeight = 52.dp
private val ChipRadius = 26.dp
private val ChipPadding = 6.dp
private val ButtonRadius = AsconRadius.nested(ChipRadius, ChipPadding)

/** The docked detection card: a glass chip above the bar while the page is a detected chapter. */
@Composable
internal fun ReaderChip(
    card: DetectionCard,
    readerAvailable: Boolean,
    onOpenReader: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(ChipRadius)
    Row(
        modifier
            .fillMaxWidth()
            .height(ChipHeight)
            .dropShadow(shape, Shadow(radius = 28.dp, color = AsconColors.ShadowCover, offset = DpOffset(0.dp, 10.dp)))
            .clip(shape)
            .background(AsconColors.Glass)
            .border(1.dp, AsconColors.GlassBorder, shape)
            .padding(ChipPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        card.cover?.let { CoverArt(it, CircleShape, Modifier.size(40.dp)) }
        Column(Modifier.weight(1f)) {
            card.chapter?.let {
                Text(
                    stringResource(R.string.reader_chip_chapter, it.toChapterLabel()),
                    style = AsconType.CardTitle,
                    color = Color.White
                )
            }
            Text(
                card.title ?: stringResource(R.string.detection_unknown_title),
                style = AsconType.Small,
                color = AsconColors.OnDarkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val page = card.page
        val pageCount = card.pageCount
        if (!readerAvailable && page != null && pageCount != null) {
            // Read as the site shows it: the page on screen stands where the Reader button would.
            Text(
                stringResource(R.string.reader_chip_page, page, pageCount),
                style = AsconType.CardTitle,
                color = AsconColors.OnDarkMuted,
                modifier = Modifier.padding(end = 10.dp)
            )
        }
        if (readerAvailable) {
            Row(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(ButtonRadius))
                    .background(AsconColors.Accent)
                    .clickable(role = Role.Button, onClick = onOpenReader)
                    .padding(start = 12.dp, end = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(AsconIcons.Reader, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.reader_chip_open), style = AsconType.CardTitle, color = Color.White)
            }
        }
    }
}
