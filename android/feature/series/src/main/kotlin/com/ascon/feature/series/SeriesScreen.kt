package com.ascon.feature.series

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.designsystem.component.CircleIconButton
import com.ascon.core.designsystem.component.CoverArt
import com.ascon.core.designsystem.component.CoverTintBackground
import com.ascon.core.designsystem.component.CoverTintLayout
import com.ascon.core.designsystem.component.Eyebrow
import com.ascon.core.designsystem.component.PillButton
import com.ascon.core.designsystem.component.PillColors
import com.ascon.core.designsystem.component.ProgressTrack
import com.ascon.core.designsystem.component.RowDivider
import com.ascon.core.designsystem.component.StatusBarIcons
import com.ascon.core.designsystem.component.StatusBarScrim
import com.ascon.core.designsystem.component.chapterLabel
import com.ascon.core.designsystem.component.coverTint
import com.ascon.core.designsystem.component.labelRes
import com.ascon.core.designsystem.component.overlapAbove
import com.ascon.core.designsystem.component.solidFill
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.designsystem.theme.AsconType
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Immutable
data class SeriesActions(
    val onBack: () -> Unit = {},
    val onMore: () -> Unit = {},
    val onPrimary: () -> Unit = {},
    val onDownload: () -> Unit = {},
    val onAlerts: () -> Unit = {},
    val onSelectSource: (String) -> Unit = {},
    val onToggleOrder: () -> Unit = {},
    val onOpenChapter: (String) -> Unit = {}
)

@Composable
fun SeriesRoute(viewModel: SeriesViewModel, actions: SeriesActions) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SeriesScreen(
        state,
        actions.copy(onSelectSource = viewModel::selectSource, onToggleOrder = viewModel::toggleOrder)
    )
}

/** How far the content sheet slides up over the header. */
private val SheetOverlap = 30.dp
private val SheetShape = RoundedCornerShape(topStart = AsconRadius.Sheet, topEnd = AsconRadius.Sheet)

@Composable
fun SeriesScreen(state: SeriesUiState, actions: SeriesActions) {
    val list = rememberLazyListState()
    var headerHeight by remember { mutableIntStateOf(0) }
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)
    val pastHeader by remember { derivedStateOf { list.isPast(headerHeight - statusBarHeight) } }
    StatusBarIcons(darkIcons = pastHeader || state.header == null)

    Box(Modifier.fillMaxSize().background(AsconColors.Ground)) {
        val header = state.header
        if (header == null) {
            if (state.notFound) NotFound(actions.onBack)
        } else {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom =
                    24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
            ) {
                item(contentType = "header") {
                    Header(header, actions, Modifier.onSizeChanged { headerHeight = it.height })
                }
                item(contentType = "actions") { Actions(state.primaryAction, actions) }
                if (state.sources.isNotEmpty()) {
                    item(contentType = "sources") { Sources(state.sources, actions.onSelectSource) }
                }
                item(contentType = "chaptersTitle") {
                    ChaptersTitle(state.chapters.size, state.newestFirst, actions.onToggleOrder)
                }
                itemsIndexed(state.chapters, key = { _, row ->
                    row.number
                }, contentType = { _, row -> row.state::class }) { index, row ->
                    ChapterListRow(
                        row = row,
                        today = state.today,
                        first = index == 0,
                        last = index == state.chapters.lastIndex,
                        onClick = { actions.onOpenChapter(row.number) }
                    )
                }
            }
        }
        StatusBarScrim(visible = pastHeader)
    }
}

private fun LazyListState.isPast(offset: Int): Boolean =
    offset > 0 && (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset >= offset)

@Composable
private fun Header(header: SeriesHeader, actions: SeriesActions, modifier: Modifier = Modifier) {
    val tint = remember(header.cover) { coverTint(header.cover) }
    CoverTintBackground(tint, modifier.fillMaxWidth(), layout = CoverTintLayout.Series) {
        Column(
            Modifier.statusBarsPadding().padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom =
                64.dp + SheetOverlap
            )
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CircleIconButton(
                    AsconIcons.Back,
                    stringResource(R.string.series_back),
                    actions.onBack,
                    iconSize = 22.dp,
                    container = Color.White.copy(alpha = 0.14f),
                    content = Color.White
                )
                CircleIconButton(
                    AsconIcons.More,
                    stringResource(R.string.series_more),
                    actions.onMore,
                    iconSize = 22.dp,
                    container = Color.White.copy(alpha = 0.14f),
                    content = Color.White
                )
            }
            Row(
                Modifier.padding(start = 4.dp, end = 4.dp, top = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val coverShape = RoundedCornerShape(AsconRadius.CoverLarge)
                CoverArt(
                    header.cover,
                    coverShape,
                    Modifier
                        .size(108.dp, 154.dp)
                        .dropShadow(
                            coverShape,
                            Shadow(radius = 36.dp, color = AsconColors.ShadowCover, offset = DpOffset(0.dp, 18.dp))
                        ),
                    toneAlpha = 0.25f
                )
                Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        header.title,
                        style = AsconType.HeaderTitleSmall,
                        color = Color.White,
                        modifier = Modifier.semantics {
                            heading()
                        }
                    )
                    if (header.altTitle != null) {
                        Text(
                            stringResource(R.string.series_also, header.altTitle),
                            style = AsconType.Meta,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (header.linkedToAniList) {
                            HeaderPill(stringResource(R.string.series_linked_anilist), checked = true)
                        }
                        HeaderPill(stringResource(header.status.labelRes))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderPill(text: String, checked: Boolean = false) {
    Row(
        Modifier
            .height(28.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (checked) Icon(AsconIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        Text(text, style = AsconType.CaptionStrong, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun Actions(action: PrimaryAction?, actions: SeriesActions) {
    // The top of the content sheet: it slides over the header with rounded corners.
    Row(
        Modifier
            .overlapAbove(SheetOverlap)
            .clip(SheetShape)
            .background(AsconColors.Ground)
            .padding(start = 16.dp, end = 16.dp, top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (action != null) {
            PillButton(
                onClick = actions.onPrimary,
                colors = PillColors.Primary,
                modifier = Modifier.weight(1f),
                spacing = 10.dp
            ) {
                Icon(AsconIcons.Play, null, modifier = Modifier.size(18.dp))
                Column {
                    when (action) {
                        is PrimaryAction.Continue -> {
                            Text(
                                stringResource(R.string.series_continue, action.chapter),
                                style = AsconType.ButtonLarge
                            )
                            Text(
                                stringResource(R.string.series_continue_page, action.page, action.pageCount),
                                style = AsconType.Caption,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                        }
                        is PrimaryAction.Start ->
                            Text(stringResource(R.string.series_start, action.chapter), style = AsconType.ButtonLarge)
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
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

private val SourceShape = RoundedCornerShape(AsconRadius.SourceCard)

@Composable
private fun Sources(sources: List<SourceCard>, onSelect: (String) -> Unit) {
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Eyebrow(stringResource(R.string.series_reading_from))
        sources.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { source -> SourceCardView(source, { onSelect(source.id) }, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SourceCardView(source: SourceCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val range = chapterLabel(source.chapters.first, source.chapters.second)
    Column(
        modifier
            .height(64.dp)
            .clip(SourceShape)
            .background(AsconColors.Surface)
            .border(BorderStroke(2.dp, if (source.selected) AsconColors.Ink else Color.Transparent), SourceShape)
            .selectable(selected = source.selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Text(source.name, style = AsconType.CardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (source.official) stringResource(R.string.series_source_official, range) else range,
            style = AsconType.Small,
            color = AsconColors.TextMuted,
            maxLines = 1
        )
    }
}

@Composable
private fun ChaptersTitle(count: Int, newestFirst: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            pluralStringResource(R.plurals.series_chapter_count, count, count),
            style = AsconType.SectionTitle,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            stringResource(if (newestFirst) R.string.series_newest_first else R.string.series_oldest_first),
            style = AsconType.ButtonSecondary,
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 4.dp, vertical = 8.dp)
        )
    }
}

/** One row of the chapter card. Rows draw their own slice of the card so the list stays lazy. */
@Composable
private fun ChapterListRow(row: ChapterRow, today: LocalDate, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val shape = cardSlice(first, last)
    val stateLabel = stateLabel(row.state)
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(AsconColors.Surface)
            .clickable(onClick = onClick)
            .semantics { if (stateLabel != null) stateDescription = stateLabel }
            .padding(start = 16.dp, end = 16.dp, top = if (first) 2.dp else 0.dp, bottom = if (last) 2.dp else 0.dp)
    ) {
        when (val state = row.state) {
            is ChapterState.InProgress -> InProgressRow(row.number, state)
            else -> Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Marker(state)
                val read = state == ChapterState.Read
                Text(
                    stringResource(R.string.series_chapter, row.number),
                    style = if (read) AsconType.RowTitleRead else AsconType.RowTitle,
                    color = if (read) AsconColors.TextMuted else AsconColors.Ink,
                    modifier = Modifier.weight(1f)
                )
                Trailing(row.trailing, today)
            }
        }
        if (!last) RowDivider()
    }
}

/** The part of the chapter card one row draws: rounded top on the first, bottom on the last. */
private fun cardSlice(first: Boolean, last: Boolean): RoundedCornerShape {
    val top = if (first) AsconRadius.Card else 0.dp
    val bottom = if (last) AsconRadius.Card else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Composable
private fun stateLabel(state: ChapterState): String? = when (state) {
    ChapterState.New -> stringResource(R.string.series_new)
    ChapterState.Read -> stringResource(R.string.series_read)
    else -> null
}

@Composable
private fun InProgressRow(number: String, state: ChapterState.InProgress) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(8.dp).border(2.dp, AsconColors.Ink, CircleShape))
            Text(
                stringResource(R.string.series_chapter, number),
                style = AsconType.RowTitle,
                modifier = Modifier.weight(1f)
            )
            Text(stringResource(R.string.series_page_of, state.page, state.pageCount), style = AsconType.MetaStrong)
        }
        ProgressTrack(state.fraction, Modifier.padding(start = 20.dp), fill = solidFill(AsconColors.Ink))
    }
}

@Composable
private fun Marker(state: ChapterState) {
    when (state) {
        ChapterState.New -> Spacer(Modifier.size(8.dp).clip(CircleShape).background(AsconColors.Accent))
        // The check is wider than the dot. It hangs 3 past each side of the 8 wide slot so
        // every title starts at the same x.
        ChapterState.Read -> Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
            Icon(
                AsconIcons.Check,
                contentDescription = null,
                tint = AsconColors.TextSubtle,
                modifier = Modifier.requiredSize(14.dp)
            )
        }
        else -> Spacer(Modifier.size(8.dp))
    }
}

private val MonthDay = DateTimeFormatter.ofPattern("MMM d")
private val MonthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy")

@Composable
private fun Trailing(trailing: ChapterTrailing, today: LocalDate) {
    val text = when (trailing) {
        is ChapterTrailing.Date -> when (trailing.date) {
            today -> stringResource(R.string.series_today)
            today.minusDays(1) -> stringResource(R.string.series_yesterday)
            else -> trailing.date.format(if (trailing.date.year == today.year) MonthDay else MonthDayYear)
        }
        is ChapterTrailing.ReadVia -> stringResource(R.string.series_via, trailing.sourceName)
        ChapterTrailing.Downloaded -> stringResource(R.string.series_downloaded)
        ChapterTrailing.None -> return
    }
    Text(text, style = AsconType.Meta, color = AsconColors.TextMuted)
}

@Composable
private fun NotFound(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        CircleIconButton(AsconIcons.Back, stringResource(R.string.series_back), onBack)
        Text(
            stringResource(R.string.series_not_found),
            style = AsconType.Body,
            color = AsconColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp)
        )
    }
}

internal fun previewSeriesState(clock: Clock = Clock.systemDefaultZone()) = seriesUiState(
    series = FakeLibrary.series(clock).first(),
    newestFirst = true,
    today = LocalDate.now(clock)
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SeriesPreview() {
    AsconTheme { SeriesScreen(previewSeriesState(), SeriesActions()) }
}
