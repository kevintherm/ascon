package com.ascon.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.Chapter
import com.ascon.core.model.toChapterLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val ReadText = Color(0xB3FFFFFF)
private val ReadIcon = Color(0x8CFFFFFF)
private val Current = Color(0x14FFFFFF)
private val TrackFill = Color(0x24FFFFFF)

@Composable
internal fun ChapterRow(
    chapter: Chapter,
    state: ReaderUiState,
    current: Boolean,
    divider: Boolean,
    onClick: (() -> Unit)?
) {
    val title = stringResource(R.string.reader_end_chapter, chapter.number.toChapterLabel())
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (current) Current else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = if (current) 12.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.height(if (current) 32.dp else 56.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Marker(chapter, current)
                Text(
                    title,
                    style = AsconType.Button.copy(fontWeight = titleWeight(chapter, current)),
                    color = if (chapter.read && !current) ReadText else Color.White,
                    modifier = Modifier.weight(1f)
                )
                Trailing(chapter, state, current)
            }
            if (current && state.pageCount > 0) {
                Box(
                    Modifier
                        .padding(start = 20.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TrackFill)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(state.page.toFloat() / state.pageCount)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(AsconColors.BrandGradient)
                    )
                }
            }
        }
        if (divider) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Track))
    }
}

private fun titleWeight(chapter: Chapter, current: Boolean) = when {
    current -> FontWeight.ExtraBold
    chapter.isNew && !chapter.read -> FontWeight.Bold
    else -> FontWeight.SemiBold
}

/** A ring for this chapter, a check for a read one, the accent dot for a new one. */
@Composable
private fun Marker(chapter: Chapter, current: Boolean) {
    when {
        current -> Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White)
                .padding(2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SheetFill)
        )
        chapter.read -> {
            // The check is wider than the dots but keeps their slot, so the titles line up.
            val read = stringResource(R.string.reader_chapters_read)
            Box(Modifier.width(8.dp), contentAlignment = Alignment.Center) {
                Icon(
                    AsconIcons.Check,
                    contentDescription = read,
                    tint = ReadIcon,
                    modifier = Modifier.requiredSize(14.dp)
                )
            }
        }
        chapter.isNew -> {
            val new = stringResource(R.string.reader_chapters_new)
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AsconColors.Accent)
                    .semantics { contentDescription = new }
            )
        }
        else -> Box(Modifier.size(8.dp))
    }
}

@Composable
private fun Trailing(chapter: Chapter, state: ReaderUiState, current: Boolean) {
    val elsewhere = chapter.readOnSourceId?.let { state.siteNames[it] }?.takeIf { it != state.host }
    when {
        current -> Text(
            stringResource(R.string.reader_chapters_reading, state.page, state.pageCount).uppercase(),
            style = AsconType.Eyebrow,
            color = Unselected
        )
        chapter.downloaded -> Icon(
            AsconIcons.Download,
            contentDescription = stringResource(R.string.reader_chapters_downloaded),
            tint = ReadIcon,
            modifier = Modifier.size(18.dp)
        )
        elsewhere != null -> Text(
            stringResource(R.string.reader_chapters_via, elsewhere),
            style = AsconType.Meta,
            color = Muted
        )
        else -> chapter.publishedOn?.let { date ->
            val text = if (date == state.today) {
                stringResource(R.string.reader_chapters_today)
            } else {
                shortDate(date, state.today)
            }
            Text(text, style = AsconType.Meta, color = Muted)
        }
    }
}

/** "Oct 1" this year, "Oct 1, 2025" before it. */
private fun shortDate(date: LocalDate, today: LocalDate?): String {
    val pattern = if (today == null || date.year == today.year) "MMM d" else "MMM d, yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern))
}
