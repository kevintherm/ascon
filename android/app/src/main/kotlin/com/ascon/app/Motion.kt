package com.ascon.app

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/*
 * Screen motion.
 * - Tabs slide a short way toward the tab that was picked, so the nav's left-to-right
 *   order reads as space. See TabHost.
 * - Detail screens such as a series slide in from the edge over the tabs, which drift left
 *   and dim, and reverse on back, including the predictive back gesture.
 */

// Material 3 emphasized easing: quick start and soft landing in, quick exit out.
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

private const val PUSH_MS = 380
private const val POP_MS = 320

/** How far the screen underneath drifts while a detail screen covers it, as a share of the width. */
private const val PARALLAX = 0.25f

/** How much the covered screen fades, so the one on top reads as closer. */
private const val COVERED_ALPHA = 0.5f

object TabMotion {
    /** How far a tab slides, as a share of the screen width. Enough to read as direction, not as a page turn. */
    const val SHIFT = 0.12f
    val enter: AnimationSpec<Float> = tween(durationMillis = 360, delayMillis = 40, easing = EmphasizedDecelerate)
    val exit: AnimationSpec<Float> = tween(durationMillis = 160, easing = EmphasizedAccelerate)
}

private typealias Spec = AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform

/** A detail screen slides in over the tabs. The tabs' own drift is in [coveredByDetail]. */
val pushTransition: Spec = {
    slideInHorizontally(tween(PUSH_MS, easing = Emphasized)) { it } togetherWith
        slideOutHorizontally(tween(PUSH_MS, easing = Emphasized)) { -(it * PARALLAX).toInt() }
}

/** The detail screen slides back out; the screen under it returns from its drift. */
val popTransition: Spec = {
    val transform = slideInHorizontally(tween(POP_MS, easing = Emphasized)) { -(it * PARALLAX).toInt() } togetherWith
        slideOutHorizontally(tween(POP_MS, easing = Emphasized)) { it }
    // The screen being revealed sits under the one leaving.
    transform.apply { targetContentZIndex = -1f }
}

/** The back gesture scrubs the same motion as the back button. */
val predictivePopTransition: AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = {
    popTransition()
}

/**
 * How visible the tabs are under the detail screens: 1 when nothing covers them, 0 when a
 * detail screen fully covers them. The root entry feeds it from its own transition, so the
 * tabs move in step with the slide and with the back gesture.
 */
@Stable
class CoverState {
    internal var source: State<Float>? by mutableStateOf(null)

    /** Last value seen, kept while the root entry is not composed. */
    internal var resting by mutableFloatStateOf(1f)

    val visibility: Float get() = source?.value ?: resting
}

/**
 * Content for the root entry. It draws nothing itself; it reports the entry's enter and
 * exit progress to [cover] so the tabs drawn under the back stack can follow it.
 */
@Composable
fun RootEntry(cover: CoverState) {
    val transition = LocalNavAnimatedContentScope.current.transition
    val progress = transition.animateFloat(
        transitionSpec = { tween(if (targetState == EnterExitState.Visible) POP_MS else PUSH_MS, easing = Emphasized) },
        label = "cover"
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    DisposableEffect(progress) {
        cover.source = progress
        onDispose {
            cover.resting = progress.value
            cover.source = null
        }
    }
}

/** Drifts and dims the tabs while a detail screen covers them. */
fun Modifier.coveredByDetail(cover: CoverState): Modifier = graphicsLayer {
    val p = cover.visibility
    translationX = -(1f - p) * size.width * PARALLAX
    alpha = COVERED_ALPHA + (1f - COVERED_ALPHA) * p
}
