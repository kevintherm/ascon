package com.ascon.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.component.CircleIconButton
import com.ascon.core.designsystem.component.PillButton
import com.ascon.core.designsystem.component.PillColors
import com.ascon.core.designsystem.component.overlapAbove
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType

// Sizes from SeriesCaughtUp: a 54 tall block of radius 27 nests 38 circles of radius 19, 8 in.
private val BlockHeight = 54.dp
private val BlockInset = 8.dp
private val BlockCircle = 38.dp

private val SubtextOnAccent = Color.White.copy(alpha = 0.82f)

/** The top of the content sheet: the main button, then Download and alerts. */
@Composable
internal fun Actions(action: PrimaryAction?, actions: SeriesActions) {
    // It slides over the header with rounded corners.
    Row(
        Modifier
            .overlapAbove(SheetOverlap)
            .clip(SheetShape)
            .background(AsconColors.Ground)
            .padding(start = 16.dp, end = 16.dp, top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (action) {
            null -> Spacer(Modifier.weight(1f))
            is PrimaryAction.CaughtUp -> CaughtUpBlock(action) { action.url?.let(actions.onOpenChapter) }
            else -> MainButton(action) { action.url?.let(actions.onOpenChapter) }
        }
        CircleIconButton(
            AsconIcons.Download,
            stringResource(R.string.series_download),
            actions.onDownload,
            size = 54.dp,
            iconSize = 22.dp
        )
        CircleIconButton(
            AsconIcons.Bell,
            stringResource(R.string.series_alerts),
            actions.onAlerts,
            size = 54.dp,
            iconSize = 22.dp
        )
    }
}

@Composable
private fun RowScope.MainButton(action: PrimaryAction, onClick: () -> Unit) {
    PillButton(
        onClick = onClick,
        colors = PillColors.Primary,
        modifier = Modifier.weight(1f),
        spacing = 10.dp
    ) {
        Icon(AsconIcons.Play, null, modifier = Modifier.size(18.dp))
        Column {
            when (action) {
                is PrimaryAction.Continue -> {
                    Text(stringResource(R.string.series_continue, action.chapter), style = AsconType.ButtonLarge)
                    Text(
                        stringResource(R.string.series_continue_page, action.page, action.pageCount),
                        style = AsconType.Caption,
                        color = SubtextOnAccent
                    )
                }
                is PrimaryAction.Start ->
                    Text(stringResource(R.string.series_start, action.chapter), style = AsconType.ButtonLarge)
                is PrimaryAction.Ahead -> {
                    Text(stringResource(R.string.series_read_chapter, action.chapter), style = AsconType.ButtonLarge)
                    Text(
                        stringResource(R.string.series_only_on, action.sourceName),
                        style = AsconType.Caption,
                        color = SubtextOnAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                is PrimaryAction.CaughtUp -> Unit
            }
        }
    }
}

/** Caught up: a white status block in place of the button, with Reread at its end. No accent. */
@Composable
private fun RowScope.CaughtUpBlock(action: PrimaryAction.CaughtUp, onReread: () -> Unit) {
    Row(
        Modifier
            .weight(1f)
            .height(BlockHeight)
            .clip(CircleShape)
            .background(AsconColors.Surface)
            .padding(horizontal = BlockInset),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(BlockCircle).clip(CircleShape).background(AsconColors.Ink),
            contentAlignment = Alignment.Center
        ) {
            Icon(AsconIcons.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.series_caught_up), style = AsconType.ButtonLarge, color = AsconColors.Ink)
            Text(
                stringResource(R.string.series_latest, action.latest),
                style = AsconType.Caption.copy(fontWeight = FontWeight.Normal),
                color = AsconColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        CircleIconButton(
            AsconIcons.Reread,
            stringResource(R.string.series_reread),
            onReread,
            size = BlockCircle,
            iconSize = 18.dp,
            container = AsconColors.SurfaceSunken
        )
    }
}
