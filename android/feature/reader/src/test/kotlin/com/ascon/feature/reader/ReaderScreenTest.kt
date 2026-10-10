package com.ascon.feature.reader

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ascon.core.designsystem.theme.AsconTheme
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w390dp-h844dp-xxhdpi")
class ReaderScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = ReaderUiState(
        url = "https://mangafire.to/read/aztec/chapter-13",
        title = "Aztec Turning of Heaven",
        chapter = BigDecimal(13),
        host = "mangafire.to",
        pages = (1..20).map { "https://cdn.example/$it.webp" },
        next = null,
        previous = "https://mangafire.to/read/aztec/chapter-12"
    )

    private fun show(state: ReaderUiState, commands: ReaderCommands = ReaderCommands()) = compose.setContent {
        AsconTheme { ReaderScreen(state, commands) { index, _, modifier -> PendingPage("${index + 1}", modifier) } }
    }

    @Test
    fun `shows the series, chapter, site and page`() {
        show(state.copy(page = 3))
        compose.onNodeWithText("Aztec Turning of Heaven").assertExists()
        compose.onNodeWithText("Chapter 13 · mangafire.to").assertExists()
        compose.onNodeWithText("3 / 20").assertExists()
    }

    @Test
    fun `chapter buttons follow the links the site has`() {
        var opened = ""
        show(state, ReaderCommands(onPrevious = { opened = "previous" }))
        compose.onNodeWithContentDescription("Next chapter").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous").assertIsEnabled().performClick()
        assertEquals("previous", opened)
    }

    @Test
    fun `a tap on the pages toggles the bars and the first page counts as shown`() {
        var toggles = 0
        val shown = mutableListOf<Int>()
        show(state, ReaderCommands(onToggleBars = { toggles++ }, onPageShown = { shown += it }))
        compose.onNodeWithTag(PAGES_TAG).performTouchInput { click(center) }
        compose.waitForIdle()
        assertEquals(1, toggles)
        assertEquals(listOf(0), shown)
    }

    @Test
    fun `hidden bars leave only the pages`() {
        show(state.copy(barsVisible = false))
        compose.onNodeWithText("Aztec Turning of Heaven").assertDoesNotExist()
    }
}
