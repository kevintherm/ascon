package com.ascon.feature.reader

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.model.PageGap
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Side taps and volume keys scroll this share of the screen. */
internal const val TURN_SCROLL = 0.8f

/**
 * The pages in one long strip, then the chapter end. Side taps scroll, a middle tap
 * toggles the bars, a double tap in the middle zooms 2x and pinch zooms up to 4x. Zoom lays the pages
 * out wider rather than scaling the drawing, so the list still scrolls to its end.
 */
@Composable
internal fun ReaderPages(
    state: ReaderUiState,
    list: LazyListState,
    commands: ReaderCommands,
    pageSizes: Map<Int, IntSize>,
    endSpace: Dp,
    onLongPress: (page: Int) -> Unit,
    page: @Composable (index: Int, url: String, modifier: Modifier) -> Unit
) {
    val scope = rememberCoroutineScope()
    var zoom by remember(state.url) { mutableStateOf(Zoom()) }
    var width by remember { mutableFloatStateOf(0f) }

    // Zooms around a point, scrolling the list so the point stays under it vertically too.
    fun zoomTo(next: Zoom, focus: Offset) {
        val factor = next.scale / zoom.scale
        zoom = next
        list.dispatchRawDelta((list.firstVisibleItemScrollOffset + focus.y) * (factor - 1))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { width = it.width.toFloat() }
            .pinchAndPan(
                zoomed = { zoom.zoomed },
                onPan = { delta -> zoom = zoom.panBy(delta.x, width) },
                onPinch = { factor, centroid, pan ->
                    zoomTo(zoom.zoomBy(factor, centroid.x, width).panBy(pan.x, width), centroid)
                    list.dispatchRawDelta(-pan.y)
                }
            )
            .pointerInput(state.url) {
                // Side taps scroll at once, so quick taps each scroll. A middle tap waits to
                // see whether a second one makes it a double tap, which zooms.
                var pending: Job? = null
                detectTapGestures(
                    onTap = { at ->
                        val step = list.layoutInfo.viewportSize.height * TURN_SCROLL
                        when (tapZone(at.x, size.width.toFloat())) {
                            TapZone.Back -> scope.launch { list.animateScrollBy(-step) }
                            TapZone.Forward -> scope.launch { list.animateScrollBy(step) }
                            TapZone.Middle -> if (pending?.isActive == true) {
                                pending?.cancel()
                                val target = zoom.toggled(at.x, width)
                                scope.launch {
                                    animate(zoom.scale, target.scale) { scale, _ ->
                                        zoomTo(zoom.zoomBy(scale / zoom.scale, at.x, width), at)
                                    }
                                }
                            } else {
                                pending = scope.launch {
                                    delay(viewConfiguration.doubleTapTimeoutMillis)
                                    commands.onToggleBars()
                                }
                            }
                        }
                    },
                    onLongPress = { at -> list.pageAt(at.y, state.pageCount)?.let(onLongPress) }
                )
            }
    ) {
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(bottom = endSpace),
            modifier = Modifier
                .zoomed { zoom }
                .testTag(PAGES_TAG)
        ) {
            itemsIndexed(state.pages, key = { index, _ -> index }) { index, url ->
                Column {
                    page(index, url, Modifier.fillMaxWidth())
                    if (index < state.pages.lastIndex) {
                        val gap = when (state.settings.gap) {
                            PageGap.Auto -> autoPageGap(pageSizes[index], pageSizes[index + 1])
                            PageGap.None -> 0.dp
                            PageGap.Small -> SmallPageGap
                        }
                        Spacer(Modifier.height(gap))
                    }
                }
            }
            item(key = END_KEY) {
                // The chapter end is drawn for a dark ground, whatever the page background,
                // and stays the screen's width when the pages are zoomed.
                Box(Modifier.onScreen { zoom }.background(AsconColors.ReaderGround)) {
                    ChapterEnd(state, commands.onNext, commands.onOpenSeries)
                }
            }
        }
    }
}

/** Lays the list out [Zoom.scale] times as wide as the screen, shifted left by the pan. */
private fun Modifier.zoomed(zoom: () -> Zoom): Modifier = fillMaxSize().layout { measurable, constraints ->
    val current = zoom()
    val wide = (constraints.maxWidth * current.scale).roundToInt()
    val placeable = measurable.measure(Constraints.fixed(wide, constraints.maxHeight))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-current.pan.roundToInt(), 0) }
}

/** Undoes [zoomed] for one item: the screen's width, where the screen is. */
private fun Modifier.onScreen(zoom: () -> Zoom): Modifier = layout { measurable, constraints ->
    val current = zoom()
    val screen = (constraints.maxWidth / current.scale).roundToInt()
    val placeable = measurable.measure(constraints.copy(minWidth = screen, maxWidth = screen))
    layout(constraints.maxWidth, placeable.height) { placeable.place(current.pan.roundToInt(), 0) }
}

/** The page at [y] on screen, or null over the chapter end. */
private fun LazyListState.pageAt(y: Float, pageCount: Int): Int? = layoutInfo.visibleItemsInfo
    .firstOrNull { y >= it.offset && y < it.offset + it.size }
    ?.index
    ?.takeIf { it < pageCount }

private const val END_KEY = "end"
