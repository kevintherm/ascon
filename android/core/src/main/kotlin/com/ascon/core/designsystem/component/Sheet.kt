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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import kotlinx.coroutines.launch

/** A drag down past this share of the sheet's height, or a fling, closes it. */
private const val DISMISS_FRACTION = 0.3f
private const val DISMISS_VELOCITY = 1200f

/**
 * A sheet from design/tokens.md: white, top radius 28, a 40×5 grabber, 16 side padding,
 * over the scrim. A tap on the scrim, back, or dragging it down closes it.
 */
@Composable
fun BottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 16.dp,
    bottomPadding: Dp = 28.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    val drag = remember { Animatable(0f) }
    var height by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(visible) { if (visible) drag.snapTo(0f) }
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
            modifier = Modifier.align(Alignment.BottomCenter),
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
                        onDragStopped = { velocity ->
                            if (drag.value > height * DISMISS_FRACTION || velocity > DISMISS_VELOCITY) {
                                onDismiss()
                            } else {
                                drag.animateTo(0f)
                            }
                        }
                    )
                    .clip(RoundedCornerShape(topStart = AsconRadius.Sheet, topEnd = AsconRadius.Sheet))
                    .background(AsconColors.Surface)
                    // Taps inside the sheet must not reach the scrim.
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(spacing)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AsconColors.Border)
                )
                content()
            }
        }
    }
}
