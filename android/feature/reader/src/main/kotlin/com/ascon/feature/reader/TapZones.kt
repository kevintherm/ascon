package com.ascon.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.theme.AsconType
import com.ascon.core.model.ReadingMode

private val SideShade = Color(0x4014151A)
private val LabelFill = Color(0xD114151A)

/**
 * Shades the side thirds and names what each third does, while the bars show and the
 * Show tap zones setting is on. It takes no touches, so taps reach the pages below.
 */
@Composable
internal fun TapZones(visible: Boolean, mode: ReadingMode) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        val (left, right) = when (mode) {
            ReadingMode.LongStrip -> R.string.reader_zone_scroll_up to R.string.reader_zone_scroll_down
            ReadingMode.LeftToRight -> R.string.reader_zone_previous to R.string.reader_zone_next
            ReadingMode.RightToLeft -> R.string.reader_zone_next to R.string.reader_zone_previous
        }
        Row(Modifier.fillMaxSize()) {
            Zone(stringResource(left), SideShade)
            Zone(stringResource(R.string.reader_zone_menu), Color.Transparent)
            Zone(stringResource(right), SideShade)
        }
    }
}

@Composable
private fun RowScope.Zone(label: String, shade: Color) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(shade),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = AsconType.Small,
            color = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .background(LabelFill)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
