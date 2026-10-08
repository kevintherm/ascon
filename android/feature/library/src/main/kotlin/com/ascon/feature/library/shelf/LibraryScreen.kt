package com.ascon.feature.library.shelf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.designsystem.component.Chip
import com.ascon.core.designsystem.component.CircleIconButton
import com.ascon.core.designsystem.component.CoverArt
import com.ascon.core.designsystem.component.ProgressTrack
import com.ascon.core.designsystem.component.StatusBarIcons
import com.ascon.core.designsystem.component.StatusBarScrim
import com.ascon.core.designsystem.component.solidFill
import com.ascon.core.designsystem.component.statusChipLabel
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.ReadingStatus
import com.ascon.feature.library.R
import java.time.Clock

@Immutable
data class LibraryActions(
    val onOpenSeries: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onSort: () -> Unit = {},
    val onOpenSite: () -> Unit = {},
    val onImport: () -> Unit = {}
)

@Composable
fun LibraryRoute(viewModel: LibraryViewModel, actions: LibraryActions, bottomPadding: Dp) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LibraryScreen(state, viewModel::selectFilter, actions, bottomPadding)
}

private const val COLUMNS = 3

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onFilter: (ReadingStatus) -> Unit,
    actions: LibraryActions,
    bottomPadding: Dp
) {
    StatusBarIcons(darkIcons = true)
    Box(Modifier.fillMaxSize().background(AsconColors.Ground)) {
        if (state.libraryEmpty) {
            EmptyLibrary(actions.onOpenSite, actions.onImport, bottomPadding)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = bottomPadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                header(state, onFilter, actions)
                if (state.items.isEmpty() && !state.loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.library_filter_empty),
                            style = AsconType.Body,
                            color = AsconColors.TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 48.dp)
                        )
                    }
                }
                items(state.items, key = { it.seriesId }) { item ->
                    SeriesTile(item, onClick = { actions.onOpenSeries(item.seriesId) })
                }
            }
        }
        StatusBarScrim(visible = true)
    }
}

private fun LazyGridScope.header(state: LibraryUiState, onFilter: (ReadingStatus) -> Unit, actions: LibraryActions) {
    item(span = { GridItemSpan(maxLineSpan) }, contentType = "title") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.library_title),
                style = AsconType.ScreenTitle,
                modifier = Modifier.weight(1f).semantics { heading() }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleIconButton(AsconIcons.Search, stringResource(R.string.library_search), actions.onSearch)
                CircleIconButton(AsconIcons.Sort, stringResource(R.string.library_sort), actions.onSort)
            }
        }
    }
    item(span = { GridItemSpan(maxLineSpan) }, contentType = "chips") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.statusCounts.forEach { (status, count) ->
                Chip(statusChipLabel(status, count), selected = status == state.filter, onClick = { onFilter(status) })
            }
        }
    }
    if (state.loading) return
    item(span = { GridItemSpan(maxLineSpan) }, contentType = "meta") {
        Row(Modifier.padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.library_sorted_by_last_read),
                style = AsconType.Meta,
                color = AsconColors.TextMuted
            )
            Text(
                pluralStringResource(R.plurals.library_series_count, state.items.size, state.items.size),
                style = AsconType.Meta,
                color = AsconColors.TextMuted
            )
        }
    }
}

private val CoverShape = RoundedCornerShape(AsconRadius.CoverLarge)

/** Inset of the badge and progress bar from the cover's edges. */
private val CoverInset = 6.dp

@Composable
private fun SeriesTile(item: LibraryItem, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(AsconRadius.CoverLarge))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(ratio = 2f / 3f)) {
            CoverArt(item.cover, CoverShape, Modifier.matchParentSize())
            if (item.newCount > 0) {
                NewBadge(item.newCount, Modifier.align(Alignment.TopEnd).padding(CoverInset))
            }
            if (item.currentChapter != null) {
                ProgressTrack(
                    fraction = item.progress,
                    track = Color.White.copy(alpha = 0.3f),
                    fill = solidFill(Color.White),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(CoverInset)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = AsconType.GridTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (item.currentChapter != null && item.latestChapter != null) {
                    stringResource(R.string.library_chapter_of, item.currentChapter, item.latestChapter)
                } else {
                    stringResource(R.string.library_not_started)
                },
                style = AsconType.Small,
                color = AsconColors.TextMuted
            )
        }
    }
}

/** The badge sits 6 from the cover's corner, so its radius is the cover's 12 minus 6. */
private val BadgeShape = RoundedCornerShape(AsconRadius.nested(AsconRadius.CoverLarge, CoverInset))

@Composable
private fun NewBadge(count: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.library_new_badge, count, count)
    Box(
        modifier
            .height(22.dp)
            .widthIn(min = 22.dp)
            .clip(BadgeShape)
            .background(AsconColors.Accent)
            .padding(horizontal = 6.dp)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(count.toString(), style = AsconType.Badge, color = Color.White)
    }
}

internal fun previewLibraryState(
    filter: ReadingStatus = ReadingStatus.Reading,
    clock: Clock = Clock.systemDefaultZone()
) = libraryUiState(FakeLibrary.series(clock), filter)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LibraryPreview() {
    AsconTheme { LibraryScreen(previewLibraryState(), {}, LibraryActions(), bottomPadding = 120.dp) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun LibraryEmptyPreview() {
    AsconTheme { LibraryScreen(libraryUiState(emptyList(), ReadingStatus.Reading), {}, LibraryActions(), 120.dp) }
}
