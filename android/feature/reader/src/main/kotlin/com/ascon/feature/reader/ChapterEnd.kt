package com.ascon.feature.reader

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascon.core.designsystem.component.CoverArt
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.toChapterLabel

// Sizes from ReaderEnd.
private val CardRadius = 28.dp
private val CardPadding = 16.dp
private val ButtonRadius = 12.dp
private val CoverWidth = 64.dp
private val CoverHeight = 90.dp
private val CoverRadius = 12.dp
private val CardBorder = Color(0x0FFFFFFF)
private val Eyebrow = Color(0x99FFFFFF)
private val CardEyebrow = Color(0x8CFFFFFF)
private val CardTitle = AsconType.SectionTitle.copy(fontSize = 20.sp)

/**
 * After the last page: the chapter is finished, and what comes next. With a next chapter
 * on the site, Up next and its Read button; without one, the user is caught up on this
 * site. Back to series shows for a series in the library.
 */
@Composable
internal fun ChapterEnd(state: ReaderUiState, onNext: () -> Unit, onOpenSeries: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 24.dp)) {
        Finished(state)
        Column(
            Modifier
                .padding(start = 12.dp, end = 12.dp, top = 28.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(CardRadius))
                .background(AsconColors.DarkCard)
                .border(1.dp, CardBorder, RoundedCornerShape(CardRadius))
                .padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val next = state.next
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                state.cover?.let {
                    CoverArt(it, RoundedCornerShape(CoverRadius), Modifier.size(CoverWidth, CoverHeight))
                }
                if (next != null) UpNext(state) else CaughtUp(state)
            }
            if (next != null) {
                val label = state.nextChapter?.let {
                    stringResource(R.string.reader_end_read_chapter, it.toChapterLabel())
                } ?: stringResource(R.string.reader_next)
                EndButton(label, AsconColors.Accent, AsconType.ButtonLarge, height = 52.dp, onClick = onNext)
            }
            state.seriesId?.let { id ->
                EndButton(stringResource(R.string.reader_end_back), AsconColors.OnDarkFill, AsconType.ButtonSecondary) {
                    onOpenSeries(id)
                }
            }
        }
    }
}

@Composable
private fun Finished(state: ReaderUiState) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(AsconColors.OnDarkFill),
            contentAlignment = Alignment.Center
        ) {
            Icon(AsconIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        val title = state.chapter?.let { stringResource(R.string.reader_end_finished, it.toChapterLabel()) }
            ?: stringResource(R.string.reader_end_finished_unknown)
        Text(title.uppercase(), style = AsconType.Eyebrow, color = Eyebrow, modifier = Modifier.padding(top = 6.dp))
        if (state.seriesId != null) {
            Text(stringResource(R.string.reader_end_saved), style = AsconType.Value, color = AsconColors.OnDarkMuted)
        }
    }
}

@Composable
private fun UpNext(state: ReaderUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.reader_end_up_next).uppercase(), style = AsconType.Eyebrow, color = CardEyebrow)
        Text(
            state.nextChapter?.let { stringResource(R.string.reader_end_chapter, it.toChapterLabel()) }
                ?: stringResource(R.string.reader_next),
            style = CardTitle,
            color = Color.White
        )
        Text(
            stringResource(R.string.reader_end_on, state.host),
            style = AsconType.Meta,
            color = AsconColors.OnDarkMuted
        )
    }
}

@Composable
private fun CaughtUp(state: ReaderUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.reader_end_caught_up).uppercase(), style = AsconType.Eyebrow, color = CardEyebrow)
        state.chapter?.let {
            Text(
                stringResource(R.string.reader_end_latest, it.toChapterLabel()),
                style = CardTitle,
                color = Color.White
            )
        }
        val body =
            state.nextChapter?.let { stringResource(R.string.reader_end_not_out, it.toChapterLabel(), state.host) }
                ?: stringResource(R.string.reader_end_no_next, state.host)
        Text(body, style = AsconType.Meta.copy(lineHeight = 18.sp), color = AsconColors.OnDarkMuted)
    }
}

@Composable
private fun EndButton(text: String, fill: Color, style: TextStyle, height: Dp = 48.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(ButtonRadius))
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = style, color = Color.White)
    }
}
