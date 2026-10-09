package com.ascon.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.ascon.core.designsystem.theme.AsconTheme
import java.math.BigDecimal

// Paper tones of the stand-in pages, as in the Reader screen.
private val PaperLight = Color(0xFFECE8E0)
private val PaperMid = Color(0xFFCFC8BA)
private val PaperDark = Color(0xFF9E9483)

/** Stands in for a page image in previews and screenshot tests. */
@Composable
internal fun FakeReaderPage(index: Int, modifier: Modifier = Modifier) {
    val colors = if (index % 2 == 0) listOf(PaperLight, PaperMid) else listOf(PaperMid, PaperDark)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1 / PENDING_PAGE_RATIO)
            .background(Brush.verticalGradient(colors))
    )
}

internal val PreviewReaderState = ReaderUiState(
    url = "https://mangaplus.example/viewer/aztec/12",
    title = "Aztec Turning of Heaven",
    chapter = BigDecimal(12),
    host = "mangaplus.example",
    pages = (1..58).map { "https://cdn.example/aztec/12/$it.webp" },
    page = 34,
    next = "https://mangaplus.example/viewer/aztec/13",
    previous = "https://mangaplus.example/viewer/aztec/11"
)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun ReaderPreview() {
    AsconTheme {
        ReaderScreen(PreviewReaderState, ReaderCommands()) { index, _, modifier -> FakeReaderPage(index, modifier) }
    }
}
