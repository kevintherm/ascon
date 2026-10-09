package com.ascon.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.Cover
import com.github.takahirom.roborazzi.captureRoboImage
import java.math.BigDecimal
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class Screenshots {
    @Test
    fun reader() = capture("reader") { reader(PreviewReaderState) }

    @Test
    fun readerLoadingAndLastChapter() = capture("reader_loading") {
        ReaderScreen(PreviewReaderState.copy(page = 1, next = null), ReaderCommands()) { index, _, modifier ->
            PendingPage((index + 1).toString(), modifier)
        }
    }

    @Test
    fun readerEnd() = capture("reader_end") { end(PreviewReaderState.copy(nextChapter = BigDecimal(13))) }

    @Test
    fun readerEndCaughtUp() = capture("reader_end_caught_up") {
        end(PreviewReaderState.copy(next = null, nextChapter = BigDecimal(13)))
    }

    @Composable
    private fun end(state: ReaderUiState) {
        val withSeries = state.copy(seriesId = "aztec", cover = Cover.Placeholder(0xFFE9A27A, 0xFFB33A3A, 0xFF2B1530))
        Box(Modifier.fillMaxSize().background(AsconColors.ReaderGround)) {
            ChapterEnd(withSeries, onNext = {}, onOpenSeries = {})
        }
    }

    @Composable
    private fun reader(state: ReaderUiState) {
        ReaderScreen(state, ReaderCommands()) { index, _, modifier -> FakeReaderPage(index, modifier) }
    }

    private fun capture(name: String, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") { AsconTheme { content() } }
}
