package com.ascon.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconType

@Composable
fun ProvideTextStyle(style: TextStyle, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.merge(style), content = content)
}

/** A filter chip: 40 tall, radius 12. Selected is ink, others surfaceMuted. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(AsconRadius.Chip))
            .background(if (selected) AsconColors.Ink else AsconColors.SurfaceMuted)
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = AsconType.ButtonSecondary,
            color = if (selected) Color.White else AsconColors.Ink,
            maxLines = 1
        )
    }
}

/** A section title with an optional text action on the right. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = AsconType.SectionTitle, modifier = Modifier.semantics { heading() })
        if (action != null) {
            Text(
                text = action,
                style = AsconType.ButtonSecondary,
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            )
        }
    }
}

/** Small caps label above a group, for example "READING FROM". */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = AsconColors.TextMuted) {
    Text(
        text = text.uppercase(),
        style = AsconType.EyebrowSection,
        color = color,
        modifier = modifier.padding(horizontal = 4.dp).semantics { heading() }
    )
}

/** A small round dot, for new-chapter and sync markers. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 7.dp) {
    Spacer(modifier.size(size).clip(CircleShape).background(color))
}

/** A thin progress bar. The fill uses the brand gradient unless told otherwise. */
@Composable
fun ProgressTrack(
    fraction: Float,
    modifier: Modifier = Modifier,
    track: Color = AsconColors.SurfaceSunken,
    fill: Brush = AsconColors.BrandGradient,
    height: Dp = 4.dp
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
    ) {
        Spacer(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(fill)
        )
    }
}

/** Solid fill for [ProgressTrack]. */
fun solidFill(color: Color): Brush = SolidColor(color)
