package com.ascon.feature.browser

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.designsystem.theme.AsconTheme
import com.ascon.feature.browser.web.WebViewHealth
import java.math.BigDecimal
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

    private fun show(page: OpenPage?) = compose.setContent {
        AsconTheme {
            BrowseScreen(
                emptyList(),
                WebViewHealth.Ok,
                onOpen = {},
                bottomPadding = 0.dp,
                openPage = page,
                onReturnToPage = { calls += "return" },
                onClosePage = { calls += "close" }
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
}
