package com.ascon.core.designsystem.component

import android.app.Activity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.ascon.core.designsystem.theme.AsconColors

/**
 * False for a screen that stays composed while hidden, such as a tab that is not
 * selected. Only the visible screen may change window-wide state like bar colors.
 */
val LocalScreenVisible = compositionLocalOf { true }

/**
 * Sets the status bar icon color for the screen on top. [darkIcons] for light grounds,
 * light icons over cover-tinted headers. Hidden screens leave the bar alone, and a screen
 * applies its choice again when it becomes visible.
 */
@Composable
fun StatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode || !LocalScreenVisible.current) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
    }
}

/**
 * A ground-colored band behind the status bar, so scrolled content does not run under
 * the clock. Fades in when [visible].
 */
@Composable
fun StatusBarScrim(visible: Boolean, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "scrim")
    Spacer(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .graphicsLayer { this.alpha = alpha }
            .background(AsconColors.Ground)
    )
}
