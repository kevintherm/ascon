package com.ascon.feature.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.component.StatusBarIcons
import com.ascon.core.designsystem.component.StatusBarScrim
import com.ascon.core.designsystem.icon.AsconIcons
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.designsystem.theme.AsconType

/** Stands in for the browser until step 5 builds the WebView. */
@Composable
fun BrowseScreen(bottomPadding: Dp) {
    StatusBarIcons(darkIcons = true)
    Box(Modifier.fillMaxSize().background(AsconColors.Ground)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(
                start = 16.dp,
                end = 16.dp,
                top = 20.dp,
                bottom = bottomPadding
            )
        ) {
            Text(
                stringResource(R.string.browse_title),
                style = AsconType.ScreenTitle,
                modifier = Modifier.semantics { heading() }
            )
            Column(
                Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
            ) {
                Image(AsconIcons.Mark, contentDescription = null, modifier = Modifier.size(72.dp))
                Text(
                    stringResource(R.string.browse_placeholder_title),
                    style = AsconType.EmptyTitle,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    stringResource(R.string.browse_placeholder_body),
                    style = AsconType.Body,
                    color = AsconColors.TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
        StatusBarScrim(visible = true)
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BrowsePreview() {
    AsconTheme { BrowseScreen(bottomPadding = 120.dp) }
}
