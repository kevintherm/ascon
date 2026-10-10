package com.ascon.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.designsystem.theme.AsconColors
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.Chapter
import com.ascon.core.model.Cover
import com.ascon.core.model.ReaderSettings
import com.github.takahirom.roborazzi.captureRoboImage
import java.math.BigDecimal
import java.time.LocalDate
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

    @Test
    fun readerSettings() = capture("reader_settings") {
        val state = PreviewReaderState.copy(
            seriesId = "aztec",
            settingsScope = SettingsScope.Series,
            settings = ReaderSettings(keepScreenOn = true)
        )
        Box {
            reader(state)
            ReaderSettingsSheet(
                visible = true,
                state = state,
                commands = ReaderSettingsCommands({
                }, {}),
                onDismiss = {}
            )
        }
    }

    @Test
    fun readerChapters() = capture("reader_chapters") {
        val today = LocalDate.of(2026, 10, 10)
        val chapters = listOf(
            Chapter(BigDecimal("8.5"), LocalDate.of(2026, 9, 10), read = true),
            Chapter(BigDecimal(9), LocalDate.of(2026, 9, 17), read = true),
            Chapter(BigDecimal(10), LocalDate.of(2026, 9, 24), read = true, downloaded = true),
            Chapter(BigDecimal(11), LocalDate.of(2026, 9, 27), read = true, readOnSourceId = "fire"),
            Chapter(BigDecimal(12), LocalDate.of(2026, 9, 30), read = false),
            Chapter(BigDecimal(13), LocalDate.of(2026, 10, 1), read = false, isNew = true),
            Chapter(BigDecimal(14), today, read = false, isNew = true)
        )
        val state = PreviewReaderState.copy(
            seriesId = "aztec",
            chapters = chapters,
            siteNames = mapOf("fire" to "mangafire"),
            today = today
        )
        Box {
            reader(state)
            ReaderChaptersSheet(visible = true, state = state, onOpen = {}, onDismiss = {})
        }
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
