package com.ascon.app

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import com.ascon.core.designsystem.component.LocalScreenVisible

/** Frames to wait after a switch before the slide starts. See the comment where it is used. */
private const val SETTLE_FRAMES = 2

/** A hidden tab ignores touches and is skipped by screen readers, though it stays composed. */
private val HiddenTab = Modifier
    .clearAndSetSemantics { }
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

/**
 * Shows the selected tab and keeps every visited tab composed underneath it.
 *
 * Switching tabs then only animates position and alpha, so it stays smooth even when a
 * tab is expensive to build, and each tab keeps its scroll position and filters. A tab is
 * built the first time it is opened. Its slide starts after that first frame, so a slow
 * first build delays the motion instead of skipping it.
 *
 * [visible] is false while a detail screen covers the tabs.
 */
@Composable
fun TabHost(selected: Tab, visible: Boolean, modifier: Modifier = Modifier, content: @Composable (Tab) -> Unit) {
    val stateHolder = rememberSaveableStateHolder()
    var visitedMask by rememberSaveable { mutableIntStateOf(1 shl selected.ordinal) }
    visitedMask = visitedMask or (1 shl selected.ordinal)

    // Which way the last switch went: +1 toward a tab on the right, -1 toward the left.
    var previous by remember { mutableIntStateOf(selected.ordinal) }
    val direction = if (selected.ordinal >= previous) 1 else -1
    LaunchedEffect(selected) { previous = selected.ordinal }

    // Only the tab shown when the host first appears starts fully visible. A tab built
    // later starts hidden, so its first visit slides in like every other switch.
    val firstTab = remember { selected }

    Box(modifier.fillMaxSize()) {
        Tab.entries.filter { visitedMask and (1 shl it.ordinal) != 0 }.forEach { tab ->
            key(tab) {
                val isSelected = tab == selected
                val shown = remember { Animatable(if (isSelected && tab == firstTab) 1f else 0f) }
                LaunchedEffect(isSelected) {
                    // Wait until the frame that builds a new tab has been drawn. Animations count
                    // time from the frame they start in, so starting during a slow first build
                    // would let the clock run out before anything is shown.
                    repeat(SETTLE_FRAMES) { withFrameNanos { } }
                    if (isSelected) shown.animateTo(1f, TabMotion.enter) else shown.animateTo(0f, TabMotion.exit)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        // The incoming tab draws on top and takes the touches.
                        .zIndex(if (isSelected) 1f else 0f)
                        .graphicsLayer {
                            val p = shown.value
                            alpha = p
                            // Incoming arrives from the side it lies on; outgoing leaves the other way.
                            val sign = if (isSelected) direction else -direction
                            translationX = (1f - p) * size.width * TabMotion.SHIFT * sign
                        }
                        .then(if (isSelected) Modifier else HiddenTab)
                ) {
                    CompositionLocalProvider(LocalScreenVisible provides (isSelected && visible)) {
                        stateHolder.SaveableStateProvider(tab.name) { content(tab) }
                    }
                }
            }
        }
    }
}
