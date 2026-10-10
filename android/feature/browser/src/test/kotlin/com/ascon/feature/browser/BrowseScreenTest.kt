package com.ascon.feature.browser

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.data.fake.FakeLibrary
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.feature.browser.web.WebViewHealth
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class BrowseScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val opened = mutableListOf<String>()

    private fun show(page: OpenPage?, recent: List<RecentPage> = emptyList()) = compose.setContent {
        AsconTheme {
            BrowseScreen(
                FakeLibrary.sites,
                WebViewHealth.Ok,
                onOpen = { opened += it },
                bottomPadding = 0.dp,
                openPage = page,
                onReturnToPage = { calls += "return" },
                onClosePage = { calls += "close" },
                recent = recent,
                today = LocalDate.parse("2026-10-11"),
                zone = ZoneOffset.UTC
            )
        }
    }

    @Test
    fun `a kept page offers a way back to it and a way to close it`() {
        show(OpenPage("mangafire.to", "Aztec Turning of Heaven", BigDecimal(12), page = 34))
        compose.onNodeWithText("OPEN PAGE · MANGAFIRE.TO").assertExists()
        compose.onNodeWithText("Chapter 12 · p. 34").assertExists()
        compose.onNodeWithText("Return to page").performClick()
        compose.onNodeWithContentDescription("Close page").performClick()
        assertEquals(listOf("return", "close"), calls)
    }

    @Test
    fun `with no page kept there is no card`() {
        show(null)
        compose.onNodeWithText("Return to page").assertDoesNotExist()
    }

    @Test
    fun `sites open from the grid, and recently visited pages from their rows`() {
        val at = Instant.parse("2026-10-10T20:00:00Z")
        show(
            null,
            listOf(
                RecentPage("Salt & Iron Kitchen", BigDecimal(113), "https://mangadex.org/c/113", "MangaDex", "MD", at)
            )
        )
        compose.onNodeWithText("mangafire").performClick()
        compose.onNodeWithText("Salt & Iron Kitchen · Ch. 113").performClick()
        compose.onNodeWithText("MangaDex · Yesterday").assertExists()
        assertEquals(listOf("https://mangafire.to/", "https://mangadex.org/c/113"), opened)
    }

    @Test
    fun `the add tile focuses the address field`() {
        show(null)
        compose.onNodeWithContentDescription("Add site").performClick()
        compose.onNode(hasSetTextAction()).assertIsFocused()
    }
}
