package com.ascon.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.component.ProgressTrack
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.toChapterLabel

// Sizes from screen 04. The panel's buttons nest inside its padding: 28 = 14 + 14.
private val BarHeight = 56.dp
private val BarRadius = 28.dp
private val BarButton = 44.dp
private val PanelRadius = 28.dp
private val PanelPadding = 14.dp
private val PanelButton = 48.dp
private val PanelButtonRadius = 14.dp
private val TrackHeight = 6.dp
private val Knob = 20.dp
private val TrackFill = Color(0x29FFFFFF)

/** Back, the series title, and the chapter with the site it is read on. */
@Composable
internal fun ReaderTopBar(state: ReaderUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BarRadius)
    Row(
        modifier
            .fillMaxWidth()
            .height(BarHeight)
            .glass(shape)
            .padding(horizontal = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(BarButton)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(AsconIcons.Back, stringResource(R.string.reader_back), Modifier.size(22.dp), tint = Color.White)
        }
        Column(Modifier.weight(1f)) {
            Text(
                state.title ?: state.host,
                style = AsconType.CardTitle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            state.chapter?.let {
                Text(
                    stringResource(R.string.reader_subtitle, it.toChapterLabel(), state.host),
                    style = AsconType.Small,
                    color = AsconColors.OnDarkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** The page counter with a track to drag through the chapter, and the chapter buttons. */
@Composable
internal fun ReaderPanel(
    state: ReaderUiState,
    onSeek: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(PanelRadius)
    Column(
        modifier
            .fillMaxWidth()
            .glass(shape)
            .padding(PanelPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.reader_page_count, state.page, state.pageCount),
                style = AsconType.ButtonSmall,
                color = Color.White
            )
            PageTrack(state.page, state.pageCount, onSeek, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChapterButton(
                text = stringResource(R.string.reader_previous),
                icon = AsconIcons.PreviousChapter,
                url = state.previous,
                onClick = onPrevious,
                primary = false
            )
            ChapterButton(
                text = stringResource(R.string.reader_next),
                icon = AsconIcons.NextChapter,
                url = state.next,
                onClick = onNext,
                primary = true
            )
        }
    }
}

/** A drag or a tap on the track jumps to the page under the finger. */
@Composable
private fun PageTrack(page: Int, pageCount: Int, onSeek: (Int) -> Unit, modifier: Modifier = Modifier) {
    val fraction = if (pageCount <= 1) 1f else (page - 1f) / (pageCount - 1)
    val description = stringResource(R.string.reader_progress)
    BoxWithConstraints(
        modifier
            .height(Knob)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(page.toFloat(), 1f..pageCount.coerceAtLeast(1).toFloat())
            }
            .pointerInput(pageCount) { detectTapGestures { onSeek(pageAt(it.x, size.width, pageCount)) } }
            .pointerInput(pageCount) {
                detectHorizontalDragGestures { change, _ -> onSeek(pageAt(change.position.x, size.width, pageCount)) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        ProgressTrack(fraction = fraction, track = TrackFill, height = TrackHeight)
        Box(
            Modifier
                .offset(x = (maxWidth - Knob) * fraction)
                .size(Knob)
                .shadow(4.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/** The page index under [x] on a track [width] wide. */
private fun pageAt(x: Float, width: Int, pageCount: Int): Int =
    ((x / width) * pageCount).toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))

@Composable
private fun RowScope.ChapterButton(
    text: String,
    icon: ImageVector,
    url: String?,
    onClick: () -> Unit,
    primary: Boolean
) {
    val enabled = url != null
    val content = when {
        !enabled -> AsconColors.OnDarkMuted.copy(alpha = 0.4f)
        primary -> AsconColors.Ink
        else -> Color.White
    }
    Row(
        Modifier
            .weight(1f)
            .height(PanelButton)
            .clip(RoundedCornerShape(PanelButtonRadius))
            .background(if (primary && enabled) Color.White else AsconColors.OnDarkFill)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = content)
        Box(Modifier.width(8.dp))
        Text(text, style = AsconType.ButtonSecondary, color = content)
    }
}

private fun Modifier.glass(shape: RoundedCornerShape): Modifier =
    clip(shape).background(AsconColors.Glass).border(1.dp, AsconColors.GlassBorder, shape)
