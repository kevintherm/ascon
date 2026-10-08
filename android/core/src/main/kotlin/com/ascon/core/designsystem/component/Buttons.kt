package com.ascon.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconSpacing
import com.ascon.core.designsystem.theme.AsconType

/** Colors for a pill button. */
data class PillColors(val container: Color, val content: Color, val border: BorderStroke? = null) {
    companion object {
        /** The one accent action on a screen. */
        val Primary = PillColors(AsconColors.Accent, Color.White)

        /** A strong action that is not the screen's primary one. */
        val Ink = PillColors(AsconColors.Ink, Color.White)
        val Surface = PillColors(AsconColors.Surface, AsconColors.Ink)
        val Outline = PillColors(AsconColors.Surface, AsconColors.Ink, BorderStroke(1.dp, AsconColors.Border))
    }
}

/**
 * A fully rounded button. The radius is always half the height, so it stays a pill at any
 * height. Inside a card, use a plain shape that follows the nesting rule instead.
 */
@Composable
fun PillButton(
    onClick: () -> Unit,
    colors: PillColors,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    textStyle: TextStyle = AsconType.ButtonLarge,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    spacing: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .height(height)
            .clip(CircleShape)
            .background(colors.container)
            .then(if (colors.border != null) Modifier.border(colors.border, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.content) {
            ProvideTextStyle(textStyle) { content() }
        }
    }
}

/** A pill button with a single label. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    colors: PillColors,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    textStyle: TextStyle = AsconType.ButtonLarge,
    icon: ImageVector? = null
) {
    PillButton(
        onClick = onClick,
        colors = colors,
        modifier = modifier,
        height = height,
        textStyle = textStyle,
        contentPadding = PaddingValues(start = if (icon != null) 16.dp else 20.dp, end = 20.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, maxLines = 1)
    }
}

/** A round icon-only button. [contentDescription] is required: there is no visible label. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = AsconSpacing.MinTouchTarget,
    iconSize: Dp = 20.dp,
    container: Color = AsconColors.Surface,
    content: Color = AsconColors.Ink
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = content, modifier = Modifier.size(iconSize))
    }
}
