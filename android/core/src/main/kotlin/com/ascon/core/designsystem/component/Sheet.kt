package com.ascon.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import kotlinx.coroutines.launch

/** A drag down past this share of the sheet's height, or a fling, closes it. */
private const val DISMISS_FRACTION = 0.3f
private const val DISMISS_VELOCITY = 1200f

/**
 * A sheet from design/tokens.md: white unless [container] says otherwise, top radius 28,
 * a 40×5 grabber, 16 side padding, over the scrim. A tap on the scrim, back, or dragging it down closes it.
 * A sheet taller than the screen below the status bar scrolls under its grabber, and a drag
 * down at the top of its scroll moves the sheet.
 */
@Composable
fun BottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    bottomPadding: Dp = 28.dp,
    container: Color = AsconColors.Surface,
    grabber: Color = AsconColors.Border,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    val drag = remember { Animatable(0f) }
    var height by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) { if (visible) drag.snapTo(0f) }
    fun settle(velocity: Float) {
        if (drag.value > height * DISMISS_FRACTION || velocity > DISMISS_VELOCITY) {
            onDismiss()
        } else {
            scope.launch { drag.animateTo(0f) }
        }
    }
    val scroll = rememberScrollState()
    val dragOnScroll = remember {
        object : NestedScrollConnection {
            // Scrolling back up first returns a dragged sheet to its place.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0 || drag.value <= 0) return Offset.Zero
                val used = available.y.coerceAtLeast(-drag.value)
                scope.launch { drag.snapTo(drag.value + used) }
                return Offset(0f, used)
            }

            // A pull down that the content can't scroll drags the sheet.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0 || source != NestedScrollSource.UserInput) return Offset.Zero
                scope.launch { drag.snapTo(drag.value + available.y) }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drag.value <= 0) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AsconColors.Scrim)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter).statusBarsPadding(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { height = it.height }
                    .graphicsLayer { translationY = drag.value }
                    .draggable(
                        rememberDraggableState { delta ->
                            scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) }
                        },
                        Orientation.Vertical,
                        onDragStopped = { velocity -> settle(velocity) }
                    )
                    .nestedScroll(dragOnScroll)
                    .clip(RoundedCornerShape(topStart = AsconRadius.Sheet, topEnd = AsconRadius.Sheet))
                    .background(container)
                    // Taps inside the sheet must not reach the scrim. Not a click, so screen
                    // readers still see the sheet's own rows one by one.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .navigationBarsPadding()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(grabber)
                )
                // Scrolls only when it has to, so a short sheet drags from anywhere on it.
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scroll, enabled = scroll.canScrollForward || scroll.canScrollBackward)
                        .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    content = content
                )
            }
        }
    }
}
