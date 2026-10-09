package com.ascon.feature.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.Cover
import java.math.BigDecimal

// Paper tones of the stand-in page, as in the Browser screen.
private val PaperLight = Color(0xFFE8E4DC)
private val PaperMid = Color(0xFFCFC8BA)
private val PaperDark = Color(0xFFA99F8E)

/** Stands in for web content in previews and screenshot tests. */
@Composable
internal fun FakePage(modifier: Modifier = Modifier) {
    Column(modifier.background(PaperLight)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(AsconColors.BrowserGround)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(PaperMid)
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(PaperDark)
        )
    }
}

internal val PreviewCard = DetectionCard(
    url = "https://mangafire.to/read/aztec-turning-of-heaven/chapter-12",
    title = "Aztec Turning of Heaven",
    chapter = BigDecimal(12),
    seriesId = "aztec-turning-of-heaven",
    cover = Cover.Placeholder(0xFFE9A27A, 0xFFB33A3A, 0xFF2B1530),
    saved = true
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun BrowserPreview() {
    AsconTheme {
        BrowserScreen(
            state = BrowserUiState(url = PreviewCard.url, card = PreviewCard),
            commands = BrowserCommands(),
            page = { FakePage(it) }
        )
    }
}
