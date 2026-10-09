package com.ascon.feature.reader

import androidx.compose.runtime.Composable
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.designsystem.theme.AsconTheme
import com.github.takahirom.roborazzi.captureRoboImage
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

    @Composable
    private fun reader(state: ReaderUiState) {
        ReaderScreen(state, ReaderCommands()) { index, _, modifier -> FakeReaderPage(index, modifier) }
    }

    private fun capture(name: String, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") { AsconTheme { content() } }
}
