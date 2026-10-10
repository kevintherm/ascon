package com.ascon.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ascon.core.designsystem.component.BottomSheet
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.Chapter
import com.ascon.core.model.toChapterLabel
import java.math.BigDecimal

/** The sheet starts 108 below the top of the screen, per ReaderChapters. */
private val SheetTop = 108.dp

/** The grabber, the space under it and the sheet's own padding, above and below the content. */
private val SheetChrome = 10.dp + 5.dp + 14.dp + 24.dp

internal val Muted = Color(0x99FFFFFF)

/**
 * The chapters sheet, per ReaderChapters, from the Chapters button: the series' chapters,
 * newest first, with this one and its page marked. A chapter opens on this site when its
 * address can be made from this chapter's, by swapping the number.
 */
@Composable
internal fun ReaderChaptersSheet(
    visible: Boolean,
    state: ReaderUiState,
    onOpen: (BigDecimal) -> Unit,
    onDismiss: () -> Unit
) {
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    val chapters = chaptersOf(state).let { if (newestFirst) it.asReversed() else it }
    BottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        spacing = 14.dp,
        bottomPadding = 24.dp,
        container = SheetFill,
        grabber = Grabber
    ) {
        Column(Modifier.height(sheetContentHeight()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Header(state, chapters.size)
            GoToChapter(state, newestFirst, { newestFirst = !newestFirst }, onOpen)
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Tile)
            ) {
                items(chapters, key = { it.number.toChapterLabel() }) { chapter ->
                    val current = chapter.number.compareTo(state.chapter ?: BigDecimal(-1)) == 0
                    val opens = current || chapterUrl(state.url, state.chapter, chapter.number) != null
                    ChapterRow(
                        chapter = chapter,
                        state = state,
                        current = current,
                        divider = chapter != chapters.last(),
                        onClick = when {
                            current -> onDismiss
                            opens -> { -> onOpen(chapter.number) }
                            else -> null
                        }
                    )
                }
            }
        }
    }
}

/** The library's chapters, with this one added when the library doesn't have it yet. */
private fun chaptersOf(state: ReaderUiState): List<Chapter> {
    val number = state.chapter
    val known = number == null || state.chapters.any { it.number.compareTo(number) == 0 }
    return if (known) state.chapters else (state.chapters + Chapter(number, null, read = false)).sortedBy { it.number }
}

@Composable
private fun sheetContentHeight() = with(LocalDensity.current) {
    val window = LocalWindowInfo.current.containerSize.height.toDp()
    val insets = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    window - insets - SheetTop - SheetChrome
}

@Composable
private fun Header(state: ReaderUiState, count: Int) {
    Row(
        Modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.reader_chapters),
                style = AsconType.SectionTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                color = Color.White
            )
            val total = pluralStringResource(R.plurals.reader_chapters_count, count, count)
            val new = state.chapters.count { it.isNew && !it.read }
            Text(
                if (new > 0) stringResource(R.string.reader_chapters_count_new, total, new) else total,
                style = AsconType.Small,
                color = Muted
            )
        }
        // The site this chapter is read on. Switching sources needs their chapter addresses.
        Box(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Track)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(state.host, style = AsconType.Meta.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
        }
    }
}

@Composable
private fun GoToChapter(state: ReaderUiState, newestFirst: Boolean, onSort: () -> Unit, onOpen: (BigDecimal) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var missing by remember { mutableStateOf<BigDecimal?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Track)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(AsconIcons.ArrowRight, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                val label = stringResource(R.string.reader_chapters_go_to)
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) Text(label, style = FieldText, color = Muted)
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            missing = null
                        },
                        singleLine = true,
                        textStyle = FieldText.copy(color = Color.White),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(onGo = {
                            val number = text.trim().toBigDecimalOrNull() ?: return@KeyboardActions
                            if (chapterUrl(state.url, state.chapter, number) !=
                                null
                            ) {
                                onOpen(number)
                            } else {
                                missing = number
                            }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = label }
                    )
                }
            }
            val sort = stringResource(
                if (newestFirst) R.string.reader_chapters_newest_first else R.string.reader_chapters_oldest_first
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Track)
                    .clickable(role = Role.Button, onClick = onSort)
                    .semantics { contentDescription = sort },
                contentAlignment = Alignment.Center
            ) {
                Icon(AsconIcons.Sort, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        missing?.let {
            Text(
                stringResource(R.string.reader_chapters_not_found, it.toChapterLabel(), state.host),
                style = AsconType.Small,
                color = Muted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

private val FieldText = AsconType.Button.copy(fontWeight = FontWeight.Normal)
