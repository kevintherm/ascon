package com.ascon.feature.browser

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.ReaderChapter
import com.ascon.feature.browser.web.BlockedKind
import com.ascon.feature.browser.web.LoadErrorKind
import com.ascon.feature.browser.web.WebViewHealth
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class Screenshots {
    private val url = PreviewCard.url

    @Test
    fun browse() = capture("browse") {
        BrowseScreen(FakeLibrary.sites, WebViewHealth.Ok, onOpen = {}, bottomPadding = 120.dp)
    }

    @Test
    fun browseOutdatedWebView() = capture("browse_outdated_webview") {
        BrowseScreen(emptyList(), WebViewHealth.Outdated, onOpen = {}, bottomPadding = 120.dp)
    }

    @Test
    fun browserDetected() = capture("browser_detected") {
        browser(BrowserUiState(url = url, card = PreviewCard, blocked = 27))
    }

    @Test
    fun browserDetectedNotInLibrary() = capture("browser_detected_unknown") {
        val card = PreviewCard.copy(title = "Moonlit Ferry", seriesId = null, cover = null, saved = false)
        browser(BrowserUiState(url = url, loading = true, progress = 40, card = card))
    }

    @Test
    fun browserError() = capture("browser_error") {
        browser(
            BrowserUiState(url = "https://mangadex.org/", error = LoadError("mangadex.org", LoadErrorKind.Unreachable))
        )
    }

    @Test
    fun browserNotice() = capture("browser_notice") {
        browser(BrowserUiState(url = url, notice = Notice(BlockedKind.Redirect, "ads.example", 1)))
    }

    @Test
    fun browserReaderUnavailable() = capture("browser_reader_unavailable") {
        browser(BrowserUiState(url = url, card = PreviewCard.copy(page = 34, pageCount = 58), readerUnavailable = true))
    }

    @Test
    fun browserDocked() = capture("browser_docked") {
        val chapter =
            ReaderChapter(url, PreviewCard.title, PreviewCard.chapter, PreviewCard.seriesId, listOf("p1"), null, null)
        browser(BrowserUiState(url = url, card = PreviewCard, cardDocked = true, readerChapter = chapter, blocked = 27))
    }

    @Test
    fun browserScrolling() = capture("browser_scrolling") {
        browser(BrowserUiState(url = url, card = PreviewCard, cardDocked = true, barCollapsed = true, blocked = 27))
    }

    @Test
    fun browserMenu() = capture("browser_menu") {
        val chapter =
            ReaderChapter(url, PreviewCard.title, PreviewCard.chapter, PreviewCard.seriesId, listOf("p1"), null, null)
        val state = BrowserUiState(url = url, blocked = 27, readerChapter = chapter, canGoBack = true)
        Box {
            browser(state)
            BrowserMenu(visible = true, state = state, commands = BrowserCommands(), onDismiss = {})
        }
    }

    @Composable
    private fun browser(state: BrowserUiState) {
        BrowserScreen(state, BrowserCommands()) { FakePage(it) }
    }

    private fun capture(name: String, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") { AsconTheme { content() } }
}
