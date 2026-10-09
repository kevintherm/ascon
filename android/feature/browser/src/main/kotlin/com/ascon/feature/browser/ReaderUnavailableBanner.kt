package com.ascon.feature.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconType

// Sizes from the BrowserFallback screen. The icon tile nests inside the padding: 24 = 10 + 14.
private val BannerRadius = 24.dp
private val BannerPadding = 14.dp
private val TileSize = 36.dp
private val TileRadius = 10.dp
private val TileFill = Color(0x1AFFFFFF)
private val BannerGlass = Color(0xDB14151A)

/** Slides in at the top of the page while [visible]. */
@Composable
internal fun ReaderUnavailableBanner(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 }
    ) {
        ReaderUnavailableBanner(onDismiss)
    }
}

/** Tells the user the chapter is read as the site shows it, and that progress still saves. */
@Composable
internal fun ReaderUnavailableBanner(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(BannerRadius)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BannerGlass)
            .border(1.dp, AsconColors.GlassBorder, shape)
            .padding(BannerPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .size(TileSize)
                .background(TileFill, RoundedCornerShape(TileRadius)),
            contentAlignment = Alignment.Center
        ) {
            Icon(AsconIcons.ReaderOff, null, Modifier.size(20.dp), tint = Color.White)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.reader_unavailable_title), style = AsconType.Button, color = Color.White)
            Text(
                stringResource(R.string.reader_unavailable_body),
                style = AsconType.Meta.copy(lineHeight = AsconType.Meta.fontSize * BODY_LINE_HEIGHT),
                color = AsconColors.OnDarkMuted
            )
        }
        Box(
            Modifier
                .size(TileSize)
                .clip(RoundedCornerShape(TileRadius))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                AsconIcons.Close,
                stringResource(R.string.reader_unavailable_dismiss),
                Modifier.size(18.dp),
                tint = AsconColors.OnDarkMuted
            )
        }
    }
}

private const val BODY_LINE_HEIGHT = 1.45f
