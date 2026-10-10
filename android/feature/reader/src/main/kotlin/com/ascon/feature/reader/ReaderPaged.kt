package com.ascon.feature.reader

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.model.PageFit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * One page per screen, turned by swiping or by the side thirds, then the chapter end.
 * [rightToLeft] mirrors the swipes and the side taps. Zoom is for the page on screen and
 * pans in both directions; the pages don't swipe while it is zoomed.
 */
@Composable
internal fun ReaderPaged(
    state: ReaderUiState,
    pager: PagerState,
    rightToLeft: Boolean,
    commands: ReaderCommands,
    endSpace: Dp,
    onLongPress: (page: Int) -> Unit,
    page: @Composable (index: Int, url: String, modifier: Modifier) -> Unit
) {
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableStateOf(Zoom()) }
    var size by remember { mutableStateOf(Size.Zero) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage.coerceAtMost((state.pageCount - 1).coerceAtLeast(0)) }
            .collect { commands.onPageShown(it) }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage >= state.pageCount - 1 }.distinctUntilChanged().filter { it }
            .collect { commands.onNearEnd() }
    }
    LaunchedEffect(pager.currentPage) { zoom = Zoom() }

    fun turn(by: Int) {
        scope.launch { pager.animateScrollToPage((pager.currentPage + by).coerceIn(0, pager.pageCount - 1)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it.toSize() }
            .pinchAndPan(
                zoomed = { zoom.zoomed },
                ownsPan = true,
                onPan = { delta -> zoom = zoom.panBy(delta.x, size.width, delta.y, size.height) },
                onPinch = { factor, centroid, pan ->
                    zoom = zoom.zoomBy(factor, centroid.x, size.width, centroid.y, size.height)
                        .panBy(pan.x, size.width, pan.y, size.height)
                }
            )
            .pointerInput(state.url, rightToLeft) {
                // As in long strip: side taps turn at once, a middle tap waits for a double tap.
                var pending: Job? = null
                detectTapGestures(
                    onTap = { at ->
                        val zone = tapZone(at.x, size.width)
                        when {
                            zone != TapZone.Middle -> turn(pageTurn(zone, rightToLeft))
                            pending?.isActive == true -> {
                                pending?.cancel()
                                scope.launch { animateZoom(at, zoom, size) { zoom = it } }
                            }
                            else -> pending = scope.launch {
                                delay(viewConfiguration.doubleTapTimeoutMillis)
                                commands.onToggleBars()
                            }
                        }
                    },
                    onLongPress = { pager.currentPage.takeIf { it < state.pageCount }?.let(onLongPress) }
                )
            }
    ) {
        HorizontalPager(
            state = pager,
            reverseLayout = rightToLeft,
            userScrollEnabled = !zoom.zoomed,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier
                .fillMaxSize()
                .testTag(PAGES_TAG)
        ) { index ->
            if (index < state.pageCount) {
                val current = index == pager.currentPage
                Box(
                    Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .graphicsLayer {
                            if (current) {
                                transformOrigin = TransformOrigin(0f, 0f)
                                scaleX = zoom.scale
                                scaleY = zoom.scale
                                translationX = -zoom.pan
                                translationY = -zoom.panY
                            }
                        }
                ) {
                    PagedPage(state.settings.fit) { page(index, state.pages[index], Modifier.fillMaxWidth()) }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(AsconColors.ReaderGround)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = endSpace),
                    contentAlignment = Alignment.Center
                ) {
                    ChapterEnd(state, commands.onNext, commands.onOpenSeries)
                }
            }
        }
    }
}

/** A page centered on the screen. Fit width lets a page taller than the screen scroll. */
@Composable
private fun PagedPage(fit: PageFit, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screen = maxHeight
        val scroll = if (fit == PageFit.Width) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Box(Modifier.fillMaxSize().then(scroll)) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = screen),
                contentAlignment = Alignment.Center
            ) { content() }
        }
    }
}

/** A double tap's zoom, animated: 2x around [at], or back to 1x. */
private suspend fun animateZoom(at: Offset, from: Zoom, size: Size, onZoom: (Zoom) -> Unit) {
    val target = from.toggled(at.x, size.width, at.y, size.height)
    var current = from
    animate(from.scale, target.scale) { scale, _ ->
        current = current.zoomBy(scale / current.scale, at.x, size.width, at.y, size.height)
        onZoom(current)
    }
}
