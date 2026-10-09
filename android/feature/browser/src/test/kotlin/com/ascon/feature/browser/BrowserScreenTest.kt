package com.ascon.feature.browser

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.core.model.ReaderChapter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class BrowserScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val url = PreviewCard.url
    private val calls = mutableListOf<String>()
    private val commands = BrowserCommands(
        onBack = { calls += "back" },
        onCloseBrowser = { calls += "back to ascon" },
        onEndSession = { calls += "end" },
        onOpenReader = { calls += "reader" }
    )

    private fun show(state: BrowserUiState) = compose.setContent {
        AsconTheme { BrowserScreen(state, commands) { FakePage(it) } }
    }

    @Test
    fun `back walks history while there is some`() {
        show(BrowserUiState(url = url, canGoBack = true))
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("back"), calls)
        compose.onNodeWithText("Close the browser?").assertDoesNotExist()
    }

    @Test
    fun `back on the first page asks before closing the browser`() {
        show(BrowserUiState(url = url))
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Close the browser?").assertExists()
        compose.onNodeWithText("Keep browsing").performClick()
        compose.onNodeWithText("Close the browser?").assertDoesNotExist()
        assertEquals(emptyList<String>(), calls)

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Close").performClick()
        assertEquals(listOf("end"), calls)
    }

    @Test
    fun `the menu leaves the browser with or without the page`() {
        show(BrowserUiState(url = url, canGoBack = true))
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithText("Back to Ascon").performClick()
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithContentDescription("Close browser and discard this page").performClick()

        assertEquals(listOf("back to ascon", "end"), calls)
    }

    @Test
    fun `the toolbar opens the reader on a chapter it can take`() {
        val chapter = ReaderChapter(url, "Aztec", null, null, listOf("p1"), null, null)
        show(BrowserUiState(url = url, card = PreviewCard, cardDocked = true, readerChapter = chapter))
        compose.onNodeWithText("Reader").performClick()

        assertEquals(listOf("reader"), calls)
    }

    @Test
    fun `a chapter read as the site shows it tracks its page instead`() {
        show(BrowserUiState(url = url, card = PreviewCard.copy(page = 34, pageCount = 58), readerUnavailable = true))

        compose.onNodeWithContentDescription("Tracking chapter 12, page 34").assertExists()
        compose.onNodeWithText("Reader").assertDoesNotExist()
        compose.onNodeWithText("Reader mode isn't available on this site. Your progress still saves.").assertExists()
    }
}
