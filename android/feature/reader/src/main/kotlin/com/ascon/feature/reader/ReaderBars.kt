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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.component.ProgressTrack
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.toChapterLabel

// Sizes from the Reader screen. The panel's buttons nest inside its padding: 28 = 14 + 14.
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

/** Back, the series title, the chapter with the site it is read on, and Aa for the settings. */
@Composable
internal fun ReaderTopBar(
    state: ReaderUiState,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        val settings = stringResource(R.string.reader_settings_open)
        Box(
            Modifier
                .size(BarButton)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onSettings)
                .semantics { contentDescription = settings },
            contentAlignment = Alignment.Center
        ) {
            Text("Aa", style = AsconType.Button.copy(fontWeight = FontWeight.Bold), color = Color.White)
        }
    }
}

/** The page counter with a track to drag through the chapter, the chapter buttons and Chapters. */
@Composable
internal fun ReaderPanel(
    state: ReaderUiState,
    onSeek: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChapters: () -> Unit,
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
            state.minutesLeft?.let {
                val spoken = pluralStringResource(R.plurals.reader_minutes_left_spoken, it, it)
                Text(
                    stringResource(R.string.reader_minutes_left, it),
                    style = AsconType.Meta,
                    color = Muted,
                    modifier = Modifier.semantics {
                        contentDescription = spoken
                    }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChapterIconButton(
                AsconIcons.PreviousChapter,
                stringResource(R.string.reader_previous),
                enabled = state.previous != null,
                primary = false,
                onClick = onPrevious
            )
            PanelButton(AsconIcons.Chapters, stringResource(R.string.reader_chapters), onChapters)
            ChapterIconButton(
                AsconIcons.NextChapter,
                stringResource(R.string.reader_next),
                enabled = state.next != null,
                primary = true,
                onClick = onNext
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

/** Previous or next chapter, per Reader: the next one is white, and a missing one is dimmed. */
@Composable
private fun ChapterIconButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit
) {
    val content = when {
        !enabled -> AsconColors.OnDarkMuted.copy(alpha = 0.4f)
        primary -> AsconColors.Ink
        else -> Color.White
    }
    Box(
        Modifier
            .size(PanelButton)
            .clip(RoundedCornerShape(PanelButtonRadius))
            .background(if (primary && enabled) Color.White else AsconColors.OnDarkFill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = content)
    }
}

@Composable
private fun RowScope.PanelButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .weight(1f)
            .height(PanelButton)
            .clip(RoundedCornerShape(PanelButtonRadius))
            .background(AsconColors.OnDarkFill)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
        Text(text, style = AsconType.ButtonSmall.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
    }
}

private fun Modifier.glass(shape: RoundedCornerShape): Modifier =
    clip(shape).background(AsconColors.Glass).border(1.dp, AsconColors.GlassBorder, shape)
