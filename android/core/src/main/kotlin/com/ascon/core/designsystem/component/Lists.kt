package com.ascon.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconRadius
import com.ascon.core.designsystem.theme.AsconType

/** Side padding inside a grouped-list row. */
val RowInset = 16.dp

/**
 * A white card, radius 20, holding rows separated by [RowDivider].
 *
 * Rows fill the card edge to edge and pad their own content, so a pressed row lights up
 * to the card's edges and the card's corners clip the first and last rows.
 */
@Composable
fun GroupedCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AsconRadius.Card))
            .background(AsconColors.Surface)
            .padding(contentPadding),
        content = content
    )
}

/**
 * A hairline between rows, inset to line up with the rows' content. [start] can be larger
 * than [inset] to line up with text after a leading icon.
 */
@Composable
fun RowDivider(modifier: Modifier = Modifier, inset: Dp = RowInset, start: Dp = inset) {
    Spacer(
        modifier
            .padding(start = start, end = inset)
            .fillMaxWidth()
            .height(1.dp)
            .background(AsconColors.SurfaceSunken)
    )
}

/**
 * A grouped-list row with a title, an optional subtitle and a trailing value with a
 * chevron. Rows are at least 56 tall, 60 when they carry a subtitle.
 */
@Composable
fun ListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle != null) 60.dp else 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = RowInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RowLabels(title, subtitle, Modifier.weight(1f))
        if (value != null) Text(value, style = AsconType.Value, color = AsconColors.TextMuted)
        Icon(
            AsconIcons.ChevronRight,
            contentDescription = null,
            tint = AsconColors.TextSubtle,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** A grouped-list row that toggles a [Switch]. The whole row is the touch target. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle != null) 60.dp else 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = RowInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RowLabels(title, subtitle, Modifier.weight(1f).padding(vertical = 10.dp))
        Switch(checked)
    }
}

@Composable
private fun RowLabels(title: String, subtitle: String?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = AsconType.RowTitleRead)
        if (subtitle != null) Text(subtitle, style = AsconType.Small, color = AsconColors.TextMuted)
    }
}

private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val KnobInset = 4.dp
private val Knob = TrackHeight - KnobInset * 2

/**
 * The switch: a 52×32 track with radius 16 and a 24 knob inset 4, so the knob's radius
 * of 12 is the track's 16 minus the 4 gap. On is ink, off is border. Never the accent.
 * Display only; the row around it handles input and semantics.
 */
@Composable
fun Switch(checked: Boolean, modifier: Modifier = Modifier) {
    val track by animateColorAsState(if (checked) AsconColors.Ink else AsconColors.Border, label = "track")
    val knobOffset: Dp by animateDpAsState(if (checked) TrackWidth - Knob - KnobInset else KnobInset, label = "knob")
    Box(
        modifier
            .size(TrackWidth, TrackHeight)
            .clip(CircleShape)
            .background(track)
    ) {
        Spacer(
            Modifier
                .offset { IntOffset(knobOffset.roundToPx(), KnobInset.roundToPx()) }
                .size(Knob)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
